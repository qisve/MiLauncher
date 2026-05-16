package com.mi.launcher;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import com.mi.launcher.anim.AnimManager;
import com.mi.launcher.blur.BlurManager;
import com.mi.launcher.data.AppInfo;
import com.mi.launcher.data.DataStore;
import com.mi.launcher.data.FolderInfo;
import com.mi.launcher.data.IconCache;
import com.mi.launcher.settings.SettingsActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class LauncherActivity extends Activity {
    private static final String TAG = "MiLauncher";
    private HorizontalScrollView hscroll;
    private LinearLayout pagesContainer;
    private LinearLayout dockLayout;
    private LinearLayout pageIndicator;
    private FrameLayout blurOverlay;
    private FrameLayout folderOverlay;
    private FrameLayout contextOverlay;
    private FrameLayout editOverlay;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> dockApps = new ArrayList<>();
    private DataStore dataStore;
    private IconCache iconCache;
    private int iconSize;
    private int screenWidth;
    private int columns = 4;
    private int rows = 5;
    private int appsPerPage;
    private int currentPage = 0;
    private int totalPages = 0;
    // Drag state
    private boolean isDragging = false;
    private AppInfo draggedApp = null;
    private boolean dragFromDock = false;
    private int dragSourceIndex = -1;
    // Snap
    private Runnable snapRunnable;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupFullScreen();
        setContentView(R.layout.activity_launcher);
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenWidth = dm.widthPixels;
        iconSize = getResources().getDimensionPixelSize(R.dimen.icon_size);
        dataStore = new DataStore(this);
        iconCache = IconCache.getInstance();
        columns = dataStore.getGridColumns();
        appsPerPage = columns * rows;
        initViews();
        loadApps();
        setupPageSnap();
    }
    @Override
    protected void onResume() {
        super.onResume();
        setupFullScreen();
        // Reload apps in case something changed
        loadApps();
    }
    private void setupFullScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0x00000000);
        getWindow().setNavigationBarColor(0x00000000);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private void initViews() {
        hscroll = findViewById(R.id.hscroll);
        pagesContainer = findViewById(R.id.pages_container);
        dockLayout = findViewById(R.id.dock);
        pageIndicator = findViewById(R.id.page_indicator);
        blurOverlay = findViewById(R.id.blur_overlay);
        folderOverlay = findViewById(R.id.folder_overlay);
        contextOverlay = findViewById(R.id.context_overlay);
        editOverlay = findViewById(R.id.edit_overlay);
        // Hide overlays on tap
        contextOverlay.setOnClickListener(v -> hideContextMenu());
        editOverlay.setOnClickListener(v -> hideEditMode());
        folderOverlay.setOnClickListener(v -> hideFolder());
        // Long press on empty area -> edit mode
        hscroll.setOnLongClickListener(v -> {
            showEditMode();
            return true;
        });
    }
    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent mainIntent = new Intent(Intent.ACTION_MAIN);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveList = pm.queryIntentActivities(mainIntent, 0);
            List<AppInfo> apps = new ArrayList<>();
            for (ResolveInfo info : resolveList) {
                if (info.activityInfo.packageName.equals(getPackageName())) continue;
                if (dataStore.isAppHidden(info.activityInfo.packageName)) continue;
                AppInfo app = new AppInfo();
                app.label = info.loadLabel(pm).toString();
                app.packageName = info.activityInfo.packageName;
                app.activityName = info.activityInfo.name;
                Drawable cached = iconCache.get(app.packageName);
                if (cached != null) {
                    app.icon = cached;
                } else {
                    try {
                        app.icon = pm.getApplicationIcon(info.activityInfo.packageName);
                    } catch (Exception e) {
                        app.icon = info.loadIcon(pm);
                    }
                    iconCache.put(app.packageName, app.icon);
                }
                apps.add(app);
            }
            Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
            allApps = apps;
            // Load dock from saved data
            List<String> savedDock = dataStore.getDockApps();
            dockApps.clear();
            if (!savedDock.isEmpty()) {
                for (String pkg : savedDock) {
                    for (AppInfo app : allApps) {
                        if (app.packageName.equals(pkg)) {
                            dockApps.add(app);
                            break;
                        }
                    }
                }
            }
            if (dockApps.isEmpty()) {
                for (int i = 0; i < Math.min(4, apps.size()); i++) {
                    dockApps.add(apps.get(i));
                }
            }
            handler.post(this::buildAll);
        }).start();
    }
    private void buildAll() {
        buildPages();
        buildDock();
        buildPageIndicator();
    }
    // ==================== Pages ====================
    private void buildPages() {
        pagesContainer.removeAllViews();
        totalPages = (int) Math.ceil((double) allApps.size() / appsPerPage);
        if (totalPages == 0) totalPages = 1;
        for (int page = 0; page < totalPages; page++) {
            int startIndex = page * appsPerPage;
            LinearLayout pageView = buildOnePage(startIndex);
            pageView.setLayoutParams(new LinearLayout.LayoutParams(screenWidth, LinearLayout.LayoutParams.MATCH_PARENT));
            pagesContainer.addView(pageView);
        }
    }
    private LinearLayout buildOnePage(int startIndex) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.setPadding(
                getResources().getDimensionPixelSize(R.dimen.grid_padding), 0,
                getResources().getDimensionPixelSize(R.dimen.grid_padding),
                getResources().getDimensionPixelSize(R.dimen.grid_padding));
        int count = Math.min(appsPerPage, allApps.size() - startIndex);
        int totalRows = (int) Math.ceil((double) Math.max(count, 1) / columns);
        for (int row = 0; row < totalRows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            rowLayout.setLayoutParams(rowParams);
            for (int col = 0; col < columns; col++) {
                int appIndex = startIndex + row * columns + col;
                View cell = createGridCell(appIndex);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                cell.setLayoutParams(cellParams);
                rowLayout.addView(cell);
            }
            page.addView(rowLayout);
        }
        return page;
    }
    private View createGridCell(int appIndex) {
        // Every cell is a valid drop target
        FrameLayout cell = new FrameLayout(this);
        cell.setBackgroundColor(Color.TRANSPARENT);
        cell.setTag(appIndex);
        if (appIndex < allApps.size()) {
            AppInfo app = allApps.get(appIndex);
            View iconView = buildIconView(app, iconSize, true);
            cell.addView(iconView);
        }
        // All cells accept drops
        cell.setOnDragListener(createCellDragListener(appIndex));
        return cell;
    }
    private View buildIconView(AppInfo app, int size, boolean showLabel) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(8, 8, 8, 4);
        container.setBackgroundColor(Color.TRANSPARENT);
        container.setTag(app);
        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
        icon.setLayoutParams(iconParams);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        container.addView(icon);
        if (showLabel) {
            TextView label = new TextView(this);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = getResources().getDimensionPixelSize(R.dimen.label_margin);
            label.setLayoutParams(labelParams);
            label.setText(app.label);
            label.setTextSize(getResources().getDimension(R.dimen.label_size));
            label.setTextColor(0xDDFFFFFF);
            label.setMaxLines(1);
            label.setShadowLayer(5, 0, 1, 0xAA000000);
            label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            container.addView(label);
        }
        // Touch feedback (iOS style)
        container.setOnTouchListener((v, event) -> {
            if (isDragging) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    AnimManager.pressDown(v);
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    AnimManager.pressUp(v);
                    return false;
            }
            return false;
        });
        // Click -> launch
        container.setOnClickListener(v -> {
            if (!isDragging) launchApp(app);
        });
        // Long press -> start drag or show context menu
        container.setOnLongClickListener(v -> {
            draggedApp = app;
            dragFromDock = false;
            dragSourceIndex = (int) ((View) v.getParent()).getTag();
            isDragging = true;
            ClipData clipData = ClipData.newPlainText("app_pkg", app.packageName);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            v.startDragAndDrop(clipData, shadow, v, 0);
            v.setAlpha(0.3f);
            return true;
        });
        return container;
    }
    private View.OnDragListener createCellDragListener(int cellIndex) {
        return (v, event) -> {
            if (!isDragging) return false;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setBackgroundColor(0x22FFFFFF);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    v.setBackgroundColor(Color.TRANSPARENT);
                    break;
                case DragEvent.ACTION_DROP:
                    v.setBackgroundColor(Color.TRANSPARENT);
                    if (dragFromDock) {
                        // From dock to grid
                        int insertAt = Math.min(cellIndex, allApps.size());
                        allApps.add(insertAt, draggedApp);
                        dockApps.remove(draggedApp);
                    } else {
                        // Grid move
                        if (cellIndex != dragSourceIndex) {
                            AppInfo moved = allApps.remove(dragSourceIndex);
                            int target = cellIndex;
                            if (target > dragSourceIndex) target--;
                            target = Math.min(target, allApps.size());
                            allApps.add(target, moved);
                        }
                    }
                    endDrag();
                    saveAndRebuild();
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setBackgroundColor(Color.TRANSPARENT);
                    if (!event.getResult()) {
                        endDrag();
                        saveAndRebuild();
                    }
                    break;
            }
            return true;
        };
    }
    // ==================== Dock ====================
    private void buildDock() {
        dockLayout.removeAllViews();
        dockLayout.setOnDragListener((v, event) -> {
            if (!isDragging) return false;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    dockLayout.animate().translationY(-8f).setDuration(200).start();
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    dockLayout.animate().translationY(0f).setDuration(200).start();
                    break;
                case DragEvent.ACTION_DROP:
                    dockLayout.animate().translationY(0f).setDuration(300)
                            .setInterpolator(new OvershootInterpolator(2f)).start();
                    if (!dragFromDock && dockApps.size() < 5) {
                        dockApps.add(draggedApp);
                        allApps.remove(draggedApp);
                        endDrag();
                        saveAndRebuild();
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    dockLayout.animate().translationY(0f).setDuration(200).start();
                    break;
            }
            return true;
        });
        for (int i = 0; i < dockApps.size(); i++) {
            View cell = createDockCell(i);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            cell.setLayoutParams(params);
            dockLayout.addView(cell);
        }
    }
    private View createDockCell(int dockIndex) {
        AppInfo app = dockApps.get(dockIndex);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(8, 12, 8, 8);
        container.setBackgroundColor(Color.TRANSPARENT);
        container.setTag(dockIndex);
        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        icon.setLayoutParams(iconParams);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        container.addView(icon);
        // Tap with iOS bounce
        container.setOnClickListener(v -> {
            if (!isDragging) {
                AnimManager.dockTap(v, () -> launchApp(app));
            }
        });
        // Touch feedback
        container.setOnTouchListener((v, event) -> {
            if (isDragging) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    AnimManager.pressDown(v);
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    AnimManager.pressUp(v);
                    return false;
            }
            return false;
        });
        // Long press -> drag from dock
        container.setOnLongClickListener(v -> {
            draggedApp = app;
            dragFromDock = true;
            dragSourceIndex = dockIndex;
            isDragging = true;
            ClipData clipData = ClipData.newPlainText("app_pkg", app.packageName);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            v.startDragAndDrop(clipData, shadow, v, 0);
            v.setAlpha(0.3f);
            return true;
        });
        // Dock cell accepts drops (for reordering)
        container.setOnDragListener((v, event) -> {
            if (!isDragging) return false;
            int targetIdx = (int) v.getTag();
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setAlpha(0.7f);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    v.setAlpha(1f);
                    break;
                case DragEvent.ACTION_DROP:
                    v.setAlpha(1f);
                    if (dragFromDock) {
                        if (targetIdx != dragSourceIndex && targetIdx < dockApps.size()) {
                            AppInfo src = dockApps.remove(dragSourceIndex);
                            if (targetIdx > dragSourceIndex) targetIdx--;
                            dockApps.add(targetIdx, src);
                        }
                    } else {
                        if (dockApps.size() < 5) {
                            dockApps.add(targetIdx, draggedApp);
                            allApps.remove(draggedApp);
                        }
                    }
                    endDrag();
                    saveAndRebuild();
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1f);
                    if (!event.getResult()) {
                        endDrag();
                        saveAndRebuild();
                    }
                    break;
            }
            return true;
        });
        return container;
    }
    private void endDrag() {
        isDragging = false;
        draggedApp = null;
        dragFromDock = false;
        dragSourceIndex = -1;
    }
    private void saveAndRebuild() {
        List<String> dockPkgs = new ArrayList<>();
        for (AppInfo a : dockApps) dockPkgs.add(a.packageName);
        dataStore.setDockApps(dockPkgs);
        buildAll();
    }
    // ==================== Page Snap (no jitter) ====================
    private void setupPageSnap() {
        snapRunnable = null;
        hscroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (snapRunnable != null) handler.removeCallbacks(snapRunnable);
            // Update indicator in real time
            int page = Math.round((float) scrollX / screenWidth);
            page = Math.max(0, Math.min(page, totalPages - 1));
            if (page != currentPage) {
                currentPage = page;
                updatePageIndicator();
            }
            // Wallpaper parallax
            updateWallpaperParallax(scrollX);
            // Snap after scroll stops
            snapRunnable = () -> {
                int sx = hscroll.getScrollX();
                int targetPage = Math.round((float) sx / screenWidth);
                targetPage = Math.max(0, Math.min(targetPage, totalPages - 1));
                int targetX = targetPage * screenWidth;
                if (Math.abs(sx - targetX) > 2) {
                    smoothSnapToPage(targetPage);
                }
                currentPage = targetPage;
                updatePageIndicator();
            };
            handler.postDelayed(snapRunnable, 60);
        });
    }
    private void smoothSnapToPage(int targetPage) {
        int targetX = targetPage * screenWidth;
        int currentX = hscroll.getScrollX();
        int distance = Math.abs(targetX - currentX);
        long duration = Math.min(350, 150 + distance / 4);
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(currentX, targetX);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(a -> hscroll.scrollTo((int) a.getAnimatedValue(), 0));
        animator.start();
    }
    // Wallpaper parallax (subtle shift during scroll)
    private void updateWallpaperParallax(int scrollX) {
        View root = findViewById(R.id.root);
        float offset = scrollX * 0.05f;
        root.setTranslationX(-offset);
    }
    // ==================== Page Indicator ====================
    private void buildPageIndicator() {
        pageIndicator.removeAllViews();
        if (totalPages <= 1) {
            pageIndicator.setVisibility(View.GONE);
            return;
        }
        pageIndicator.setVisibility(View.VISIBLE);
        int dotSize = getResources().getDimensionPixelSize(R.dimen.page_indicator_dot);
        int margin = getResources().getDimensionPixelSize(R.dimen.page_indicator_margin);
        for (int i = 0; i < totalPages; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.dot_active);
            dot.setAlpha(i == 0 ? 1.0f : 0.3f);
            pageIndicator.addView(dot);
        }
    }
    private void updatePageIndicator() {
        for (int i = 0; i < pageIndicator.getChildCount(); i++) {
            float targetAlpha = i == currentPage ? 1.0f : 0.3f;
            pageIndicator.getChildAt(i).animate().alpha(targetAlpha).setDuration(200).start();
        }
    }
    // ==================== Launch App with Blur ====================
    public void launchApp(AppInfo app) {
        // Show blur overlay
        BlurManager.showBlurOverlay(this, blurOverlay, true);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(app.packageName, app.activityName));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(intent);
        overridePendingTransition(R.anim.ios_open, 0);
        // Hide blur after a delay
        handler.postDelayed(() -> BlurManager.hideBlurOverlay(blurOverlay, true), 500);
    }
    // ==================== Edit Mode ====================
    private boolean editMode = false;
    private void showEditMode() {
        editMode = true;
        editOverlay.setVisibility(View.VISIBLE);
        editOverlay.setAlpha(0f);
        editOverlay.animate().alpha(1f).setDuration(200).start();
        buildEditModeUI();
        // Start wobble on all icons
        for (int i = 0; i < pagesContainer.getChildCount(); i++) {
            View page = pagesContainer.getChildAt(i);
            if (page instanceof ViewGroup) {
                wobbleAll((ViewGroup) page, 0);
            }
        }
    }
    private void hideEditMode() {
        editMode = false;
        editOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> {
                    editOverlay.setVisibility(View.GONE);
                    editOverlay.removeAllViews();
                }).start();
        // Stop wobble
        for (int i = 0; i < pagesContainer.getChildCount(); i++) {
            View page = pagesContainer.getChildAt(i);
            if (page instanceof ViewGroup) {
                stopWobbleAll((ViewGroup) page);
            }
        }
    }
    private void wobbleAll(ViewGroup parent, int delay) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                AnimManager.startWobble(child, delay + i * 50);
            }
        }
    }
    private void stopWobbleAll(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                AnimManager.stopWobble(child);
            }
        }
    }
    private void buildEditModeUI() {
        editOverlay.removeAllViews();
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = 200;
        buttons.setLayoutParams(params);
        buttons.setPadding(32, 16, 32, 16);
        buttons.setBackgroundResource(R.drawable.bg_edit_mode);
        String[] labels = {"壁纸", "设置", "完成"};
        Runnable[] actions = {this::openWallpaperChooser, this::openSettings, this::hideEditMode};
        for (int i = 0; i < labels.length; i++) {
            TextView btn = new TextView(this);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.setMargins(24, 0, 24, 0);
            btn.setLayoutParams(btnParams);
            btn.setText(labels[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(14);
            btn.setPadding(32, 16, 32, 16);
            btn.setBackgroundResource(R.drawable.bg_edit_btn);
            final int actionIdx = i;
            btn.setOnClickListener(v -> actions[actionIdx].run());
            buttons.addView(btn);
        }
        editOverlay.addView(buttons);
    }
    // ==================== Context Menu ====================
    private void showContextMenu(AppInfo app, View anchorView) {
        contextOverlay.setVisibility(View.VISIBLE);
        contextOverlay.setAlpha(0f);
        contextOverlay.animate().alpha(1f).setDuration(150).start();
        contextOverlay.removeAllViews();
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundResource(R.drawable.bg_context_menu);
        menu.setPadding(32, 16, 32, 16);
        menu.setElevation(12f);
        int[] loc = new int[2];
        anchorView.getLocationOnScreen(loc);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.context_menu_width),
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = Math.min(loc[0], screenWidth - params.width - 20);
        params.topMargin = loc[1] + anchorView.getHeight() + 8;
        menu.setLayoutParams(params);
        menu.setAlpha(0f);
        menu.setScaleX(0.8f);
        menu.setScaleY(0.8f);
        menu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(new OvershootInterpolator(2f)).start();
        String[] items = {"卸载", "应用信息", "分享", "移除"};
        int[] icons = {0, 0, 0, 0};
        for (int i = 0; i < items.length; i++) {
            TextView item = new TextView(this);
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            item.setText(items[i]);
            item.setTextColor(0xFFFFFFFF);
            item.setTextSize(14);
            item.setPadding(16, 20, 16, 20);
            item.setClickable(true);
            final int idx = i;
            item.setOnClickListener(v -> {
                hideContextMenu();
                switch (idx) {
                    case 0: uninstallApp(app); break;
                    case 1: openAppInfo(app); break;
                    case 2: shareApp(app); break;
                    case 3: removeFromDesktop(app); break;
                }
            });
            menu.addView(item);
            if (i < items.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0x22FFFFFF);
                menu.addView(divider);
            }
        }
        contextOverlay.addView(menu);
    }
    private void hideContextMenu() {
        contextOverlay.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> {
                    contextOverlay.setVisibility(View.GONE);
                    contextOverlay.removeAllViews();
                }).start();
    }
    private void uninstallApp(AppInfo app) {
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(android.net.Uri.parse("package:" + app.packageName));
        startActivity(intent);
    }
    private void openAppInfo(AppInfo app) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(android.net.Uri.parse("package:" + app.packageName));
        startActivity(intent);
    }
    private void shareApp(AppInfo app) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "推荐应用: " + app.label + "\nhttps://play.google.com/store/apps/details?id=" + app.packageName);
        startActivity(Intent.createChooser(intent, "分享应用"));
    }
    private void removeFromDesktop(AppInfo app) {
        allApps.remove(app);
        saveAndRebuild();
    }
    // ==================== Folder ====================
    private void showFolder(FolderInfo folder) {
        folderOverlay.setVisibility(View.VISIBLE);
        folderOverlay.setAlpha(0f);
        folderOverlay.animate().alpha(1f).setDuration(200).start();
        folderOverlay.removeAllViews();
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setBackgroundResource(R.drawable.bg_folder);
        container.setPadding(32, 24, 32, 24);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.leftMargin = 48;
        params.rightMargin = 48;
        container.setLayoutParams(params);
        // Folder name
        TextView nameView = new TextView(this);
        nameView.setText(folder.name);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(18);
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(0, 0, 0, 24);
        container.addView(nameView);
        // Folder apps grid
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int folderCols = 3;
        int folderIconSize = getResources().getDimensionPixelSize(R.dimen.icon_size_folder);
        List<AppInfo> folderApps = folder.apps;
        int folderRows = (int) Math.ceil((double) folderApps.size() / folderCols);
        for (int r = 0; r < folderRows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            for (int c = 0; c < folderCols; c++) {
                int idx = r * folderCols + c;
                if (idx < folderApps.size()) {
                    View icon = buildIconView(folderApps.get(idx), folderIconSize, true);
                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    icon.setLayoutParams(iconParams);
                    row.addView(icon);
                } else {
                    View spacer = new View(this);
                    spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                    row.addView(spacer);
                }
            }
            grid.addView(row);
        }
        container.addView(grid);
        // Animate open
        container.setScaleX(0.3f);
        container.setScaleY(0.3f);
        container.setAlpha(0f);
        container.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350)
                .setInterpolator(new OvershootInterpolator(2f)).start();
        folderOverlay.addView(container);
    }
    private void hideFolder() {
        ViewGroup container = folderOverlay.getChildCount() > 0 ? (ViewGroup) folderOverlay.getChildAt(0) : null;
        if (container != null) {
            container.animate().scaleX(0.3f).scaleY(0.3f).alpha(0f).setDuration(250)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .withEndAction(() -> {
                        folderOverlay.setVisibility(View.GONE);
                        folderOverlay.removeAllViews();
                    }).start();
        } else {
            folderOverlay.setVisibility(View.GONE);
        }
    }
    // ==================== Wallpaper & Settings ====================
    private void openWallpaperChooser() {
        hideEditMode();
        try {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "选择壁纸"));
        } catch (Exception e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (Exception ignored) {}
        }
    }
    private void openSettings() {
        hideEditMode();
        startActivity(new Intent(this, SettingsActivity.class));
    }
    // ==================== Gesture ====================
    private float touchStartY = 0;
    private long touchStartTime = 0;
    private long lastTapTime = 0;
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = ev.getY();
                touchStartTime = System.currentTimeMillis();
                break;
            case MotionEvent.ACTION_UP:
                float dy = ev.getY() - touchStartY;
                long dt = System.currentTimeMillis() - touchStartTime;
                // Swipe down -> notification panel
                if (dy > 200 && dt < 500 && Math.abs(ev.getX() - screenWidth / 2f) < screenWidth / 3f) {
                    try {
                    } catch (Exception e) {
                        sendBroadcast(new Intent("android.intent.action.EXPAND_STATUS_BAR"));
                        sendBroadcast(new Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS"));
                    }
                }
                // Double tap -> lock screen
                long now = System.currentTimeMillis();
                if (dt < 200 && Math.abs(dy) < 30) {
                    if (now - lastTapTime < 400) {
                        lockScreen();
                        lastTapTime = 0;
                        return true;
                    }
                    lastTapTime = now;
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }
    private void lockScreen() {
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            dpm.lockNow();
        } catch (Exception e) {
            Log.w(TAG, "锁屏失败(需要设备管理员权限)", e);
        }
    }
    @Override
    public void onBackPressed() {
        if (editMode) { hideEditMode(); return; }
        if (contextOverlay.getVisibility() == View.VISIBLE) { hideContextMenu(); return; }
        if (folderOverlay.getVisibility() == View.VISIBLE) { hideFolder(); return; }
    }
}
