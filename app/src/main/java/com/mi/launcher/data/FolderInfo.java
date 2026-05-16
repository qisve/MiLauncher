package com.mi.launcher.data;
import java.util.ArrayList;
import java.util.List;
public class FolderInfo {
    public String name;
    public List<AppInfo> apps = new ArrayList<>();
    public FolderInfo(String name) {
        this.name = name;
    }
    public void addApp(AppInfo app) {
        if (!apps.contains(app)) apps.add(app);
    }
    public void removeApp(AppInfo app) {
        apps.remove(app);
    }
}
