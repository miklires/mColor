package io.github.miklires.mcolor.service;

import io.github.miklires.mcolor.MColorPlugin;
import io.github.miklires.mcolor.api.ColorProfile;
import io.github.miklires.mcolor.api.MColorService;
import io.github.miklires.mcolor.color.ColorRenderer;
import io.github.miklires.mcolor.color.PlayerColor;
import io.github.miklires.mcolor.storage.ColorStorage;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ColorService implements MColorService, AutoCloseable {
    private final MColorPlugin plugin;
    private final ColorStorage storage;
    private final Map<UUID, PlayerColor> colors = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final ExecutorService databaseExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ColorService(MColorPlugin plugin, ColorStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public CompletableFuture<Void> load(UUID playerId) {
        if (storage == null) { loaded.add(playerId); return CompletableFuture.completedFuture(null); }
        return CompletableFuture.runAsync(() -> {
            storage.load(playerId).ifPresentOrElse(color -> colors.put(playerId, color), () -> colors.remove(playerId));
            loaded.add(playerId);
        }, databaseExecutor);
    }

    public CompletableFuture<Boolean> set(Player player, PlayerColor color) {
        UUID playerId = player.getUniqueId();
        PlayerColor previous = colors.put(playerId, color);
        loaded.add(playerId);
        apply(player);
        if (storage == null) return CompletableFuture.completedFuture(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                storage.save(playerId, color);
                return true;
            } catch (RuntimeException exception) {
                rollback(player, previous, exception);
                return false;
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Boolean> reset(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerColor previous = colors.remove(playerId);
        loaded.add(playerId);
        apply(player);
        if (storage == null) return CompletableFuture.completedFuture(true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                storage.delete(playerId);
                return true;
            } catch (RuntimeException exception) {
                rollback(player, previous, exception);
                return false;
            }
        }, databaseExecutor);
    }

    public Optional<PlayerColor> playerColor(UUID playerId) { return Optional.ofNullable(colors.get(playerId)); }
    public boolean loaded(UUID playerId) { return loaded.contains(playerId); }

    public void apply(Player player) {
        plugin.scheduler().player(player, () -> {
            var component = ColorRenderer.component(colors.get(player.getUniqueId()), player.getName());
            if (plugin.getConfig().getBoolean("apply.display-name", true)) player.displayName(component);
            if (plugin.getConfig().getBoolean("apply.player-list-name", true)) player.playerListName(component);
        });
    }

    public void invalidateAndReload(UUID playerId) {
        load(playerId).thenRun(() -> plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> player.getUniqueId().equals(playerId)).findFirst().ifPresent(this::apply));
    }

    private void rollback(Player player, PlayerColor previous, RuntimeException exception) {
        if (previous == null) colors.remove(player.getUniqueId()); else colors.put(player.getUniqueId(), previous);
        plugin.getLogger().warning("Could not persist color for " + player.getName() + ": " + exception.getMessage());
        apply(player);
        plugin.scheduler().player(player, () -> plugin.messages().send(player, "storage-error"));
    }

    @Override public Optional<ColorProfile> color(UUID playerId) { return playerColor(playerId).map(PlayerColor::apiProfile); }
    @Override public String renderMiniMessage(UUID playerId, String text) { return ColorRenderer.miniMessage(colors.get(playerId), text); }
    @Override public String renderLegacy(UUID playerId, String text) { return ColorRenderer.legacy(colors.get(playerId), text); }

    @Override public void close() { databaseExecutor.close(); }
}
