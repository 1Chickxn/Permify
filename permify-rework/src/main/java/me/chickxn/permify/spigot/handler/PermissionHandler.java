package me.chickxn.permify.spigot.handler;

import lombok.SneakyThrows;
import me.chickxn.permify.data.interfaces.GroupInterface;
import me.chickxn.permify.data.interfaces.PlayerInterface;
import me.chickxn.permify.data.storage.StorageHandler;
import me.chickxn.permify.spigot.permissible.WildcardPermissible;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles permission injection and management for online players
 */
public class PermissionHandler {

    private final Plugin plugin;
    private final Logger logger;

    // Player attachment management
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<UUID, WildcardPermissible> wildcardPermissibles = new ConcurrentHashMap<>();

    // Permission caching
    private final Map<UUID, Set<String>> permissionCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 5000; // 5 seconds cache

    // Context tracking
    private final Map<UUID, String> playerWorlds = new ConcurrentHashMap<>();
    private String serverName;

    public PermissionHandler(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.serverName = plugin.getConfig().getString("server-name", "default");

        // Start cleanup task for expired permissions
        startCleanupTask();
    }

    // ===================================================================================================
    // PERMISSION ATTACHMENT MANAGEMENT
    // ===================================================================================================

    /**
     * Gets or creates a permission attachment for a player
     */
    @NotNull
    private PermissionAttachment getAttachment(@NotNull Player player) {
        return attachments.computeIfAbsent(player.getUniqueId(), uuid -> {
            logger.fine("Creating permission attachment for " + player.getName());
            return player.addAttachment(plugin);
        });
    }

    /**
     * Removes permission attachment for a player
     */
    public void removeAttachment(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
                logger.fine("Removed permission attachment for " + player.getName());
            } catch (Exception e) {
                logger.warning("Failed to remove attachment for " + player.getName() + ": " + e.getMessage());
            }
        }

        // Clean up other data
        wildcardPermissibles.remove(uuid);
        permissionCache.remove(uuid);
        cacheTimestamps.remove(uuid);
        playerWorlds.remove(uuid);
    }

    /**
     * Updates all permissions for a player
     */
    public CompletableFuture<Void> updatePlayerPermissions(@NotNull Player player) {
        return CompletableFuture.runAsync(() -> {
            try {
                UUID uuid = player.getUniqueId();

                // Update world tracking
                playerWorlds.put(uuid, player.getWorld().getName());

                // Get attachment
                PermissionAttachment attachment = getAttachment(player);
                if (attachment == null) {
                    logger.warning("Could not get permission attachment for " + player.getName());
                    return;
                }

                // Clear existing permissions
                Set<String> currentPerms = new HashSet<>(attachment.getPermissions().keySet());
                currentPerms.forEach(attachment::unsetPermission);

                // Get all effective permissions
                Set<String> permissions = getAllEffectivePermissions(player);

                // Apply permissions
                for (String perm : permissions) {
                    if (perm.startsWith("-")) {
                        // Negative permission
                        String actualPerm = perm.substring(1);
                        attachment.setPermission(actualPerm, false);
                    } else {
                        // Positive permission
                        attachment.setPermission(perm, true);
                    }
                }

                // Update cache
                permissionCache.put(uuid, new HashSet<>(permissions));
                cacheTimestamps.put(uuid, System.currentTimeMillis());

                // Recalculate permissions
                player.recalculatePermissions();

                // Clear wildcard permissible cache if exists
                WildcardPermissible wp = wildcardPermissibles.get(uuid);
                if (wp != null) {
                    wp.clearCache();
                }

                logger.fine("Updated " + permissions.size() + " permissions for " + player.getName());

            } catch (Exception e) {
                logger.severe("Error updating permissions for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Updates permissions for all online players
     */
    public CompletableFuture<Void> updateAllPlayerPermissions() {
        return CompletableFuture.runAsync(() -> {
            logger.info("Updating permissions for all online players...");

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                futures.add(updatePlayerPermissions(player));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            logger.info("Finished updating permissions for " + futures.size() + " players");
        });
    }

    // ===================================================================================================
    // PERMISSION CALCULATION
    // ===================================================================================================

    /**
     * Gets all effective permissions for a player (with caching)
     */
    @NotNull
    public Set<String> getAllEffectivePermissions(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        // Check cache first
        if (isCacheValid(uuid)) {
            return new HashSet<>(permissionCache.get(uuid));
        }

        Set<String> permissions = calculateEffectivePermissions(player);

        // Update cache
        permissionCache.put(uuid, new HashSet<>(permissions));
        cacheTimestamps.put(uuid, System.currentTimeMillis());

        return permissions;
    }

    /**
     * Calculates effective permissions without caching
     */
    @NotNull
    private Set<String> calculateEffectivePermissions(@NotNull Player player) {
        if (StorageHandler.getActiveStorage() == null) {
            logger.warning("No active storage - cannot calculate permissions for " + player.getName());
            return Collections.emptySet();
        }

        try {
            PlayerInterface playerData = StorageHandler.getActiveStorage().loadPlayer(player.getUniqueId());
            if (playerData == null) {
                logger.fine("No player data found for " + player.getName() + " - creating new player");
                playerData = StorageHandler.getActiveStorage().createPlayer(player.getUniqueId(), player.getName());
            }

            Set<String> permissions = new LinkedHashSet<>();

            // 1. Add direct player permissions
            permissions.addAll(playerData.getDirectPermissions());

            // 2. Add group permissions (by priority)
            List<GroupInterface> groups = playerData.getGroups();
            for (GroupInterface group : groups) {
                permissions.addAll(group.getAllPermissions()); // This includes inherited permissions
            }

            // 3. Add temporary permissions (check expiry)
            Map<String, LocalDateTime> tempPerms = playerData.getTemporaryPermissions();
            LocalDateTime now = LocalDateTime.now();
            for (Map.Entry<String, LocalDateTime> entry : tempPerms.entrySet()) {
                if (entry.getValue().isAfter(now)) {
                    permissions.add(entry.getKey());
                }
            }

            // 4. Add context-specific permissions
            String worldName = player.getWorld().getName();
            permissions.addAll(playerData.getWorldPermissions(worldName));
            permissions.addAll(playerData.getServerPermissions(serverName));

            // 5. Process negative permissions (remove conflicts)
            Set<String> processedPermissions = processNegativePermissions(permissions);

            logger.fine("Calculated " + processedPermissions.size() + " effective permissions for " + player.getName());
            return processedPermissions;

        } catch (Exception e) {
            logger.severe("Error calculating permissions for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    /**
     * Processes negative permissions and resolves conflicts
     */
    @NotNull
    private Set<String> processNegativePermissions(@NotNull Set<String> rawPermissions) {
        Set<String> positivePerms = new HashSet<>();
        Set<String> negativePerms = new HashSet<>();

        // Separate positive and negative permissions
        for (String perm : rawPermissions) {
            if (perm.startsWith("-")) {
                negativePerms.add(perm.substring(1));
            } else {
                positivePerms.add(perm);
            }
        }

        // Remove negated permissions
        positivePerms.removeAll(negativePerms);

        // Add back negative permissions with prefix for attachment
        Set<String> finalPermissions = new HashSet<>(positivePerms);
        for (String negPerm : negativePerms) {
            finalPermissions.add("-" + negPerm);
        }

        return finalPermissions;
    }

    /**
     * Checks if a specific player has a permission
     */
    public boolean hasPermission(@NotNull Player player, @NotNull String permission) {
        Set<String> permissions = getAllEffectivePermissions(player);

        // Check direct permission
        if (permissions.contains(permission)) {
            return true;
        }

        // Check negative permission
        if (permissions.contains("-" + permission)) {
            return false;
        }

        // Check wildcard permissions
        return checkWildcardPermission(permission, permissions);
    }

    /**
     * Checks wildcard permissions
     */
    private boolean checkWildcardPermission(@NotNull String permission, @NotNull Set<String> permissions) {
        // Check for exact wildcard match
        if (permissions.contains("*")) {
            return true;
        }

        // Check hierarchical wildcards
        String[] parts = permission.split("\\.");
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append(".");
            builder.append(parts[i]);

            String wildcard = builder.toString() + ".*";
            String negativeWildcard = "-" + wildcard;

            if (permissions.contains(negativeWildcard)) {
                return false;
            }
            if (permissions.contains(wildcard)) {
                return true;
            }
        }

        return false;
    }

    // ===================================================================================================
    // WILDCARD PERMISSIBLE INJECTION
    // ===================================================================================================

    /**
     * Injects custom WildcardPermissible into a player
     */
    @SneakyThrows
    public boolean injectWildcardPermissible(@NotNull Player player) {
        try {
            Field field = findPermissibleField(player.getClass());
            if (field == null) {
                logger.warning("Could not find PermissibleBase field for " + player.getName());
                return false;
            }

            WildcardPermissible wp = new WildcardPermissible(player);
            field.set(player, wp);

            wildcardPermissibles.put(player.getUniqueId(), wp);
            logger.fine("Injected WildcardPermissible for " + player.getName());
            return true;

        } catch (Exception e) {
            logger.severe("Failed to inject WildcardPermissible for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Finds the PermissibleBase field in player class hierarchy
     */
    @Nullable
    private static Field findPermissibleField(@NotNull Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType().getSimpleName().equals("PermissibleBase")) {
                field.setAccessible(true);
                return field;
            }
        }
        if (clazz.getSuperclass() != null) {
            return findPermissibleField(clazz.getSuperclass());
        }
        return null;
    }

    // ===================================================================================================
    // PLAYER LIFECYCLE MANAGEMENT
    // ===================================================================================================

    /**
     * Called when a player joins the server
     */
    public CompletableFuture<Void> onPlayerJoin(@NotNull Player player) {
        return CompletableFuture.runAsync(() -> {
            try {
                logger.fine("Processing join for " + player.getName());

                // Load/create player data
                PlayerInterface playerData = StorageHandler.getActiveStorage().loadPlayer(player.getUniqueId());
                if (playerData == null) {
                    logger.info("Creating new player data for " + player.getName());
                    playerData = StorageHandler.getActiveStorage().createPlayer(player.getUniqueId(), player.getName());

                    // Add to default groups
                    addToDefaultGroups(playerData);
                }

                // Update metadata
                StorageHandler.getActiveStorage().updatePlayerMetadata(
                        playerData,
                        playerData.getFirstJoin() != null ? playerData.getFirstJoin() : LocalDateTime.now(),
                        LocalDateTime.now(),
                        playerData.getPlaytimeSeconds()
                );

                // Inject wildcard permissible
                Bukkit.getScheduler().runTask(plugin, () -> {
                    injectWildcardPermissible(player);
                    updatePlayerPermissions(player);
                });

            } catch (Exception e) {
                logger.severe("Error processing join for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Called when a player leaves the server
     */
    public void onPlayerQuit(@NotNull Player player) {
        try {
            // Update last seen
            PlayerInterface playerData = StorageHandler.getActiveStorage().loadPlayer(player.getUniqueId());
            if (playerData != null) {
                StorageHandler.getActiveStorage().updatePlayerMetadata(
                        playerData,
                        playerData.getFirstJoin(),
                        LocalDateTime.now(),
                        playerData.getPlaytimeSeconds()
                );
            }

            // Clean up
            removeAttachment(player);

        } catch (Exception e) {
            logger.warning("Error processing quit for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Called when a player changes worlds
     */
    public void onWorldChange(@NotNull Player player, @NotNull String newWorld) {
        String oldWorld = playerWorlds.get(player.getUniqueId());
        if (!newWorld.equals(oldWorld)) {
            playerWorlds.put(player.getUniqueId(), newWorld);
            invalidateCache(player.getUniqueId());
            updatePlayerPermissions(player);
        }
    }

    // ===================================================================================================
    // UTILITY METHODS
    // ===================================================================================================

    /**
     * Adds a player to all default groups
     */
    private void addToDefaultGroups(@NotNull PlayerInterface playerData) {
        try {
            List<GroupInterface> allGroups = StorageHandler.getActiveStorage().getAllGroups();
            for (GroupInterface group : allGroups) {
                if (group.isDefault()) {
                    StorageHandler.getActiveStorage().addPlayerToGroup(playerData, group);
                    logger.fine("Added " + playerData.getName() + " to default group: " + group.getName());
                }
            }
        } catch (Exception e) {
            logger.warning("Error adding player to default groups: " + e.getMessage());
        }
    }

    /**
     * Checks if permission cache is valid for a player
     */
    private boolean isCacheValid(@NotNull UUID uuid) {
        Long timestamp = cacheTimestamps.get(uuid);
        return timestamp != null &&
                (System.currentTimeMillis() - timestamp) < CACHE_DURATION &&
                permissionCache.containsKey(uuid);
    }

    /**
     * Invalidates permission cache for a player
     */
    public void invalidateCache(@NotNull UUID uuid) {
        permissionCache.remove(uuid);
        cacheTimestamps.remove(uuid);

        // Also clear wildcard permissible cache
        WildcardPermissible wp = wildcardPermissibles.get(uuid);
        if (wp != null) {
            wp.clearCache();
        }
    }

    /**
     * Invalidates cache for all players
     */
    public void invalidateAllCaches() {
        permissionCache.clear();
        cacheTimestamps.clear();
        wildcardPermissibles.values().forEach(WildcardPermissible::clearCache);
    }

    /**
     * Gets permission statistics
     */
    @NotNull
    public Map<String, Object> getStatistics() {
        return Map.of(
                "attachments", attachments.size(),
                "cached_players", permissionCache.size(),
                "wildcard_permissibles", wildcardPermissibles.size(),
                "cache_hit_rate", calculateCacheHitRate()
        );
    }

    private double calculateCacheHitRate() {
        // Simple cache hit rate calculation - could be improved
        return permissionCache.isEmpty() ? 0.0 :
                Math.min(1.0, permissionCache.size() / (double) Math.max(1, Bukkit.getOnlinePlayers().size()));
    }

    /**
     * Starts cleanup task for expired permissions
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                if (StorageHandler.getActiveStorage() != null) {
                    int cleaned = StorageHandler.getActiveStorage().cleanupExpiredEntries();
                    if (cleaned > 0) {
                        logger.info("Cleaned up " + cleaned + " expired permission entries");
                        // Invalidate all caches since permissions might have changed
                        invalidateAllCaches();
                    }
                }
            } catch (Exception e) {
                logger.warning("Error during permission cleanup: " + e.getMessage());
            }
        }, 20L * 60L, 20L * 60L); // Run every minute
    }

    /**
     * Shuts down the permission handler
     */
    public void shutdown() {
        logger.info("Shutting down PermissionHandler...");

        // Remove all attachments
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeAttachment(player);
        }

        // Clear all data
        attachments.clear();
        wildcardPermissibles.clear();
        permissionCache.clear();
        cacheTimestamps.clear();
        playerWorlds.clear();

        logger.info("PermissionHandler shutdown complete");
    }
}