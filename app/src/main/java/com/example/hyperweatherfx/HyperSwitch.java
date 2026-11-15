
package com.example.hyperweatherfx;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

public class HyperSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(HyperSwitch view, boolean checked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();

    private final int accentColor;
    private final int offTrackColor;
    private final int thumbColor = 0xFFFFFFFF;

    private final float trackWidth;
    private final float trackHeight;
    private final float thumbRadius;
    private final float touchSlop;
    private final float density;

    private boolean checked;
    private float progress;
    private float downX;
    private float startProgress;
    private boolean dragging;

    private ValueAnimator animator;
    private OnCheckedChangeListener listener;

    // 无障碍标签
    private String accessibilityLabel = "开关";

    public HyperSwitch(
            Context context,
            boolean dark,
            int accentColor,
            boolean initialChecked
    ) {
        super(context);

        this.accentColor = accentColor;
        this.offTrackColor = dark
                ? 0xFF3A4254
                : 0xFFD3D8E3;

        density = getResources()
                .getDisplayMetrics().density;

        trackWidth = 54f * density;
        trackHeight = 28f * density;
        thumbRadius = 11f * density;

        touchSlop = ViewConfiguration
                .get(context)
                .getScaledTouchSlop();

        checked = initialChecked;
        progress = initialChecked ? 1f : 0f;

        setClickable(true);
        setFocusable(true);

        setMinimumWidth((int) (58f * density));
        setMinimumHeight((int) (48f * density));

        updateContentDescription();
    }

    public void setOnCheckedChangeListener(
            OnCheckedChangeListener listener
    ) {
        this.listener = listener;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        changeChecked(checked, true);
    }

    // 设置无障碍标签
    public void setAccessibilityLabel(String label) {
        accessibilityLabel = label;
        updateContentDescription();
    }

    private void updateContentDescription() {
        setContentDescription(
                accessibilityLabel
                        + (checked ? "，已开启" : "，已关闭")
        );
    }

    private void changeChecked(
            boolean value,
            boolean animate
    ) {
        boolean changed = checked != value;
        checked = value;

        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        float target = value ? 1f : 0f;

        if (animate) {
            animator = ValueAnimator.ofFloat(
                    progress, target
            );

            animator.setDuration(220);
            animator.setInterpolator(
                    new DecelerateInterpolator(1.5f)
            );

            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });

            animator.start();
        } else {
            progress = target;
            invalidate();
        }

        if (changed) {
            updateContentDescription();

            sendAccessibilityEvent(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            );

            if (listener != null) {
                listener.onCheckedChanged(this, value);
            }
        }
    }

    @Override
    protected void onMeasure(
            int widthMeasureSpec,
            int heightMeasureSpec
    ) {
        int width = (int) (58f * density);
        int height = (int) (48f * density);

        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        track.set(
                cx - trackWidth / 2f,
                cy - trackHeight / 2f,
                cx + trackWidth / 2f,
                cy + trackHeight / 2f
        );

        // 绘制轨道
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(
                blendColor(
                        offTrackColor,
                        accentColor,
                        progress
                )
        );

        paint.clearShadowLayer();

        canvas.drawRoundRect(
                track,
                trackHeight / 2f,
                trackHeight / 2f,
                paint
        );

        // 计算滑块位置
        float left = track.left + thumbRadius + 2f;
        float right = track.right - thumbRadius - 2f;

        float thumbX = left
                + (right - left) * progress;

        // 绘制滑块阴影
        paint.setColor(0xFFFFFFFF);

        paint.setShadowLayer(
                2.5f * density,
                0f,
                1.5f * density,
                0x35000000
        );

        canvas.drawCircle(
                thumbX,
                cy,
                thumbRadius,
                paint
        );

        // 绘制滑块主体
        paint.clearShadowLayer();
        paint.setColor(thumbColor);

        canvas.drawCircle(
                thumbX,
                cy,
                thumbRadius,
                paint
        );
    }

    private int blendColor(
            int from,
            int to,
            float amount
    ) {
        amount = Math.max(
                0f,
                Math.min(1f, amount)
        );

        int a = (int) (
                ((from >>> 24) & 0xFF)
                        + (((to >>> 24) & 0xFF)
                        - ((from >>> 24) & 0xFF)) * amount
        );

        int r = (int) (
                ((from >>> 16) & 0xFF)
                        + (((to >>> 16) & 0xFF)
                        - ((from >>> 16) & 0xFF)) * amount
        );

        int g = (int) (
                ((from >>> 8) & 0xFF)
                        + (((to >>> 8) & 0xFF)
                        - ((from >>> 8) & 0xFF)) * amount
        );

        int b = (int) (
                (from & 0xFF)
                        + ((to & 0xFF) - (from & 0xFF)) * amount
        );

        return (a << 24)
                | (r << 16)
                | (g << 8)
                | b;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                startProgress = progress;
                dragging = false;

                if (animator != null) {
                    animator.cancel();
                    animator = null;
                }

                setPressed(true);

                animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(100)
                        .start();

                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;

                if (Math.abs(dx) > touchSlop) {
                    dragging = true;
                }

                if (dragging) {
                    float travel = trackWidth
                            - thumbRadius * 2f - 4f;

                    progress = Math.max(
                            0f,
                            Math.min(
                                    1f,
                                    startProgress + dx / travel
                            )
                    );

                    invalidate();
                }

                return true;

            case MotionEvent.ACTION_UP:
                setPressed(false);

                animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(130)
                        .start();

                if (dragging) {
                    boolean target = progress >= 0.5f;
                    boolean changed = checked != target;

                    changeChecked(target, true);

                    if (changed) {
                        performHapticFeedback(
                                HapticFeedbackConstants.VIRTUAL_KEY
                        );
                    }
                } else {
                    performClick();
                }

                return true;

            case MotionEvent.ACTION_CANCEL:
                setPressed(false);

                animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(130)
                        .start();

                changeChecked(checked, true);

                return true;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();

        changeChecked(!checked, true);

        performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY
        );

        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(
            AccessibilityNodeInfo info
    ) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
        info.setClickable(true);
    }
}
