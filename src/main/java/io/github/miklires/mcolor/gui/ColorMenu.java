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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ColorMenu implements Listener {
    private final MColorPlugin plugin;

    public ColorMenu(MColorPlugin plugin) { this.plugin = plugin; }

    public void open(Player player) { open(player, Page.BASIC); }

    private void open(Player player, Page page) {
        MenuHolder holder = new MenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("mColor - " + page.title));
        holder.inventory = inventory;
        int slot = 0;
        if (page == Page.BASIC) {
            for (var entry : plugin.namedColors().entrySet()) {
                if (slot >= 45) break;
                PlayerColor color = PlayerColor.solid(entry.getValue());
                add(holder, slot++, Material.PAPER, entry.getKey(), color, ColorRenderer.component(color, player.getName()));
            }
        } else if (page == Page.GRADIENTS) {
            List<String> colors = plugin.namedColors().values().stream().toList();
            for (int index = 0; index + 1 < colors.size() && slot < 45; index++) {
                PlayerColor color = PlayerColor.gradient(List.of(colors.get(index), colors.get(index + 1)));
                add(holder, slot++, Material.FIREWORK_STAR, "Gradient " + (index + 1), color,
                        ColorRenderer.component(color, player.getName()));
            }
        } else {
            for (var entry : plugin.presets().entrySet()) {
                if (slot >= 45) break;
                PlayerColor color = PlayerColor.gradient(entry.getValue());
                add(holder, slot++, Material.NETHER_STAR, entry.getKey(), color, ColorRenderer.component(color, player.getName()));
            }
        }
        addPage(holder, 45, Material.RED_DYE, "Basic", Page.BASIC);
        addPage(holder, 46, Material.FIREWORK_STAR, "Gradients", Page.GRADIENTS);
        addPage(holder, 47, Material.BOOK, "Presets", Page.PRESETS);
        add(holder, 49, Material.NETHER_STAR, "Rainbow", PlayerColor.rainbow(), ColorRenderer.component(PlayerColor.rainbow(), player.getName()));
        add(holder, 53, Material.BARRIER, "Reset", null, Component.text("Reset color"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Page targetPage = holder.pages.get(event.getRawSlot());
        if (targetPage != null) { open(player, targetPage); return; }
        if (!holder.actions.containsKey(event.getRawSlot())) return;
        PlayerColor color = holder.actions.get(event.getRawSlot());
        if (color != null && !permitted(player, color, event.getRawSlot())) {
            plugin.messages().send(player, "no-permission"); return;
        }
        player.closeInventory();
        var future = color == null ? plugin.service().reset(player) : plugin.service().set(player, color);
        future.thenAccept(success -> { if (success) plugin.scheduler().player(player,
                () -> plugin.messages().send(player, color == null ? "reset" : "updated")); });
    }

    private boolean permitted(Player player, PlayerColor color, int slot) {
        return switch (color.kind()) {
            case RAINBOW -> player.hasPermission("mcolor.rainbow");
            case GRADIENT -> player.hasPermission("mcolor.gradient");
            case SOLID -> player.hasPermission("mcolor.color.*") || player.hasPermission("mcolor.color." + plugin.namedColors().entrySet().stream()
                    .filter(entry -> entry.getValue().equals(color.colors().getFirst())).map(Map.Entry::getKey).findFirst().orElse(""));
        };
    }

    private void add(MenuHolder holder, int slot, Material material, String name, PlayerColor action, Component preview) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Component.text(name));
        meta.lore(List.of(preview));
        item.setItemMeta(meta);
        holder.inventory.setItem(slot, item);
        holder.actions.put(slot, action);
    }

    private void addPage(MenuHolder holder, int slot, Material material, String name, Page page) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Component.text(name));
        item.setItemMeta(meta);
        holder.inventory.setItem(slot, item);
        holder.pages.put(slot, page);
    }

    private static final class MenuHolder implements InventoryHolder {
        private final Map<Integer, PlayerColor> actions = new HashMap<>();
        private final Map<Integer, Page> pages = new HashMap<>();
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private enum Page {
        BASIC("Basic colors"), GRADIENTS("Gradients"), PRESETS("Presets");
        private final String title;
        Page(String title) { this.title = title; }
    }
}
