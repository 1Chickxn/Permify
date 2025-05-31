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

    private Set<String> cachedPermissions;
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 1000; // 1 second cache

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

        if (matchesWildcard(permission, effectivePermissions)) {
            return true;
        }

        return super.hasPermission(permission);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return hasPermission(perm.getName());
    }

    @Override
    public boolean isPermissionSet(@NotNull String permission) {
        Set<String> effectivePermissions = getAllEffectivePermissions();

        if (effectivePermissions.contains(permission) || effectivePermissions.contains("-" + permission)) {
            return true;
        }

        if (effectivePermissions.contains("*")) {
            return true;
        }

        return matchesWildcard(permission, effectivePermissions) || isDenied(permission, effectivePermissions);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return isPermissionSet(perm.getName());
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
        if (effectivePermissions.contains("-" + permission)) {
            return true;
        }

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
        long currentTime = System.currentTimeMillis();
        if (cachedPermissions != null && (currentTime - lastCacheUpdate) < CACHE_DURATION) {
            return cachedPermissions;
        }

        Set<String> permissions = new HashSet<>();

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String permissionName = info.getPermission();
            permissions.add(info.getValue() ? permissionName : "-" + permissionName);
        }

        if (permissions.contains("*")) {
            addCommonWildcards(permissions);
        }

        cachedPermissions = permissions;
        lastCacheUpdate = currentTime;

        return permissions;
    }

    private void addCommonWildcards(Set<String> permissions) {
        permissions.add("minecraft.command.*");
        permissions.add("bukkit.command.*");
        permissions.add("spigot.command.*");
        String[] commonPermissions = {
                "minecraft.command.me",
                "minecraft.command.tell",
                "minecraft.command.msg",
                "minecraft.command.say",
                "minecraft.command.give",
                "minecraft.command.teleport",
                "minecraft.command.tp",
                "minecraft.command.gamemode",
                "minecraft.command.time",
                "minecraft.command.weather",
                "minecraft.command.fill",
                "minecraft.command.setblock",
                "minecraft.command.summon",
                "minecraft.command.kill",
                "minecraft.command.clear",
                "minecraft.command.effect",
                "bukkit.command.help",
                "bukkit.command.plugins",
                "bukkit.command.version",
                "bukkit.command.reload",
                "bukkit.command.stop",
                "bukkit.command.restart",
                "server.command.op",
                "server.command.deop"
        };

        for (String perm : commonPermissions) {
            permissions.add(perm);
        }
    }

    public void clearCache() {
        cachedPermissions = null;
        lastCacheUpdate = 0;
    }
}