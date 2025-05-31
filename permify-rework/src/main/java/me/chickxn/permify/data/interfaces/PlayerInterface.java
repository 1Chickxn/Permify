package me.chickxn.permify.data.interfaces;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Interface for players in the Permify permission system.
 * Represents a player with permissions, groups, and display properties.
 */
public interface PlayerInterface {

    // === Basic Properties ===

    /**
     * Gets the player's current name.
     * @return the player name, never null
     */
    @NotNull
    String getName();

    /**
     * Gets the player's unique identifier.
     * @return the player UUID, never null
     */
    @NotNull
    UUID getUuid();

    /**
     * Checks if this player is currently online.
     * @return true if the player is online
     */
    boolean isOnline();

    // === Direct Permission Management ===

    /**
     * Gets all permissions directly assigned to this player (not from groups).
     * @return unmodifiable set of direct permissions
     */
    @NotNull
    Set<String> getDirectPermissions();

    /**
     * Gets all effective permissions (direct + inherited from groups).
     * @return unmodifiable set of all permissions
     */
    @NotNull
    Set<String> getAllPermissions();

    /**
     * Checks if the player has a specific permission.
     * @param permission the permission to check
     * @return true if the player has this permission
     */
    boolean hasPermission(@NotNull String permission);

    /**
     * Adds a direct permission to this player.
     * @param permission the permission to add
     * @return true if the permission was added, false if already existed
     */
    boolean addPermission(@NotNull String permission);

    /**
     * Removes a direct permission from this player.
     * @param permission the permission to remove
     * @return true if the permission was removed, false if didn't exist
     */
    boolean removePermission(@NotNull String permission);

    /**
     * Adds multiple permissions to this player.
     * @param permissions the permissions to add
     */
    default void addPermissions(@NotNull String... permissions) {
        for (String permission : permissions) {
            addPermission(permission);
        }
    }

    /**
     * Removes multiple permissions from this player.
     * @param permissions the permissions to remove
     */
    default void removePermissions(@NotNull String... permissions) {
        for (String permission : permissions) {
            removePermission(permission);
        }
    }

    /**
     * Adds a temporary permission that expires after a duration.
     * @param permission the permission to add
     * @param durationSeconds duration in seconds
     * @return true if added successfully
     */
    boolean addTemporaryPermission(@NotNull String permission, long durationSeconds);

    /**
     * Gets all temporary permissions and their expiry times.
     * @return map of permissions to expiry timestamps
     */
    @NotNull
    java.util.Map<String, LocalDateTime> getTemporaryPermissions();

    // === Group Management ===

    /**
     * Gets all groups this player belongs to, ordered by priority.
     * @return unmodifiable list of groups (highest priority first)
     */
    @NotNull
    List<GroupInterface> getGroups();

    /**
     * Gets the primary group (highest priority group).
     * @return the primary group, or empty if no groups assigned
     */
    @NotNull
    Optional<GroupInterface> getPrimaryGroup();

    /**
     * Checks if the player is in a specific group.
     * @param group the group to check
     * @return true if the player is in this group
     */
    boolean hasGroup(@NotNull GroupInterface group);

    /**
     * Checks if the player is in a group with the specified name.
     * @param groupName the group name to check
     * @return true if the player is in a group with this name
     */
    boolean hasGroup(@NotNull String groupName);

    /**
     * Adds the player to a group.
     * @param group the group to add
     * @return true if added successfully, false if already in group
     */
    boolean addGroup(@NotNull GroupInterface group);

    /**
     * Removes the player from a group.
     * @param group the group to remove
     * @return true if removed successfully, false if not in group
     */
    boolean removeGroup(@NotNull GroupInterface group);

    /**
     * Sets the player's primary group, removing all other groups.
     * @param group the new primary group
     */
    void setPrimaryGroup(@NotNull GroupInterface group);

    /**
     * Adds a temporary group membership that expires after a duration.
     * @param group the group to add
     * @param durationSeconds duration in seconds
     * @return true if added successfully
     */
    boolean addTemporaryGroup(@NotNull GroupInterface group, long durationSeconds);

    /**
     * Gets all temporary groups and their expiry times.
     * @return map of groups to expiry timestamps
     */
    @NotNull
    java.util.Map<GroupInterface, LocalDateTime> getTemporaryGroups();

    // === Display Properties ===

    /**
     * Gets the effective prefix for this player (from primary group or direct).
     * @return the prefix, may be null
     */
    @Nullable
    String getPrefix();

    /**
     * Gets the effective suffix for this player (from primary group or direct).
     * @return the suffix, may be null
     */
    @Nullable
    String getSuffix();

    /**
     * Gets the effective color for this player (from primary group or direct).
     * @return the color, may be null
     */
    @Nullable
    NamedTextColor getColor();

    /**
     * Gets the display name with prefix and suffix applied.
     * @return formatted display name component
     */
    @NotNull
    Component getDisplayName();

    /**
     * Sets a custom prefix for this player (overrides group prefix).
     * @param prefix the custom prefix, null to use group prefix
     */
    void setCustomPrefix(@Nullable String prefix);

    /**
     * Sets a custom suffix for this player (overrides group suffix).
     * @param suffix the custom suffix, null to use group suffix
     */
    void setCustomSuffix(@Nullable String suffix);

    /**
     * Sets a custom color for this player (overrides group color).
     * @param color the custom color, null to use group color
     */
    void setCustomColor(@Nullable NamedTextColor color);

    // === Metadata ===

    /**
     * Gets when this player first joined.
     * @return first join timestamp, may be null for legacy players
     */
    @Nullable
    LocalDateTime getFirstJoin();

    /**
     * Gets when this player was last seen.
     * @return last seen timestamp, may be null
     */
    @Nullable
    LocalDateTime getLastSeen();

    /**
     * Gets when this player's permissions were last modified.
     * @return last modification timestamp, may be null
     */
    @Nullable
    LocalDateTime getLastModified();

    /**
     * Gets the total playtime in seconds.
     * @return playtime in seconds
     */
    long getPlaytimeSeconds();

    // === Server/World Context ===

    /**
     * Gets permissions specific to a server (for multi-server setups).
     * @param serverName the server name
     * @return unmodifiable set of server-specific permissions
     */
    @NotNull
    Set<String> getServerPermissions(@NotNull String serverName);

    /**
     * Gets permissions specific to a world.
     * @param worldName the world name
     * @return unmodifiable set of world-specific permissions
     */
    @NotNull
    Set<String> getWorldPermissions(@NotNull String worldName);

    /**
     * Adds a server-specific permission.
     * @param serverName the server name
     * @param permission the permission to add
     * @return true if added successfully
     */
    boolean addServerPermission(@NotNull String serverName, @NotNull String permission);

    /**
     * Adds a world-specific permission.
     * @param worldName the world name
     * @param permission the permission to add
     * @return true if added successfully
     */
    boolean addWorldPermission(@NotNull String worldName, @NotNull String permission);

    // === Utility Methods ===

    /**
     * Refreshes the player's permissions (useful after external changes).
     */
    void refreshPermissions();

    /**
     * Saves any pending changes to persistent storage.
     * @return true if saved successfully
     */
    boolean save();

    /**
     * Reloads this player's data from persistent storage.
     */
    void reload();

    /**
     * Clears all permissions and groups (admin function).
     */
    void reset();

    /**
     * Gets a summary of this player's permission status.
     * @return formatted summary component
     */
    @NotNull
    Component getPermissionSummary();

    /**
     * Checks if this player data is valid.
     * @return true if the player data is valid
     */
    default boolean isValid() {
        return getName() != null && !getName().isEmpty() && getUuid() != null;
    }

    // === Permission History (Optional) ===

    /**
     * Gets the permission history for this player.
     * @return list of permission changes, newest first
     */
    @NotNull
    List<PermissionLogEntry> getPermissionHistory();

    /**
     * Represents a single permission change in the history.
     */
    interface PermissionLogEntry {
        @NotNull LocalDateTime getTimestamp();
        @NotNull String getAction(); // "ADD", "REMOVE", "GROUP_ADD", etc.
        @NotNull String getTarget(); // permission or group name
        @Nullable String getActor(); // who made the change
        @Nullable String getReason(); // optional reason
    }
}