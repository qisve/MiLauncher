package com.mi.launcher.data;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class DataStore {
    private static final String TAG = "MiLauncher";
    private static final String PREFS_NAME = "mi_launcher_prefs";
    private static final String KEY_DOCK_APPS = "dock_apps";
    private static final String KEY_HIDDEN_APPS = "hidden_apps";
    private static final String KEY_GRID_COLUMNS = "grid_columns";
    private static final String KEY_ICON_SCALE = "icon_scale";
    private static final String KEY_ANIM_ENABLED = "anim_enabled";
    private static final String KEY_GESTURE_UP = "gesture_up";
    private static final String KEY_GESTURE_DOWN = "gesture_down";
    private static final String KEY_GESTURE_DOUBLE_TAP = "gesture_double_tap";
    private SharedPreferences prefs;
    public DataStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    // Grid columns (3/4/5)
    public int getGridColumns() { return prefs.getInt(KEY_GRID_COLUMNS, 4); }
    public void setGridColumns(int cols) { prefs.edit().putInt(KEY_GRID_COLUMNS, cols).apply(); }
    // Icon scale (0.8 ~ 1.2)
    public float getIconScale() { return prefs.getFloat(KEY_ICON_SCALE, 1.0f); }
    public void setIconScale(float scale) { prefs.edit().putFloat(KEY_ICON_SCALE, scale).apply(); }
    // Animation
    public boolean isAnimEnabled() { return prefs.getBoolean(KEY_ANIM_ENABLED, true); }
    public void setAnimEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_ANIM_ENABLED, enabled).apply(); }
    // Dock apps (package names)
    public List<String> getDockApps() {
        Set<String> set = prefs.getStringSet(KEY_DOCK_APPS, new HashSet<>());
        return new ArrayList<>(set);
    }
    public void setDockApps(List<String> pkgs) {
        prefs.edit().putStringSet(KEY_DOCK_APPS, new HashSet<>(pkgs)).apply();
    }
    // Hidden apps
    public Set<String> getHiddenApps() {
        return prefs.getStringSet(KEY_HIDDEN_APPS, new HashSet<>());
    }
    public void setHiddenApps(Set<String> pkgs) {
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, pkgs).apply();
    }
    public boolean isAppHidden(String pkg) {
        return getHiddenApps().contains(pkg);
    }
    // Gesture actions
    public String getGestureAction(String key) {
        return prefs.getString(key, "none");
    }
    public void setGestureAction(String key, String action) {
        prefs.edit().putString(key, action).apply();
    }
}
