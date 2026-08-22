package io.github.miklires.mcolor.integration;

import io.github.miklires.mcolor.MColorPlugin;
import io.github.miklires.mcolor.color.ColorRenderer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class MColorExpansion extends PlaceholderExpansion {
    private final MColorPlugin plugin;
    public MColorExpansion(MColorPlugin plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "mcolor"; }
    @Override public @NotNull String getAuthor() { return "miklires"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        OfflinePlayer target = player;
        if (params.toLowerCase().startsWith("of_")) {
            Player online = plugin.getServer().getPlayerExact(params.substring(3));
            if (online == null) return "";
            target = online;
            params = "name";
        }
        var color = plugin.service().playerColor(target.getUniqueId()).orElse(null);
        String name = target.getName() == null ? "" : target.getName();
        return switch (params.toLowerCase()) {
            case "name" -> ColorRenderer.legacy(color, name);
            case "name_mm" -> ColorRenderer.miniMessage(color, name);
            case "name_stripped" -> name;
            case "color" -> color == null || color.colors().isEmpty() ? "" : color.colors().getFirst();
            case "color2" -> color == null || color.colors().size() < 2 ? "" : color.colors().get(1);
            case "gradient" -> color == null ? "" : String.join(",", color.renderColors());
            case "has_color" -> color == null ? "no" : "yes";
            default -> null;
        };
    }
}
