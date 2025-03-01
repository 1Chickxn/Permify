package me.chickxn.permify.data.interfaces;

import java.util.List;
import java.util.UUID;

public interface PlayerInterface {

    String name();
    UUID uuid();
    List<String> permissions();
    void addPermission(String permission);
    void removePermission(String permission);

    List<GroupInterface> groups();
    void setGroup(GroupInterface groupInterface);
    void removeGroup(GroupInterface groupInterface);
    GroupInterface getPrimaryGroup();

}
