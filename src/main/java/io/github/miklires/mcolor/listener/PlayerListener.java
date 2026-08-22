package io.github.miklires.mcolor.listener;

import io.github.miklires.mcolor.MColorPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerListener implements Listener {
    private final MColorPlugin plugin;
    public PlayerListener(MColorPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        plugin.service().load(event.getPlayer().getUniqueId()).thenRun(() -> plugin.service().apply(event.getPlayer()));
    }
}
