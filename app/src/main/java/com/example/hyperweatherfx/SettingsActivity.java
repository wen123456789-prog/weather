package com.example.hyperweatherfx;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** 模块设置界面：总开关、自动/手动、手动时的类型和强度。 */
public class SettingsActivity extends Activity {

    private interface OnPick {
        void pick(int index);
    }

    private static final String[] TYPE_LABELS = {"无", "下雨", "下雪", "冰雹", "雾"};

    /** 下标和 WeatherMapper 里的类型编号一致 */
    private static final String[][] LEVEL_LABELS = {
            {},
            {"小雨", "中雨", "大雨", "暴雨", "雷暴雨"},
            {"小雪", "中雪", "大雪", "暴雪"},
            {"小", "中", "大"},
            {"薄雾", "中雾", "浓雾"},
    };

    private SharedPreferences sp;
    private RadioGroup typeGroup;
    private RadioGroup levelGroup;
    private LinearLayout levelBox;
    private TextView levelTitle;
    private boolean manualEnabled;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean active = true;
        try {
            sp = getSharedPreferences(Prefs.FILE, MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            sp = getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
            active = false;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        root.addView(text("桌面天气特效", 22, true));
        if (!active) {
            root.addView(text("⚠ 模块还没在 LSPosed 里启用，设置不会生效。请先启用并勾选「系统桌面」，再回来设置。", 14, false));
        }

        Switch swEnabled = new Switch(this);
        swEnabled.setText("启用特效");
        swEnabled.setChecked(sp.getBoolean(Prefs.KEY_ENABLED, true));
        swEnabled.setOnCheckedChangeListener((b, checked) ->
                sp.edit().putBoolean(Prefs.KEY_ENABLED, checked).apply());
        root.addView(swEnabled);

        final Switch swAuto = new Switch(this);
        swAuto.setText("自动：跟随系统天气");
        swAuto.setChecked(sp.getBoolean(Prefs.KEY_AUTO, true));
        root.addView(swAuto);

        root.addView(text("手动模式（关闭「自动」后生效）", 16, true));

        root.addView(text("特效类型", 14, false));
        int curType = clamp(sp.getInt(Prefs.KEY_TYPE, 0), 0, 4);
        typeGroup = radios(TYPE_LABELS, curType, i -> {
            int max = WeatherMapper.maxLevel(i);
            int lv = clamp(sp.getInt(Prefs.KEY_LEVEL, 1), 1, max);
            sp.edit().putInt(Prefs.KEY_TYPE, i).putInt(Prefs.KEY_LEVEL, lv).apply();
            rebuildLevels(i);
        });
        root.addView(typeGroup);

        levelTitle = text("强度", 14, false);
        root.addView(levelTitle);
        levelBox = new LinearLayout(this);
        levelBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(levelBox);

        manualEnabled = !swAuto.isChecked();
        rebuildLevels(curType);
        setGroupEnabled(typeGroup, manualEnabled);

        swAuto.setOnCheckedChangeListener((b, checked) -> {
            sp.edit().putBoolean(Prefs.KEY_AUTO, checked).apply();
            manualEnabled = !checked;
            setGroupEnabled(typeGroup, manualEnabled);
            setGroupEnabled(levelGroup, manualEnabled);
        });

        root.addView(text(
                "自动规则：晴、阴、多云、霾、沙尘不显示特效；雨分小雨、中雨、大雨、暴雨、雷暴雨（雷阵雨按雷暴雨）；"
                        + "雪分小雪、中雪、大雪、暴雪；另有雾和冰雹特效。\n\n"
                        + "修改后回到桌面几秒内生效。排查问题请在 LSPosed 日志里搜索 HyperWeatherFx。",
                13, false));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private void rebuildLevels(int type) {
        levelBox.removeAllViews();
        levelGroup = null;
        if (type <= 0 || type >= LEVEL_LABELS.length) {
            levelTitle.setVisibility(View.GONE);
            return;
        }
        levelTitle.setVisibility(View.VISIBLE);
        String[] labels = LEVEL_LABELS[type];
        int lv = clamp(sp.getInt(Prefs.KEY_LEVEL, 1), 1, labels.length);
        levelGroup = radios(labels, lv - 1,
                i -> sp.edit().putInt(Prefs.KEY_LEVEL, i + 1).apply());
        levelBox.addView(levelGroup);
        setGroupEnabled(levelGroup, manualEnabled);
    }

    private static void setGroupEnabled(RadioGroup g, boolean enabled) {
        if (g == null) return;
        g.setEnabled(enabled);
        for (int i = 0; i < g.getChildCount(); i++) {
            g.getChildAt(i).setEnabled(enabled);
        }
    }

    private RadioGroup radios(String[] labels, int selected, OnPick cb) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.VERTICAL);
        for (String label : labels) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(label);
            g.addView(rb);
        }
        g.check(g.getChildAt(selected).getId());
        g.setOnCheckedChangeListener((group, checkedId) -> {
            View v = group.findViewById(checkedId);
            if (v != null) cb.pick(group.indexOfChild(v));
        });
        return g;
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
