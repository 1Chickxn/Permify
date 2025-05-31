package me.chickxn.permify.data.interfaces;

import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Accessors(fluent = true)
public interface StorageInterface {

    // === Player Data Operations ===

    /**
     * Loads a player's data from storage
     * @param uuid the player's UUID
     * @return PlayerInterface instance or null if not found
     */
    @Nullable
    PlayerInterface loadPlayer(@NotNull UUID uuid);

    /**
     * Saves a player's complete data to storage
     * @param player the player to save
     * @return true if saved successfully
     */
    boolean savePlayer(@NotNull PlayerInterface player);

    /**
     * Creates a new player entry in storage
     * @param uuid the player's UUID
     * @param name the player's name
     * @return newly created PlayerInterface
     */
    @NotNull
    PlayerInterface createPlayer(@NotNull UUID uuid, @NotNull String name);

    /**
     * Deletes a player from storage completely
     * @param uuid the player's UUID
     * @return true if deleted successfully
     */
    boolean deletePlayer(@NotNull UUID uuid);

    /**
     * Gets all player UUIDs from storage
     * @return set of all stored player UUIDs
     */
    @NotNull
    Set<UUID> getAllPlayerUUIDs();

    // === Group Data Operations ===

    /**
     * Loads a group's data from storage
     * @param name the group name
     * @return GroupInterface instance or null if not found
     */
    @Nullable
    GroupInterface loadGroup(@NotNull String name);

    /**
     * Loads a group's data from storage by ID
     * @param id the group ID
     * @return GroupInterface instance or null if not found
     */
    @Nullable
    GroupInterface loadGroup(int id);

    /**
     * Saves a group's complete data to storage
     * @param group the group to save
     * @return true if saved successfully
     */
    boolean saveGroup(@NotNull GroupInterface group);

    /**
     * Creates a new group in storage
     * @param name the group name
     * @return newly created GroupInterface with unique ID
     */
    @NotNull
    GroupInterface createGroup(@NotNull String name);

    /**
     * Deletes a group from storage completely
     * @param group the group to delete
     * @return true if deleted successfully
     */
    boolean deleteGroup(@NotNull GroupInterface group);

    /**
     * Gets all group names from storage
     * @return set of all stored group names
     */
    @NotNull
    Set<String> getAllGroupNames();

    /**
     * Gets all groups from storage
     * @return list of all stored groups
     */
    @NotNull
    List<GroupInterface> getAllGroups();

    /**
     * Gets the next available group ID
     * @return next unique group ID
     */
    int getNextGroupId();

    // === Permission Operations ===

    /**
     * Adds a direct permission to a player
     * @param player the player
     * @param permission the permission to add
     * @return true if added successfully
     */
    boolean addPlayerPermission(@NotNull PlayerInterface player, @NotNull String permission);

    /**
     * Removes a direct permission from a player
     * @param player the player
     * @param permission the permission to remove
     * @return true if removed successfully
     */
    boolean removePlayerPermission(@NotNull PlayerInterface player, @NotNull String permission);

    /**
     * Adds a permission to a group
     * @param group the group
     * @param permission the permission to add
     * @return true if added successfully
     */
    boolean addGroupPermission(@NotNull GroupInterface group, @NotNull String permission);

    /**
     * Removes a permission from a group
     * @param group the group
     * @param permission the permission to remove
     * @return true if removed successfully
     */
    boolean removeGroupPermission(@NotNull GroupInterface group, @NotNull String permission);

    // === Group Membership Operations ===

    /**
     * Adds a player to a group
     * @param player the player
     * @param group the group
     * @return true if added successfully
     */
    boolean addPlayerToGroup(@NotNull PlayerInterface player, @NotNull GroupInterface group);

    /**
     * Removes a player from a group
     * @param player the player
     * @param group the group
     * @return true if removed successfully
     */
    boolean removePlayerFromGroup(@NotNull PlayerInterface player, @NotNull GroupInterface group);

    /**
     * Sets a player's primary group (removes from all others)
     * @param player the player
     * @param group the new primary group
     * @return true if set successfully
     */
    boolean setPlayerPrimaryGroup(@NotNull PlayerInterface player, @NotNull GroupInterface group);

    // === Group Hierarchy Operations ===

    /**
     * Adds a parent group to a group
     * @param childGroup the child group
     * @param parentGroup the parent group
     * @return true if added successfully
     */
    boolean addGroupParent(@NotNull GroupInterface childGroup, @NotNull GroupInterface parentGroup);

    /**
     * Removes a parent group from a group
     * @param childGroup the child group
     * @param parentGroup the parent group to remove
     * @return true if removed successfully
     */
    boolean removeGroupParent(@NotNull GroupInterface childGroup, @NotNull GroupInterface parentGroup);

    // === Temporary Permissions ===

    /**
     * Adds a temporary permission to a player
     * @param player the player
     * @param permission the permission
     * @param expiry when the permission expires
     * @return true if added successfully
     */
    boolean addTemporaryPlayerPermission(@NotNull PlayerInterface player, @NotNull String permission, @NotNull LocalDateTime expiry);

    /**
     * Adds a temporary group membership to a player
     * @param player the player
     * @param group the group
     * @param expiry when the membership expires
     * @return true if added successfully
     */
    boolean addTemporaryPlayerGroup(@NotNull PlayerInterface player, @NotNull GroupInterface group, @NotNull LocalDateTime expiry);

    /**
     * Removes expired temporary permissions and groups
     * @return number of expired entries removed
     */
    int cleanupExpiredEntries();

    // === Context-Specific Permissions ===

    /**
     * Adds a server-specific permission to a player
     * @param player the player
     * @param serverName the server name
     * @param permission the permission
     * @return true if added successfully
     */
    boolean addPlayerServerPermission(@NotNull PlayerInterface player, @NotNull String serverName, @NotNull String permission);

    /**
     * Adds a world-specific permission to a player
     * @param player the player
     * @param worldName the world name
     * @param permission the permission
     * @return true if added successfully
     */
    boolean addPlayerWorldPermission(@NotNull PlayerInterface player, @NotNull String worldName, @NotNull String permission);

    // === Display Properties ===

    /**
     * Updates a player's custom display properties
     * @param player the player
     * @param customPrefix custom prefix (null to use group prefix)
     * @param customSuffix custom suffix (null to use group suffix)
     * @param customColor custom color (null to use group color)
     * @return true if updated successfully
     */
    boolean updatePlayerDisplayProperties(@NotNull PlayerInterface player,
                                          @Nullable String customPrefix,
                                          @Nullable String customSuffix,
                                          @Nullable net.kyori.adventure.text.format.NamedTextColor customColor);

    /**
     * Updates a group's display properties
     * @param group the group
     * @param displayName display name
     * @param prefix prefix
     * @param suffix suffix
     * @param color color
     * @param priority priority
     * @return true if updated successfully
     */
    boolean updateGroupDisplayProperties(@NotNull GroupInterface group,
                                         @Nullable String displayName,
                                         @Nullable String prefix,
                                         @Nullable String suffix,
                                         @Nullable net.kyori.adventure.text.format.NamedTextColor color,
                                         int priority);

    // === Permission History ===

    /**
     * Logs a permission change
     * @param targetUuid target player/group UUID
     * @param action the action performed
     * @param target the permission or group affected
     * @param actor who performed the action
     * @param reason optional reason
     */
    void logPermissionChange(@NotNull UUID targetUuid, @NotNull String action, @NotNull String target,
                             @Nullable String actor, @Nullable String reason);

    /**
     * Gets permission history for a player
     * @param player the player
     * @param limit maximum number of entries
     * @return list of log entries, newest first
     */
    @NotNull
    List<PlayerInterface.PermissionLogEntry> getPlayerPermissionHistory(@NotNull PlayerInterface player, int limit);

    // === Metadata Operations ===

    /**
     * Updates player metadata (join times, playtime, etc.)
     * @param player the player
     * @param firstJoin first join time
     * @param lastSeen last seen time
     * @param playtimeSeconds total playtime
     * @return true if updated successfully
     */
    boolean updatePlayerMetadata(@NotNull PlayerInterface player,
                                 @Nullable LocalDateTime firstJoin,
                                 @Nullable LocalDateTime lastSeen,
                                 long playtimeSeconds);

    // === Bulk Operations ===

    /**
     * Saves multiple players in a batch operation
     * @param players the players to save
     * @return true if all saved successfully
     */
    boolean savePlayers(@NotNull List<PlayerInterface> players);

    /**
     * Saves multiple groups in a batch operation
     * @param groups the groups to save
     * @return true if all saved successfully
     */
    boolean saveGroups(@NotNull List<GroupInterface> groups);

    // === Storage Management ===

    /**
     * Performs storage maintenance (cleanup, optimization, etc.)
     * @return true if maintenance completed successfully
     */
    boolean performMaintenance();

    /**
     * Checks if the storage is healthy and responsive
     * @return true if storage is healthy
     */
    boolean isHealthy();

    /**
     * Gets storage statistics
     * @return map of statistic names to values
     */
    @NotNull
    Map<String, Object> getStorageStats();
}