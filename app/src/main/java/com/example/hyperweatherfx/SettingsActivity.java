
package com.example.hyperweatherfx;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 模块设置界面：顶部横幅、总开关、自动/手动、手动时的类型和强度。跟随系统深色/浅色。 */
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
    private boolean dark;
    private int cBg, cCard, cText, cSub, cLine, cChip, cChipText, cAccent, cOnAccent;

    private boolean manualEnabled;
    private LinearLayout manualCard;
    private LinearLayout levelBox;
    private TextView levelTitle;
    private TextView summary;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();

        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        initColors();

        boolean active = true;
        try {
            sp = getSharedPreferences(Prefs.FILE, MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            sp = getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
            active = false;
        }

        getWindow().setStatusBarColor(cBg);
        getWindow().setNavigationBarColor(cBg);
        if (!dark) {
            View d = getWindow().getDecorView();
            d.setSystemUiVisibility(d.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(32));

        root.addView(buildHeader(active));

        // ---- 卡片一：总开关和自动模式 ----
        LinearLayout general = card();

        HyperSwitch swEnabled = newSwitch(
                sp.getBoolean(Prefs.KEY_ENABLED, true)
        );

        swEnabled.setOnCheckedChangeListener((b, on) -> {
            sp.edit().putBoolean(Prefs.KEY_ENABLED, on).apply();
            refreshSummary();
        });

        general.addView(switchRow(
                "启用特效",
                "关掉后，桌面上不显示任何天气特效",
                swEnabled
        ));

        general.addView(divider());

        HyperSwitch swAuto = newSwitch(
                sp.getBoolean(Prefs.KEY_AUTO, true)
        );

        swAuto.setOnCheckedChangeListener((b, on) -> {
            sp.edit().putBoolean(Prefs.KEY_AUTO, on).apply();
            applyManualState(!on);
            refreshSummary();
        });

        general.addView(switchRow(
                "自动：跟随系统天气",
                "读取系统天气，天气变了特效跟着变",
                swAuto
        ));

        root.addView(general);

        // ---- 卡片二：手动设置 ----
        manualCard = card();
        manualCard.addView(pad(text("手动设置", 16, cText, true), 0, 12, 0, 0));
        manualCard.addView(text("先关掉上面的「自动」，这里才能选择", 12.5f, cSub, false));
        manualCard.addView(sectionLabel("特效类型"));

        int curType = clamp(sp.getInt(Prefs.KEY_TYPE, 0), 0, 4);

        manualCard.addView(chipGroup(TYPE_LABELS, curType, i -> {
            int max = WeatherMapper.maxLevel(i);
            int lv = clamp(sp.getInt(Prefs.KEY_LEVEL, 1), 1, max);
            sp.edit()
                    .putInt(Prefs.KEY_TYPE, i)
                    .putInt(Prefs.KEY_LEVEL, lv)
                    .apply();

            rebuildLevels(i);
            refreshSummary();
        }));

        levelTitle = sectionLabel("强度");
        manualCard.addView(levelTitle);

        levelBox = new LinearLayout(this);
        levelBox.setOrientation(LinearLayout.VERTICAL);
        manualCard.addView(levelBox);
        manualCard.addView(spacer(12));

        root.addView(manualCard);

        applyManualState(!swAuto.isChecked());
        rebuildLevels(curType);

        // ---- 卡片三：说明 ----
        LinearLayout help = card();
        help.addView(pad(text("说明", 16, cText, true), 0, 12, 0, 4));

        TextView body = text(
                "自动规则\n"
                        + "晴、阴、多云、霾、沙尘：不显示特效\n"
                        + "雨：小雨、中雨、大雨、暴雨、雷暴雨（雷阵雨按雷暴雨）\n"
                        + "雪：小雪、中雪、大雪、暴雪\n"
                        + "另有雾和冰雹特效\n\n"
                        + "小技巧\n"
                        + "雨、雪、冰雹会跟着手机左右倾斜改变方向\n"
                        + "下雪时，雪会慢慢积在图标、文件夹、小组件顶部和屏幕底部，雪停后慢慢化掉\n\n"
                        + "修改后回到桌面几秒内生效。遇到问题请在 LSPosed 日志里搜索 HyperWeatherFx。",
                13.5f, cSub, false
        );

        body.setLineSpacing(0f, 1.25f);
        body.setPadding(0, 0, 0, dp(14));
        help.addView(body);
        root.addView(help);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(cBg);
        sv.addView(root);
        setContentView(sv);

        refreshSummary();
    }

    // ---------- 颜色和小工具 ----------

    private void initColors() {
        if (dark) {
            cBg = 0xFF0F1218;
            cCard = 0xFF1A1F29;
            cText = 0xFFECEFF6;
            cSub = 0xFF9AA3B5;
            cLine = 0xFF2A3140;
            cChip = 0xFF262D3A;
            cChipText = 0xFFC9D0E0;
            cAccent = 0xFF7C93FF;
            cOnAccent = 0xFF0F1218;
        } else {
            cBg = 0xFFF3F5F9;
            cCard = 0xFFFFFFFF;
            cText = 0xFF1B2233;
            cSub = 0xFF6B7385;
            cLine = 0xFFE6E9F0;
            cChip = 0xFFEEF1F7;
            cChipText = 0xFF3A4258;
            cAccent = 0xFF3F5BD9;
            cOnAccent = 0xFFFFFFFF;
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                v,
                getResources().getDisplayMetrics()
        );
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);

        if (bold) {
            t.setTypeface(null, Typeface.BOLD);
        }

        return t;
    }

    private TextView pad(TextView t, float l, float tp, float r, float b) {
        t.setPadding(dp(l), dp(tp), dp(r), dp(b));
        return t;
    }

    private TextView sectionLabel(String s) {
        return pad(text(s, 13, cSub, true), 0, 16, 0, 8);
    }

    private View spacer(float heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(cLine);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(0.5f))
        ));
        return v;
    }

    
private LinearLayout card() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);

    // 卡片背景和圆角
    l.setBackground(round(cCard, 22));

    // 卡片内部留白
    l.setPadding(
            dp(18),
            dp(8),
            dp(18),
            dp(8)
    );

    // 卡片之间的间距
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    );

    lp.topMargin = dp(16);
    lp.bottomMargin = dp(2);

    l.setLayoutParams(lp);

    // 轻微阴影，增强卡片层次
    l.setElevation(dp(1.5f));

    return l;
}


    // ---------- 顶部横幅 ----------

    private View buildHeader(boolean active) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF2B3A67, 0xFF0E1530}
        );

        bg.setCornerRadius(dp(22));
        h.setBackground(bg);
        h.setPadding(dp(20), dp(22), dp(20), dp(20));

        h.addView(text("桌面天气特效", 24, 0xFFFFFFFF, true));

        summary = text("", 14, 0xFFC9D7F5, false);
        summary.setPadding(0, dp(6), 0, dp(14));
        h.addView(summary);

        TextView pill = text(
                active ? "●  模块已生效"
                        : "⚠  还没在 LSPosed 里启用：请启用并勾选「系统桌面」",
                12.5f,
                active ? 0xFFBDF5D0 : 0xFFFFD38A,
                false
        );

        pill.setBackground(round(0x26FFFFFF, 14));
        pill.setPadding(dp(12), dp(6), dp(12), dp(6));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        pill.setLayoutParams(lp);
        h.addView(pill);

        return h;
    }

    private void refreshSummary() {
        if (summary == null) return;

        String s;

        if (!sp.getBoolean(Prefs.KEY_ENABLED, true)) {
            s = "特效已关闭";
        } else if (sp.getBoolean(Prefs.KEY_AUTO, true)) {
            s = "自动 · 跟随系统天气";
        } else {
            int t = clamp(sp.getInt(Prefs.KEY_TYPE, 0), 0, 4);

            if (t == 0) {
                s = "手动 · 无特效";
            } else {
                String[] lv = LEVEL_LABELS[t];
                int l = clamp(sp.getInt(Prefs.KEY_LEVEL, 1), 1, lv.length);

                s = "手动 · " + ((t == 1 || t == 2)
                        ? lv[l - 1]
                        : TYPE_LABELS[t] + " · " + lv[l - 1]);
            }
        }

        summary.setText("当前：" + s);
    }

    // ---------- 开关行 ----------

    private HyperSwitch newSwitch(boolean checked) {
        return new HyperSwitch(this, dark, cAccent, checked);
    }

    private View switchRow(String title, String sub, HyperSwitch sw) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        texts.addView(text(title, 16, cText, false));

        TextView st = text(sub, 12.5f, cSub, false);
        st.setPadding(0, dp(2), dp(12), 0);
        texts.addView(st);

        sw.setContentDescription(title + "开关");

        row.addView(texts);
        row.addView(sw);

        return row;
    }

    // ---------- 手动设置：选项按钮 ----------

    private void applyManualState(boolean enabled) {
        manualEnabled = enabled;

        if (manualCard != null) {
            manualCard.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private void rebuildLevels(int type) {
        levelBox.removeAllViews();

        if (type <= 0 || type >= LEVEL_LABELS.length) {
            levelTitle.setVisibility(View.GONE);
            levelBox.setVisibility(View.GONE);
            return;
        }

        levelTitle.setVisibility(View.VISIBLE);
        levelBox.setVisibility(View.VISIBLE);

        String[] labels = LEVEL_LABELS[type];
        int lv = clamp(sp.getInt(Prefs.KEY_LEVEL, 1), 1, labels.length);

        levelBox.addView(chipGroup(labels, lv - 1, i -> {
            sp.edit().putInt(Prefs.KEY_LEVEL, i + 1).apply();
            refreshSummary();
        }));
    }

    private TextView chip(String label, boolean selected) {
    TextView t = text(label, 14, cChipText, false);
    t.setGravity(Gravity.CENTER);
    t.setMinWidth(dp(56));
    t.setPadding(dp(18), dp(10), dp(18), dp(10));
    t.setBackground(round(selected ? cAccent : cChip, 12));
    styleChip(t, selected);
    return t;
}

private void styleChip(TextView t, boolean selected) {
    t.setBackground(round(selected ? cAccent : cChip, 12));
    t.setTextColor(selected ? cOnAccent : cChipText);
    t.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    t.setAlpha(selected ? 1.0f : 0.85f);
    t.setElevation(dp(selected ? 2 : 0));
}

    private Flow chipGroup(String[] labels, int selected, OnPick cb) {
        Flow flow = new Flow(this, dp(8), dp(8));
        final TextView[] chips = new TextView[labels.length];

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;

            TextView c = chip(labels[i], i == selected);
            chips[i] = c;

            c.setOnClickListener(v -> {
                if (!manualEnabled) return;

                for (int k = 0; k < chips.length; k++) {
                    styleChip(chips[k], k == idx);
                }

                cb.pick(idx);
            });

            flow.addView(c);
        }

        return flow;
    }

    /** 自动换行的横向排列容器：放不下就折到下一行。 */
    private static final class Flow extends ViewGroup {
        private final int hGap;
        private final int vGap;

        Flow(Context context, int hGap, int vGap) {
            super(context);
            this.hGap = hGap;
            this.vGap = vGap;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int padH = getPaddingLeft() + getPaddingRight();
            int maxW = MeasureSpec.getSize(widthSpec) - padH;

            int x = 0;
            int y = 0;
            int rowH = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);

                if (c.getVisibility() == GONE) continue;

                measureChild(
                        c,
                        MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                );

                int cw = c.getMeasuredWidth();
                int ch = c.getMeasuredHeight();

                if (x > 0 && x + cw > maxW) {
                    x = 0;
                    y += rowH + vGap;
                    rowH = 0;
                }

                x += cw + hGap;
                rowH = Math.max(rowH, ch);
            }

            int totalH = y + rowH + getPaddingTop() + getPaddingBottom();

            setMeasuredDimension(
                    resolveSize(maxW + padH, widthSpec),
                    resolveSize(totalH, heightSpec)
            );
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l - getPaddingLeft() - getPaddingRight();

            int x = 0;
            int y = 0;
            int rowH = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);

                if (c.getVisibility() == GONE) continue;

                int cw = c.getMeasuredWidth();
                int ch = c.getMeasuredHeight();

                if (x > 0 && x + cw > maxW) {
                    x = 0;
                    y += rowH + vGap;
                    rowH = 0;
                }

                int left = getPaddingLeft() + x;
                int top = getPaddingTop() + y;

                c.layout(left, top, left + cw, top + ch);

                x += cw + hGap;
                rowH = Math.max(rowH, ch);
            }
        }
    }
}
