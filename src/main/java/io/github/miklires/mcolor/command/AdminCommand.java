package io.github.miklires.mcolor.command;

import io.github.miklires.mcolor.MColorPlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class AdminCommand implements BasicCommand {
    private final MColorPlugin plugin;
    public AdminCommand(MColorPlugin plugin) { this.plugin = plugin; }

    @Override public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        if (!source.getSender().hasPermission("mcolor.admin")) { plugin.messages().send(source.getSender(), "no-permission"); return; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadSettings();
            plugin.messages().send(source.getSender(), "reloaded");
        } else plugin.messages().send(source.getSender(), "admin-usage");
    }

    @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        return source.getSender().hasPermission("mcolor.admin") ? List.of("reload") : List.of();
    }
}
