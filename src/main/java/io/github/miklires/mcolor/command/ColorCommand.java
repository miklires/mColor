package io.github.miklires.mcolor.command;

import io.github.miklires.mcolor.MColorPlugin;
import io.github.miklires.mcolor.color.*;
import io.github.miklires.mcolor.storage.ColorStorage;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ColorCommand implements BasicCommand {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final MColorPlugin plugin;

    public ColorCommand(MColorPlugin plugin) { this.plugin = plugin; }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) { plugin.messages().send(sender, "player-only"); return; }
        if (!player.hasPermission("mcolor.use")) { plugin.messages().send(player, "no-permission"); return; }
        if (plugin.service() == null) { plugin.messages().send(player, "loading"); return; }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) { plugin.gui().open(player); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reset", "off" -> change(player, null, 0);
            case "rainbow" -> changeRequested(player, List.of("rainbow"), 0);
            case "gradient" -> changeRequested(player, List.of(args), 0);
            case "preset" -> changeRequested(player, List.of(args), 0);
            case "preview" -> preview(player, List.of(args).subList(1, args.length));
            case "temporary", "temp" -> temporary(player, List.of(args));
            case "copy" -> copy(player, args);
            case "privacy" -> privacy(player, args);
            case "history" -> history(player);
            case "undo" -> undo(player);
            default -> changeRequested(player, List.of(args[0]), 0);
        }
    }

    private void temporary(Player player, List<String> args) {
        if (!player.hasPermission("mcolor.temporary")) { plugin.messages().send(player, "no-permission"); return; }
        if (args.size() < 3) { plugin.messages().send(player, "temporary-usage"); return; }
        Duration maximum = Duration.ofDays(Math.clamp(plugin.getConfig().getLong("limits.maximum-temporary-days", 365), 1, 3650));
        Optional<Duration> duration = DurationParser.parse(args.get(1), maximum);
        if (duration.isEmpty()) { plugin.messages().send(player, "invalid-duration"); return; }
        PlayerColor requested = requested(player, args.subList(2, args.size()), true);
        if (requested != null) change(player, requested, System.currentTimeMillis() + duration.get().toMillis());
    }

    private void copy(Player player, String[] args) {
        if (!player.hasPermission("mcolor.copy")) { plugin.messages().send(player, "no-permission"); return; }
        if (args.length != 2) { plugin.messages().send(player, "copy-usage"); return; }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null || target.getUniqueId().equals(player.getUniqueId())) { plugin.messages().send(player, "player-not-found"); return; }
        if (!plugin.service().copyAllowed(target.getUniqueId()) && !player.hasPermission("mcolor.copy.bypass")) {
            plugin.messages().send(player, "copy-private"); return;
        }
        PlayerColor color = plugin.service().playerColor(target.getUniqueId()).orElse(null);
        if (color == null) { plugin.messages().send(player, "copy-no-color"); return; }
        change(player, color, plugin.service().expiresAt(target.getUniqueId()));
    }

    private void privacy(Player player, String[] args) {
        if (args.length != 2 || !Set.of("public", "private").contains(args[1].toLowerCase(Locale.ROOT))) {
            plugin.messages().send(player, "privacy-usage"); return;
        }
        boolean allowed = args[1].equalsIgnoreCase("public");
        plugin.service().setCopyAllowed(player, allowed).thenAccept(success -> {
            if (success) plugin.scheduler().player(player, () -> plugin.messages().send(player,
                    allowed ? "privacy-public" : "privacy-private"));
        });
    }

    private void history(Player player) {
        if (!player.hasPermission("mcolor.history")) { plugin.messages().send(player, "no-permission"); return; }
        int limit = Math.clamp(plugin.getConfig().getInt("history.display-limit", 10), 1, 25);
        plugin.service().history(player.getUniqueId(), limit).thenAccept(entries -> plugin.scheduler().player(player, () -> {
            if (entries.isEmpty()) { plugin.messages().send(player, "history-empty"); return; }
            plugin.messages().send(player, "history-header");
            int index = 1;
            for (ColorStorage.HistoryEntry entry : entries) {
                String colors = entry.color() == null ? "reset" : entry.color().kind() + " " + String.join(" → ", entry.color().renderColors());
                String expiry = entry.expiresAt() == 0 ? "permanent" : DATE.format(Instant.ofEpochMilli(entry.expiresAt()));
                plugin.messages().send(player, "history-entry", Map.of("index", Integer.toString(index++), "color", colors,
                        "changed", DATE.format(Instant.ofEpochMilli(entry.changedAt())), "expires", expiry));
            }
        }));
    }

    private void undo(Player player) {
        if (!player.hasPermission("mcolor.history")) { plugin.messages().send(player, "no-permission"); return; }
        plugin.service().undo(player).thenAccept(success -> plugin.scheduler().player(player,
                () -> plugin.messages().send(player, success ? "undo-success" : "history-empty")));
    }

    private void preview(Player player, List<String> inputs) {
        PlayerColor color = inputs.isEmpty() ? plugin.service().playerColor(player.getUniqueId()).orElse(null)
                : requested(player, inputs, false);
        if (!inputs.isEmpty() && color == null) return;
        player.sendMessage(ColorRenderer.component(color, player.getName()));
    }

    private void changeRequested(Player player, List<String> inputs, long expiresAt) {
        PlayerColor color = requested(player, inputs, true);
        if (color != null) change(player, color, expiresAt);
    }

    private PlayerColor requested(Player player, List<String> inputs, boolean enforcePermissions) {
        if (inputs.isEmpty()) { plugin.messages().send(player, "invalid-color"); return null; }
        String mode = inputs.getFirst().toLowerCase(Locale.ROOT);
        if (mode.equals("rainbow")) {
            if (inputs.size() != 1) { plugin.messages().send(player, "invalid-color"); return null; }
            if (enforcePermissions && !player.hasPermission("mcolor.rainbow")) { plugin.messages().send(player, "no-permission"); return null; }
            return PlayerColor.rainbow();
        }
        if (mode.equals("preset")) {
            if (inputs.size() != 2) { plugin.messages().send(player, "preset-usage"); return null; }
            String name = inputs.get(1).toLowerCase(Locale.ROOT);
            List<String> colors = plugin.presets().get(name);
            if (colors == null) { plugin.messages().send(player, "invalid-color"); return null; }
            if (enforcePermissions && !player.hasPermission("mcolor.preset.*") && !player.hasPermission("mcolor.preset." + name)) {
                plugin.messages().send(player, "no-permission"); return null;
            }
            return PlayerColor.gradient(colors);
        }
        List<String> requested = mode.equals("gradient") ? inputs.subList(1, inputs.size()) : inputs;
        int maximum = Math.clamp(plugin.getConfig().getInt("limits.gradient-colors", 8), 2, 16);
        if (requested.isEmpty() || requested.size() > maximum || mode.equals("gradient") && requested.size() < 2) {
            plugin.messages().send(player, mode.equals("gradient") ? "gradient-usage" : "invalid-color"); return null;
        }
        if (requested.size() > 1 && enforcePermissions && !player.hasPermission("mcolor.gradient")) {
            plugin.messages().send(player, "no-permission"); return null;
        }
        List<String> parsed = requested.stream().map(input -> ColorParser.color(input, plugin.namedColors()).orElse(null)).toList();
        if (parsed.contains(null)) { plugin.messages().send(player, "invalid-color"); return null; }
        if (enforcePermissions && requested.stream().anyMatch(input -> !canUse(player, input))) {
            plugin.messages().send(player, "no-permission"); return null;
        }
        return parsed.size() == 1 ? PlayerColor.solid(parsed.getFirst()) : PlayerColor.gradient(parsed);
    }

    private boolean canUse(Player player, String input) {
        if (ColorParser.hex(input).isPresent()) return player.hasPermission("mcolor.hex");
        return player.hasPermission("mcolor.color.*") || player.hasPermission("mcolor.color." + input.toLowerCase(Locale.ROOT));
    }

    private void change(Player player, PlayerColor color, long expiresAt) {
        var future = color == null ? plugin.service().reset(player) : plugin.service().set(player, color, expiresAt);
        future.thenAccept(success -> {
            if (success) plugin.scheduler().player(player, () -> plugin.messages().send(player, color == null ? "reset" : "updated"));
        });
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        if (!(source.getSender() instanceof Player player)) return List.of();
        List<String> values;
        if (args.length <= 1) {
            values = new ArrayList<>(List.of("gui", "preview", "gradient", "preset", "rainbow", "temporary", "copy", "privacy", "history", "undo", "reset"));
            values.addAll(plugin.namedColors().keySet());
        } else if (args[0].equalsIgnoreCase("preset")) values = new ArrayList<>(plugin.presets().keySet());
        else if (args[0].equalsIgnoreCase("privacy")) values = new ArrayList<>(List.of("public", "private"));
        else if (args[0].equalsIgnoreCase("copy")) values = plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        else if (args[0].equalsIgnoreCase("temporary") && args.length == 2) values = new ArrayList<>(List.of("1h", "1d", "7d", "30d"));
        else values = new ArrayList<>(plugin.namedColors().keySet());
        String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input)).distinct().toList();
    }
}
