package com.example.hyperweatherfx;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AppreciationActivity extends Activity {

    private int bgColor;
    private int textColor;
    private int subColor;

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        bgColor = dark ? 0xFF0F1218 : 0xFFF3F5F9;
        textColor = dark ? 0xFFECEFF6 : 0xFF1B2233;
        subColor = dark ? 0xFF9AA3B5 : 0xFF858DA0;

        getWindow().setStatusBarColor(bgColor);
        getWindow().setNavigationBarColor(bgColor);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bgColor);

        // 中央小精灵
        CloudMascotView mascot = new CloudMascotView(this);
        mascot.setOnMascotClickListener(
                this::showDonationDialog
        );

        root.addView(mascot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // 左上角返回按钮
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(38);
        back.setTextColor(textColor);
        back.setGravity(Gravity.CENTER);
        back.setBackground(createCircleBackground(
                dark ? 0xFF252B38 : 0xFFE5E9F2
        ));

        FrameLayout.LayoutParams backParams =
                new FrameLayout.LayoutParams(
                        dp(48), dp(48),
                        Gravity.TOP | Gravity.START
                );

        backParams.setMargins(
                dp(18), dp(18), 0, 0
        );

        root.addView(back, backParams);
        back.setOnClickListener(v -> finish());

        setContentView(root);
    }

    private GradientDrawable createCircleBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setShape(GradientDrawable.OVAL);
        return drawable;
    }

    // 点击小精灵后弹出二维码
    private void showDonationDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(
                dp(22), dp(22), dp(22), dp(22)
        );

        GradientDrawable background = new GradientDrawable();
        background.setColor(
                (getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES
                        ? 0xFF252B38 : Color.WHITE
        );
        background.setCornerRadius(dp(24));
        content.setBackground(background);

        TextView title = new TextView(this);
        title.setText("赞赏支持");
        title.setTextSize(20);
        title.setTextColor(textColor);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        content.addView(title);

        ImageView qrImage = new ImageView(this);
        qrImage.setImageResource(R.drawable.donation_qr);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        dp(220), dp(220)
                );

        imageParams.topMargin = dp(20);
        imageParams.bottomMargin = dp(16);

        content.addView(qrImage, imageParams);

        TextView thanks = new TextView(this);
        thanks.setText("感谢你的支持！ ♥");
        thanks.setTextSize(14);
        thanks.setTextColor(subColor);
        thanks.setGravity(Gravity.CENTER);

        content.addView(thanks);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent
            );
        }
    }
}
