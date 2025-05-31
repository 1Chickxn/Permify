package me.chickxn.permify.spigot.handler;

import lombok.SneakyThrows;
import me.chickxn.permify.data.interfaces.GroupInterface;
import me.chickxn.permify.data.interfaces.PlayerInterface;
import me.chickxn.permify.data.interfaces.StorageInterface;
import me.chickxn.permify.data.storage.StorageHandler;
import me.chickxn.permify.spigot.permissible.WildcardPermissible;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.*;

public class PermissionHandler {

    private final Plugin plugin;
    private final Map<Player, PermissionAttachment> attachments = new HashMap<>();

    public PermissionHandler(Plugin plugin) {
        this.plugin = plugin;
    }

    private PermissionAttachment getAttachment(Player player) {
        return attachments.computeIfAbsent(player, p -> p.addAttachment(plugin));
    }

    public void removeAttachment(Player player) {
        if (attachments.containsKey(player)) {
            player.removeAttachment(attachments.get(player));
            attachments.remove(player);
        }
    }

    public void updatePlayerPermissions(Player player) {
        PermissionAttachment attachment = getAttachment(player);
        if (attachment == null) {
            return;
        }
        attachment.getPermissions().keySet().forEach(attachment::unsetPermission);
        Set<String> permissions = getPlayerAndGroupPermissions(player);

        for (String perm : permissions) {
            if (perm.startsWith("-")) {
                attachment.setPermission(perm.substring(1), false);
            } else {
                attachment.setPermission(perm, true);
            }
        }
        player.recalculatePermissions();
    }

    public Set<String> getPlayerAndGroupPermissions(Player player) {
        StorageInterface storage = StorageHandler.getActiveStorage();
        if (storage == null) {
            return Collections.emptySet();
        }

        PlayerInterface playerData = storage.getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return Collections.emptySet();
        }

        Set<String> permissions = new HashSet<>(playerData.permissions());

        GroupInterface group = storage.getPlayerGroup(playerData);
        if (group != null) {
            permissions.addAll(group.permissions());
        }

        return permissions;
    }

    @SneakyThrows
    public void injectWildcardPermissible(Player player) {
        Field field = findPermissibleField(player.getClass());
        if (field == null) {
            return;
        }
        WildcardPermissible wp = new WildcardPermissible(player);
        field.set(player, wp);
    }

    private static Field findPermissibleField(Class<?> clazz) {
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
}
