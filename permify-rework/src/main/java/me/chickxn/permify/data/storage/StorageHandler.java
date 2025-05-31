package me.chickxn.permify.data.storage;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Central handler for managing storage modules in Permify
 */
@Getter
public class StorageHandler {

    @Getter
    private static StorageModule activeStorage;
    private static final Map<String, StorageModule> registeredModules = new HashMap<>();
    private static final Map<String, StorageModuleInfo> moduleInfos = new HashMap<>();
    private static JavaPlugin plugin;
    private static Logger logger;

    /**
     * Initializes the storage handler
     * @param pluginInstance the main plugin instance
     */
    public static void initialize(@NotNull JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        logger = pluginInstance.getLogger();
    }

    /**
     * Registers a storage module
     * @param name the module name (case-insensitive)
     * @param module the storage module instance
     * @param info metadata about the module
     */
    public static void registerStorage(@NotNull String name, @NotNull StorageModule module, @NotNull StorageModuleInfo info) {
        String normalizedName = name.toLowerCase();
        registeredModules.put(normalizedName, module);
        moduleInfos.put(normalizedName, info);

        logger.info("Storage module '" + name + "' (v" + info.getVersion() + ") registered successfully");
    }

    /**
     * Sets the active storage module
     * @param name the module name
     * @return CompletableFuture that completes when the switch is done
     */
    public static CompletableFuture<Boolean> setActiveStorage(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            String normalizedName = name.toLowerCase();
            StorageModule module = registeredModules.get(normalizedName);

            if (module == null) {
                logger.warning("Storage module '" + name + "' is not registered!");
                return false;
            }

            try {
                // Stop current storage if active
                if (activeStorage != null) {
                    logger.info("Stopping current storage module: " + getCurrentStorageName());
                    activeStorage.stop();
                }

                // Start new storage
                logger.info("Starting storage module: " + name);
                module.start();

                // Test connection
                if (!module.isHealthy()) {
                    logger.warning("Storage module '" + name + "' failed health check!");
                    module.stop();
                    return false;
                }

                activeStorage = module;
                logger.info("Successfully switched to storage module: " + name);
                return true;

            } catch (Exception e) {
                logger.severe("Failed to switch to storage module '" + name + "': " + e.getMessage());
                e.printStackTrace();

                // Try to restore previous storage
                if (activeStorage != null) {
                    try {
                        activeStorage.start();
                    } catch (Exception restoreEx) {
                        logger.severe("Failed to restore previous storage: " + restoreEx.getMessage());
                        activeStorage = null;
                    }
                }
                return false;
            }
        });
    }

    /**
     * Gets all registered storage module names
     * @return set of module names
     */
    @NotNull
    public static Set<String> getRegisteredModules() {
        return registeredModules.keySet();
    }

    /**
     * Gets information about a registered module
     * @param name the module name
     * @return module info or null if not found
     */
    @Nullable
    public static StorageModuleInfo getModuleInfo(@NotNull String name) {
        return moduleInfos.get(name.toLowerCase());
    }

    /**
     * Gets the current active storage module name
     * @return module name or "none" if no active storage
     */
    @NotNull
    public static String getCurrentStorageName() {
        if (activeStorage == null) return "none";

        return registeredModules.entrySet().stream()
                .filter(entry -> entry.getValue() == activeStorage)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("unknown");
    }

    /**
     * Checks if a storage module is registered
     * @param name the module name
     * @return true if registered
     */
    public static boolean isModuleRegistered(@NotNull String name) {
        return registeredModules.containsKey(name.toLowerCase());
    }

    /**
     * Unregisters a storage module
     * @param name the module name
     * @return true if unregistered successfully
     */
    public static boolean unregisterStorage(@NotNull String name) {
        String normalizedName = name.toLowerCase();
        StorageModule module = registeredModules.get(normalizedName);

        if (module == null) return false;

        // Can't unregister active storage
        if (module == activeStorage) {
            logger.warning("Cannot unregister active storage module '" + name + "'");
            return false;
        }

        try {
            module.stop();
            registeredModules.remove(normalizedName);
            moduleInfos.remove(normalizedName);
            logger.info("Storage module '" + name + "' unregistered successfully");
            return true;
        } catch (Exception e) {
            logger.warning("Error unregistering storage module '" + name + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Shuts down all storage modules
     */
    public static void shutdown() {
        logger.info("Shutting down storage system...");

        if (activeStorage != null) {
            try {
                activeStorage.stop();
                logger.info("Active storage stopped successfully");
            } catch (Exception e) {
                logger.warning("Error stopping active storage: " + e.getMessage());
            }
            activeStorage = null;
        }

        // Stop all other modules
        registeredModules.forEach((name, module) -> {
            try {
                module.stop();
            } catch (Exception e) {
                logger.warning("Error stopping module '" + name + "': " + e.getMessage());
            }
        });

        registeredModules.clear();
        moduleInfos.clear();
        logger.info("Storage system shutdown complete");
    }

    /**
     * Performs health check on active storage
     * @return true if healthy
     */
    public static boolean isActiveStorageHealthy() {
        return activeStorage != null && activeStorage.isHealthy();
    }
}