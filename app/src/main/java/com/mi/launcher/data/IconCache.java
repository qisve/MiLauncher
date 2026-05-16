package com.mi.launcher.data;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
public class IconCache {
    private static final int MAX_SIZE = 200;
    private LruCache<String, Drawable> cache = new LruCache<>(MAX_SIZE);
    private static IconCache instance;
    public static IconCache getInstance() {
        if (instance == null) instance = new IconCache();
        return instance;
    }
    public Drawable get(String packageName) {
        return cache.get(packageName);
    }
    public void put(String packageName, Drawable icon) {
        cache.put(packageName, icon);
    }
    public void clear() {
        cache.evictAll();
    }
}
