package io.github.miklires.mcolor.gui;

import io.github.miklires.mcolor.MColorPlugin;
import io.github.miklires.mcolor.color.ColorRenderer;
import io.github.miklires.mcolor.color.PlayerColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class ColorMenu implements Listener {
    private final MColorPlugin plugin;
    public ColorMenu(MColorPlugin plugin) { this.plugin = plugin; }
    public void open(Player player) { open(player, Page.BASIC); }

    private void open(Player player, Page page) {
        MenuHolder holder = new MenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().component(player, page.titleKey));
        holder.inventory = inventory;
        int slot = 0;
        if (page == Page.BASIC) {
            for (var entry : plugin.namedColors().entrySet()) {
                if (slot >= 45) break;
                PlayerColor color = PlayerColor.solid(entry.getValue());
                add(holder, slot++, Material.PAPER, Component.text(entry.getKey()), color,
                        Set.of("mcolor.color." + entry.getKey()), ColorRenderer.component(color, player.getName()));
            }
        } else if (page == Page.GRADIENTS) {
            List<Map.Entry<String, String>> colors = plugin.namedColors().entrySet().stream().toList();
            for (int index = 0; index + 1 < colors.size() && slot < 45; index++) {
                var left = colors.get(index);
                var right = colors.get(index + 1);
                PlayerColor color = PlayerColor.gradient(List.of(left.getValue(), right.getValue()));
                add(holder, slot++, Material.FIREWORK_STAR,
                        plugin.messages().component(player, "gui.gradient", Map.of("index", Integer.toString(index + 1))),
                        color, Set.of("mcolor.gradient", "mcolor.color." + left.getKey(), "mcolor.color." + right.getKey()),
                        ColorRenderer.component(color, player.getName()));
            }
        } else {
            for (var entry : plugin.presets().entrySet()) {
                if (slot >= 45) break;
                PlayerColor color = PlayerColor.gradient(entry.getValue());
                add(holder, slot++, Material.NETHER_STAR, Component.text(entry.getKey()), color,
                        Set.of("mcolor.preset." + entry.getKey()), ColorRenderer.component(color, player.getName()));
            }
        }
        addPage(holder, 45, Material.RED_DYE, plugin.messages().component(player, "gui.basic"), Page.BASIC);
        addPage(holder, 46, Material.FIREWORK_STAR, plugin.messages().component(player, "gui.gradients"), Page.GRADIENTS);
        addPage(holder, 47, Material.BOOK, plugin.messages().component(player, "gui.presets"), Page.PRESETS);
        add(holder, 49, Material.NETHER_STAR, plugin.messages().component(player, "gui.rainbow"),
                PlayerColor.rainbow(), Set.of("mcolor.rainbow"), ColorRenderer.component(PlayerColor.rainbow(), player.getName()));
        add(holder, 53, Material.BARRIER, plugin.messages().component(player, "gui.reset"),
                null, Set.of(), plugin.messages().component(player, "gui.reset-lore"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Page targetPage = holder.pages.get(event.getRawSlot());
        if (targetPage != null) { open(player, targetPage); return; }
        MenuAction action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        if (!action.permissions().stream().allMatch(permission -> permitted(player, permission))) {
            plugin.messages().send(player, "no-permission"); return;
        }
        player.closeInventory();
        var future = action.color() == null ? plugin.service().reset(player) : plugin.service().set(player, action.color());
        future.thenAccept(success -> { if (success) plugin.scheduler().player(player,
                () -> plugin.messages().send(player, action.color() == null ? "reset" : "updated")); });
    }

    private static boolean permitted(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        if (permission.startsWith("mcolor.color.")) return player.hasPermission("mcolor.color.*");
        if (permission.startsWith("mcolor.preset.")) return player.hasPermission("mcolor.preset.*");
        return false;
    }

    private static void add(MenuHolder holder, int slot, Material material, Component name, PlayerColor color,
                            Set<String> permissions, Component preview) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(name);
        meta.lore(List.of(preview));
        item.setItemMeta(meta);
        holder.inventory.setItem(slot, item);
        holder.actions.put(slot, new MenuAction(color, Set.copyOf(permissions)));
    }

    private static void addPage(MenuHolder holder, int slot, Material material, Component name, Page page) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(name);
        item.setItemMeta(meta);
        holder.inventory.setItem(slot, item);
        holder.pages.put(slot, page);
    }

    private static final class MenuHolder implements InventoryHolder {
        private final Map<Integer, MenuAction> actions = new HashMap<>();
        private final Map<Integer, Page> pages = new HashMap<>();
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private record MenuAction(PlayerColor color, Set<String> permissions) { }
    private enum Page {
        BASIC("gui.title-basic"), GRADIENTS("gui.title-gradients"), PRESETS("gui.title-presets");
        private final String titleKey;
        Page(String titleKey) { this.titleKey = titleKey; }
    }
}
