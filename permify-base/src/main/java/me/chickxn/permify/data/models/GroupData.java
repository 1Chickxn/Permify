package me.chickxn.permify.data.models;

import me.chickxn.permify.data.interfaces.GroupInterface;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.ArrayList;
import java.util.List;

public class GroupData implements GroupInterface {

    private final String groupName;
    private final String groupPrefix;
    private final String groupSuffix;
    private final NamedTextColor groupColor;
    private final List<String> permissions = new ArrayList<>();
    private final int groupId;

    public GroupData(String groupName, String groupPrefix, String groupSuffix, NamedTextColor groupColor, int groupId) {
        this.groupName = groupName;
        this.groupPrefix = groupPrefix;
        this.groupSuffix = groupSuffix;
        this.groupColor = groupColor;
        this.groupId = groupId;
    }

    @Override
    public String groupName() {
        return groupName;
    }

    @Override
    public String groupPrefix() {
        return groupPrefix;
    }

    @Override
    public String groupSuffix() {
        return groupSuffix;
    }

    @Override
    public NamedTextColor groupColor() {
        return groupColor;
    }

    @Override
    public List<String> permissions() {
        return permissions;
    }

    @Override
    public int groupId() {
        return groupId;
    }

    @Override
    public void addPermission(String permission) {
        permissions.add(permission);
    }

    @Override
    public void removePermission(String permission) {
        permissions.remove(permission);
    }
}
