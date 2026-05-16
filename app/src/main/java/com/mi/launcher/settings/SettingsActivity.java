package com.mi.launcher.settings;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import com.mi.launcher.R;
import com.mi.launcher.data.DataStore;
public class SettingsActivity extends Activity {
    private DataStore dataStore;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupFullScreen();
        dataStore = new DataStore(this);
        setContentView(buildUI());
    }
    private void setupFullScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0x00000000);
        getWindow().setNavigationBarColor(0x00000000);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private View buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0E14);
        root.setPadding(48, 80, 48, 48);
        // Title
        TextView title = new TextView(this);
        title.setText("桌面设置");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, 48);
        root.addView(title);
        // Grid columns
        root.addView(createSectionLabel("桌面布局"));
        root.addView(createSeekBarRow("列数", 3, 5, dataStore.getGridColumns(), value -> {
            dataStore.setGridColumns(value);
        }));
        // Icon size
        root.addView(createSeekBarRow("图标大小", 40, 64, (int)(dataStore.getIconScale() * 52), value -> {
            dataStore.setIconScale(value / 52f);
        }));
        // Animation
        root.addView(createSwitchRow("过渡动画", dataStore.isAnimEnabled(), enabled -> {
            dataStore.setAnimEnabled(enabled);
        }));
        // Divider
        root.addView(createDivider());
        // Default launcher
        root.addView(createSectionLabel("默认桌面"));
        root.addView(createButtonRow("设置默认桌面", () -> {
            try {
                Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                startActivity(intent);
            }
        }));
        // Divider
        root.addView(createDivider());
        // About
        root.addView(createSectionLabel("关于"));
        root.addView(createInfoRow("版本", "2.0"));
        root.addView(createInfoRow("作者", "qisve"));
        root.addView(createInfoRow("包名", getPackageName()));
        // Back
        root.addView(createDivider());
        root.addView(createButtonRow("返回", this::finish));
        // Wrap in ScrollView
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        scroll.addView(root);
        return scroll;
    }
    private TextView createSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF3D8BFF);
        tv.setTextSize(14);
        tv.setPadding(0, 32, 0, 16);
        return tv;
    }
    private View createDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.topMargin = 16;
        params.bottomMargin = 16;
        divider.setLayoutParams(params);
        return divider;
    }
    private View createSeekBarRow(String label, int min, int max, int current, OnIntChanged listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 12, 0, 12);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowParams);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelText = new TextView(this);
        labelText.setText(label);
        labelText.setTextColor(0xFFEAEEF3);
        labelText.setTextSize(15);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelText.setLayoutParams(labelParams);
        header.addView(labelText);
        TextView valueText = new TextView(this);
        valueText.setText(String.valueOf(current));
        valueText.setTextColor(0xFF788494);
        valueText.setTextSize(14);
        header.addView(valueText);
        row.addView(header);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(current - min);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = 8;
        seekBar.setLayoutParams(seekParams);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int value = progress + min;
                valueText.setText(String.valueOf(value));
                listener.onChanged(value);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        row.addView(seekBar);
        return row;
    }
    private View createSwitchRow(String label, boolean checked, OnBoolChanged listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 16, 0, 16);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowParams);
        TextView labelText = new TextView(this);
        labelText.setText(label);
        labelText.setTextColor(0xFFEAEEF3);
        labelText.setTextSize(15);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelText.setLayoutParams(labelParams);
        row.addView(labelText);
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((btn, isChecked) -> listener.onChanged(isChecked));
        row.addView(toggle);
        return row;
    }
    private View createButtonRow(String label, Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(15);
        btn.setPadding(16, 20, 16, 20);
        btn.setBackgroundColor(0x22FFFFFF);
        btn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 16;
        btn.setLayoutParams(params);
        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }
    private View createInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 12, 0, 12);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView labelText = new TextView(this);
        labelText.setText(label);
        labelText.setTextColor(0xFF788494);
        labelText.setTextSize(14);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelText.setLayoutParams(labelParams);
        row.addView(labelText);
        TextView valueText = new TextView(this);
        valueText.setText(value);
        valueText.setTextColor(0xFFEAEEF3);
        valueText.setTextSize(14);
        row.addView(valueText);
        return row;
    }
    interface OnIntChanged { void onChanged(int value); }
    interface OnBoolChanged { void onChanged(boolean value); }
}
