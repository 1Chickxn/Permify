package me.chickxn.permify.data.interfaces;

import net.kyori.adventure.text.format.NamedTextColor;
import java.util.List;

public interface GroupInterface {

    String groupName();
    String groupPrefix();
    String groupSuffix();
    NamedTextColor groupColor();
    List<String> permissions();
    int groupId();
    void addPermission(String permission);
    void removePermission(String permission);

}
