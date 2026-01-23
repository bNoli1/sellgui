package me.quantum.QuantumSell;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class QuantumSell extends JavaPlugin implements Listener, CommandExecutor {

	private Economy econ = null;
	private final List<ItemStack> sellableItems = new ArrayList<>();
	private final List<Double> prices = new ArrayList<>();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		if (!setupEconomy()) {
			getLogger().severe("Vault nem talalhato!");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
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

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) return true;
		Player player = (Player) sender;
		FileConfiguration config = getConfig();

		if (cmd.getName().equalsIgnoreCase("sell")) {
			openSellGUI(player);
			return true;
		}

		if (cmd.getName().equalsIgnoreCase("selladmin")) {
			if (!player.hasPermission("sellgui.admin")) {
				player.sendMessage(color(config.getString("messages.prefix") + config.getString("messages.no-permission")));
				return true;
			}
			if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
				ItemStack hand = player.getInventory().getItemInMainHand().clone();
				if (hand.getType() == Material.AIR) {
					player.sendMessage(color(config.getString("messages.prefix") + "&cNincs semmi a kezedben!"));
					return true;
				}
				try {
					double price = Double.parseDouble(args[1]);
					sellableItems.add(hand);
					prices.add(price);
					player.sendMessage(color(config.getString("messages.prefix") + config.getString("messages.admin-add")
						.replace("%item%", hand.getType().name()).replace("%price%", String.valueOf(price))));
				} catch (NumberFormatException e) {
					player.sendMessage("§cErvenytelen ar!");
				}
			} else if (args.length >= 1 && args[0].equalsIgnoreCase("wipe")) {
				sellableItems.clear();
				prices.clear();
				player.sendMessage(color(config.getString("messages.prefix") + config.getString("messages.database-wipe")));
			}
		}
		return true;
	}

	public void openSellGUI(Player player) {
		FileConfiguration config = getConfig();
		Inventory inv = Bukkit.createInventory(null, 45, color(config.getString("gui-settings.title")));
		ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
		ItemMeta meta = glass.getItemMeta();
		meta.setDisplayName(" ");
		glass.setItemMeta(meta);
		for (int i = 0; i < 9; i++) inv.setItem(i, glass);
		ItemStack button = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
		ItemMeta bMeta = button.getItemMeta();
		bMeta.setDisplayName(color(config.getString("gui-settings.button-name")));
		List<String> lore = new ArrayList<>();
		for (String s : config.getStringList("gui-settings.button-lore")) lore.add(color(s));
		bMeta.setLore(lore);
		button.setItemMeta(bMeta);
		inv.setItem(4, button);
		player.openInventory(inv);
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		FileConfiguration config = getConfig();
		if (!event.getView().getTitle().equals(color(config.getString("gui-settings.title")))) return;
		if (event.getRawSlot() < 9) {
			event.setCancelled(true);
			if (event.getRawSlot() == 4) {
				handleSell((Player) event.getWhoClicked(), event.getInventory());
			}
		}
	}

	private void handleSell(Player player, Inventory inv) {
		FileConfiguration config = getConfig();
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
			if (player.hasPermission("sellgui.multiplier.gold")) mult = config.getDouble("multipliers.gold");
			else if (player.hasPermission("sellgui.multiplier.silver")) mult = config.getDouble("multipliers.silver");
			else if (player.hasPermission("sellgui.multiplier.bronze")) mult = config.getDouble("multipliers.bronze");
			double finalAmt = total * mult;
			econ.depositPlayer(player, finalAmt);
			player.sendMessage(color(config.getString("messages.prefix") + config.getString("messages.sell-success")
				.replace("%amount%", String.format("%.2f", finalAmt))));
			player.closeInventory();
		} else {
			player.sendMessage(color(config.getString("messages.prefix") + config.getString("messages.no-item")));
		}
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		FileConfiguration config = getConfig();
		if (event.getView().getTitle().equals(color(config.getString("gui-settings.title")))) {
			for (int i = 9; i < 45; i++) {
				ItemStack item = event.getInventory().getItem(i);
				if (item != null) event.getPlayer().getInventory().addItem(item);
			}
		}
	}

	private String color(String s) {
		return s == null ? "" : s.replace("&", "§");
	}
}
