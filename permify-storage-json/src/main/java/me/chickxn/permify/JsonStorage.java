package me.chickxn.permify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.reflect.TypeToken;
import me.chickxn.permify.data.interfaces.PlayerInterface;
import me.chickxn.permify.data.interfaces.GroupInterface;
import me.chickxn.permify.data.models.GroupData;
import me.chickxn.permify.data.models.PlayerData;
import me.chickxn.permify.data.storage.StorageModule;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.util.HSVLike;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JsonStorage extends StorageModule {

    private List<GroupInterface> groups = new ArrayList<>();
    private List<PlayerInterface> players = new ArrayList<>();
    private final File playerDataFile;
    private final File groupDataFile;
    private final Gson gson;
    private final String DEFAULT_GROUP_NAME = "default";

    public JsonStorage() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(HSVLike.class, new HSVLikeAdapter());
        builder.registerTypeAdapter(GroupInterface.class, new InstanceCreator<GroupInterface>() {
            @Override
            public GroupInterface createInstance(Type type) {
                return new GroupData(DEFAULT_GROUP_NAME, "§7[Default]", "§7[Default]", NamedTextColor.GRAY, 0);
            }
        });
        gson = builder.create();

        groups.add(new GroupData(DEFAULT_GROUP_NAME, "§7[Default]", "§7[Default]", NamedTextColor.GRAY, 0));
        playerDataFile = new File("plugins/Permify/players.json");
        groupDataFile = new File("plugins/Permify/groups.json");
        load();
    }

    @Override
    public void start() {
        load();
    }

    @Override
    public void stop() {
        save();
    }

    private void load() {
        if (playerDataFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(playerDataFile))) {
                List<PlayerData> loadedPlayers = gson.fromJson(reader, new TypeToken<List<PlayerData>>() {}.getType());
                players = (loadedPlayers != null) ? new ArrayList<>(loadedPlayers) : new ArrayList<>();
            } catch (Exception e) {
                System.out.println("Fehler beim Laden der Spieler-Daten: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Spieler-Datei nicht gefunden, erstelle neue Liste.");
            players = new ArrayList<>();
        }

        if (groupDataFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(groupDataFile))) {
                List<GroupData> loadedGroups = gson.fromJson(reader, new TypeToken<List<GroupData>>() {}.getType());
                groups = (loadedGroups != null) ? new ArrayList<>(loadedGroups) : new ArrayList<>();
            } catch (Exception e) {
                System.out.println("Fehler beim Laden der Gruppen-Daten: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Gruppen-Datei nicht gefunden, erstelle neue Liste.");
            groups = new ArrayList<>();
        }

        if (getGroupByName(DEFAULT_GROUP_NAME) == null) {
            GroupData defaultGroup = new GroupData(DEFAULT_GROUP_NAME, "§7[Default]", "§7[Default]", NamedTextColor.GRAY, 0);
            groups.add(defaultGroup);
            System.out.println("Standardgruppe '" + DEFAULT_GROUP_NAME + "' wurde erstellt.");
        }
    }

    private void save() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(playerDataFile))) {
            String json = gson.toJson(players, new TypeToken<List<PlayerData>>() {}.getType());
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            System.out.println("Fehler beim Speichern der Spieler-Daten: " + e.getMessage());
            e.printStackTrace();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(groupDataFile))) {
            String json = gson.toJson(groups, new TypeToken<List<GroupData>>() {}.getType());
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            System.out.println("Fehler beim Speichern der Gruppen-Daten: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<String> playerPermissions(PlayerInterface playerInterface) {
        PlayerInterface player = getPlayerByUUID(playerInterface.uuid());
        if (player == null) return new ArrayList<>();
        return new ArrayList<>(player.permissions());
    }

    @Override
    public void addPlayerPermissions(PlayerInterface playerInterface, String permission) {
        PlayerInterface player = getPlayerByUUID(playerInterface.uuid());
        if (player != null) {
            player.addPermission(permission);
        } else {
            players.add(playerInterface);
            playerInterface.addPermission(permission);
        }
    }

    @Override
    public void removePlayerPermissions(PlayerInterface playerInterface, String permission) {
        PlayerInterface player = getPlayerByUUID(playerInterface.uuid());
        if (player != null) {
            player.removePermission(permission);
        }
    }

    @Override
    public void setPlayerToGroup(PlayerInterface playerInterface, GroupInterface group) {
        PlayerInterface player = getPlayerByUUID(playerInterface.uuid());
        if (player != null) {
            player.setGroup(group);
        } else {
            players.add(playerInterface);
            playerInterface.setGroup(group);
        }
    }

    @Override
    public List<String> playerGroup(PlayerInterface playerInterface) {
        PlayerInterface player = getPlayerByUUID(playerInterface.uuid());
        if (player != null && player.groups() != null) {
            List<String> groupNames = new ArrayList<>();
            for (GroupInterface group : player.groups()) {
                groupNames.add(group.groupName());
            }
            return groupNames;
        }
        return new ArrayList<>();
    }

    @Override
    public List<String> groupPermissions(GroupInterface groupInterface) {
        if (groupInterface != null) {
            return groupInterface.permissions();
        }
        return new ArrayList<>();
    }

    @Override
    public void createGroup(GroupInterface groupInterface) {
        groups.add(groupInterface);
    }

    @Override
    public void removeGroup(GroupInterface groupInterface) {
        groups.remove(groupInterface);
    }

    @Override
    public void addGroupPermissions(GroupInterface groupInterface, String permission) {
        if (groupInterface != null) {
            groupInterface.addPermission(permission);
        }
    }

    @Override
    public void removeGroupPermissions(GroupInterface groupInterface, String permission) {
        if (groupInterface != null) {
            groupInterface.removePermission(permission);
        }
    }

    @Override
    public GroupInterface getPlayerGroup(PlayerInterface playerInterface) {
        PlayerInterface player = getPlayerByUUID(playerInterface.uuid());
        if (player != null && !player.groups().isEmpty()) {
            return player.groups().get(0);
        }
        return getGroupByName(DEFAULT_GROUP_NAME);
    }

    @Override
    public List<GroupInterface> getAllGroups() {
        return new ArrayList<>(groups);
    }

    @Override
    public GroupInterface getGroupByName(String groupName) {
        for (GroupInterface group : groups) {
            if (group.groupName().equalsIgnoreCase(groupName)) {
                return group;
            }
        }
        return null;
    }

    private PlayerInterface getPlayerByUUID(UUID uuid) {
        for (PlayerInterface player : players) {
            if (player.uuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    @Override
    public PlayerInterface getPlayerData(UUID uuid) {
        return getPlayerByUUID(uuid);
    }
}
