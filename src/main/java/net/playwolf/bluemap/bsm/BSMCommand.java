package net.playwolf.bluemap.bsm;

import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BSMCommand implements CommandExecutor {

    public BSMCommand(BlueSignMarkers plugin) {
        Bukkit.getPluginCommand("bluesignmarkers").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if(args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            ConfigManager.reloadConfig();
            MarkerStorage.removeMarkerSets();
            MarkerStorage.loadMarkerSets();
            BlueSignMarkers.PREFIX = ChatColor.translateAlternateColorCodes('&', ConfigManager.getConfig().getString("message-prefix"));
            sender.sendMessage(BlueSignMarkers.PREFIX + "§aConfig reloaded!");
            return true;
        }


        sender.sendMessage(BlueSignMarkers.PREFIX + "============================");
        sender.sendMessage(BlueSignMarkers.PREFIX + "Currently managed marker-sets: " + MarkerStorage.getNumMarkerSets());
        sender.sendMessage(BlueSignMarkers.PREFIX + "Currently managed markers: " + MarkerStorage.getNumMarkers());
        sender.sendMessage(BlueSignMarkers.PREFIX);
        sender.sendMessage(BlueSignMarkers.PREFIX + "§a/bluesignmarkers reload §7- Reloads the config");
        sender.sendMessage(BlueSignMarkers.PREFIX + "============================");

        return true;
    }
}
