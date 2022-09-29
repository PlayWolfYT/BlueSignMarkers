package net.playwolf.bluemap.bsm;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static net.playwolf.bluemap.bsm.BlueSignMarkers.PREFIX;
import static net.playwolf.bluemap.bsm.BlueSignMarkers.console;

public class MarkerStorage {

    private static BlueSignMarkers plugin;
    private static BlueMapAPI api;

    private static HashMap<String, MarkerSet> markerSets = new HashMap<>();

    public static void init(BlueSignMarkers plugin, BlueMapAPI api) {
        MarkerStorage.plugin = plugin;
        MarkerStorage.api = api;
    }

    public static void saveMarkerSet(MarkerSet markerSet, String mapId) {
        markerSets.put(mapId, markerSet);
        File file = new File(plugin.getDataFolder(), "marker-sets/" + mapId + "/" + markerSet.getLabel() + ".json");
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            MarkerGson.INSTANCE.toJson(markerSet, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void removeMarkerSet(MarkerSet markerSet, String mapId) {
        markerSets.remove(mapId);
        File file = new File(plugin.getDataFolder(), "marker-sets/" + mapId + "/" + markerSet.getLabel() + ".json");
        if (file.exists()) file.delete();
    }

    public static int getNumMarkers() {
        AtomicInteger num = new AtomicInteger();
        foreachMarkerSet((mapId, markerSet) -> num.addAndGet(markerSet.getMarkers().size()));
        return num.get();
    }

    public static int getNumMarkerSets() {
        AtomicInteger num = new AtomicInteger();
        foreachMarkerSet((mapId, markerSet) -> num.getAndIncrement());
        return num.get();
    }

    public static void loadMarkerSets() {
        console.sendMessage(PREFIX + "§aLoading marker-sets...");
        HashMap<String, ArrayList<Marker>> migratedMarkers = new HashMap<>();
        foreachMarkerSet((mapId, markerSet) -> {
            markerSet.getMarkers().forEach((markerId, marker) -> {
                World world = Bukkit.getWorld(mapId);
                if(world == null) {
                    console.sendMessage(PREFIX + "§cWorld with name "+mapId+" does not exist for bukkit!");
                    // World does not exist anymore, delete the marker
                    markerSet.getMarkers().remove(markerId);
                    saveMarkerSet(markerSet, mapId);
                    return;
                }

                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    Block block = world.getBlockAt(marker.getPosition().getFloorX(), marker.getPosition().getFloorY(), marker.getPosition().getFloorZ());

                    if(!(block.getState() instanceof Sign)) {
                        console.sendMessage(PREFIX + "§cSign at " + marker.getPosition().getFloorX() + ", " + marker.getPosition().getFloorY() + ", " + marker.getPosition().getFloorZ() + " does not exist anymore!");
                        // Sign does not exist anymore, delete the marker
                        markerSet.getMarkers().remove(markerId);
                        saveMarkerSet(markerSet, mapId);
                        return;
                    }

                    // We still have a sign at this position, check if the information still matches, otherwise update the marker
                    Sign sign = (Sign) world.getBlockAt(marker.getPosition().getFloorX(), marker.getPosition().getFloorY(), marker.getPosition().getFloorZ()).getState();
                    String migrationPrefix = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(ConfigManager.getConfig().getString("sign-prefix.migration")));
                    String currentPrefix = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(ConfigManager.getConfig().getString("sign-prefix.output")));

                    if(sign.getLine(0).equals(migrationPrefix)) {
                        // This sign needs to be migrated, change the prefix on the sign
                        sign.setLine(0, currentPrefix);
                        sign.update();
                        return;
                    }

                    if(!sign.getLine(0).equals(migrationPrefix) && !sign.getLine(0).equals(currentPrefix)) {
                        console.sendMessage(PREFIX + "§cFound a sign with an invalid prefix, please fix it manually: " + sign.getLocation().toString());
                        console.sendMessage(PREFIX + "§cSign prefix: " + sign.getLine(0));
                        console.sendMessage(PREFIX + "§cExpected Migration Prefix: " + migrationPrefix);
                        console.sendMessage(PREFIX + "§cExpected Current Prefix: " + currentPrefix);
                        // Sign does not have the correct prefix anymore, delete the marker
                        markerSet.getMarkers().remove(markerId);
                        saveMarkerSet(markerSet, mapId);
                        return;
                    }

                    if(!sign.getLine(1).equals(marker.getLabel())) {
                        console.sendMessage(PREFIX + "§cFound a sign with an invalid label, updated it...");
                        // Sign does not have the correct label anymore, update the marker
                        marker.setLabel(sign.getLine(1));
                        saveMarkerSet(markerSet, mapId);
                        return;
                    }

                    String markerSetName = sign.getLine(3);
                    if(markerSetName.isEmpty()) {
                        markerSetName = SignListener.DEFAULT_MARKER_SET_NAME;
                        return;
                    }
                    if(!(markerSetName.equals(markerSet.getLabel()))) {
                        console.sendMessage(PREFIX + "§cFound a sign with an invalid marker set, updated it...");
                        // This is in the wrong marker set, move it to the correct one
                        markerSet.getMarkers().remove(markerId);
                        saveMarkerSet(markerSet, mapId);
                        ArrayList<Marker> markersToMigrate = migratedMarkers.getOrDefault(markerSetName, new ArrayList<>());
                        markersToMigrate.add(marker);
                        migratedMarkers.put(markerSetName, markersToMigrate);
                    }

                    if(!migratedMarkers.isEmpty()) {
                        if(migratedMarkers.containsKey(markerSet.getLabel())) {
                            ArrayList<Marker> markersToMigrate = migratedMarkers.get(markerSet.getLabel());
                            markersToMigrate.forEach(markerToMigrate -> markerSet.getMarkers().put(markerToMigrate.getLabel(), markerToMigrate));
                        }
                    }
                });
            });


            api.getMap(mapId).ifPresent(map -> map.getMarkerSets().put(markerSet.getLabel(), markerSet));

        });

        if(!migratedMarkers.isEmpty()) {

            console.sendMessage(PREFIX + "§eMigrating " + migratedMarkers.size() + " markers...");

            // We have markers to migrate
            foreachMarkerSet((mapId, markerSet) -> {
                if(migratedMarkers.containsKey(markerSet.getLabel())) {
                    ArrayList<Marker> markersToMigrate = migratedMarkers.get(markerSet.getLabel());
                    markersToMigrate.forEach(markerToMigrate -> markerSet.getMarkers().put(markerToMigrate.getLabel(), markerToMigrate));
                }
            });
        }
    }

    public static void removeMarkerSets() {
        markerSets.forEach((mid, ms) -> api.getMap(mid).ifPresent(m -> m.getMarkerSets().remove(ms.getLabel())));
        markerSets.clear();
    }

    private static void foreachMarkerSet(BiConsumer<String, MarkerSet> consumer) {
        // Scan through the "marker-sets" directory and load each json file
        File folder = new File(plugin.getDataFolder(), "marker-sets");
        if (!folder.exists()) {
            folder.mkdirs();
            return;
        }

        File[] mapFolders = folder.listFiles();
        if (mapFolders == null) return;

        for (File mapFolder : mapFolders) {
            if (!mapFolder.isDirectory()) continue;

            File[] markerSetFiles = mapFolder.listFiles();
            if (markerSetFiles == null) continue;

            for (File markerSetFile : markerSetFiles) {
                if (!markerSetFile.getName().endsWith(".json")) continue;

                try (FileReader reader = new FileReader(markerSetFile)) {
                    MarkerSet markerSet = MarkerGson.INSTANCE.fromJson(reader, MarkerSet.class);
                    consumer.accept(mapFolder.getName(), markerSet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
