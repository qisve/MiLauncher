package com.mi.launcher.data;
import android.graphics.drawable.Drawable;
public class AppInfo {
    public String label;
    public String packageName;
    public String activityName;
    public Drawable icon;
    public int badgeCount;
    public AppInfo() {}
    public AppInfo(String label, String pkg, String act) {
        this.label = label;
        this.packageName = pkg;
        this.activityName = act;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppInfo)) return false;
        return packageName.equals(((AppInfo) o).packageName);
    }
    @Override
    public int hashCode() { return packageName.hashCode(); }
}
