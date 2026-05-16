package com.mi.launcher.blur;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;
import android.view.View;
import java.io.File;
public class BlurManager {
    private static final String TAG = "MiLauncher";
    private static final String CACHE_DIR = "/sdcard/BlurCache";
    // 从缓存读取静态模糊
    public static Bitmap getStaticBlur(String component, boolean dark) {
        String prefix = dark ? "dark" : "light";
        String filename;
        switch (component) {
            case "recent": filename = prefix + "_recent_15.png"; break;
            case "statusbar": filename = prefix + "_statusbar_20.png"; break;
            case "capsule": filename = prefix + "_capsule_30.png"; break;
            default: filename = prefix + "_recent_15.png"; break;
        }
        File file = new File(CACHE_DIR, filename);
        if (file.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) return bitmap;
        }
        Log.w(TAG, "模糊缓存未命中: " + filename);
        return null;
    }
    // 叠加半透明白底（通用组件用）
    public static Bitmap applyWhiteOverlay(Bitmap blur, int alpha) {
        if (blur == null) return null;
        Bitmap result = blur.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColor((alpha << 24) | 0xFFFFFF);
        canvas.drawRect(0, 0, result.getWidth(), result.getHeight(), paint);
        return result;
    }
    // 打开应用时的模糊覆盖
    public static void showBlurOverlay(Activity activity, View overlay, boolean animate) {
        Bitmap blur = getStaticBlur("recent", true);
        if (blur != null) {
            Bitmap blurred = applyWhiteOverlay(blur, 0x44);
            overlay.setBackground(new android.graphics.drawable.BitmapDrawable(activity.getResources(), blurred));
        } else {
            overlay.setBackgroundColor(0xAA0A0E14);
        }
        overlay.setAlpha(0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.animate()
                .alpha(1f)
                .setDuration(animate ? 250 : 0)
                .start();
    }
    // 关闭应用时隐藏模糊
    public static void hideBlurOverlay(View overlay, boolean animate) {
        if (!animate) {
            overlay.setVisibility(View.GONE);
            return;
        }
        overlay.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> overlay.setVisibility(View.GONE))
                .start();
    }
    // 动态模糊（API 31+）
    public static void applyDynamicBlur(View view, int radius) {
        if (Build.VERSION.SDK_INT >= 31) {
            RenderEffect effect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);
            view.setRenderEffect(effect);
        }
    }
    public static void clearDynamicBlur(View view) {
        if (Build.VERSION.SDK_INT >= 31) {
            view.setRenderEffect(null);
        }
    }
}
