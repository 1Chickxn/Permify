package me.chickxn.permify.data.models;

import me.chickxn.permify.data.interfaces.PlayerInterface;
import me.chickxn.permify.data.interfaces.GroupInterface;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

public class PlayerData implements PlayerInterface {

    private final UUID uuid;
    private final String name;
    private final List<String> permissions = new ArrayList<>();
    private final List<GroupInterface> groups = new ArrayList<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public List<String> permissions() {
        return permissions;
    }

    @Override
    public void addPermission(String permission) {
        permissions.add(permission);
    }

    @Override
    public void removePermission(String permission) {
        permissions.remove(permission);
    }

    @Override
    public List<GroupInterface> groups() {
        return groups;
    }

    @Override
    public GroupInterface getPrimaryGroup() {
        return groups.isEmpty() ? null : groups.get(0);
    }

    @Override
    public void setGroup(GroupInterface groupInterface) {
        groups.clear();
        groups.add(groupInterface);
    }

    @Override
    public void removeGroup(GroupInterface groupInterface) {
        groups.remove(groupInterface);
    }
}