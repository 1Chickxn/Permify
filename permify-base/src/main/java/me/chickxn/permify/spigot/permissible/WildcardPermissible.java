package me.chickxn.permify.spigot.permissible;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class WildcardPermissible extends PermissibleBase {
    private final Player player;

    public WildcardPermissible(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public boolean hasPermission(@NotNull String permission) {
        Set<String> effectivePermissions = getAllEffectivePermissions();

        if (isDenied(permission, effectivePermissions)) {
            return false;
        }

        if (effectivePermissions.contains(permission)) {
            return true;
        }

        if (effectivePermissions.contains("*")) {
            return true;
        }

        if (permission.startsWith("bukkit.command.") && effectivePermissions.contains("bukkit.command.*")) {
            return true;
        }

        if (matchesWildcard(permission, effectivePermissions)) {
            return true;
        }
        
        return super.hasPermission(permission);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return hasPermission(perm.getName());
    }

    private boolean matchesWildcard(String permission, Set<String> effectivePermissions) {
        String[] parts = permission.split("\\.");
        StringBuilder checkPerm = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                checkPerm.append(".");
            }
            checkPerm.append(parts[i]);

            String wildcard = checkPerm + ".*";

            if (effectivePermissions.contains("-" + wildcard)) {
                return false;
            }
            if (effectivePermissions.contains(wildcard)) {
                return true;
            }
        }

        return false;
    }

    private boolean isDenied(String permission, Set<String> effectivePermissions) {
        String[] parts = permission.split("\\.");
        StringBuilder checkPerm = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                checkPerm.append(".");
            }
            checkPerm.append(parts[i]);

            if (effectivePermissions.contains("-" + checkPerm.toString()) ||
                    effectivePermissions.contains("-" + checkPerm + ".*")) {
                return true;
            }
        }

        return false;
    }

    private Set<String> getAllEffectivePermissions() {
        Set<String> permissions = new HashSet<>();
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            permissions.add((info.getValue() ? "" : "-") + info.getPermission());
        }
        return permissions;
    }
}
