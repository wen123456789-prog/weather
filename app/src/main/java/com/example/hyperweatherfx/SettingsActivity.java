
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

public class SettingsActivity extends Activity {

    private interface OnPick {
        void pick(int index);
    }

    private static final String[] TYPE_LABELS = {
            "无", "下雨", "下雪", "冰雹", "雾"
    };

    private static final String[][] LEVEL_LABELS = {
            {},
            {"小雨", "中雨", "大雨", "暴雨", "雷暴雨"},
            {"小雪", "中雪", "大雪", "暴雪"},
            {"小", "中", "大"},
            {"薄雾", "中雾", "浓雾"}
    };

    private SharedPreferences sp;
    private boolean dark;
    private boolean manualEnabled;

    private int cBg, cCard, cText, cSub, cLine;
    private int cChip, cChipText, cAccent, cOnAccent;

    private LinearLayout manualCard;
    private LinearLayout levelBox;
    private TextView levelTitle;
    private TextView summary;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
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

        View decor = getWindow().getDecorView();

        if (!dark) {
            decor.setSystemUiVisibility(
                    decor.getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        } else {
            decor.setSystemUiVisibility(
                    decor.getSystemUiVisibility()
                            & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(32));

        root.addView(buildHeader(active));

        // 第一张卡片：总开关和自动模式
        LinearLayout general = card();

        HyperSwitch swEnabled = newSwitch(
                sp.getBoolean(Prefs.KEY_ENABLED, true)
        );

        swEnabled.setOnCheckedChangeListener((button, checked) -> {
            sp.edit().putBoolean(Prefs.KEY_ENABLED, checked).apply();
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

        swAuto.setOnCheckedChangeListener((button, checked) -> {
            sp.edit().putBoolean(Prefs.KEY_AUTO, checked).apply();
            applyManualState(!checked);
            refreshSummary();
        });

        general.addView(switchRow(
                "自动：跟随系统天气",
                "读取系统天气，天气变了特效跟着变",
                swAuto
        ));

        root.addView(general);

        // 第二张卡片：手动设置
        manualCard = card();

        manualCard.addView(
                pad(text("手动设置", 16, cText, true),
                        0, 12, 0, 0)
        );

        manualCard.addView(text(
                "先关掉上面的「自动」，这里才能选择",
                12.5f, cSub, false
        ));

        manualCard.addView(sectionLabel("特效类型"));

        int curType = clamp(
                sp.getInt(Prefs.KEY_TYPE, 0), 0, 4
        );

        manualCard.addView(chipGroup(TYPE_LABELS, curType, i -> {
            int max = WeatherMapper.maxLevel(i);
            int lv = clamp(
                    sp.getInt(Prefs.KEY_LEVEL, 1), 1, max
            );

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

        // 第三张卡片：说明
        LinearLayout help = card();

        help.addView(
                pad(text("说明", 16, cText, true),
                        0, 12, 0, 4)
        );

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

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(cBg);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(root);

        setContentView(scrollView);

        refreshSummary();
    }

    // 颜色
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

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private TextView text(
            String value, float size, int color, boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private TextView pad(
            TextView view,
            float left, float top, float right, float bottom
    ) {
        view.setPadding(
                dp(left), dp(top), dp(right), dp(bottom)
        );
        return view;
    }

    private TextView sectionLabel(String value) {
        return pad(
                text(value, 13, cSub, true),
                0, 16, 0, 8
        );
    }

    private View spacer(float heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                1, dp(heightDp)
        ));
        return view;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(cLine);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(0.5f))
        ));
        return view;
    }

    // 卡片
    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(round(cCard, 22));

        layout.setPadding(
                dp(18), dp(8), dp(18), dp(8)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(12);
        params.bottomMargin = dp(0);

        layout.setLayoutParams(params);
        layout.setElevation(dp(1.5f));

        return layout;
    }

    // 顶部横幅
    private View buildHeader(boolean active) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        0xFF5269A8,
                        0xFF293D72,
                        0xFF17264D
                }
        );

        background.setCornerRadius(dp(26));
        header.setBackground(background);
        header.setPadding(
                dp(22), dp(26), dp(22), dp(22)
        );

        TextView title = text(
                "桌面天气特效", 25, 0xFFFFFFFF, true
        );
        header.addView(title);

        summary = text(
                "", 14, 0xFFE0E8FF, false
        );
        summary.setPadding(0, dp(8), 0, dp(18));
        header.addView(summary);

        TextView pill = text(
                active
                        ? "●  模块已生效"
                        : "⚠  还没在 LSPosed 里启用：请启用并勾选「系统桌面」",
                12.5f,
                active ? 0xFFBDF5D0 : 0xFFFFD38A,
                false
        );

        pill.setBackground(round(0x30FFFFFF, 16));
        pill.setPadding(
                dp(14), dp(8), dp(14), dp(8)
        );

        header.addView(pill);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(4);
        header.setLayoutParams(params);

        return header;
    }

    // 当前状态
    private void refreshSummary() {
        if (summary == null) return;

        String value;

        if (!sp.getBoolean(Prefs.KEY_ENABLED, true)) {
            value = "特效已关闭";
        } else if (sp.getBoolean(Prefs.KEY_AUTO, true)) {
            value = "自动 · 跟随系统天气";
        } else {
            int type = clamp(
                    sp.getInt(Prefs.KEY_TYPE, 0), 0, 4
            );

            if (type == 0) {
                value = "手动 · 无特效";
            } else {
                String[] levels = LEVEL_LABELS[type];

                int level = clamp(
                        sp.getInt(Prefs.KEY_LEVEL, 1),
                        1, levels.length
                );

                value = "手动 · "
                        + ((type == 1 || type == 2)
                        ? levels[level - 1]
                        : TYPE_LABELS[type]
                        + " · " + levels[level - 1]);
            }
        }

        summary.setText("当前：" + value);
    }

    // 开关
    private HyperSwitch newSwitch(boolean checked) {
        return new HyperSwitch(
                this, dark, cAccent, checked
        );
    }

    private View switchRow(
            String title, String subtitle, HyperSwitch sw
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setMinimumHeight(dp(72));
        row.setFocusable(true);
        row.setClickable(true);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        texts.addView(text(title, 16, cText, false));

        TextView subtitleView = text(
                subtitle, 12.5f, cSub, false
        );

        subtitleView.setPadding(0, dp(2), dp(12), 0);
        texts.addView(subtitleView);

        sw.setAccessibilityLabel(title);

        row.addView(texts);
        row.addView(sw);

        // 点击文字区域也可以切换开关
        row.setOnClickListener(v ->
                sw.setChecked(!sw.isChecked())
        );

        return row;
    }

    // 手动模式启用/禁用
    private void applyManualState(boolean enabled) {
        manualEnabled = enabled;

        if (manualCard == null) return;

        manualCard.setAlpha(enabled ? 1f : 0.4f);
        setChildrenEnabled(manualCard, enabled);
    }

    private void setChildrenEnabled(
            ViewGroup parent, boolean enabled
    ) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            child.setEnabled(enabled);

            if (child instanceof ViewGroup) {
                setChildrenEnabled(
                        (ViewGroup) child, enabled
                );
            }
        }
    }

    // 强度选项
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

        int level = clamp(
                sp.getInt(Prefs.KEY_LEVEL, 1),
                1, labels.length
        );

        levelBox.addView(chipGroup(
                labels, level - 1, index -> {
                    sp.edit()
                            .putInt(Prefs.KEY_LEVEL, index + 1)
                            .apply();

                    refreshSummary();
                }
        ));
    }

    // 选项按钮
    private TextView chip(
            String label, boolean selected
    ) {
        TextView view = text(
                label, 14, cChipText, false
        );

        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(56));
        view.setMinHeight(dp(44));
        view.setPadding(
                dp(18), dp(10), dp(18), dp(10)
        );

        styleChip(view, selected);

        view.setFocusable(true);
        view.setClickable(true);

        return view;
    }

    private void styleChip(
            TextView view, boolean selected
    ) {
        view.setBackground(round(
                selected ? cAccent : cChip, 12
        ));

        view.setTextColor(
                selected ? cOnAccent : cChipText
        );

        view.setTypeface(
                null,
                selected ? Typeface.BOLD : Typeface.NORMAL
        );

        view.setAlpha(selected ? 1f : 0.85f);
        view.setElevation(dp(selected ? 2 : 0));

        view.setSelected(selected);
        view.setContentDescription(
                view.getText().toString()
                        + (selected ? "，已选中" : "")
        );
    }

    private Flow chipGroup(
            String[] labels, int selected, OnPick callback
    ) {
        Flow flow = new Flow(this, dp(8), dp(8));

        final TextView[] chips =
                new TextView[labels.length];

        for (int i = 0; i < labels.length; i++) {
            final int index = i;

            TextView chip = chip(
                    labels[i], i == selected
            );

            chips[i] = chip;

            chip.setOnClickListener(v -> {
                if (!manualEnabled || !chip.isEnabled()) {
                    return;
                }

                for (int k = 0; k < chips.length; k++) {
                    styleChip(chips[k], k == index);
                }

                callback.pick(index);
            });

            flow.addView(chip);
        }

        return flow;
    }

    // 自动换行布局
    private static final class Flow extends ViewGroup {

        private final int hGap;
        private final int vGap;

        Flow(Context context, int hGap, int vGap) {
            super(context);
            this.hGap = hGap;
            this.vGap = vGap;
        }

        @Override
        protected void onMeasure(
                int widthMeasureSpec,
                int heightMeasureSpec
        ) {
            int horizontalPadding =
                    getPaddingLeft() + getPaddingRight();

            int availableWidth =
                    MeasureSpec.getSize(widthMeasureSpec)
                            - horizontalPadding;

            int x = 0;
            int y = 0;
            int rowHeight = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);

                if (child.getVisibility() == GONE) {
                    continue;
                }

                measureChild(
                        child,
                        MeasureSpec.makeMeasureSpec(
                                availableWidth,
                                MeasureSpec.AT_MOST
                        ),
                        MeasureSpec.makeMeasureSpec(
                                0,
                                MeasureSpec.UNSPECIFIED
                        )
                );

                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();

                if (x > 0 && x + childWidth > availableWidth) {
                    x = 0;
                    y += rowHeight + vGap;
                    rowHeight = 0;
                }

                x += childWidth + hGap;
                rowHeight = Math.max(rowHeight, childHeight);
            }

            int totalHeight = y + rowHeight
                    + getPaddingTop()
                    + getPaddingBottom();

            setMeasuredDimension(
                    resolveSize(
                            MeasureSpec.getSize(widthMeasureSpec),
                            widthMeasureSpec
                    ),
                    resolveSize(totalHeight, heightMeasureSpec)
            );
        }

        @Override
        protected void onLayout(
                boolean changed,
                int left,
                int top,
                int right,
                int bottom
        ) {
            int availableWidth = right - left
                    - getPaddingLeft()
                    - getPaddingRight();

            int x = 0;
            int y = 0;
            int rowHeight = 0;

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);

                if (child.getVisibility() == GONE) {
                    continue;
                }

                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();

                if (x > 0 && x + childWidth > availableWidth) {
                    x = 0;
                    y += rowHeight + vGap;
                    rowHeight = 0;
                }

                int childLeft = getPaddingLeft() + x;
                int childTop = getPaddingTop() + y;

                child.layout(
                        childLeft,
                        childTop,
                        childLeft + childWidth,
                        childTop + childHeight
                );

                x += childWidth + hGap;
                rowHeight = Math.max(rowHeight, childHeight);
            }
        }
    }
}
