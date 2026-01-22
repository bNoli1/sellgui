package me.szerverneved.sellgui;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.UUID;

public class SellGUI extends JavaPlugin implements Listener {

    private static Economy econ = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault nem található!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("sell").setExecutor(new SellCommand());
        getCommand("selladmin").setExecutor(new AdminCommand());
        getServer().getPluginManager().registerEvents(this, this);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    // --- SELL PARANCS ---
    public class SellCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) return true;

            Inventory gui = Bukkit.createInventory(null, 45, "§2§lEladás §7(Tedd be a cuccokat)");
            ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta fm = filler.getItemMeta();
            if (fm != null) { fm.setDisplayName(" "); filler.setItemMeta(fm); }

            for (int i = 0; i < 9; i++) gui.setItem(i, filler);

            int buttonSlot = getConfig().getInt("settings.button-slot", 4);
            ItemStack sellBtn = getConfig().getItemStack("button-item");
            if (sellBtn == null) {
                sellBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta sm = sellBtn.getItemMeta();
                if (sm != null) { sm.setDisplayName("§a§lELADÁS"); sellBtn.setItemMeta(sm); }
            }
            gui.setItem(buttonSlot, sellBtn);
            player.openInventory(gui);
            return true;
        }
    }

    // --- ADMIN PARANCS ---
    public class AdminCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("sellgui.admin")) {
                sender.sendMessage(getConfig().getString("messages.no-permission", "&cNincs jogod!").replace("&", "§"));
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage("§a[SellGUI] Újratöltve!");
                return true;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
                Player p = (Player) sender;
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item.getType() == Material.AIR) return true;
                
                String id = UUID.randomUUID().toString().substring(0, 8);
                getConfig().set("prices." + id + ".item", item);
                getConfig().set("prices." + id + ".price", Double.parseDouble(args[1]));
                saveConfig();
                p.sendMessage("§aTárgy hozzáadva!");
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("setbutton")) {
                Player p = (Player) sender;
                getConfig().set("button-item", p.getInventory().getItemInMainHand());
                saveConfig();
                p.sendMessage("§aGomb beállítva!");
                return true;
            }
            return true;
        }
    }

    // --- EVENT LISTENER ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§2§lEladás §7(Tedd be a cuccokat)")) return;
        int slot = event.getRawSlot();
        int btnSlot = getConfig().getInt("settings.button-slot", 4);

        if (slot >= 0 && slot <= 8) {
            event.setCancelled(true);
            if (slot == btnSlot) processSell((Player) event.getWhoClicked(), event.getInventory());
        }
    }

    private void processSell(Player player, Inventory inv) {
        double totalValue = 0;
        ConfigurationSection prices = getConfig().getConfigurationSection("prices");

        for (int i = 9; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            boolean sold = false;
            if (prices != null) {
                for (String key : prices.getKeys(false)) {
                    ItemStack saved = prices.getItemStack(key + ".item");
                    if (item.isSimilar(saved)) {
                        totalValue += prices.getDouble(key + ".price") * item.getAmount();
                        inv.setItem(i, null);
                        sold = true; break;
                    }
                }
            }
            if (!sold) {
                player.getInventory().addItem(item).values().forEach(d -> player.getWorld().dropItemNaturally(player.getLocation(), d));
                inv.setItem(i, null);
            }
        }

        if (totalValue > 0) {
            econ.depositPlayer(player, totalValue);
            playSound(player, "settings.sell-sound");
            player.sendMessage("§aSikeres eladás: §f" + totalValue + " $");
        } else {
            playSound(player, "settings.fail-sound");
        }
        player.closeInventory();
    }

    private void playSound(Player p, String path) {
        try { p.playSound(p.getLocation(), Sound.valueOf(getConfig().getString(path)), 1f, 1f); } catch (Exception ignored) {}
    }
}
