package me.chickxn.permify.data.storage;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Getter
public class StorageHandler {

    @Getter
    private static StorageModule activeStorage;
    private static final Map<String, StorageModule> storageModules = new HashMap<>();

    public static void registerStorage(String name, StorageModule storageModule) {
        storageModules.put(name.toLowerCase(), storageModule);
    }

    public static boolean setActiveStorage(String name) {
        StorageModule module = storageModules.get(name.toLowerCase());
        if (module != null) {
            if (activeStorage != null) {
                activeStorage.stop();
            }
            activeStorage = module;
            activeStorage.start();
            return true;
        }
        return false;
    }
}
