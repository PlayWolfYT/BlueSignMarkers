package net.playwolf.bluemap.bsm;

import com.flowpowered.math.vector.Vector2i;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class SignListener implements Listener {

    private BlueMapAPI api;

    public static final String DEFAULT_MARKER_SET_NAME = "BSM-Signs";

    public SignListener(BlueSignMarkers plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setupAPI(BlueMapAPI api) {
        this.api = api;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        if (api == null) return;
        if (e.isCancelled()) return;
        FileConfiguration config = ConfigManager.getConfig();

        Player player = e.getPlayer();
        if (!player.hasPermission("bsm.sign.create")) {
            notifyPlayer(player, "Seems like you wanted to create a marker... Sadly you do not have permission for that.");
            return;
        }


        if (!e.getLine(0).equalsIgnoreCase(config.getString("sign-prefix.user-input"))) return;

        api.getWorld(player.getWorld()).ifPresentOrElse((world) -> {
            try {
                String label = e.getLine(1);
                String iconName = e.getLine(2);
                String markerSetName = e.getLine(3);

                if (label == null || label.isEmpty()) {
                    notifyPlayer(player, "Please enter a label!");
                    return;
                }

                String iconURL = null;
                if (iconName != null && !iconName.isEmpty()) {
                    // Check if an icon with this name exists
                    if (!api.getWebApp().availableImages().containsKey(iconName)) {
                        notifyPlayer(player, "This icon does not exist!");
                        return;
                    } else {
                        iconURL = api.getWebApp().availableImages().get(iconName);
                    }
                }

                if (markerSetName == null || markerSetName.isEmpty()) {
                    markerSetName = DEFAULT_MARKER_SET_NAME;
                }

                POIMarker.Builder markerBuilder = POIMarker.toBuilder()
                        .position(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ())
                        .label(label);

                if (iconURL != null)
                    markerBuilder.icon(iconURL, Vector2i.from(e.getBlock().getX(), e.getBlock().getY()));
                else markerBuilder.defaultIcon();

                AtomicReference<Boolean> markerSetExists = new AtomicReference<>(false);
                String finalMarkerSetName = markerSetName;
                world.getMaps().forEach((map) -> {
                    if (markerSetExists.get()) return;

                    MarkerSet markerSet;
                    if (map.getMarkerSets().containsKey(finalMarkerSetName)) {
                        markerSet = map.getMarkerSets().get(finalMarkerSetName);
                        if (markerSet.getMarkers().containsKey(label)) {
                            markerSetExists.set(true);
                            notifyPlayer(player, "A marker with this name already exists.");
                            return;
                        }
                        markerSet.getMarkers().put(label, markerBuilder.build());
                        map.getMarkerSets().put(finalMarkerSetName, markerSet);
                    } else {
                        markerSet = new MarkerSet(finalMarkerSetName, true, false);
                        markerSet.getMarkers().put(label, markerBuilder.build());
                        map.getMarkerSets().put(finalMarkerSetName, markerSet);
                    }

                    MarkerStorage.saveMarkerSet(markerSet, map.getId());
                });

                if (markerSetExists.get()) return;

                e.setLine(0, ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(config.getString("sign-prefix.output"))));

                notifyPlayer(player, "Successfully created a marker!");
            } catch (IOException ex) {
                notifyPlayer(player, "An error occurred while creating the marker!");
                ex.printStackTrace();
            }
        }, () -> notifyPlayer(player, "I'm sorry, but BlueMap doesn't support this world!"));


    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent e) {
        if (api == null) return;
        if (e.isCancelled()) return;
        FileConfiguration config = ConfigManager.getConfig();

        if (!(e.getBlock().getState() instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) e.getBlock().getState();


        String signPrefix = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(config.getString("sign-prefix.output")));
        if (sign.getLine(0).equals(signPrefix)) {
            Player player = e.getPlayer();
            if (!player.hasPermission("bsm.sign.remove")) {
                notifyPlayer(player, "Seems like you wanted to remove a marker... Sadly you do not have permission for that.");
                return;
            }

            api.getWorld(player.getWorld()).ifPresentOrElse((world) -> {
                String label = sign.getLine(1);
                String markerSetName = sign.getLine(3);

                if (label.isEmpty()) {
                    notifyPlayer(player, "Hmm, it appears this marker is broken. I'll let you break that sign.");
                    return;
                }

                if (markerSetName.isEmpty()) {
                    markerSetName = DEFAULT_MARKER_SET_NAME;
                }

                AtomicReference<Boolean> markerSetExists = new AtomicReference<>(false);
                String finalMarkerSetName = markerSetName;
                world.getMaps().forEach((map) -> {
                    if (markerSetExists.get()) return;

                    if (map.getMarkerSets().containsKey(finalMarkerSetName)) {
                        MarkerSet markerSet = map.getMarkerSets().get(finalMarkerSetName);
                        if (markerSet.getMarkers().containsKey(label)) {
                            markerSetExists.set(true);
                            markerSet.getMarkers().remove(label);
                            map.getMarkerSets().put(finalMarkerSetName, markerSet);

                            if (markerSet.getMarkers().isEmpty()) {
                                map.getMarkerSets().remove(finalMarkerSetName);
                                MarkerStorage.removeMarkerSet(markerSet, map.getId());
                            } else {
                                MarkerStorage.saveMarkerSet(markerSet, map.getId());
                            }
                        }
                    }
                });

                if (markerSetExists.get()) {
                    notifyPlayer(player, "Successfully removed a marker!");
                } else {
                    notifyPlayer(player, "This marker does not exist!");
                }
            }, () -> notifyPlayer(player, "I'm sorry, but BlueMap doesn't support this world!"));
        }

    }

    public void notifyPlayer(Player player, String message) {
        String messagePrefix = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(ConfigManager.getConfig().getString("message-prefix")));
        String messageType = Objects.requireNonNull(ConfigManager.getConfig().getString("message-type")).toLowerCase().trim();

        switch (messageType) {
            case "actionbar" -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(messagePrefix + ChatColor.translateAlternateColorCodes('&',message)));
            case "chat" -> player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&',message));
        }
    }

}
