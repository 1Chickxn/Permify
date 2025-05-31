package me.chickxn.permify.spigot;

import lombok.Getter;
import me.chickxn.permify.data.interfaces.GroupInterface;
import me.chickxn.permify.data.interfaces.StorageInterface;
import me.chickxn.permify.data.storage.StorageHandler;
import me.chickxn.permify.data.storage.StorageLoader;
import me.chickxn.permify.spigot.command.PermifyCommand;
import me.chickxn.permify.spigot.handler.PermissionHandler;
import me.chickxn.permify.spigot.listener.PlayerJoinListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

@Getter
public class PermifyBase extends JavaPlugin {

    @Getter
    private static PermifyBase instance;

    private PermissionHandler permissionHandler;
    private StorageLoader storageLoader;
    private boolean isFullyLoaded = false;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Starting Permify v" + getDescription().getVersion() + "...");

        // Save default config
        saveDefaultConfig();
        reloadConfig();

        // Initialize storage system
        initializeStorage().thenAccept(success -> {
            if (success) {
                // Storage loaded successfully, continue initialization
                Bukkit.getScheduler().runTask(this, this::finishInitialization);
            } else {
                // Storage failed to load
                getLogger().severe("Failed to initialize storage system! Disabling plugin...");
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.getPluginManager().disablePlugin(this);
                });
            }
        }).exceptionally(ex -> {
            getLogger().severe("Exception during storage initialization: " + ex.getMessage());
            ex.printStackTrace();
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.getPluginManager().disablePlugin(this);
            });
            return null;
        });
    }

    /**
     * Initializes the storage system asynchronously
     */
    private CompletableFuture<Boolean> initializeStorage() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                getLogger().info("Initializing storage system...");

                // Initialize storage handler
                StorageHandler.initialize(this);

                // Create storage loader
                storageLoader = new StorageLoader(this);

                // Get configured storage type
                String storageType = getConfig().getString("storage", "json").toLowerCase();
                getLogger().info("Attempting to load storage type: " + storageType);

                // Try to load the configured storage
                return storageLoader.loadAndActivate(storageType).get();

            } catch (Exception e) {
                getLogger().severe("Error during storage initialization: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Finishes plugin initialization after storage is ready
     */
    private void finishInitialization() {
        try {
            getLogger().info("Storage system ready. Continuing initialization...");

            // Initialize permission handler
            this.permissionHandler = new PermissionHandler(this);

            // Register event listeners
            registerListeners();

            // Register commands
            registerCommands();

            // Setup existing players (if server reload)
            setupExistingPlayers();

            // Mark as fully loaded
            this.isFullyLoaded = true;

            getLogger().info("Permify has been enabled successfully!");
            getLogger().info("Active storage: " + StorageHandler.getCurrentStorageName());
            getLogger().info("Online players: " + Bukkit.getOnlinePlayers().size());

        } catch (Exception e) {
            getLogger().severe("Error during final initialization: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Registers all event listeners
     */
    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerJoinListener(permissionHandler), this);
        getLogger().info("Event listeners registered");
    }

    /**
     * Registers all commands
     */
    private void registerCommands() {
        PermifyCommand permifyCommand = new PermifyCommand(this);
        getCommand("permify").setExecutor(permifyCommand);
        getCommand("permify").setTabCompleter(permifyCommand);
        getLogger().info("Commands registered");
    }

    /**
     * Setup permissions for players who are already online (plugin reload case)
     */
    private void setupExistingPlayers() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        getLogger().info("Setting up permissions for " + Bukkit.getOnlinePlayers().size() + " existing players...");

        CompletableFuture.runAsync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    permissionHandler.onPlayerJoin(player).get();
                } catch (Exception e) {
                    getLogger().warning("Failed to setup existing player " + player.getName() + ": " + e.getMessage());
                }
            }
            getLogger().info("Finished setting up existing players");
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling Permify...");

        try {
            // Mark as not fully loaded
            this.isFullyLoaded = false;

            // Shutdown permission handler
            if (permissionHandler != null) {
                permissionHandler.shutdown();
                getLogger().info("Permission handler shut down");
            }

            // Shutdown storage system
            StorageHandler.shutdown();
            getLogger().info("Storage system shut down");

            getLogger().info("Permify has been disabled successfully!");

        } catch (Exception e) {
            getLogger().severe("Error during plugin shutdown: " + e.getMessage());
            e.printStackTrace();
        } finally {
            instance = null;
        }
    }

    /**
     * Reloads the plugin configuration and storage if needed
     */
    public CompletableFuture<Boolean> reloadPlugin() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                getLogger().info("Reloading Permify...");

                // Reload config
                reloadConfig();

                // Check if storage type changed
                String newStorageType = getConfig().getString("storage", "json").toLowerCase();
                String currentStorageType = StorageHandler.getCurrentStorageName();

                if (!newStorageType.equals(currentStorageType)) {
                    getLogger().info("Storage type changed from " + currentStorageType + " to " + newStorageType);

                    // Load new storage
                    boolean success = storageLoader.loadAndActivate(newStorageType).get();
                    if (!success) {
                        getLogger().warning("Failed to switch to " + newStorageType + ", keeping " + currentStorageType);
                        return false;
                    }
                }

                // Refresh all player permissions
                if (permissionHandler != null) {
                    permissionHandler.updateAllPlayerPermissions().get();
                }

                getLogger().info("Permify reloaded successfully!");
                return true;

            } catch (Exception e) {
                getLogger().severe("Error during plugin reload: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Checks if the plugin is fully loaded and ready
     */
    public boolean isFullyLoaded() {
        return isFullyLoaded && StorageHandler.getActiveStorage() != null;
    }

    /**
     * Gets plugin statistics
     */
    public String getPluginStats() {
        if (!isFullyLoaded()) {
            return "Plugin not fully loaded";
        }

        var permStats = permissionHandler.getStatistics();
        var storageStats = StorageHandler.getActiveStorage().getStorageStats();

        return String.format(
                "Permify Statistics:\n" +
                        "- Storage: %s (healthy: %s)\n" +
                        "- Online Players: %d\n" +
                        "- Permission Attachments: %s\n" +
                        "- Cached Players: %s\n" +
                        "- Cache Hit Rate: %.1f%%",
                StorageHandler.getCurrentStorageName(),
                storageStats.getOrDefault("healthy", false),
                Bukkit.getOnlinePlayers().size(),
                permStats.getOrDefault("attachments", 0),
                permStats.getOrDefault("cached_players", 0),
                ((Double) permStats.getOrDefault("cache_hit_rate", 0.0)) * 100
        );
    }
}