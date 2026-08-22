package io.github.miklires.mcolor;

import io.github.miklires.mcolor.api.MColorService;
import io.github.miklires.mcolor.command.AdminCommand;
import io.github.miklires.mcolor.command.ColorCommand;
import io.github.miklires.mcolor.gui.ColorMenu;
import io.github.miklires.mcolor.integration.MColorExpansion;
import io.github.miklires.mcolor.listener.PlayerListener;
import io.github.miklires.mcolor.message.MessageService;
import io.github.miklires.mcolor.service.ColorService;
import io.github.miklires.mcolor.storage.ColorStorage;
import io.github.miklires.mcolor.util.PluginScheduler;
import io.github.miklires.mcolor.update.UpdateChecker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class MColorPlugin extends JavaPlugin {
    private final Map<String, String> namedColors = new LinkedHashMap<>();
    private final Map<String, List<String>> presets = new LinkedHashMap<>();
    private PluginScheduler scheduler;
    private MessageService messages;
    private ColorStorage storage;
    private ColorService service;
    private ColorMenu gui;
    private MColorExpansion expansion;
    private volatile boolean stopping;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        saveLanguage("en_US");
        saveLanguage("ru_RU");
        scheduler = new PluginScheduler(this);
        messages = new MessageService(this);
        reloadPalettes();
        gui = new ColorMenu(this);
        getServer().getPluginManager().registerEvents(gui, this);
        registerCommands();
        if (getConfig().getBoolean("metrics.enabled", true)) new Metrics(this, getConfig().getInt("metrics.bstats-id", 33355));
        scheduler.async(this::initialize);
        getLogger().info("mColor " + getPluginMeta().getVersion() + " is starting");
    }

    private void initialize() {
        ColorStorage connectedStorage = null;
        try {
            connectedStorage = new ColorStorage(this);
            connectedStorage.start();
            getLogger().info("Storage connection established");
        } catch (RuntimeException exception) {
            if (connectedStorage != null) connectedStorage.close();
            connectedStorage = null;
            getLogger().warning("Storage is unavailable; colors will remain in memory: " + exception.getMessage());
        }
        if (stopping) { if (connectedStorage != null) connectedStorage.close(); return; }
        storage = connectedStorage;
        service = new ColorService(this, connectedStorage);
        scheduler.global(this::finishInitialization);
    }

    private void finishInitialization() {
        if (stopping || !isEnabled()) return;
        getServer().getServicesManager().register(MColorService.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new MColorExpansion(this);
            expansion.register();
        }
        getServer().getOnlinePlayers().forEach(player -> service.load(player.getUniqueId()).thenRun(() -> service.apply(player)));
        startSync();
        new UpdateChecker(this).start();
        getLogger().info("mColor is ready" + (storage == null ? " in memory-only mode" : ""));
    }

    private void startSync() {
        if (storage == null || !getConfig().getBoolean("sync.enabled", false)) return;
        long interval = Math.max(2, getConfig().getLong("sync.poll-seconds", 5));
        final long[] lastPoll = {System.currentTimeMillis()};
        scheduler.asyncTimer(() -> {
            long since = lastPoll[0];
            lastPoll[0] = System.currentTimeMillis();
            try { storage.changesSince(since).forEach(service::invalidateAndReload); }
            catch (RuntimeException exception) { getLogger().warning("Color sync failed: " + exception.getMessage()); }
        }, interval, interval, TimeUnit.SECONDS);
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("color", List.of("colour"), new ColorCommand(this));
            event.registrar().register("mcolor", new AdminCommand(this));
        });
    }

    public void reloadSettings() {
        reloadConfig();
        reloadPalettes();
        messages.reload();
    }

    private void reloadPalettes() {
        namedColors.clear();
        ConfigurationSection colors = getConfig().getConfigurationSection("named-colors");
        if (colors != null) colors.getKeys(false).forEach(key -> {
            String value = colors.getString(key);
            if (value != null) io.github.miklires.mcolor.color.ColorParser.hex(value)
                    .ifPresent(hex -> namedColors.put(key.toLowerCase(), hex));
        });
        presets.clear();
        ConfigurationSection gradients = getConfig().getConfigurationSection("gradient-presets");
        if (gradients != null) gradients.getKeys(false).forEach(key -> {
            List<String> values = gradients.getStringList(key).stream()
                    .map(io.github.miklires.mcolor.color.ColorParser::hex).flatMap(java.util.Optional::stream).toList();
            if (values.size() >= 2) presets.put(key, values);
        });
    }

    private void saveLanguage(String locale) {
        java.io.File file = new java.io.File(getDataFolder(), "lang/" + locale + ".yml");
        if (!file.exists()) saveResource("lang/" + locale + ".yml", false);
    }

    @Override
    public void onDisable() {
        stopping = true;
        if (expansion != null) expansion.unregister();
        getServer().getServicesManager().unregisterAll(this);
        if (service != null) service.close();
        if (storage != null) storage.close();
    }

    public PluginScheduler scheduler() { return scheduler; }
    public MessageService messages() { return messages; }
    public ColorService service() { return service; }
    public ColorMenu gui() { return gui; }
    public Map<String, String> namedColors() { return Map.copyOf(namedColors); }
    public Map<String, List<String>> presets() { return Map.copyOf(presets); }
}
