package io.github.miklires.mcolor.command;

import io.github.miklires.mcolor.MColorPlugin;
import io.github.miklires.mcolor.color.*;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;

public final class AdminCommand implements BasicCommand {
    private final MColorPlugin plugin;
    public AdminCommand(MColorPlugin plugin) { this.plugin = plugin; }

    @Override public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission("mcolor.admin")) { plugin.messages().send(sender, "no-permission"); return; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadSettings();
            plugin.messages().send(sender, "reloaded");
            return;
        }
        if (args.length < 2) { plugin.messages().send(sender, "admin-usage"); return; }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) { plugin.messages().send(sender, "player-not-found"); return; }
        String targetName = target.getName();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reset" -> plugin.service().reset(target).thenAccept(success -> respond(sender,
                    () -> plugin.messages().send(sender, success ? "admin-reset" : "storage-error", Map.of("player", targetName))));
            case "info" -> info(sender, target);
            case "set" -> {
                PlayerColor color = parse(Arrays.asList(args).subList(2, args.length));
                if (color == null) { plugin.messages().send(sender, "invalid-color"); return; }
                update(sender, target, targetName, color, 0);
            }
            case "temporary" -> {
                if (args.length < 4) { plugin.messages().send(sender, "admin-usage"); return; }
                Duration maximum = Duration.ofDays(Math.clamp(plugin.getConfig().getLong("limits.maximum-temporary-days", 365), 1, 3650));
                Optional<Duration> duration = DurationParser.parse(args[2], maximum);
                PlayerColor color = parse(Arrays.asList(args).subList(3, args.length));
                if (duration.isEmpty()) { plugin.messages().send(sender, "invalid-duration"); return; }
                if (color == null) { plugin.messages().send(sender, "invalid-color"); return; }
                update(sender, target, targetName, color, System.currentTimeMillis() + duration.get().toMillis());
            }
            default -> plugin.messages().send(sender, "admin-usage");
        }
    }

    private void update(CommandSender sender, Player target, String targetName, PlayerColor color, long expiresAt) {
        plugin.service().set(target, color, expiresAt).thenAccept(success -> respond(sender,
                () -> plugin.messages().send(sender, success ? "admin-updated" : "storage-error", Map.of("player", targetName))));
    }

    private void info(CommandSender sender, Player target) {
        PlayerColor color = plugin.service().playerColor(target.getUniqueId()).orElse(null);
        String value = color == null ? "none" : color.kind() + " " + String.join(" → ", color.renderColors());
        long expiresAt = plugin.service().expiresAt(target.getUniqueId());
        plugin.messages().send(sender, "admin-info", Map.of("player", target.getName(), "color", value,
                "privacy", plugin.service().copyAllowed(target.getUniqueId()) ? "public" : "private",
                "expires", expiresAt == 0 ? "permanent" : Long.toString(expiresAt)));
    }

    private PlayerColor parse(List<String> values) {
        if (values.isEmpty()) return null;
        String mode = values.getFirst().toLowerCase(Locale.ROOT);
        if (mode.equals("rainbow") && values.size() == 1) return PlayerColor.rainbow();
        if (mode.equals("preset") && values.size() == 2) {
            List<String> preset = plugin.presets().get(values.get(1).toLowerCase(Locale.ROOT));
            return preset == null ? null : PlayerColor.gradient(preset);
        }
        List<String> requested = mode.equals("gradient") ? values.subList(1, values.size()) : values;
        if (requested.isEmpty() || requested.size() > Math.clamp(plugin.getConfig().getInt("limits.gradient-colors", 8), 2, 16)) return null;
        List<String> colors = requested.stream().map(value -> ColorParser.color(value, plugin.namedColors()).orElse(null)).toList();
        if (colors.contains(null)) return null;
        return colors.size() == 1 ? PlayerColor.solid(colors.getFirst()) : PlayerColor.gradient(colors);
    }

    private void respond(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) plugin.scheduler().player(player, task); else plugin.scheduler().global(task);
    }

    @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        if (!source.getSender().hasPermission("mcolor.admin")) return List.of();
        if (args.length <= 1) return filter(List.of("reload", "set", "temporary", "reset", "info"), args);
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) return filter(
                plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args);
        if (args[0].equalsIgnoreCase("temporary") && args.length == 3) return filter(List.of("1h", "1d", "7d", "30d"), args);
        List<String> values = new ArrayList<>(List.of("gradient", "preset", "rainbow"));
        values.addAll(plugin.namedColors().keySet());
        return filter(values, args);
    }

    private static List<String> filter(Collection<String> values, String[] args) {
        String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input)).toList();
    }
}
