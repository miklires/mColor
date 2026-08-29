package io.github.miklires.mcolor.service;

import io.github.miklires.mcolor.MColorPlugin;
import io.github.miklires.mcolor.api.ColorProfile;
import io.github.miklires.mcolor.api.MColorService;
import io.github.miklires.mcolor.color.ColorRenderer;
import io.github.miklires.mcolor.color.PlayerColor;
import io.github.miklires.mcolor.storage.ColorStorage;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class ColorService implements MColorService, AutoCloseable {
    private final MColorPlugin plugin;
    private final ColorStorage storage;
    private final Map<UUID, PlayerColor> colors = new ConcurrentHashMap<>();
    private final Map<UUID, Long> expirations = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> copySettings = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> revisions = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("mcolor-database", 0).factory());
    private final boolean defaultCopyAllowed;

    public ColorService(MColorPlugin plugin, ColorStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.defaultCopyAllowed = plugin.getConfig().getBoolean("privacy.copy-public-by-default", false);
    }

    public CompletableFuture<Void> load(UUID playerId) {
        if (storage == null) {
            loaded.add(playerId);
            copySettings.putIfAbsent(playerId, defaultCopyAllowed());
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            Optional<ColorStorage.StoredColor> stored = storage.load(playerId);
            if (stored.isPresent() && !stored.get().expired(System.currentTimeMillis())) {
                colors.put(playerId, stored.get().color());
                setExpiration(playerId, stored.get().expiresAt());
            } else {
                colors.remove(playerId);
                expirations.remove(playerId);
                if (stored.isPresent()) storage.delete(playerId);
            }
            copySettings.put(playerId, storage.copyAllowed(playerId, defaultCopyAllowed()));
            revisions.computeIfAbsent(playerId, ignored -> new AtomicLong()).incrementAndGet();
            loaded.add(playerId);
        }, databaseExecutor).exceptionally(error -> {
            loaded.add(playerId);
            copySettings.putIfAbsent(playerId, defaultCopyAllowed());
            plugin.getLogger().warning("Could not load color for " + playerId + ": " + root(error).getMessage());
            return null;
        });
    }

    public CompletableFuture<Boolean> set(Player player, PlayerColor color) { return set(player, color, 0); }

    public CompletableFuture<Boolean> set(Player player, PlayerColor color, long expiresAt) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        PlayerColor previous = colors.put(playerId, color);
        long previousExpiry = expirations.getOrDefault(playerId, 0L);
        setExpiration(playerId, expiresAt);
        loaded.add(playerId);
        long revision = revision(playerId);
        apply(player);
        if (storage == null) return CompletableFuture.completedFuture(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                storage.save(playerId, color, expiresAt);
                return true;
            } catch (RuntimeException exception) {
                rollback(player, playerName, previous, previousExpiry, revision, exception);
                return false;
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Boolean> reset(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        PlayerColor previous = colors.remove(playerId);
        long previousExpiry = expirations.getOrDefault(playerId, 0L);
        expirations.remove(playerId);
        loaded.add(playerId);
        long revision = revision(playerId);
        apply(player);
        if (storage == null) return CompletableFuture.completedFuture(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                storage.delete(playerId);
                return true;
            } catch (RuntimeException exception) {
                rollback(player, playerName, previous, previousExpiry, revision, exception);
                return false;
            }
        }, databaseExecutor);
    }

    public CompletableFuture<List<ColorStorage.HistoryEntry>> history(UUID playerId, int limit) {
        if (storage == null) return CompletableFuture.completedFuture(List.of());
        return CompletableFuture.supplyAsync(() -> storage.history(playerId, limit), databaseExecutor);
    }

    public CompletableFuture<Boolean> undo(Player player) {
        if (storage == null) return CompletableFuture.completedFuture(false);
        UUID playerId = player.getUniqueId();
        long revision = revision(playerId);
        return CompletableFuture.supplyAsync(() -> storage.undo(playerId), databaseExecutor).thenApply(result -> {
            if (!result.found() || currentRevision(playerId) != revision) return false;
            if (result.restored() == null || result.restored().expired(System.currentTimeMillis())) {
                colors.remove(playerId);
                expirations.remove(playerId);
            } else {
                colors.put(playerId, result.restored().color());
                setExpiration(playerId, result.restored().expiresAt());
            }
            apply(player);
            return true;
        });
    }

    public boolean copyAllowed(UUID playerId) { return copySettings.getOrDefault(playerId, defaultCopyAllowed()); }

    public CompletableFuture<Boolean> setCopyAllowed(Player player, boolean allowed) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        boolean previous = copyAllowed(playerId);
        copySettings.put(playerId, allowed);
        if (storage == null) return CompletableFuture.completedFuture(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                storage.setCopyAllowed(playerId, allowed);
                return true;
            } catch (RuntimeException exception) {
                copySettings.put(playerId, previous);
                plugin.getLogger().warning("Could not persist privacy for " + playerName + ": " + exception.getMessage());
                plugin.scheduler().player(player, () -> plugin.messages().send(player, "storage-error"));
                return false;
            }
        }, databaseExecutor);
    }

    public Optional<PlayerColor> playerColor(UUID playerId) {
        expireIfNeeded(playerId);
        return Optional.ofNullable(colors.get(playerId));
    }

    public long expiresAt(UUID playerId) { expireIfNeeded(playerId); return expirations.getOrDefault(playerId, 0L); }
    public boolean loaded(UUID playerId) { return loaded.contains(playerId); }
    public void expireDue() { List.copyOf(expirations.keySet()).forEach(this::expireIfNeeded); }

    public void apply(Player player) {
        plugin.scheduler().player(player, () -> {
            PlayerColor color = playerColor(player.getUniqueId()).orElse(null);
            var component = ColorRenderer.component(color, player.getName());
            if (plugin.getConfig().getBoolean("apply.display-name", true)) player.displayName(component);
            if (plugin.getConfig().getBoolean("apply.player-list-name", true)) player.playerListName(component);
        });
    }

    public void invalidateAndReload(UUID playerId) {
        load(playerId).thenRun(() -> plugin.scheduler().global(() -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) apply(player);
        }));
    }

    private void expireIfNeeded(UUID playerId) {
        long expiresAt = expirations.getOrDefault(playerId, 0L);
        if (expiresAt == 0 || expiresAt > System.currentTimeMillis() || !expirations.remove(playerId, expiresAt)) return;
        colors.remove(playerId);
        revision(playerId);
        if (storage != null) CompletableFuture.runAsync(() -> {
            try { storage.delete(playerId); }
            catch (RuntimeException exception) { plugin.getLogger().warning("Could not expire color for " + playerId + ": " + exception.getMessage()); }
        }, databaseExecutor);
        plugin.scheduler().global(() -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) apply(player);
        });
    }

    private void rollback(Player player, String playerName, PlayerColor previous, long previousExpiry, long revision, RuntimeException exception) {
        if (currentRevision(player.getUniqueId()) != revision) return;
        if (previous == null) colors.remove(player.getUniqueId()); else colors.put(player.getUniqueId(), previous);
        setExpiration(player.getUniqueId(), previousExpiry);
        plugin.getLogger().warning("Could not persist color for " + playerName + ": " + exception.getMessage());
        apply(player);
        plugin.scheduler().player(player, () -> plugin.messages().send(player, "storage-error"));
    }

    private void setExpiration(UUID playerId, long expiresAt) {
        if (expiresAt > 0) expirations.put(playerId, expiresAt); else expirations.remove(playerId);
    }

    private long revision(UUID playerId) { return revisions.computeIfAbsent(playerId, ignored -> new AtomicLong()).incrementAndGet(); }
    private long currentRevision(UUID playerId) { return revisions.computeIfAbsent(playerId, ignored -> new AtomicLong()).get(); }
    private boolean defaultCopyAllowed() { return defaultCopyAllowed; }
    private static Throwable root(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error; }

    @Override public Optional<ColorProfile> color(UUID playerId) { return playerColor(playerId).map(PlayerColor::apiProfile); }
    @Override public String renderMiniMessage(UUID playerId, String text) { return ColorRenderer.miniMessage(playerColor(playerId).orElse(null), text); }
    @Override public String renderLegacy(UUID playerId, String text) { return ColorRenderer.legacy(playerColor(playerId).orElse(null), text); }
    @Override public void close() { databaseExecutor.close(); }
}
