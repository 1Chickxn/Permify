package me.chickxn.permify.data.interfaces;

import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Interface for permission groups in the Permify system.
 * Represents a group with permissions, display properties, and metadata.
 */
public interface GroupInterface {

    // === Basic Properties ===

    /**
     * Gets the unique name/identifier of this group.
     * @return the group name, never null
     */
    @NotNull
    String getName();

    /**
     * Gets the unique numeric ID of this group.
     * @return the group ID
     */
    int getId();

    /**
     * Gets the display name of this group (may differ from internal name).
     * @return the display name, or the regular name if no display name is set
     */
    @NotNull
    default String getDisplayName() {
        return getName();
    }

    /**
     * Gets the priority/weight of this group for inheritance resolution.
     * Higher values = higher priority.
     * @return the group priority
     */
    int getPriority();

    // === Display Properties ===

    /**
     * Gets the prefix for this group.
     * @return the prefix, may be null if not set
     */
    @Nullable
    String getPrefix();

    /**
     * Gets the suffix for this group.
     * @return the suffix, may be null if not set
     */
    @Nullable
    String getSuffix();

    /**
     * Gets the primary color for this group.
     * @return the color, may be null if not set
     */
    @Nullable
    NamedTextColor getColor();

    // === Permission Management ===

    /**
     * Gets all permissions directly assigned to this group (not inherited).
     * @return unmodifiable set of permissions
     */
    @NotNull
    Set<String> getPermissions();

    /**
     * Gets all permissions including inherited ones from parent groups.
     * @return unmodifiable set of all effective permissions
     */
    @NotNull
    Set<String> getAllPermissions();

    /**
     * Checks if this group has a specific permission (including inherited).
     * @param permission the permission to check
     * @return true if the group has this permission
     */
    boolean hasPermission(@NotNull String permission);

    /**
     * Adds a permission to this group.
     * @param permission the permission to add
     * @return true if the permission was added, false if it already existed
     */
    boolean addPermission(@NotNull String permission);

    /**
     * Removes a permission from this group.
     * @param permission the permission to remove
     * @return true if the permission was removed, false if it didn't exist
     */
    boolean removePermission(@NotNull String permission);

    /**
     * Adds multiple permissions to this group.
     * @param permissions the permissions to add
     */
    default void addPermissions(@NotNull String... permissions) {
        for (String permission : permissions) {
            addPermission(permission);
        }
    }

    /**
     * Removes multiple permissions from this group.
     * @param permissions the permissions to remove
     */
    default void removePermissions(@NotNull String... permissions) {
        for (String permission : permissions) {
            removePermission(permission);
        }
    }

    // === Group Inheritance ===

    /**
     * Gets the parent groups that this group inherits from.
     * @return unmodifiable list of parent groups
     */
    @NotNull
    List<GroupInterface> getParentGroups();

    /**
     * Gets the child groups that inherit from this group.
     * @return unmodifiable list of child groups
     */
    @NotNull
    List<GroupInterface> getChildGroups();

    /**
     * Adds a parent group to inherit from.
     * @param group the parent group to add
     * @return true if added successfully, false if already a parent or would create cycle
     */
    boolean addParentGroup(@NotNull GroupInterface group);

    /**
     * Removes a parent group.
     * @param group the parent group to remove
     * @return true if removed successfully, false if wasn't a parent
     */
    boolean removeParentGroup(@NotNull GroupInterface group);

    /**
     * Checks if this group inherits from another group (directly or indirectly).
     * @param group the group to check
     * @return true if this group inherits from the specified group
     */
    boolean inheritsFrom(@NotNull GroupInterface group);

    // === Metadata ===

    /**
     * Gets when this group was created.
     * @return creation timestamp, may be null for legacy groups
     */
    @Nullable
    LocalDateTime getCreatedAt();

    /**
     * Gets when this group was last modified.
     * @return last modification timestamp, may be null
     */
    @Nullable
    LocalDateTime getLastModified();

    /**
     * Gets the description of this group.
     * @return description, may be null if not set
     */
    @Nullable
    String getDescription();

    /**
     * Checks if this group is a default group (automatically assigned to new players).
     * @return true if this is a default group
     */
    boolean isDefault();

    // === Modification Methods ===

    /**
     * Sets the display name of this group.
     * @param displayName the new display name
     */
    void setDisplayName(@Nullable String displayName);

    /**
     * Sets the priority of this group.
     * @param priority the new priority
     */
    void setPriority(int priority);

    /**
     * Sets the prefix for this group.
     * @param prefix the new prefix
     */
    void setPrefix(@Nullable String prefix);

    /**
     * Sets the suffix for this group.
     * @param suffix the new suffix
     */
    void setSuffix(@Nullable String suffix);

    /**
     * Sets the color for this group.
     * @param color the new color
     */
    void setColor(@Nullable NamedTextColor color);

    /**
     * Sets the description for this group.
     * @param description the new description
     */
    void setDescription(@Nullable String description);

    /**
     * Sets whether this group is a default group.
     * @param isDefault true to make this a default group
     */
    void setDefault(boolean isDefault);

    // === Utility Methods ===

    /**
     * Saves any pending changes to persistent storage.
     * @return true if saved successfully
     */
    boolean save();

    /**
     * Reloads this group's data from persistent storage.
     */
    void reload();

    /**
     * Creates a copy of this group with a new name.
     * @param newName the name for the copied group
     * @return a new group instance with copied properties
     */
    @NotNull
    GroupInterface copy(@NotNull String newName);

    /**
     * Checks if this group is valid and can be used.
     * @return true if the group is valid
     */
    default boolean isValid() {
        return getName() != null && !getName().isEmpty() && getId() >= 0;
    }
}