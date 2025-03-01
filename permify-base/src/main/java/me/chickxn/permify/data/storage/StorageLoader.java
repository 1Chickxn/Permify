package me.chickxn.permify.data.storage;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class StorageLoader {

    private final JavaPlugin plugin;

    public StorageLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean loadStorageModule(String storageType) {
        File moduleFile = new File(plugin.getDataFolder(), "modules/" + storageType + ".jar");
        if (!moduleFile.exists() && !downloadModule(storageType, moduleFile)) {
            plugin.getLogger().warning("Modul '" + storageType + "' konnte nicht geladen werden. Fallback auf JSON.");
            storageType = "json"; // Fallback
            moduleFile = new File(plugin.getDataFolder(), "modules/json.jar");
        }

        return registerStorage(storageType, moduleFile);
    }

    private boolean downloadModule(String storageType, File moduleFile) {
        String moduleUrl = plugin.getConfig().getString("storageModules." + storageType);
        if (moduleUrl == null || moduleUrl.isEmpty()) {
            plugin.getLogger().warning("Kein Download-Link für '" + storageType + "' in der Config.");
            return false;
        }

        plugin.getLogger().info("Lade Modul '" + storageType + "' von: " + moduleUrl);
        moduleFile.getParentFile().mkdirs();

        try {
            URL url = new URL(moduleUrl);
            try (var inputStream = url.openStream()) {
                Files.copy(inputStream, moduleFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Modul '" + storageType + "' erfolgreich heruntergeladen.");
                return true;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Fehler beim Download von '" + storageType + "': " + e.getMessage());
            return false;
        }
    }

    private boolean registerStorage(String storageType, File moduleFile) {
        if (!moduleFile.exists()) {
            plugin.getLogger().warning("Modul-Datei für '" + storageType + "' nicht gefunden.");
            return false;
        }

        try {
            URLClassLoader classLoader = new URLClassLoader(new URL[]{moduleFile.toURI().toURL()}, plugin.getClass().getClassLoader());
            Class<?> storageClass = classLoader.loadClass("me.chickxn.permify." + capitalize(storageType) + "Storage");

            Object storageInstance = storageClass.getDeclaredConstructor().newInstance();

            if (storageInstance instanceof StorageModule) {
                StorageModule storage = (StorageModule) storageInstance;
                StorageHandler.registerStorage(storageType, storage);
            } else {
                plugin.getLogger().warning("Die Klasse " + storageClass.getName() + " ist kein gültiges Storage-Modul!");
                return false;
            }


            plugin.getLogger().info("Speicher '" + storageType + "' erfolgreich registriert.");
            return StorageHandler.setActiveStorage(storageType);

        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Laden des Speicher-Moduls '" + storageType + "': " + e.getMessage());
            return false;
        }
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}
