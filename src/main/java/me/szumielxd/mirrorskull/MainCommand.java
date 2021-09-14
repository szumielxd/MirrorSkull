package me.szumielxd.mirrorskull;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MainCommand implements TabExecutor {
	
	
	private final MirrorSkull plugin;
	
	
	public MainCommand(MirrorSkull plugin) {
		this.plugin = plugin;
	}
	

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> list = new ArrayList<>();
		if (args.length == 1) {
			String arg = args[0].toLowerCase();
			if ("force".startsWith(arg)) list.add("get");
		}
		return list;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("get")) {
			
			if (sender instanceof Player) {
				Player player = (Player) sender;
				ItemStack item = this.plugin.createSkull();
				if (!player.getInventory().addItem(item).isEmpty()) {
					player.getWorld().dropItem(player.getLocation(), item);
				}
				sender.sendMessage(MirrorSkull.PREFIX + "Here you go. Do not hurt yourself.");
				return true;
			}
			sender.sendMessage(MirrorSkull.PREFIX + "§cWhere is yours inventory, Console?");
			return true;
		}
		sender.sendMessage(MirrorSkull.PREFIX + String.format("Usage: §a/%s get", label));
		return true;
	}

}
