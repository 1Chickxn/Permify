// ===================================================================================================
// COMPLETE EXTERNAL JSON STORAGE MODULE
// Package: me.chickxn.permify (separate JAR file)
// ===================================================================================================

package me.chickxn.permify;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import me.chickxn.permify.data.interfaces.GroupInterface;
import me.chickxn.permify.data.interfaces.PlayerInterface;
import me.chickxn.permify.data.storage.StorageModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Complete External JSON Storage Module for Permify
 * This class will be loaded dynamically from a separate JAR file
 */
public class JsonStorage extends StorageModule {

    private static final String STORAGE_TYPE = "json";
    private static final String PLAYERS_FILE = "players.json";
    private static final String GROUPS_FILE = "groups.json";
    private static final String BACKUP_SUFFIX = ".backup";

    // Data storage
    private final Map<UUID, JsonPlayerImpl> playerCache = new ConcurrentHashMap<>();
    private final Map<String, JsonGroupImpl> groupCache = new ConcurrentHashMap<>();
    private final Map<Integer, JsonGroupImpl> groupIdCache = new ConcurrentHashMap<>();

    // File management
    private Path dataDirectory;
    private Path playersFile;
    private Path groupsFile;

    // Thread safety
    private final ReadWriteLock dataLock = new ReentrantReadWriteLock();

    // JSON handling
    private final Gson gson;
    private final Logger logger;

    // Auto-save
    private Timer autoSaveTimer;
    private boolean isDirty = false;

    // ID management
    private int nextGroupId = 1;

    public JsonStorage() {
        this.logger = Logger.getLogger("Permify-JsonStorage");
        this.gson = createGson();
    }

    // ===================================================================================================
    // LIFECYCLE MANAGEMENT
    // ===================================================================================================

    @Override
    public void start() throws Exception {
        logger.info("Starting JSON storage module...");

        setupDataDirectory();
        loadAllData();
        startAutoSave();

        markStarted();
        logger.info("JSON storage module started successfully");
    }

    @Override
    public void stop() throws Exception {
        logger.info("Stopping JSON storage module...");

        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
            autoSaveTimer = null;
        }

        saveAllData();

        dataLock.writeLock().lock();
        try {
            playerCache.clear();
            groupCache.clear();
            groupIdCache.clear();
        } finally {
            dataLock.writeLock().unlock();
        }

        markStopped();
        logger.info("JSON storage module stopped successfully");
    }

    @Override
    @NotNull
    public String getStorageType() {
        return STORAGE_TYPE;
    }

    @Override
    @NotNull
    public Map<String, String> getConfigRequirements() {
        return Map.of(
                "data-directory", "Directory to store JSON files (optional, defaults to plugin folder)"
        );
    }

    @Override
    public boolean validateConfig(@NotNull Map<String, Object> config) {
        return true; // JSON storage has minimal requirements
    }

    @Override
    public boolean isHealthy() {
        return isRunning() &&
                Files.exists(dataDirectory) &&
                Files.isWritable(dataDirectory);
    }

    // ===================================================================================================
    // PLAYER OPERATIONS
    // ===================================================================================================

    @Override
    @Nullable
    public PlayerInterface loadPlayer(@NotNull UUID uuid) {
        dataLock.readLock().lock();
        try {
            return playerCache.get(uuid);
        } finally {
            dataLock.readLock().unlock();
        }
    }

    @Override
    public boolean savePlayer(@NotNull PlayerInterface player) {
        if (!(player instanceof JsonPlayerImpl)) {
            logger.warning("Cannot save non-JsonPlayerImpl: " + player.getClass().getSimpleName());
            return false;
        }

        dataLock.writeLock().lock();
        try {
            JsonPlayerImpl jsonPlayer = (JsonPlayerImpl) player;
            playerCache.put(player.getUuid(), jsonPlayer);
            isDirty = true;
            return true;
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    @NotNull
    public PlayerInterface createPlayer(@NotNull UUID uuid, @NotNull String name) {
        dataLock.writeLock().lock();
        try {
            JsonPlayerImpl player = new JsonPlayerImpl(uuid, name, this);
            player.setFirstJoin(LocalDateTime.now());
            player.setLastSeen(LocalDateTime.now());

            playerCache.put(uuid, player);
            isDirty = true;

            logger.info("Created new player: " + name + " (" + uuid + ")");
            return player;
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    public boolean deletePlayer(@NotNull UUID uuid) {
        dataLock.writeLock().lock();
        try {
            JsonPlayerImpl removed = playerCache.remove(uuid);
            if (removed != null) {
                isDirty = true;
                logger.info("Deleted player: " + removed.getName() + " (" + uuid + ")");
                return true;
            }
            return false;
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    @NotNull
    public Set<UUID> getAllPlayerUUIDs() {
        dataLock.readLock().lock();
        try {
            return new HashSet<>(playerCache.keySet());
        } finally {
            dataLock.readLock().unlock();
        }
    }

    // ===================================================================================================
    // GROUP OPERATIONS
    // ===================================================================================================

    @Override
    @Nullable
    public GroupInterface loadGroup(@NotNull String name) {
        dataLock.readLock().lock();
        try {
            return groupCache.get(name.toLowerCase());
        } finally {
            dataLock.readLock().unlock();
        }
    }

    @Override
    @Nullable
    public GroupInterface loadGroup(int id) {
        dataLock.readLock().lock();
        try {
            return groupIdCache.get(id);
        } finally {
            dataLock.readLock().unlock();
        }
    }

    @Override
    public boolean saveGroup(@NotNull GroupInterface group) {
        if (!(group instanceof JsonGroupImpl)) {
            logger.warning("Cannot save non-JsonGroupImpl: " + group.getClass().getSimpleName());
            return false;
        }

        dataLock.writeLock().lock();
        try {
            JsonGroupImpl jsonGroup = (JsonGroupImpl) group;
            groupCache.put(group.getName().toLowerCase(), jsonGroup);
            groupIdCache.put(group.getId(), jsonGroup);
            isDirty = true;
            return true;
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    @NotNull
    public GroupInterface createGroup(@NotNull String name) {
        dataLock.writeLock().lock();
        try {
            int id = nextGroupId++;
            JsonGroupImpl group = new JsonGroupImpl(id, name, this);
            group.setCreatedAt(LocalDateTime.now());
            group.setLastModified(LocalDateTime.now());

            groupCache.put(name.toLowerCase(), group);
            groupIdCache.put(id, group);
            isDirty = true;

            logger.info("Created new group: " + name + " (ID: " + id + ")");
            return group;
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    public boolean deleteGroup(@NotNull GroupInterface group) {
        dataLock.writeLock().lock();
        try {
            JsonGroupImpl removed1 = groupCache.remove(group.getName().toLowerCase());
            JsonGroupImpl removed2 = groupIdCache.remove(group.getId());

            if (removed1 != null || removed2 != null) {
                // Remove group from all players
                for (JsonPlayerImpl player : playerCache.values()) {
                    player.removeGroup(group);
                }

                // Remove as parent from other groups
                for (JsonGroupImpl otherGroup : groupCache.values()) {
                    otherGroup.removeParentGroup(group);
                }

                isDirty = true;
                logger.info("Deleted group: " + group.getName() + " (ID: " + group.getId() + ")");
                return true;
            }
            return false;
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    @NotNull
    public Set<String> getAllGroupNames() {
        dataLock.readLock().lock();
        try {
            return groupCache.values().stream()
                    .map(GroupInterface::getName)
                    .collect(Collectors.toSet());
        } finally {
            dataLock.readLock().unlock();
        }
    }

    @Override
    @NotNull
    public List<GroupInterface> getAllGroups() {
        dataLock.readLock().lock();
        try {
            return new ArrayList<>(groupCache.values());
        } finally {
            dataLock.readLock().unlock();
        }
    }

    @Override
    public int getNextGroupId() {
        dataLock.readLock().lock();
        try {
            return nextGroupId;
        } finally {
            dataLock.readLock().unlock();
        }
    }

    // ===================================================================================================
    // ALL OTHER STORAGE INTERFACE METHODS
    // ===================================================================================================

    @Override
    public boolean addPlayerPermission(@NotNull PlayerInterface player, @NotNull String permission) {
        if (player instanceof JsonPlayerImpl) {
            boolean added = player.addPermission(permission);
            if (added) isDirty = true;
            return added;
        }
        return false;
    }

    @Override
    public boolean removePlayerPermission(@NotNull PlayerInterface player, @NotNull String permission) {
        if (player instanceof JsonPlayerImpl) {
            boolean removed = player.removePermission(permission);
            if (removed) isDirty = true;
            return removed;
        }
        return false;
    }

    @Override
    public boolean addGroupPermission(@NotNull GroupInterface group, @NotNull String permission) {
        if (group instanceof JsonGroupImpl) {
            boolean added = group.addPermission(permission);
            if (added) isDirty = true;
            return added;
        }
        return false;
    }

    @Override
    public boolean removeGroupPermission(@NotNull GroupInterface group, @NotNull String permission) {
        if (group instanceof JsonGroupImpl) {
            boolean removed = group.removePermission(permission);
            if (removed) isDirty = true;
            return removed;
        }
        return false;
    }

    @Override
    public boolean addPlayerToGroup(@NotNull PlayerInterface player, @NotNull GroupInterface group) {
        if (player instanceof JsonPlayerImpl) {
            boolean added = player.addGroup(group);
            if (added) isDirty = true;
            return added;
        }
        return false;
    }

    @Override
    public boolean removePlayerFromGroup(@NotNull PlayerInterface player, @NotNull GroupInterface group) {
        if (player instanceof JsonPlayerImpl) {
            boolean removed = player.removeGroup(group);
            if (removed) isDirty = true;
            return removed;
        }
        return false;
    }

    @Override
    public boolean setPlayerPrimaryGroup(@NotNull PlayerInterface player, @NotNull GroupInterface group) {
        if (player instanceof JsonPlayerImpl) {
            player.setPrimaryGroup(group);
            isDirty = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean addGroupParent(@NotNull GroupInterface childGroup, @NotNull GroupInterface parentGroup) {
        if (childGroup instanceof JsonGroupImpl) {
            boolean added = childGroup.addParentGroup(parentGroup);
            if (added) isDirty = true;
            return added;
        }
        return false;
    }

    @Override
    public boolean removeGroupParent(@NotNull GroupInterface childGroup, @NotNull GroupInterface parentGroup) {
        if (childGroup instanceof JsonGroupImpl) {
            boolean removed = childGroup.removeParentGroup(parentGroup);
            if (removed) isDirty = true;
            return removed;
        }
        return false;
    }

    @Override
    public boolean addTemporaryPlayerPermission(@NotNull PlayerInterface player, @NotNull String permission, @NotNull LocalDateTime expiry) {
        if (player instanceof JsonPlayerImpl) {
            long durationSeconds = java.time.Duration.between(LocalDateTime.now(), expiry).toSeconds();
            boolean added = player.addTemporaryPermission(permission, durationSeconds);
            if (added) isDirty = true;
            return added;
        }
        return false;
    }

    @Override
    public boolean addTemporaryPlayerGroup(@NotNull PlayerInterface player, @NotNull GroupInterface group, @NotNull LocalDateTime expiry) {
        if (player instanceof JsonPlayerImpl) {
            long durationSeconds = java.time.Duration.between(LocalDateTime.now(), expiry).toSeconds();
            boolean added = player.addTemporaryGroup(group, durationSeconds);
            if (added) isDirty = true;
            return added;
        }
        return false;
    }

    @Override
    public int cleanupExpiredEntries() {
        int cleaned = 0;
        LocalDateTime now = LocalDateTime.now();

        dataLock.writeLock().lock();
        try {
            for (JsonPlayerImpl player : playerCache.values()) {
                Map<String, LocalDateTime> tempPerms = player.getTemporaryPermissions();
                int beforePerms = tempPerms.size();
                tempPerms.entrySet().removeIf(entry -> entry.getValue().isBefore(now));

                Map<GroupInterface, LocalDateTime> tempGroups = player.getTemporaryGroups();
                int beforeGroups = tempGroups.size();
                tempGroups.entrySet().removeIf(entry -> entry.getValue().isBefore(now));

                int playerCleaned = (beforePerms - tempPerms.size()) + (beforeGroups - tempGroups.size());
                if (playerCleaned > 0) {
                    cleaned += playerCleaned;
                    isDirty = true;
                }
            }
        } finally {
            dataLock.writeLock().unlock();
        }

        if (cleaned > 0) {
            logger.info("Cleaned up " + cleaned + " expired entries");
        }

        return cleaned;
    }

    @Override
    public boolean addPlayerServerPermission(@NotNull PlayerInterface player, @NotNull String serverName, @NotNull String permission) {
        if (player instanceof JsonPlayerImpl) {
            boolean added = ((JsonPlayerImpl) player).addServerPermission(serverName, permission);
            if (added) isDirty = true;
            return added;
        }
        return false;
    }

    @Override
    public boolean addPlayerWorldPermission(@NotNull PlayerInterface player, @NotNull String worldName, @NotNull String permission) {
        if (player instanceof JsonPlayerImpl) {
            boolean added = ((JsonPlayerImpl) player).addWorldPermission(worldName, permission);
            if (added) isDirty = true;
            return added;
        }
        return false;
    }

    @Override
    public boolean updatePlayerDisplayProperties(@NotNull PlayerInterface player, @Nullable String customPrefix,
                                                 @Nullable String customSuffix, @Nullable NamedTextColor customColor) {
        if (player instanceof JsonPlayerImpl) {
            player.setCustomPrefix(customPrefix);
            player.setCustomSuffix(customSuffix);
            player.setCustomColor(customColor);
            isDirty = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean updateGroupDisplayProperties(@NotNull GroupInterface group, @Nullable String displayName,
                                                @Nullable String prefix, @Nullable String suffix,
                                                @Nullable NamedTextColor color, int priority) {
        if (group instanceof JsonGroupImpl) {
            JsonGroupImpl jsonGroup = (JsonGroupImpl) group;

            if (displayName != null) group.setDisplayName(displayName);
            if (prefix != null) group.setPrefix(prefix);
            if (suffix != null) group.setSuffix(suffix);
            if (color != null) group.setColor(color);
            if (priority > 0) group.setPriority(priority);

            jsonGroup.setLastModified(LocalDateTime.now());
            isDirty = true;
            return true;
        }
        return false;
    }

    @Override
    public void logPermissionChange(@NotNull UUID targetUuid, @NotNull String action, @NotNull String target,
                                    @Nullable String actor, @Nullable String reason) {
        logger.info(String.format("Permission change: %s %s %s (by %s) - %s",
                targetUuid, action, target, actor != null ? actor : "system", reason != null ? reason : "no reason"));
    }

    @Override
    @NotNull
    public List<PlayerInterface.PermissionLogEntry> getPlayerPermissionHistory(@NotNull PlayerInterface player, int limit) {
        return Collections.emptyList();
    }

    @Override
    public boolean updatePlayerMetadata(@NotNull PlayerInterface player, @Nullable LocalDateTime firstJoin,
                                        @Nullable LocalDateTime lastSeen, long playtimeSeconds) {
        if (player instanceof JsonPlayerImpl) {
            JsonPlayerImpl jsonPlayer = (JsonPlayerImpl) player;
            if (firstJoin != null) jsonPlayer.setFirstJoin(firstJoin);
            if (lastSeen != null) jsonPlayer.setLastSeen(lastSeen);
            jsonPlayer.setPlaytimeSeconds(playtimeSeconds);
            isDirty = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean savePlayers(@NotNull List<PlayerInterface> players) {
        dataLock.writeLock().lock();
        try {
            boolean allSaved = true;
            for (PlayerInterface player : players) {
                if (player instanceof JsonPlayerImpl) {
                    playerCache.put(player.getUuid(), (JsonPlayerImpl) player);
                } else {
                    allSaved = false;
                }
            }
            if (allSaved) isDirty = true;
            return allSaved;
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    public boolean saveGroups(@NotNull List<GroupInterface> groups) {
        dataLock.writeLock().lock();
        try {
            boolean allSaved = true;
            for (GroupInterface group : groups) {
                if (group instanceof JsonGroupImpl) {
                    JsonGroupImpl jsonGroup = (JsonGroupImpl) group;
                    groupCache.put(group.getName().toLowerCase(), jsonGroup);
                    groupIdCache.put(group.getId(), jsonGroup);
                } else {
                    allSaved = false;
                }
            }
            if (allSaved) isDirty = true;
            return allSaved;
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    @Override
    public boolean performMaintenance() {
        try {
            logger.info("Performing maintenance...");
            int cleaned = cleanupExpiredEntries();
            createBackup();
            saveAllData();
            logger.info("Maintenance completed. Cleaned " + cleaned + " expired entries.");
            return true;
        } catch (Exception e) {
            logger.severe("Maintenance failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    @NotNull
    public Map<String, Object> getStorageStats() {
        dataLock.readLock().lock();
        try {
            long totalPermissions = playerCache.values().stream()
                    .mapToLong(p -> p.getDirectPermissions().size())
                    .sum();

            long totalGroupPermissions = groupCache.values().stream()
                    .mapToLong(g -> g.getPermissions().size())
                    .sum();

            return Map.of(
                    "type", getStorageType(),
                    "running", isRunning(),
                    "uptime", getUptime(),
                    "healthy", isHealthy(),
                    "players", playerCache.size(),
                    "groups", groupCache.size(),
                    "total_player_permissions", totalPermissions,
                    "total_group_permissions", totalGroupPermissions,
                    "dirty", isDirty,
                    "data_directory", dataDirectory.toString()
            );
        } finally {
            dataLock.readLock().unlock();
        }
    }

    // ===================================================================================================
    // PRIVATE HELPER METHODS
    // ===================================================================================================

    private void setupDataDirectory() throws Exception {
        dataDirectory = Paths.get("plugins", "Permify", "data");

        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
            logger.info("Created data directory: " + dataDirectory);
        }

        playersFile = dataDirectory.resolve(PLAYERS_FILE);
        groupsFile = dataDirectory.resolve(GROUPS_FILE);
    }

    private void loadAllData() {
        try {
            loadGroups();
            loadPlayers();
            createDefaultGroup();
        } catch (Exception e) {
            logger.severe("Failed to load data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadGroups() {
        if (!Files.exists(groupsFile)) {
            logger.info("Groups file does not exist, starting with empty groups");
            return;
        }

        try (FileReader reader = new FileReader(groupsFile.toFile())) {
            Type listType = new TypeToken<List<JsonGroupData>>() {}.getType();
            List<JsonGroupData> groupDataList = gson.fromJson(reader, listType);

            if (groupDataList != null) {
                dataLock.writeLock().lock();
                try {
                    for (JsonGroupData data : groupDataList) {
                        JsonGroupImpl group = JsonGroupImpl.fromData(data, this);
                        groupCache.put(group.getName().toLowerCase(), group);
                        groupIdCache.put(group.getId(), group);

                        if (group.getId() >= nextGroupId) {
                            nextGroupId = group.getId() + 1;
                        }
                    }
                } finally {
                    dataLock.writeLock().unlock();
                }
                logger.info("Loaded " + groupDataList.size() + " groups");
            }
        } catch (Exception e) {
            logger.severe("Failed to load groups: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadPlayers() {
        if (!Files.exists(playersFile)) {
            logger.info("Players file does not exist, starting with empty players");
            return;
        }

        try (FileReader reader = new FileReader(playersFile.toFile())) {
            Type listType = new TypeToken<List<JsonPlayerData>>() {}.getType();
            List<JsonPlayerData> playerDataList = gson.fromJson(reader, listType);

            if (playerDataList != null) {
                dataLock.writeLock().lock();
                try {
                    for (JsonPlayerData data : playerDataList) {
                        JsonPlayerImpl player = JsonPlayerImpl.fromData(data, this);
                        playerCache.put(player.getUuid(), player);
                    }
                } finally {
                    dataLock.writeLock().unlock();
                }
                logger.info("Loaded " + playerDataList.size() + " players");
            }
        } catch (Exception e) {
            logger.severe("Failed to load players: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createDefaultGroup() {
        dataLock.writeLock().lock();
        try {
            if (groupCache.isEmpty()) {
                JsonGroupImpl defaultGroup = new JsonGroupImpl(nextGroupId++, "default", this);
                defaultGroup.setDisplayName("Default");
                defaultGroup.setPrefix("§7");
                defaultGroup.setSuffix("");
                defaultGroup.setColor(NamedTextColor.GRAY);
                defaultGroup.setPriority(0);
                defaultGroup.setDefault(true);
                defaultGroup.setCreatedAt(LocalDateTime.now());
                defaultGroup.setLastModified(LocalDateTime.now());

                groupCache.put("default", defaultGroup);
                groupIdCache.put(defaultGroup.getId(), defaultGroup);
                isDirty = true;

                logger.info("Created default group");
            }
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    private void saveAllData() {
        if (!isDirty) {
            return;
        }

        try {
            saveGroups();
            savePlayers();
            isDirty = false;
            logger.fine("Saved all data to JSON files");
        } catch (Exception e) {
            logger.severe("Failed to save data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveGroups() throws IOException {
        dataLock.readLock().lock();
        try {
            List<JsonGroupData> groupDataList = groupCache.values().stream()
                    .map(JsonGroupImpl::toData)
                    .collect(Collectors.toList());

            try (FileWriter writer = new FileWriter(groupsFile.toFile())) {
                gson.toJson(groupDataList, writer);
            }
        } finally {
            dataLock.readLock().unlock();
        }
    }

    private void savePlayers() throws IOException {
        dataLock.readLock().lock();
        try {
            List<JsonPlayerData> playerDataList = playerCache.values().stream()
                    .map(JsonPlayerImpl::toData)
                    .collect(Collectors.toList());

            try (FileWriter writer = new FileWriter(playersFile.toFile())) {
                gson.toJson(playerDataList, writer);
            }
        } finally {
            dataLock.readLock().unlock();
        }
    }

    private void createBackup() {
        try {
            if (Files.exists(playersFile)) {
                Path playerBackup = dataDirectory.resolve(PLAYERS_FILE + BACKUP_SUFFIX);
                Files.copy(playersFile, playerBackup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            if (Files.exists(groupsFile)) {
                Path groupBackup = dataDirectory.resolve(GROUPS_FILE + BACKUP_SUFFIX);
                Files.copy(groupsFile, groupBackup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            logger.warning("Failed to create backup: " + e.getMessage());
        }
    }

    private void startAutoSave() {
        autoSaveTimer = new Timer("JsonStorage-AutoSave", true);
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isDirty) {
                    saveAllData();
                }
            }
        }, 30000, 30000);
    }

    private Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(NamedTextColor.class, new NamedTextColorAdapter())
                .create();
    }

    // ===================================================================================================
    // GSON TYPE ADAPTERS
    // ===================================================================================================

    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.format(formatter));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }

    private static class NamedTextColorAdapter implements JsonSerializer<NamedTextColor>, JsonDeserializer<NamedTextColor> {
        @Override
        public JsonElement serialize(NamedTextColor src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public NamedTextColor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return NamedTextColor.NAMES.value(json.getAsString());
        }
    }

    // ===================================================================================================
    // DATA CLASSES FOR JSON SERIALIZATION
    // ===================================================================================================

    public static class JsonPlayerData {
        public UUID uuid;
        public String name;
        public Set<String> directPermissions = new HashSet<>();
        public List<String> groupNames = new ArrayList<>();
        public Map<String, LocalDateTime> temporaryPermissions = new HashMap<>();
        public Map<String, LocalDateTime> temporaryGroups = new HashMap<>();
        public Map<String, Set<String>> serverPermissions = new HashMap<>();
        public Map<String, Set<String>> worldPermissions = new HashMap<>();
        public String customPrefix;
        public String customSuffix;
        public String customColor;
        public LocalDateTime firstJoin;
        public LocalDateTime lastSeen;
        public long playtimeSeconds;
    }

    public static class JsonGroupData {
        public int id;
        public String name;
        public String displayName;
        public int priority;
        public Set<String> permissions = new HashSet<>();
        public List<String> parentGroups = new ArrayList<>();
        public String prefix;
        public String suffix;
        public String color;
        public String description;
        public boolean isDefault;
        public LocalDateTime createdAt;
        public LocalDateTime lastModified;
    }
}

// ===================================================================================================
// EXTERNAL JSON PLAYER IMPLEMENTATION
// ===================================================================================================

/**
 * External JSON Player Implementation for the JSON Storage Module
 */
class JsonPlayerImpl implements PlayerInterface {

    // Basic properties
    private final UUID uuid;
    private String name;

    // Permissions
    private final Set<String> directPermissions = ConcurrentHashMap.newKeySet();
    private final Map<String, LocalDateTime> temporaryPermissions = new ConcurrentHashMap<>();

    // Groups
    private final List<GroupInterface> groups = new CopyOnWriteArrayList<>();
    private final Map<GroupInterface, LocalDateTime> temporaryGroups = new ConcurrentHashMap<>();

    // Context-specific permissions
    private final Map<String, Set<String>> serverPermissions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> worldPermissions = new ConcurrentHashMap<>();

    // Display properties
    private String customPrefix;
    private String customSuffix;
    private NamedTextColor customColor;

    // Metadata
    private LocalDateTime firstJoin;
    private LocalDateTime lastSeen;
    private LocalDateTime lastModified;
    private long playtimeSeconds;

    // Storage reference
    private final JsonStorage storage;

    public JsonPlayerImpl(@NotNull UUID uuid, @NotNull String name, @NotNull JsonStorage storage) {
        this.uuid = uuid;
        this.name = name;
        this.storage = storage;
        this.lastModified = LocalDateTime.now();
        this.firstJoin = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
    }

    // ===================================================================================================
    // BASIC PROPERTIES
    // ===================================================================================================

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    void setName(@NotNull String name) {
        this.name = name;
        updateLastModified();
    }

    @Override
    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public boolean isOnline() {
        try {
            return org.bukkit.Bukkit.getPlayer(uuid) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ===================================================================================================
    // DIRECT PERMISSION MANAGEMENT
    // ===================================================================================================

    @Override
    @NotNull
    public Set<String> getDirectPermissions() {
        return Collections.unmodifiableSet(new HashSet<>(directPermissions));
    }

    @Override
    @NotNull
    public Set<String> getAllPermissions() {
        Set<String> allPerms = new HashSet<>(directPermissions);

        // Add group permissions (ordered by priority)
        List<GroupInterface> sortedGroups = groups.stream()
                .sorted(Comparator.comparingInt(GroupInterface::getPriority).reversed())
                .collect(Collectors.toList());

        for (GroupInterface group : sortedGroups) {
            allPerms.addAll(group.getAllPermissions());
        }

        // Add temporary permissions (check expiry)
        LocalDateTime now = LocalDateTime.now();
        temporaryPermissions.entrySet().stream()
                .filter(entry -> entry.getValue().isAfter(now))
                .forEach(entry -> allPerms.add(entry.getKey()));

        return Collections.unmodifiableSet(allPerms);
    }

    @Override
    public boolean hasPermission(@NotNull String permission) {
        // Check direct permissions
        if (directPermissions.contains(permission)) {
            return true;
        }

        // Check temporary permissions
        LocalDateTime expiry = temporaryPermissions.get(permission);
        if (expiry != null && expiry.isAfter(LocalDateTime.now())) {
            return true;
        }

        // Check group permissions
        for (GroupInterface group : groups) {
            if (group.hasPermission(permission)) {
                return true;
            }
        }

        // Check wildcard permissions
        return hasWildcardPermission(permission);
    }

    @Override
    public boolean addPermission(@NotNull String permission) {
        boolean added = directPermissions.add(permission);
        if (added) {
            updateLastModified();
        }
        return added;
    }

    @Override
    public boolean removePermission(@NotNull String permission) {
        boolean removed = directPermissions.remove(permission);
        if (removed) {
            updateLastModified();
        }
        return removed;
    }

    @Override
    public boolean addTemporaryPermission(@NotNull String permission, long durationSeconds) {
        LocalDateTime expiry = LocalDateTime.now().plusSeconds(durationSeconds);
        temporaryPermissions.put(permission, expiry);
        updateLastModified();
        return true;
    }

    @Override
    @NotNull
    public Map<String, LocalDateTime> getTemporaryPermissions() {
        // Clean expired permissions
        LocalDateTime now = LocalDateTime.now();
        temporaryPermissions.entrySet().removeIf(entry -> entry.getValue().isBefore(now));

        return Collections.unmodifiableMap(new HashMap<>(temporaryPermissions));
    }

    // ===================================================================================================
    // GROUP MANAGEMENT
    // ===================================================================================================

    @Override
    @NotNull
    public List<GroupInterface> getGroups() {
        return groups.stream()
                .sorted(Comparator.comparingInt(GroupInterface::getPriority).reversed())
                .collect(Collectors.toList());
    }

    @Override
    @NotNull
    public Optional<GroupInterface> getPrimaryGroup() {
        return groups.stream()
                .max(Comparator.comparingInt(GroupInterface::getPriority));
    }

    @Override
    public boolean hasGroup(@NotNull GroupInterface group) {
        return groups.contains(group);
    }

    @Override
    public boolean hasGroup(@NotNull String groupName) {
        return groups.stream().anyMatch(g -> g.getName().equalsIgnoreCase(groupName));
    }

    @Override
    public boolean addGroup(@NotNull GroupInterface group) {
        if (!groups.contains(group)) {
            groups.add(group);
            updateLastModified();
            return true;
        }
        return false;
    }

    @Override
    public boolean removeGroup(@NotNull GroupInterface group) {
        boolean removed = groups.remove(group);
        if (removed) {
            updateLastModified();
        }
        return removed;
    }

    @Override
    public void setPrimaryGroup(@NotNull GroupInterface group) {
        groups.clear();
        groups.add(group);
        updateLastModified();
    }

    @Override
    public boolean addTemporaryGroup(@NotNull GroupInterface group, long durationSeconds) {
        LocalDateTime expiry = LocalDateTime.now().plusSeconds(durationSeconds);
        temporaryGroups.put(group, expiry);
        addGroup(group);
        updateLastModified();
        return true;
    }

    @Override
    @NotNull
    public Map<GroupInterface, LocalDateTime> getTemporaryGroups() {
        // Clean expired groups
        LocalDateTime now = LocalDateTime.now();
        temporaryGroups.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(now)) {
                removeGroup(entry.getKey());
                return true;
            }
            return false;
        });

        return Collections.unmodifiableMap(new HashMap<>(temporaryGroups));
    }

    // ===================================================================================================
    // DISPLAY PROPERTIES
    // ===================================================================================================

    @Override
    @Nullable
    public String getPrefix() {
        if (customPrefix != null) {
            return customPrefix;
        }

        return getPrimaryGroup()
                .map(GroupInterface::getPrefix)
                .orElse(null);
    }

    @Override
    @Nullable
    public String getSuffix() {
        if (customSuffix != null) {
            return customSuffix;
        }

        return getPrimaryGroup()
                .map(GroupInterface::getSuffix)
                .orElse(null);
    }

    @Override
    @Nullable
    public NamedTextColor getColor() {
        if (customColor != null) {
            return customColor;
        }

        return getPrimaryGroup()
                .map(GroupInterface::getColor)
                .orElse(null);
    }

    @Override
    @NotNull
    public Component getDisplayName() {
        Component nameComponent = Component.text(name);

        // Apply color
        NamedTextColor color = getColor();
        if (color != null) {
            nameComponent = nameComponent.color(color);
        }

        // Add prefix and suffix
        String prefix = getPrefix();
        String suffix = getSuffix();

        Component result = nameComponent;
        if (prefix != null) {
            result = Component.text(prefix).append(result);
        }
        if (suffix != null) {
            result = result.append(Component.text(suffix));
        }

        return result;
    }

    @Override
    public void setCustomPrefix(@Nullable String prefix) {
        this.customPrefix = prefix;
        updateLastModified();
    }

    @Override
    public void setCustomSuffix(@Nullable String suffix) {
        this.customSuffix = suffix;
        updateLastModified();
    }

    @Override
    public void setCustomColor(@Nullable NamedTextColor color) {
        this.customColor = color;
        updateLastModified();
    }

    // ===================================================================================================
    // METADATA
    // ===================================================================================================

    @Override
    @Nullable
    public LocalDateTime getFirstJoin() {
        return firstJoin;
    }

    void setFirstJoin(@Nullable LocalDateTime firstJoin) {
        this.firstJoin = firstJoin;
    }

    @Override
    @Nullable
    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    void setLastSeen(@Nullable LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    @Override
    @Nullable
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    void setLastModified(@Nullable LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }

    void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds = playtimeSeconds;
        updateLastModified();
    }

    // ===================================================================================================
    // SERVER/WORLD CONTEXT
    // ===================================================================================================

    @Override
    @NotNull
    public Set<String> getServerPermissions(@NotNull String serverName) {
        return Collections.unmodifiableSet(
                serverPermissions.getOrDefault(serverName, Collections.emptySet())
        );
    }

    @Override
    @NotNull
    public Set<String> getWorldPermissions(@NotNull String worldName) {
        return Collections.unmodifiableSet(
                worldPermissions.getOrDefault(worldName, Collections.emptySet())
        );
    }

    @Override
    public boolean addServerPermission(@NotNull String serverName, @NotNull String permission) {
        Set<String> perms = serverPermissions.computeIfAbsent(serverName, k -> ConcurrentHashMap.newKeySet());
        boolean added = perms.add(permission);
        if (added) {
            updateLastModified();
        }
        return added;
    }

    @Override
    public boolean addWorldPermission(@NotNull String worldName, @NotNull String permission) {
        Set<String> perms = worldPermissions.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());
        boolean added = perms.add(permission);
        if (added) {
            updateLastModified();
        }
        return added;
    }

    // ===================================================================================================
    // UTILITY METHODS
    // ===================================================================================================

    @Override
    public void refreshPermissions() {
        getTemporaryPermissions();
        getTemporaryGroups();
    }

    @Override
    public boolean save() {
        return storage.savePlayer(this);
    }

    @Override
    public void reload() {
        PlayerInterface reloaded = storage.loadPlayer(uuid);
        if (reloaded instanceof JsonPlayerImpl) {
            JsonPlayerImpl other = (JsonPlayerImpl) reloaded;
            this.name = other.name;
            this.directPermissions.clear();
            this.directPermissions.addAll(other.directPermissions);
            this.groups.clear();
            this.groups.addAll(other.groups);
            this.temporaryPermissions.clear();
            this.temporaryPermissions.putAll(other.temporaryPermissions);
            this.temporaryGroups.clear();
            this.temporaryGroups.putAll(other.temporaryGroups);
            this.serverPermissions.clear();
            this.serverPermissions.putAll(other.serverPermissions);
            this.worldPermissions.clear();
            this.worldPermissions.putAll(other.worldPermissions);
            this.customPrefix = other.customPrefix;
            this.customSuffix = other.customSuffix;
            this.customColor = other.customColor;
            this.firstJoin = other.firstJoin;
            this.lastSeen = other.lastSeen;
            this.playtimeSeconds = other.playtimeSeconds;
        }
    }

    @Override
    public void reset() {
        directPermissions.clear();
        temporaryPermissions.clear();
        groups.clear();
        temporaryGroups.clear();
        serverPermissions.clear();
        worldPermissions.clear();
        customPrefix = null;
        customSuffix = null;
        customColor = null;
        updateLastModified();
    }

    @Override
    @NotNull
    public Component getPermissionSummary() {
        Component summary = Component.text("Permission Summary for " + name, NamedTextColor.YELLOW)
                .appendNewline()
                .append(Component.text("Groups: ", NamedTextColor.GRAY))
                .append(Component.text(groups.size() + "", NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("Direct Permissions: ", NamedTextColor.GRAY))
                .append(Component.text(directPermissions.size() + "", NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("Temporary Permissions: ", NamedTextColor.GRAY))
                .append(Component.text(getTemporaryPermissions().size() + "", NamedTextColor.WHITE));

        Optional<GroupInterface> primary = getPrimaryGroup();
        if (primary.isPresent()) {
            summary = summary.appendNewline()
                    .append(Component.text("Primary Group: ", NamedTextColor.GRAY))
                    .append(Component.text(primary.get().getName(), NamedTextColor.GREEN));
        }

        return summary;
    }

    @Override
    @NotNull
    public List<PermissionLogEntry> getPermissionHistory() {
        return Collections.emptyList();
    }

    // ===================================================================================================
    // EXTERNAL MODULE SERIALIZATION
    // ===================================================================================================

    public JsonStorage.JsonPlayerData toData() {
        JsonStorage.JsonPlayerData data = new JsonStorage.JsonPlayerData();
        data.uuid = this.uuid;
        data.name = this.name;
        data.directPermissions = new HashSet<>(this.directPermissions);
        data.groupNames = this.groups.stream()
                .map(GroupInterface::getName)
                .collect(Collectors.toList());
        data.temporaryPermissions = new HashMap<>(this.temporaryPermissions);
        data.temporaryGroups = this.temporaryGroups.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getName(),
                        Map.Entry::getValue
                ));
        data.serverPermissions = new HashMap<>();
        this.serverPermissions.forEach((k, v) -> data.serverPermissions.put(k, new HashSet<>(v)));
        data.worldPermissions = new HashMap<>();
        this.worldPermissions.forEach((k, v) -> data.worldPermissions.put(k, new HashSet<>(v)));
        data.customPrefix = this.customPrefix;
        data.customSuffix = this.customSuffix;
        data.customColor = this.customColor != null ? this.customColor.toString() : null;
        data.firstJoin = this.firstJoin;
        data.lastSeen = this.lastSeen;
        data.playtimeSeconds = this.playtimeSeconds;
        return data;
    }

    public static JsonPlayerImpl fromData(@NotNull JsonStorage.JsonPlayerData data, @NotNull JsonStorage storage) {
        JsonPlayerImpl player = new JsonPlayerImpl(data.uuid, data.name, storage);

        if (data.directPermissions != null) {
            player.directPermissions.addAll(data.directPermissions);
        }

        if (data.groupNames != null) {
            for (String groupName : data.groupNames) {
                GroupInterface group = storage.loadGroup(groupName);
                if (group != null) {
                    player.groups.add(group);
                }
            }
        }

        if (data.temporaryPermissions != null) {
            player.temporaryPermissions.putAll(data.temporaryPermissions);
        }

        if (data.temporaryGroups != null) {
            data.temporaryGroups.forEach((groupName, expiry) -> {
                GroupInterface group = storage.loadGroup(groupName);
                if (group != null) {
                    player.temporaryGroups.put(group, expiry);
                }
            });
        }

        if (data.serverPermissions != null) {
            data.serverPermissions.forEach((server, perms) -> {
                player.serverPermissions.put(server, ConcurrentHashMap.newKeySet());
                player.serverPermissions.get(server).addAll(perms);
            });
        }

        if (data.worldPermissions != null) {
            data.worldPermissions.forEach((world, perms) -> {
                player.worldPermissions.put(world, ConcurrentHashMap.newKeySet());
                player.worldPermissions.get(world).addAll(perms);
            });
        }

        player.customPrefix = data.customPrefix;
        player.customSuffix = data.customSuffix;
        if (data.customColor != null) {
            player.customColor = NamedTextColor.NAMES.value(data.customColor);
        }
        player.firstJoin = data.firstJoin;
        player.lastSeen = data.lastSeen;
        player.playtimeSeconds = data.playtimeSeconds;

        return player;
    }

    // ===================================================================================================
    // PRIVATE HELPER METHODS
    // ===================================================================================================

    private void updateLastModified() {
        this.lastModified = LocalDateTime.now();
    }

    private boolean hasWildcardPermission(@NotNull String permission) {
        Set<String> allPerms = getAllPermissions();

        if (allPerms.contains("*")) {
            return true;
        }

        String[] parts = permission.split("\\.");
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append(".");
            builder.append(parts[i]);

            String wildcard = builder.toString() + ".*";
            if (allPerms.contains(wildcard)) {
                return true;
            }
        }

        return false;
    }
}

// ===================================================================================================
// EXTERNAL JSON GROUP IMPLEMENTATION
// ===================================================================================================

/**
 * External JSON Group Implementation for the JSON Storage Module
 */
class JsonGroupImpl implements GroupInterface {

    // Basic properties
    private final int id;
    private final String name;
    private String displayName;
    private int priority;

    // Permissions
    private final Set<String> permissions = ConcurrentHashMap.newKeySet();

    // Group hierarchy
    private final List<GroupInterface> parentGroups = new CopyOnWriteArrayList<>();
    private final List<GroupInterface> childGroups = new CopyOnWriteArrayList<>();

    // Display properties
    private String prefix;
    private String suffix;
    private NamedTextColor color;
    private String description;
    private boolean isDefault;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;

    // Storage reference
    private final JsonStorage storage;

    public JsonGroupImpl(int id, @NotNull String name, @NotNull JsonStorage storage) {
        this.id = id;
        this.name = name;
        this.displayName = name;
        this.storage = storage;
        this.priority = 0;
        this.isDefault = false;
        this.lastModified = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    // ===================================================================================================
    // BASIC PROPERTIES
    // ===================================================================================================

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return displayName != null ? displayName : name;
    }

    @Override
    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
        updateLastModified();
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
        updateLastModified();
    }

    // ===================================================================================================
    // DISPLAY PROPERTIES
    // ===================================================================================================

    @Override
    @Nullable
    public String getPrefix() {
        return prefix;
    }

    @Override
    public void setPrefix(@Nullable String prefix) {
        this.prefix = prefix;
        updateLastModified();
    }

    @Override
    @Nullable
    public String getSuffix() {
        return suffix;
    }

    @Override
    public void setSuffix(@Nullable String suffix) {
        this.suffix = suffix;
        updateLastModified();
    }

    @Override
    @Nullable
    public NamedTextColor getColor() {
        return color;
    }

    @Override
    public void setColor(@Nullable NamedTextColor color) {
        this.color = color;
        updateLastModified();
    }

    // ===================================================================================================
    // PERMISSION MANAGEMENT
    // ===================================================================================================

    @Override
    @NotNull
    public Set<String> getPermissions() {
        return Collections.unmodifiableSet(new HashSet<>(permissions));
    }

    @Override
    @NotNull
    public Set<String> getAllPermissions() {
        Set<String> allPerms = new HashSet<>(permissions);

        for (GroupInterface parent : parentGroups) {
            allPerms.addAll(parent.getAllPermissions());
        }

        return Collections.unmodifiableSet(allPerms);
    }

    @Override
    public boolean hasPermission(@NotNull String permission) {
        if (permissions.contains(permission)) {
            return true;
        }

        for (GroupInterface parent : parentGroups) {
            if (parent.hasPermission(permission)) {
                return true;
            }
        }

        return hasWildcardPermission(permission);
    }

    @Override
    public boolean addPermission(@NotNull String permission) {
        boolean added = permissions.add(permission);
        if (added) {
            updateLastModified();
        }
        return added;
    }

    @Override
    public boolean removePermission(@NotNull String permission) {
        boolean removed = permissions.remove(permission);
        if (removed) {
            updateLastModified();
        }
        return removed;
    }

    // ===================================================================================================
    // GROUP INHERITANCE
    // ===================================================================================================

    @Override
    @NotNull
    public List<GroupInterface> getParentGroups() {
        return Collections.unmodifiableList(new ArrayList<>(parentGroups));
    }

    @Override
    @NotNull
    public List<GroupInterface> getChildGroups() {
        return Collections.unmodifiableList(new ArrayList<>(childGroups));
    }

    @Override
    public boolean addParentGroup(@NotNull GroupInterface group) {
        if (group == this || inheritsFrom(group) || group.inheritsFrom(this)) {
            return false;
        }

        if (!parentGroups.contains(group)) {
            parentGroups.add(group);
            if (group instanceof JsonGroupImpl) {
                ((JsonGroupImpl) group).childGroups.add(this);
            }
            updateLastModified();
            return true;
        }
        return false;
    }

    @Override
    public boolean removeParentGroup(@NotNull GroupInterface group) {
        boolean removed = parentGroups.remove(group);
        if (removed) {
            if (group instanceof JsonGroupImpl) {
                ((JsonGroupImpl) group).childGroups.remove(this);
            }
            updateLastModified();
        }
        return removed;
    }

    @Override
    public boolean inheritsFrom(@NotNull GroupInterface group) {
        if (parentGroups.contains(group)) {
            return true;
        }

        for (GroupInterface parent : parentGroups) {
            if (parent.inheritsFrom(group)) {
                return true;
            }
        }

        return false;
    }

    // ===================================================================================================
    // METADATA
    // ===================================================================================================

    @Override
    @Nullable
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(@Nullable LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    @Nullable
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    void setLastModified(@Nullable LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    @Nullable
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(@Nullable String description) {
        this.description = description;
        updateLastModified();
    }

    @Override
    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
        updateLastModified();
    }

    // ===================================================================================================
    // UTILITY METHODS
    // ===================================================================================================

    @Override
    public boolean save() {
        return storage.saveGroup(this);
    }

    @Override
    public void reload() {
        GroupInterface reloaded = storage.loadGroup(name);
        if (reloaded instanceof JsonGroupImpl) {
            JsonGroupImpl other = (JsonGroupImpl) reloaded;
            this.displayName = other.displayName;
            this.priority = other.priority;
            this.permissions.clear();
            this.permissions.addAll(other.permissions);
            this.parentGroups.clear();
            this.parentGroups.addAll(other.parentGroups);
            this.prefix = other.prefix;
            this.suffix = other.suffix;
            this.color = other.color;
            this.description = other.description;
            this.isDefault = other.isDefault;
            this.createdAt = other.createdAt;
        }
    }

    @Override
    @NotNull
    public GroupInterface copy(@NotNull String newName) {
        JsonGroupImpl copy = new JsonGroupImpl(storage.getNextGroupId(), newName, storage);
        copy.displayName = this.displayName;
        copy.priority = this.priority;
        copy.permissions.addAll(this.permissions);
        copy.prefix = this.prefix;
        copy.suffix = this.suffix;
        copy.color = this.color;
        copy.description = this.description;
        copy.isDefault = false;
        copy.createdAt = LocalDateTime.now();
        copy.lastModified = LocalDateTime.now();
        return copy;
    }

    // ===================================================================================================
    // EXTERNAL MODULE SERIALIZATION
    // ===================================================================================================

    public JsonStorage.JsonGroupData toData() {
        JsonStorage.JsonGroupData data = new JsonStorage.JsonGroupData();
        data.id = this.id;
        data.name = this.name;
        data.displayName = this.displayName;
        data.priority = this.priority;
        data.permissions = new HashSet<>(this.permissions);
        data.parentGroups = this.parentGroups.stream()
                .map(GroupInterface::getName)
                .collect(Collectors.toList());
        data.prefix = this.prefix;
        data.suffix = this.suffix;
        data.color = this.color != null ? this.color.toString() : null;
        data.description = this.description;
        data.isDefault = this.isDefault;
        data.createdAt = this.createdAt;
        data.lastModified = this.lastModified;
        return data;
    }

    public static JsonGroupImpl fromData(@NotNull JsonStorage.JsonGroupData data, @NotNull JsonStorage storage) {
        JsonGroupImpl group = new JsonGroupImpl(data.id, data.name, storage);
        group.displayName = data.displayName;
        group.priority = data.priority;

        if (data.permissions != null) {
            group.permissions.addAll(data.permissions);
        }

        if (data.parentGroups != null) {
            for (String parentName : data.parentGroups) {
                GroupInterface parent = storage.loadGroup(parentName);
                if (parent != null) {
                    group.parentGroups.add(parent);
                }
            }
        }

        group.prefix = data.prefix;
        group.suffix = data.suffix;
        if (data.color != null) {
            group.color = NamedTextColor.NAMES.value(data.color);
        }
        group.description = data.description;
        group.isDefault = data.isDefault;
        group.createdAt = data.createdAt;
        group.lastModified = data.lastModified;

        return group;
    }

    // ===================================================================================================
    // PRIVATE HELPER METHODS
    // ===================================================================================================

    private void updateLastModified() {
        this.lastModified = LocalDateTime.now();
    }

    private boolean hasWildcardPermission(@NotNull String permission) {
        Set<String> allPerms = getAllPermissions();

        if (allPerms.contains("*")) {
            return true;
        }

        String[] parts = permission.split("\\.");
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append(".");
            builder.append(parts[i]);

            String wildcard = builder.toString() + ".*";
            if (allPerms.contains(wildcard)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return "JsonGroupImpl{name=" + name + ", id=" + id + ", priority=" + priority + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GroupInterface)) return false;
        GroupInterface other = (GroupInterface) obj;
        return this.id == other.getId() && this.name.equals(other.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}