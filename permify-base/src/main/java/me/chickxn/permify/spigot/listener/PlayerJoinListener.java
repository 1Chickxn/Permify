package me.chickxn.permify.spigot.listener;

import me.chickxn.permify.data.interfaces.StorageInterface;
import me.chickxn.permify.data.models.PlayerData;
import me.chickxn.permify.data.storage.StorageHandler;
import me.chickxn.permify.data.interfaces.GroupInterface;
import me.chickxn.permify.spigot.PermifyBase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.List;
import java.util.UUID;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
        UUID uuid = playerJoinEvent.getPlayer().getUniqueId();
        String name = playerJoinEvent.getPlayer().getName();
        StorageInterface storageInterface = StorageHandler.getActiveStorage();
        if (storageInterface == null) {
            playerJoinEvent.getPlayer().sendMessage("§cFehler: Kein aktives Speichersystem!");
            return;
        }
        PlayerData playerData = new PlayerData(uuid, name);
        List<String> existingGroups = storageInterface.playerGroup(playerData);
        if (existingGroups.isEmpty()) {
            GroupInterface defaultGroup = storageInterface.getGroupByName("default");
            if (defaultGroup != null) {
                storageInterface.setPlayerToGroup(playerData, defaultGroup);
            } else {
                playerJoinEvent.getPlayer().sendMessage("§cFehler: Die Standardgruppe existiert nicht.");
            }
        }

        PermifyBase.getInstance().getPermissionHandler().updatePlayerPermissions(playerJoinEvent.getPlayer());
    }
}
