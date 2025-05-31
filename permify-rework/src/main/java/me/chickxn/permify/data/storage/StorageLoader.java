package me.chickxn.permify.data.storage;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Handles dynamic loading of storage modules
 */
public class StorageLoader {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final File storageDir;

    public StorageLoader(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.storageDir = new File(plugin.getDataFolder(), "storage");

        // Create storage directory if it doesn't exist
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
    }

    /**
     * Loads a storage module asynchronously
     * @param storageType the storage type name
     * @return CompletableFuture that completes when loading is done
     */
    public CompletableFuture<Boolean> loadStorageModule(@NotNull String storageType) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Loading storage module: " + storageType);

            File moduleFile = new File(storageDir, storageType.toLowerCase() + ".jar");

            // Download module if it doesn't exist
            if (!moduleFile.exists()) {
                logger.info("Module file not found, attempting download...");
                if (!downloadModule(storageType, moduleFile)) {
                    logger.warning("Failed to download module '" + storageType + "', trying fallback to built-in modules");
                    return loadBuiltInModule(storageType);
                }
            }

            return registerStorageFromFile(storageType, moduleFile);
        });
    }

    /**
     * Downloads a storage module from configured URL
     */
    private boolean downloadModule(@NotNull String storageType, @NotNull File moduleFile) {
        String moduleUrl = plugin.getConfig().getString("storageModules." + storageType.toLowerCase());

        if (moduleUrl == null || moduleUrl.trim().isEmpty()) {
            logger.warning("No download URL configured for storage module '" + storageType + "'");
            return false;
        }

        logger.info("Downloading storage module '" + storageType + "' from: " + moduleUrl);

        try {
            URL url = new URL(moduleUrl);
            try (InputStream inputStream = url.openStream()) {
                Files.copy(inputStream, moduleFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Successfully downloaded storage module: " + storageType);
                return true;
            }
        } catch (IOException e) {
            logger.severe("Failed to download storage module '" + storageType + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Tries to load a built-in storage module (like JSON)
     */
    private boolean loadBuiltInModule(@NotNull String storageType) {
        try {
            // Try to load from main plugin classpath
            String className = "me.chickxn.permify.storage.builtin." + capitalize(storageType) + "Storage";
            Class<?> storageClass = Class.forName(className);
            return instantiateStorageModule(storageType, storageClass);
        } catch (ClassNotFoundException e) {
            logger.severe("Built-in storage module '" + storageType + "' not found!");
            return false;
        }
    }

    /**
     * Registers a storage module from a JAR file
     */
    private boolean registerStorageFromFile(@NotNull String storageType, @NotNull File moduleFile) {
        if (!moduleFile.exists()) {
            logger.warning("Storage module file not found: " + moduleFile.getPath());
            return false;
        }

        try {
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{moduleFile.toURI().toURL()},
                    plugin.getClass().getClassLoader()
            );

            // Load module info first
            StorageModuleInfo moduleInfo = loadModuleInfo(classLoader, storageType);
            if (moduleInfo == null) {
                logger.warning("Could not load module info for: " + storageType);
                return false;
            }

            // Load main storage class
            String className = moduleInfo.getMainClass();
            if (className == null) {
                className = "me.chickxn.permify." + capitalize(storageType) + "Storage";
            }

            Class<?> storageClass = classLoader.loadClass(className);
            return instantiateStorageModule(storageType, storageClass, moduleInfo);

        } catch (Exception e) {
            logger.severe("Failed to load storage module '" + storageType + "': " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Loads module metadata from module.properties
     */
    private StorageModuleInfo loadModuleInfo(@NotNull URLClassLoader classLoader, @NotNull String storageType) {
        try (InputStream stream = classLoader.getResourceAsStream("module.properties")) {
            if (stream == null) {
                // Create default info if no properties file
                return new StorageModuleInfo(storageType, "1.0.0", "Unknown", null);
            }

            Properties props = new Properties();
            props.load(stream);

            return new StorageModuleInfo(
                    props.getProperty("name", storageType),
                    props.getProperty("version", "1.0.0"),
                    props.getProperty("author", "Unknown"),
                    props.getProperty("main-class")
            );
        } catch (IOException e) {
            logger.warning("Failed to load module.properties for " + storageType + ": " + e.getMessage());
            return new StorageModuleInfo(storageType, "1.0.0", "Unknown", null);
        }
    }

    /**
     * Creates an instance of the storage module
     */
    private boolean instantiateStorageModule(@NotNull String storageType, @NotNull Class<?> storageClass) {
        return instantiateStorageModule(storageType, storageClass,
                new StorageModuleInfo(storageType, "1.0.0", "Built-in", null));
    }

    private boolean instantiateStorageModule(@NotNull String storageType, @NotNull Class<?> storageClass,
                                             @NotNull StorageModuleInfo moduleInfo) {
        try {
            Object storageInstance = storageClass.getDeclaredConstructor().newInstance();

            if (!(storageInstance instanceof StorageModule)) {
                logger.severe("Class " + storageClass.getName() + " is not a valid StorageModule!");
                return false;
            }

            StorageModule storage = (StorageModule) storageInstance;
            StorageHandler.registerStorage(storageType, storage, moduleInfo);

            logger.info("Successfully loaded storage module: " + storageType + " v" + moduleInfo.getVersion());
            return true;

        } catch (Exception e) {
            logger.severe("Failed to instantiate storage module '" + storageType + "': " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Capitalizes the first letter of a string
     */
    private String capitalize(@NotNull String input) {
        if (input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    /**
     * Loads and activates a storage module
     * @param storageType the storage type
     * @return CompletableFuture that completes when done
     */
    public CompletableFuture<Boolean> loadAndActivate(@NotNull String storageType) {
        return loadStorageModule(storageType)
                .thenCompose(loaded -> {
                    if (loaded) {
                        return StorageHandler.setActiveStorage(storageType);
                    } else {
                        return CompletableFuture.completedFuture(false);
                    }
                });
    }
}
