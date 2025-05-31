package me.chickxn.permify.data.storage;

import me.chickxn.permify.data.interfaces.StorageInterface;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for all storage modules
 */
public abstract class StorageModule implements StorageInterface {

    protected boolean isRunning = false;
    protected long startTime = 0;

    /**
     * Starts the storage module
     * @throws Exception if startup fails
     */
    public abstract void start() throws Exception;

    /**
     * Stops the storage module gracefully
     * @throws Exception if shutdown fails
     */
    public abstract void stop() throws Exception;

    /**
     * Checks if this storage module is currently running
     * @return true if running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Gets how long this module has been running (in milliseconds)
     * @return uptime in milliseconds
     */
    public long getUptime() {
        return isRunning ? System.currentTimeMillis() - startTime : 0;
    }

    /**
     * Gets the storage type name
     * @return storage type (e.g., "mysql", "json", "sqlite")
     */
    @NotNull
    public abstract String getStorageType();

    /**
     * Gets configuration requirements for this storage type
     * @return map of config keys to descriptions
     */
    @NotNull
    public abstract Map<String, String> getConfigRequirements();

    /**
     * Validates the configuration for this storage module
     * @param config configuration map
     * @return true if configuration is valid
     */
    public abstract boolean validateConfig(@NotNull Map<String, Object> config);

    /**
     * Performs an asynchronous health check
     * @return CompletableFuture with health status
     */
    public CompletableFuture<Boolean> performHealthCheck() {
        return CompletableFuture.supplyAsync(this::isHealthy);
    }

    /**
     * Gets detailed storage statistics
     * @return map of statistics
     */
    @Override
    @NotNull
    public Map<String, Object> getStorageStats() {
        return Map.of(
                "type", getStorageType(),
                "running", isRunning(),
                "uptime", getUptime(),
                "healthy", isHealthy()
        );
    }

    /**
     * Performs migration from another storage type
     * @param fromStorage the source storage
     * @return CompletableFuture that completes when migration is done
     */
    public CompletableFuture<Boolean> migrateFrom(@NotNull StorageModule fromStorage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Default implementation - override for custom migration logic
                performMigration(fromStorage);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Override this method to implement custom migration logic
     * @param fromStorage source storage
     * @throws Exception if migration fails
     */
    protected void performMigration(@NotNull StorageModule fromStorage) throws Exception {
        // Default: copy all data
        var allPlayers = fromStorage.getAllPlayerUUIDs();
        var allGroups = fromStorage.getAllGroups();

        // Migrate groups first
        for (var group : allGroups) {
            saveGroup(group);
        }

        // Then migrate players
        for (var uuid : allPlayers) {
            var player = fromStorage.loadPlayer(uuid);
            if (player != null) {
                savePlayer(player);
            }
        }
    }

    /**
     * Called when this module becomes the active storage
     */
    protected void onActivated() {
        // Override if needed
    }

    /**
     * Called when this module is no longer the active storage
     */
    protected void onDeactivated() {
        // Override if needed
    }

    /**
     * Helper method to mark module as started
     */
    protected final void markStarted() {
        this.isRunning = true;
        this.startTime = System.currentTimeMillis();
        onActivated();
    }

    /**
     * Helper method to mark module as stopped
     */
    protected final void markStopped() {
        this.isRunning = false;
        this.startTime = 0;
        onDeactivated();
    }
}