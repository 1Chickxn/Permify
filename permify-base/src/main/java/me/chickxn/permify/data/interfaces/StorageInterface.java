package me.chickxn.permify.data.interfaces;

import lombok.experimental.Accessors;
import java.util.List;
import java.util.UUID;

@Accessors(fluent = true)
public interface StorageInterface {

    List<String> playerPermissions(PlayerInterface playerInterface);
    void addPlayerPermissions(PlayerInterface playerInterface, String permissions);
    void removePlayerPermissions(PlayerInterface playerInterface, String permissions);
    void setPlayerToGroup(PlayerInterface playerInterface, GroupInterface groupInterface);
    List<String> playerGroup(PlayerInterface playerInterface);

    List<String> groupPermissions(GroupInterface groupInterface);
    void createGroup(GroupInterface groupInterface);
    void removeGroup(GroupInterface groupInterface);
    void addGroupPermissions(GroupInterface groupInterface, String permissions);
    void removeGroupPermissions(GroupInterface groupInterface, String permissions);

    GroupInterface getPlayerGroup(PlayerInterface playerInterface);
    List<GroupInterface> getAllGroups();
    GroupInterface getGroupByName(String groupName);
    PlayerInterface getPlayerData(UUID uuid);

}
