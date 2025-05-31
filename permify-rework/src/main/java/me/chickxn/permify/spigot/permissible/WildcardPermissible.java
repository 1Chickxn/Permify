package me.chickxn.permify.spigot.permissible;

import org.bukkit.entity.Player;
import org.bukkit.permissions.*;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Enhanced WildcardPermissible that properly handles tab completion and permission inheritance
 * This class extends PermissibleBase to provide wildcard permission support while maintaining
 * compatibility with Minecraft's tab completion system.
 */
public class WildcardPermissible extends PermissibleBase {

    private final Player player;
    private final Logger logger;

    // Cache for performance
    private Set<String> cachedPermissions;
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 1000; // 1 second cache

    /**
     * Creates a new WildcardPermissible for the specified player
     * @param player the player this permissible belongs to
     */
    public WildcardPermissible(@NotNull Player player) {
        super(player);
        this.player = player;
        this.logger = Logger.getLogger("Permify-WildcardPermissible");
    }

    // ===================================================================================================
    // CORE PERMISSION METHODS
    // ===================================================================================================

    @Override
    public boolean hasPermission(@NotNull String permission) {
        try {
            Set<String> effectivePermissions = getAllEffectivePermissions();

            // Check for explicit denial first (highest priority)
            if (isDenied(permission, effectivePermissions)) {
                return false;
            }

            // Check for explicit permission
            if (effectivePermissions.contains(permission)) {
                return true;
            }

            // Check for universal wildcard
            if (effectivePermissions.contains("*")) {
                return true;
            }

            // Check wildcard patterns
            if (matchesWildcard(permission, effectivePermissions)) {
                return true;
            }

            // Fall back to Bukkit's default behavior for built-in permissions
            return super.hasPermission(permission);

        } catch (Exception e) {
            logger.warning("Error checking permission '" + permission + "' for player " + player.getName() + ": " + e.getMessage());
            // Fall back to super implementation on error
            return super.hasPermission(permission);
        }
    }

    @Override
    public boolean hasPermission(@NotNull Permission perm) {
        // Handle Permission objects properly
        if (perm == null) {
            return false;
        }

        // Check our custom logic first
        boolean hasCustom = hasPermission(perm.getName());

        // For default permissions, also check the original implementation
        if (!hasCustom && perm.getDefault() != PermissionDefault.FALSE) {
            return super.hasPermission(perm);
        }

        return hasCustom;
    }

    @Override
    public boolean isPermissionSet(@NotNull String permission) {
        try {
            Set<String> effectivePermissions = getAllEffectivePermissions();

            // Check if explicitly set (positive or negative)
            if (effectivePermissions.contains(permission) || effectivePermissions.contains("-" + permission)) {
                return true;
            }

            // Check if covered by wildcard
            if (effectivePermissions.contains("*")) {
                return true;
            }

            // Check if covered by wildcard patterns
            if (matchesWildcard(permission, effectivePermissions) || isDenied(permission, effectivePermissions)) {
                return true;
            }

            // Check original permissible for built-in permissions
            return super.isPermissionSet(permission);

        } catch (Exception e) {
            logger.warning("Error checking if permission '" + permission + "' is set for player " + player.getName() + ": " + e.getMessage());
            return super.isPermissionSet(permission);
        }
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) {
        if (perm == null) {
            return false;
        }
        return isPermissionSet(perm.getName());
    }

    // ===================================================================================================
    // PERMISSION ATTACHMENT MANAGEMENT
    // ===================================================================================================

    @Override
    public void recalculatePermissions() {
        // Clear our cache when permissions are recalculated
        clearCache();

        // Call super to maintain Bukkit's internal state
        super.recalculatePermissions();
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        // Clear cache when attachments are added
        clearCache();
        return super.addAttachment(plugin, name, value);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        clearCache();
        return super.addAttachment(plugin);
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {
        clearCache();
        super.removeAttachment(attachment);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value, int ticks) {
        clearCache();
        return super.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
        clearCache();
        return super.addAttachment(plugin, ticks);
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        // Return the super implementation to maintain compatibility with tab completion
        // This ensures that Minecraft's tab completion system gets the raw permission data
        return super.getEffectivePermissions();
    }

    // ===================================================================================================
    // WILDCARD PERMISSION LOGIC
    // ===================================================================================================

    /**
     * Checks if a permission matches any wildcard patterns in the effective permissions
     * @param permission the permission to check
     * @param effectivePermissions the set of effective permissions to check against
     * @return true if the permission matches a wildcard pattern
     */
    private boolean matchesWildcard(@NotNull String permission, @NotNull Set<String> effectivePermissions) {
        String[] parts = permission.split("\\.");
        StringBuilder checkPerm = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                checkPerm.append(".");
            }
            checkPerm.append(parts[i]);

            String wildcard = checkPerm + ".*";

            // Check for explicit denial of this wildcard level
            if (effectivePermissions.contains("-" + wildcard)) {
                return false;
            }

            // Check for positive wildcard match
            if (effectivePermissions.contains(wildcard)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a permission is explicitly denied
     * @param permission the permission to check
     * @param effectivePermissions the set of effective permissions to check against
     * @return true if the permission is denied
     */
    private boolean isDenied(@NotNull String permission, @NotNull Set<String> effectivePermissions) {
        // Check explicit denial
        if (effectivePermissions.contains("-" + permission)) {
            return true;
        }

        // Check wildcard denials
        String[] parts = permission.split("\\.");
        StringBuilder checkPerm = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                checkPerm.append(".");
            }
            checkPerm.append(parts[i]);

            // Check for denial at this level
            if (effectivePermissions.contains("-" + checkPerm.toString()) ||
                    effectivePermissions.contains("-" + checkPerm + ".*")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets all effective permissions for this player, including wildcard expansions
     * This method uses caching for performance
     * @return a set of all effective permissions
     */
    private @NotNull Set<String> getAllEffectivePermissions() {
        long currentTime = System.currentTimeMillis();
        if (cachedPermissions != null && (currentTime - lastCacheUpdate) < CACHE_DURATION) {
            return cachedPermissions;
        }

        Set<String> permissions = new HashSet<>();

        // Get permissions from Bukkit's attachment system
        for (PermissionAttachmentInfo info : super.getEffectivePermissions()) {
            String permissionName = info.getPermission();
            permissions.add(info.getValue() ? permissionName : "-" + permissionName);
        }

        // Handle universal wildcard expansion
        if (permissions.contains("*")) {
            addCommonWildcards(permissions);
        }

        cachedPermissions = permissions;
        lastCacheUpdate = currentTime;

        return permissions;
    }

    /**
     * Adds common wildcard expansions for better tab completion support
     * This is called when the player has the universal wildcard (*)
     * @param permissions the permission set to add wildcards to
     */
    private void addCommonWildcards(@NotNull Set<String> permissions) {
        // Add common wildcard expansions for better tab completion
        permissions.add("minecraft.command.*");
        permissions.add("bukkit.command.*");
        permissions.add("spigot.command.*");
        permissions.add("paper.command.*");

        // Essential Minecraft commands for tab completion
        String[] essentialCommands = {
                // Basic commands that players should always be able to tab-complete
                "minecraft.command.help",
                "minecraft.command.me",
                "minecraft.command.tell",
                "minecraft.command.msg",
                "minecraft.command.say",
                "minecraft.command.list",

                // Admin commands (for tab completion when they have *)
                "minecraft.command.give",
                "minecraft.command.teleport",
                "minecraft.command.tp",
                "minecraft.command.gamemode",
                "minecraft.command.time",
                "minecraft.command.weather",
                "minecraft.command.worldborder",
                "minecraft.command.fill",
                "minecraft.command.setblock",
                "minecraft.command.summon",
                "minecraft.command.kill",
                "minecraft.command.clear",
                "minecraft.command.effect",
                "minecraft.command.enchant",
                "minecraft.command.experience",
                "minecraft.command.xp",
                "minecraft.command.particle",
                "minecraft.command.playsound",
                "minecraft.command.stopsound",
                "minecraft.command.title",
                "minecraft.command.tellraw",
                "minecraft.command.difficulty",
                "minecraft.command.gamerule",
                "minecraft.command.defaultgamemode",
                "minecraft.command.seed",
                "minecraft.command.setworldspawn",
                "minecraft.command.spawnpoint",
                "minecraft.command.spreadplayers",
                "minecraft.command.scoreboard",
                "minecraft.command.team",
                "minecraft.command.trigger",
                "minecraft.command.advancement",
                "minecraft.command.bossbar",
                "minecraft.command.data",
                "minecraft.command.datapack",
                "minecraft.command.debug",
                "minecraft.command.execute",
                "minecraft.command.forceload",
                "minecraft.command.function",
                "minecraft.command.locate",
                "minecraft.command.loot",
                "minecraft.command.recipe",
                "minecraft.command.reload",
                "minecraft.command.save-all",
                "minecraft.command.save-off",
                "minecraft.command.save-on",
                "minecraft.command.schedule",
                "minecraft.command.spectate",
                "minecraft.command.tag",
                "minecraft.command.team",
                "minecraft.command.teammsg",
                "minecraft.command.tm",
                "minecraft.command.w",
                "minecraft.command.worldborder",

                // Bukkit commands
                "bukkit.command.help",
                "bukkit.command.plugins",
                "bukkit.command.pl",
                "bukkit.command.version",
                "bukkit.command.ver",
                "bukkit.command.reload",
                "bukkit.command.rl",
                "bukkit.command.stop",
                "bukkit.command.restart",
                "bukkit.command.timings",
                "bukkit.command.tps",

                // Server commands
                "server.command.op",
                "server.command.deop",
                "server.command.whitelist",
                "server.command.ban",
                "server.command.ban-ip",
                "server.command.pardon",
                "server.command.pardon-ip",
                "server.command.kick",

                // Essential for tab completion functionality
                "bukkit.command.tabcomplete",

                // Paper specific commands (if available)
                "bukkit.command.paper",
                "bukkit.command.mspt"
        };

        for (String perm : essentialCommands) {
            permissions.add(perm);
        }
    }

    // ===================================================================================================
    // CACHE MANAGEMENT
    // ===================================================================================================

    /**
     * Clears the permission cache, forcing a refresh on next access
     */
    public void clearCache() {
        cachedPermissions = null;
        lastCacheUpdate = 0;
    }

    /**
     * Forces a permission cache refresh and recalculates permissions
     */
    public void forceRefresh() {
        clearCache();
        recalculatePermissions();
    }

    // ===================================================================================================
    // DEBUG AND UTILITY METHODS
    // ===================================================================================================

    /**
     * Gets debug information about this permissible
     * @return a string containing debug information
     */
    public @NotNull String getDebugInfo() {
        Set<String> perms = getAllEffectivePermissions();
        return String.format("WildcardPermissible[player=%s, permissions=%d, cached=%s, cacheAge=%dms]",
                player.getName(),
                perms.size(),
                cachedPermissions != null,
                cachedPermissions != null ? (System.currentTimeMillis() - lastCacheUpdate) : 0);
    }

    /**
     * Gets the player this permissible belongs to
     * @return the player
     */
    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * Checks if the permission cache is currently valid
     * @return true if the cache is valid and can be used
     */
    public boolean isCacheValid() {
        return cachedPermissions != null &&
                (System.currentTimeMillis() - lastCacheUpdate) < CACHE_DURATION;
    }

    /**
     * Gets the current cache size (number of cached permissions)
     * @return the number of cached permissions, or 0 if cache is invalid
     */
    public int getCacheSize() {
        return cachedPermissions != null ? cachedPermissions.size() : 0;
    }

    /**
     * Gets a copy of the currently cached permissions
     * @return a set of cached permissions, or empty set if cache is invalid
     */
    public @NotNull Set<String> getCachedPermissions() {
        if (isCacheValid()) {
            return new HashSet<>(cachedPermissions);
        }
        return new HashSet<>();
    }

    @Override
    public String toString() {
        return String.format("WildcardPermissible{player=%s, attachments=%d, cached=%s}",
                player.getName(),
                getEffectivePermissions().size(),
                isCacheValid());
    }
}