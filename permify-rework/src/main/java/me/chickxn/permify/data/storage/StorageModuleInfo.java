package me.chickxn.permify.data.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contains metadata about a storage module
 */
@Getter
@AllArgsConstructor
public class StorageModuleInfo {

    @NotNull
    private final String name;

    @NotNull
    private final String version;

    @NotNull
    private final String author;

    @Nullable
    private final String mainClass;

    @Override
    public String toString() {
        return name + " v" + version + " by " + author;
    }
}