package me.chickxn.permify.data.storage;

import me.chickxn.permify.data.interfaces.StorageInterface;

public abstract class StorageModule implements StorageInterface {

    public abstract void start();
    public abstract void stop();

}
