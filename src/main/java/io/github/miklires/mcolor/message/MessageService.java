package io.github.miklires.mcolor.message;

import io.github.miklires.mcolor.MColorPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MessageService {
    private final MColorPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration english;
    private YamlConfiguration russian;

    public MessageService(MColorPlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        english = load("en_US");
        russian = load("ru_RU");
    }

    private YamlConfiguration load(String locale) {
        YamlConfiguration custom = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/" + locale + ".yml"));
        try (var stream = plugin.getResource("lang/" + locale + ".yml")) {
            if (stream != null) {
                YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                bundled.getKeys(true).stream().filter(key -> !bundled.isConfigurationSection(key))
                        .filter(key -> !custom.contains(key)).forEach(key -> custom.set(key, bundled.get(key)));
            }
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Could not load bundled " + locale + " messages: " + exception.getMessage());
        }
        return custom;
    }

    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }

    public void send(CommandSender sender, String key, Map<String, String> values) {
        Component prefix = miniMessage.deserialize(language(sender).getString("prefix", ""));
        sender.sendMessage(prefix.append(component(sender, key, values)));
    }

    public Component component(CommandSender sender, String key) { return component(sender, key, Map.of()); }

    public Component component(CommandSender sender, String key, Map<String, String> values) {
        String value = language(sender).getString(key, english.getString(key, key));
        TagResolver[] placeholders = values.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        return miniMessage.deserialize(value, placeholders);
    }

    private YamlConfiguration language(CommandSender sender) {
        if (plugin.getConfig().getBoolean("language.per-player", true) && sender instanceof Player player
                && player.locale().getLanguage().equalsIgnoreCase("ru")) return russian;
        return plugin.getConfig().getString("language.default", "en_US").equalsIgnoreCase("ru_RU") ? russian : english;
    }
}
