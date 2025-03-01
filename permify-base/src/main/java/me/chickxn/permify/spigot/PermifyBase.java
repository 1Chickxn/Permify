package me.chickxn.permify.spigot;

import lombok.Getter;
import me.chickxn.permify.data.storage.StorageHandler;
import me.chickxn.permify.data.storage.StorageLoader;
import me.chickxn.permify.spigot.commands.PermifyCommand;
import me.chickxn.permify.spigot.handler.PermissionHandler;
import me.chickxn.permify.spigot.listener.PlayerJoinListener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class PermifyBase extends JavaPlugin {

    @Getter
    private static PermifyBase instance;
    private PermissionHandler permissionHandler;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        String storageType = getConfig().getString("storage", "json");
        StorageLoader storageLoader = new StorageLoader(this);

        String type = getConfig().getString("storage", "json");
        if (!storageLoader.loadStorageModule(storageType)) {
            getLogger().warning("Fehler beim Laden von '" + type + "'. Fallback auf JSON.");
            storageLoader.loadStorageModule("json");
        }

        permissionHandler = new PermissionHandler(this);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerJoinListener(), this);

        getCommand("permify").setExecutor(new PermifyCommand());
        getCommand("permify").setTabCompleter(new PermifyCommand());
    }

    @Override
    public void onDisable() {
        if (StorageHandler.getActiveStorage() != null) {
            StorageHandler.getActiveStorage().stop();
        }
        for (Player player : getServer().getOnlinePlayers()) {
            permissionHandler.removeAttachment(player);
        }
    }
}
