package me.quantum.QuantumSell;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuantumSell extends JavaPlugin implements Listener, CommandExecutor {

    private Economy econ = null;
    private final List<ItemStack> sellableItems = new ArrayList<>();
    private final List<Double> prices = new ArrayList<>();
    private File itemsFile;
    private FileConfiguration itemsConfig;
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createItemsConfig();
        loadData();
        setupEconomy();
        getCommand("sell").setExecutor(this);
        getCommand("selladmin").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    private void createItemsConfig() {
        itemsFile = new File(getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            itemsFile.getParentFile().mkdirs();
            try { itemsFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }

    private void saveData() {
        itemsConfig.set("items", null);
        for (int i = 0; i < sellableItems.size(); i++) {
            itemsConfig.set("items." + i + ".item", sellableItems.get(i));
            itemsConfig.set("items." + i + ".price", prices.get(i));
        }
        try { itemsConfig.save(itemsFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadData() {
        sellableItems.clear();
        prices.clear();
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        if (itemsConfig.getConfigurationSection("items") == null) return;
        for (String key : itemsConfig.getConfigurationSection("items").getKeys(false)) {
            ItemStack item = itemsConfig.getItemStack("items." + key + ".item");
            double price = itemsConfig.getDouble("items." + key + ".price");
            if (item != null) {
                sellableItems.add(item);
                prices.add(price);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadData();
                sender.sendMessage("§a[QuantumSell] Adatok ujratoltve!");
            }
            return true;
        }
        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("sell")) {
            openSellGUI(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1, 1);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("selladmin")) {
            if (!player.hasPermission("sellgui.admin")) {
                player.sendMessage(getMsg("messages.no-permission", "&cNincs jogod!"));
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadData();
                player.sendMessage(color("&a&l[!] &aHEX szinek es adatok frissitve!"));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
                ItemStack hand = player.getInventory().getItemInMainHand().clone();
                if (hand.getType() == Material.AIR) {
                    player.sendMessage(color("&cNincs semmi a kezedben!"));
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[1]);
                    sellableItems.add(hand);
                    prices.add(price);
                    saveData();
                    player.sendMessage(getMsg("messages.admin-add", "&aHozzaadva!").replace("%item%", hand.getType().name()).replace("%price%", String.valueOf(price)));
                } catch (Exception e) {
                    player.sendMessage(color("&cHasznalat: /selladmin add [ar]"));
                }
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
                playerPages.put(player.getUniqueId(), 0);
                openAdminList(player, 0);
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("wipe")) {
                sellableItems.clear();
                prices.clear();
                saveData();
                player.sendMessage(getMsg("messages.database-wipe", "&cAdatbazis torolve!"));
                return true;
            }
        }
        return true;
    }

    public void openAdminList(Player player, int page) {
        Inventory listGui = Bukkit.createInventory(null, 54, "§cAdmin Lista - " + (page + 1) + ". oldal");
        int start = page * 45;
        int end = Math.min(start + 45, sellableItems.size());
        
        for (int i = start; i < end; i++) {
            ItemStack display = sellableItems.get(i).clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("§8§m-----------------");
                lore.add("§eAr: §f" + prices.get(i) + "$");
                lore.add(" ");
                lore.add("§c§l[!] §7Shift + Kattintás");
                lore.add("§7a tárgy törléséhez!");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            listGui.setItem(i - start, display);
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pMeta = prev.getItemMeta();
            pMeta.setDisplayName("§e§l<-- Előző oldal");
            prev.setItemMeta(pMeta);
            listGui.setItem(48, prev);
        }
        if (sellableItems.size() > (page + 1) * 45) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nMeta = next.getItemMeta();
            nMeta.setDisplayName("§e§lKövetkező oldal -->");
            next.setItemMeta(nMeta);
            listGui.setItem(50, next);
        }
        player.openInventory(listGui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        if (title.contains("Admin Lista")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
            int slot = event.getRawSlot();

            if (slot == 48 && event.getCurrentItem().getType() == Material.ARROW) {
                playerPages.put(player.getUniqueId(), currentPage - 1);
                openAdminList(player, currentPage - 1);
            } else if (slot == 50 && event.getCurrentItem().getType() == Material.ARROW) {
                playerPages.put(player.getUniqueId(), currentPage + 1);
                openAdminList(player, currentPage + 1);
            } else if (slot < 45 && (event.getClick().isShiftClick() || event.getClick() == ClickType.MIDDLE)) {
                int indexInList = (currentPage * 45) + slot;
                if (indexInList < sellableItems.size()) {
                    sellableItems.remove(indexInList);
                    prices.remove(indexInList);
                    saveData();
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1, 1);
                    openAdminList(player, currentPage);
                }
            }
            return;
        }

        if (title.equals(getMsg("gui-settings.title", "&2&lEladas"))) {
            if (event.getRawSlot() < 9) {
                event.setCancelled(true);
                if (event.getRawSlot() == 4) handleSell(player, event.getInventory());
            }
        }
    }

    public void openSellGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, getMsg("gui-settings.title", "&2&lEladas"));
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); glass.setItemMeta(meta); }
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        ItemStack button = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta bMeta = button.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName(getMsg("gui-settings.button-name", "&a&lELADAS INDITASA"));
            button.setItemMeta(bMeta);
        }
        inv.setItem(4, button);
        player.openInventory(inv);
    }

    private void handleSell(Player player, Inventory inv) {
        double total = 0;
        boolean sold = false;
        for (int i = 9; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            for (int j = 0; j < sellableItems.size(); j++) {
                if (item.isSimilar(sellableItems.get(j))) {
                    total += prices.get(j) * item.getAmount();
                    inv.setItem(i, null);
                    sold = true;
                    break;
                }
            }
        }
        if (sold) {
            double mult = 1.0;
            if (player.hasPermission("sellgui.multiplier.gold")) mult = getConfig().getDouble("multipliers.gold", 1.75);
            else if (player.hasPermission("sellgui.multiplier.silver")) mult = getConfig().getDouble("multipliers.silver", 1.5);
            else if (player.hasPermission("sellgui.multiplier.bronze")) mult = getConfig().getDouble("multipliers.bronze", 1.25);
            double finalAmt = total * mult;
            if (econ != null) {
                econ.depositPlayer(player, finalAmt);
                player.sendMessage(getMsg("messages.prefix", "&7[&2Sell&7] ") + getMsg("messages.sell-success", "&aEladva: &f%amount%$").replace("%amount%", String.format("%.2f", finalAmt)));
                player.sendTitle(color("&#66ff66+ " + String.format("%.2f", finalAmt) + "$"), getMsg("messages.sell-title-sub", "&7Sikeres eladas!"), 10, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.2f);
            }
            player.closeInventory();
        } else {
            player.sendMessage(getMsg("messages.prefix", "&7[&2Sell&7] ") + getMsg("messages.no-item", "&cNincs mit eladni!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(getMsg("gui-settings.title", "&2&lEladas"))) {
            for (int i = 9; i < 45; i++) {
                ItemStack item = event.getInventory().getItem(i);
                if (item != null) event.getPlayer().getInventory().addItem(item);
            }
        }
    }

    private String getMsg(String path, String def) {
        String s = getConfig().getString(path);
        return (s == null || s.isEmpty()) ? color(def) : color(s);
    }

    private String color(String s) {
        if (s == null) return "";
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(s);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String color = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : color.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace("&", "§");
    }
}
