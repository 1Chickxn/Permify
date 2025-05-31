package me.chickxn.permify.spigot.listener;

import me.chickxn.permify.spigot.PermifyBase;
import me.chickxn.permify.spigot.handler.PermissionHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Handles all player-related events for the permission system
 */
public class PlayerJoinListener implements Listener {

    private final PermissionHandler permissionHandler;
    private final Logger logger;

    public PlayerJoinListener(@NotNull PermissionHandler permissionHandler) {
        this.permissionHandler = permissionHandler;
        this.logger = PermifyBase.getInstance().getLogger();
    }

    /**
     * Handles player join events
     * Uses HIGHEST priority to ensure we run after other plugins
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        try {
            logger.fine("Processing join event for " + event.getPlayer().getName());

            // Handle join asynchronously to avoid blocking the main thread
            permissionHandler.onPlayerJoin(event.getPlayer()).exceptionally(ex -> {
                logger.severe("Error processing join for " + event.getPlayer().getName() + ": " + ex.getMessage());
                ex.printStackTrace();

                // Send error message to player
                event.getPlayer().sendMessage("§cFehler beim Laden der Berechtigungen. Bitte kontaktiere einen Administrator.");
                return null;
            });

        } catch (Exception e) {
            logger.severe("Unexpected error in onPlayerJoin for " + event.getPlayer().getName() + ": " + e.getMessage());
            e.printStackTrace();
            event.getPlayer().sendMessage("§cSchwerwiegender Fehler beim Laden der Berechtigungen!");
        }
    }

    /**
     * Handles player quit events
     * Uses MONITOR priority to run last and clean up
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        try {
            logger.fine("Processing quit event for " + event.getPlayer().getName());
            permissionHandler.onPlayerQuit(event.getPlayer());

        } catch (Exception e) {
            logger.warning("Error processing quit for " + event.getPlayer().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Handles world change events to update context-specific permissions
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        try {
            String newWorldName = event.getPlayer().getWorld().getName();
            String oldWorldName = event.getFrom().getName();

            logger.fine(event.getPlayer().getName() + " changed from world " + oldWorldName + " to " + newWorldName);

            // Update permissions for new world context
            permissionHandler.onWorldChange(event.getPlayer(), newWorldName);

        } catch (Exception e) {
            logger.warning("Error processing world change for " + event.getPlayer().getName() + ": " + e.getMessage());
        }
    }
}