package io.github.miklires.mcolor.command;

import io.github.miklires.mcolor.MColorPlugin;
import io.github.miklires.mcolor.color.ColorParser;
import io.github.miklires.mcolor.color.ColorRenderer;
import io.github.miklires.mcolor.color.PlayerColor;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ColorCommand implements BasicCommand {
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
            case "reset", "off" -> change(player, null);
            case "rainbow" -> {
                if (!player.hasPermission("mcolor.rainbow")) plugin.messages().send(player, "no-permission");
                else change(player, PlayerColor.rainbow());
            }
            case "gradient" -> gradient(player, List.of(args).subList(1, args.length));
            case "preview" -> preview(player, List.of(args).subList(1, args.length));
            default -> solid(player, args[0]);
        }
    }

    private void solid(Player player, String input) {
        String namedKey = input.toLowerCase(Locale.ROOT);
        String color = ColorParser.color(input, plugin.namedColors()).orElse(null);
        if (color == null) { plugin.messages().send(player, "invalid-color"); return; }
        if (ColorParser.hex(input).isPresent() && !player.hasPermission("mcolor.hex")) {
            plugin.messages().send(player, "no-permission"); return;
        }
        if (ColorParser.hex(input).isEmpty() && !player.hasPermission("mcolor.color." + namedKey)) {
            plugin.messages().send(player, "no-permission"); return;
        }
        change(player, PlayerColor.solid(color));
    }

    private void gradient(Player player, List<String> inputs) {
        if (!player.hasPermission("mcolor.gradient")) { plugin.messages().send(player, "no-permission"); return; }
        if (inputs.size() < 2 || inputs.size() > plugin.getConfig().getInt("limits.gradient-colors", 8)) {
            plugin.messages().send(player, "gradient-usage"); return;
        }
        List<String> colors = inputs.stream().map(input -> ColorParser.color(input, plugin.namedColors()).orElse(null)).toList();
        if (colors.contains(null)) { plugin.messages().send(player, "invalid-color"); return; }
        if (inputs.stream().anyMatch(input -> !canUse(player, input))) { plugin.messages().send(player, "no-permission"); return; }
        change(player, PlayerColor.gradient(colors));
    }

    private void preview(Player player, List<String> inputs) {
        PlayerColor color;
        if (inputs.isEmpty()) color = plugin.service().playerColor(player.getUniqueId()).orElse(null);
        else if (inputs.getFirst().equalsIgnoreCase("rainbow")) color = PlayerColor.rainbow();
        else {
            int offset = inputs.getFirst().equalsIgnoreCase("gradient") ? 1 : 0;
            List<String> requested = inputs.subList(offset, inputs.size());
            List<String> parsed = requested.stream()
                    .map(input -> ColorParser.color(input, plugin.namedColors()).orElse(null)).toList();
            if (parsed.isEmpty() || parsed.contains(null) || parsed.size() > plugin.getConfig().getInt("limits.gradient-colors", 8)) {
                plugin.messages().send(player, "invalid-color"); return;
            }
            color = parsed.size() == 1 ? PlayerColor.solid(parsed.getFirst()) : PlayerColor.gradient(parsed);
        }
        player.sendMessage(ColorRenderer.component(color, player.getName()));
    }

    private boolean canUse(Player player, String input) {
        if (ColorParser.hex(input).isPresent()) return player.hasPermission("mcolor.hex");
        return player.hasPermission("mcolor.color." + input.toLowerCase(Locale.ROOT));
    }

    private void change(Player player, PlayerColor color) {
        var future = color == null ? plugin.service().reset(player) : plugin.service().set(player, color);
        future.thenAccept(success -> {
            if (!success) return;
            plugin.scheduler().player(player, () -> plugin.messages().send(player, color == null ? "reset" : "updated"));
        });
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        if (!(source.getSender() instanceof Player player)) return List.of();
        List<String> values = args.length <= 1
                ? List.of("gui", "preview", "gradient", "rainbow", "reset")
                : plugin.namedColors().keySet().stream().toList();
        String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(input)).toList();
    }
}
