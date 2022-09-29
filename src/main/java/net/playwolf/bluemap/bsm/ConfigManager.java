package net.playwolf.bluemap.bsm;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private static FileConfiguration config;
    private static BlueSignMarkers plugin;

    public static void init(BlueSignMarkers plugin) {
        config = plugin.getConfig();
        config.options().copyDefaults(true);
        plugin.saveDefaultConfig();
        ConfigManager.plugin = plugin;
    }


    public static void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public static FileConfiguration getConfig() {
        return config;
    }


}
