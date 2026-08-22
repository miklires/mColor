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
import java.util.Map;

public final class MessageService {
    private final MColorPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration english;
    private YamlConfiguration russian;

    public MessageService(MColorPlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        english = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/en_US.yml"));
        russian = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/ru_RU.yml"));
    }

    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }

    public void send(CommandSender sender, String key, Map<String, String> values) {
        String value = language(sender).getString(key, english.getString(key, key));
        TagResolver[] placeholders = values.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        Component prefix = miniMessage.deserialize(language(sender).getString("prefix", ""));
        sender.sendMessage(prefix.append(miniMessage.deserialize(value, placeholders)));
    }

    private YamlConfiguration language(CommandSender sender) {
        if (plugin.getConfig().getBoolean("language.per-player", true) && sender instanceof Player player
                && player.locale().getLanguage().equalsIgnoreCase("ru")) return russian;
        return plugin.getConfig().getString("language.default", "en_US").equalsIgnoreCase("ru_RU") ? russian : english;
    }
}
