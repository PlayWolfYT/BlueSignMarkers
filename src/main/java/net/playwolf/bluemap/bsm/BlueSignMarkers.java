package net.playwolf.bluemap.bsm;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import de.bluecolored.bluemap.api.BlueMapAPI;

import java.util.Objects;

public final class BlueSignMarkers extends JavaPlugin {

    public static String PREFIX = "§7[§bBlueSignMarkers§7]§r ";
    public static ConsoleCommandSender console = Bukkit.getConsoleSender();

    @Override
    public void onEnable() {
        ConfigManager.init(this);

        PREFIX = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(ConfigManager.getConfig().getString("message-prefix")));

        // Check if BlueMap (and BlueMapAPI) is installed
        if(getServer().getPluginManager().getPlugin("BlueMap") == null) {
            console.sendMessage(PREFIX + "§4BlueMap is not installed! Please install BlueMap to use this plugin!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }


        new BSMCommand(this);
        SignListener signListener = new SignListener(this);
        BlueMapAPI.onEnable((api) -> {
            if(!Bukkit.getPluginManager().isPluginEnabled(this)) return;

            console.sendMessage(PREFIX + "§bAdding BlueSignMarkers to BlueMap...");

            MarkerStorage.init(this, api);

            console.sendMessage(PREFIX + "§bLoading Sign Markers...");
            MarkerStorage.loadMarkerSets();

            console.sendMessage(PREFIX + "§bRegistering Events...");
            signListener.setupAPI(api);

            console.sendMessage(PREFIX + "§aBlueSignMarkers was added BlueMap!");
        });

        BlueMapAPI.onDisable((api) -> {
            signListener.setupAPI(null);
            console.sendMessage(PREFIX + "§cBlueSignMarkers was removed from BlueMap!");
        });
    }

    @Override
    public void onDisable() {
        console.sendMessage(PREFIX + "§cShutting down BlueSignMarkers...");
    }
}
