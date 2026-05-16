package com.mi.launcher.anim;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
public class AnimManager {
    private static final Interpolator SPRING = new OvershootInterpolator(2.8f);
    private static final Interpolator DECELERATE = new DecelerateInterpolator(2f);
    private static final Interpolator BOUNCE = new OvershootInterpolator(4f);
    // iOS 风格按压缩放
    public static void pressDown(View v) {
        v.animate().cancel();
        v.animate()
                .scaleX(0.82f).scaleY(0.82f)
                .setDuration(120)
                .setInterpolator(DECELERATE)
                .start();
    }
    // iOS 风格弹性回弹
    public static void pressUp(View v) {
        v.animate().cancel();
        v.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(400)
                .setInterpolator(SPRING)
                .start();
    }
    // iOS 风格三段弹跳（点击 Dock 图标）
    public static void dockTap(View v, Runnable onEnd) {
        v.animate().cancel();
        v.animate()
                .scaleX(0.7f).scaleY(0.7f)
                .setDuration(80)
                .withEndAction(() -> v.animate()
                        .scaleX(1.12f).scaleY(1.12f)
                        .setDuration(100)
                        .withEndAction(() -> v.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(200)
                                .setInterpolator(BOUNCE)
                                .withEndAction(() -> { if (onEnd != null) onEnd.run(); })
                                .start())
                        .start())
                .start();
    }
    // iOS 风格打开应用动画
    public static void openAppTransition(View icon, View blurOverlay, Runnable onStart, Runnable onEnd) {
        // 图标放大并淡出
        int[] loc = new int[2];
        icon.getLocationOnScreen(loc);
        icon.setPivotX(icon.getWidth() / 2f);
        icon.setPivotY(icon.getHeight() / 2f);
        icon.animate()
                .scaleX(3f).scaleY(3f)
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(DECELERATE)
                .withEndAction(() -> {
                    icon.setScaleX(1f);
                    icon.setScaleY(1f);
                    icon.setAlpha(1f);
                    if (onEnd != null) onEnd.run();
                })
                .start();
        if (onStart != null) onStart.run();
    }
    // 文件夹打开动画
    public static void folderOpen(View container) {
        container.setScaleX(0.3f);
        container.setScaleY(0.3f);
        container.setAlpha(0f);
        container.animate()
                .scaleX(1f).scaleY(1f)
                .alpha(1f)
                .setDuration(350)
                .setInterpolator(SPRING)
                .start();
    }
    // 文件夹关闭动画
    public static void folderClose(View container, Runnable onEnd) {
        container.animate()
                .scaleX(0.3f).scaleY(0.3f)
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(DECELERATE)
                .withEndAction(() -> { if (onEnd != null) onEnd.run(); })
                .start();
    }
    // 编辑模式抖动
    public static void startWobble(View v, int delay) {
        v.setPivotX(v.getWidth() / 2f);
        v.setPivotY(v.getHeight() / 2f);
        Runnable wobble = new Runnable() {
            boolean right = true;
            @Override
            public void run() {
                if (!v.isAttachedToWindow()) return;
                float angle = right ? 2f : -2f;
                right = !right;
                v.animate()
                        .rotation(angle)
                        .setDuration(150)
                        .setInterpolator(new DecelerateInterpolator(1f))
                        .withEndAction(() -> v.postDelayed(this, 300))
                        .start();
            }
        };
        v.postDelayed(wobble, delay);
    }
    public static void stopWobble(View v) {
        v.animate().cancel();
        v.animate().rotation(0f).setDuration(200).start();
    }
    // 列表项交错进入
    public static void staggerEnter(View[] views, long staggerMs) {
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            v.setAlpha(0f);
            v.setTranslationY(30f);
            v.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(300)
                    .setStartDelay(i * staggerMs)
                    .setInterpolator(DECELERATE)
                    .start();
        }
    }
}
