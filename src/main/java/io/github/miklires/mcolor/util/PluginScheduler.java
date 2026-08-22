package io.github.miklires.mcolor.util;

import io.github.miklires.mcolor.MColorPlugin;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public final class PluginScheduler {
    private final MColorPlugin plugin;

    public PluginScheduler(MColorPlugin plugin) { this.plugin = plugin; }
    public void global(Runnable task) { plugin.getServer().getGlobalRegionScheduler().execute(plugin, task); }
    public void player(Player player, Runnable task) { player.getScheduler().execute(plugin, task, null, 1L); }
    public void async(Runnable task) { plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> task.run()); }
    public void asyncTimer(Runnable task, long delay, long period, TimeUnit unit) {
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, ignored -> task.run(), delay, period, unit);
    }
}
