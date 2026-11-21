package ru.alphagrief.alphamessages;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.List;
import java.util.Objects;

public final class AlphaMessages extends JavaPlugin implements Listener, CommandExecutor {
    private static final List<String> CHECKED = List.of(
        "WorldGuard", "WorldEdit", "WGExtender", "Essentials", "LuckPerms",
        "LiteBans", "AtomBans", "AuthMe", "CoreProtect", "Vault",
        "PlaceholderAPI", "TAB", "Citizens", "DeluxeMenus", "ProtocolLib",
        "Multiverse-Core", "MythicMobs", "PlayerPoints", "NCases", "NClans"
    );
    private org.bukkit.configuration.file.FileConfiguration messages;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
        Objects.requireNonNull(getCommand("am")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AlphaMessages включён. Обнаружено плагинов: " + countDetected());
    }

    private int countDetected() {
        return (int) CHECKED.stream().filter(n -> Bukkit.getPluginManager().getPlugin(n) != null).count();
    }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private void reloadMessages() {
        messages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
    private org.bukkit.configuration.file.FileConfiguration messages() {
        if (messages == null) reloadMessages();
        return messages;
    }
    private String msg(String key) {
        String prefix = getConfig().getString("prefix", "&6AlphaGrief &8» &7");
        return color(prefix + messages().getString("messages." + key, "&cMissing message: " + key)
            .replace("%version%", getDescription().getVersion()));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("alphamessages.admin")) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        if (args.length == 0) { sender.sendMessage(msg("usage")); return true; }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                reloadMessages();
                sender.sendMessage(msg("reload"));
            }
            case "plugins" -> {
                sender.sendMessage(msg("detected-header"));
                for (String name : CHECKED) {
                    boolean found = Bukkit.getPluginManager().getPlugin(name) != null;
                    String status = messages().getString(found ? "messages.detected-enabled" : "messages.detected-missing");
                    sender.sendMessage(color(getConfig().getString("prefix", "&6AlphaGrief &8» &7")
                        + messages().getString("messages.detected-entry", "&8• &7%plugin% &8— %status%")
                        .replace("%plugin%", name).replace("%status%", status)));
                }
                sender.sendMessage(color("&8Найдено: &f" + countDetected() + "&7 из &f" + CHECKED.size()));
            }
            case "editor" -> {
                if (sender instanceof Player player) openEditor(player);
                else sender.sendMessage(msg("player-only"));
            }
            case "version" -> sender.sendMessage(msg("version"));
            default -> sender.sendMessage(msg("unknown"));
        }
        return true;
    }

    private void openEditor(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, color(messages().getString("messages.editor-title", "&6AlphaMessages")));
        int slot = 10;
        for (String name : CHECKED) {
            boolean found = Bukkit.getPluginManager().getPlugin(name) != null;
            ItemStack item = new ItemStack(found ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color((found ? "&a" : "&7") + name));
            meta.setLore(List.of(color(found ? "&7Плагин обнаружен" : "&8Не установлен"),
                color("&7Перехват сообщений требует отдельного адаптера.")));
            item.setItemMeta(meta);
            if (slot < 26) inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(color(messages().getString("messages.editor-title", "&6AlphaMessages"))))
            event.setCancelled(true);
    }
}
