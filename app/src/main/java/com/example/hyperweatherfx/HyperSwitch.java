
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

    private boolean checked;
    private float progress;
    private float downX;
    private float startProgress;
    private boolean dragging;

    private ValueAnimator animator;
    private OnCheckedChangeListener listener;

    public HyperSwitch(Context context, boolean dark, int accentColor,
                       boolean initialChecked) {
        super(context);

        this.accentColor = accentColor;
        this.offTrackColor = dark ? 0xFF3A4254 : 0xFFD3D8E3;

        float density = getResources().getDisplayMetrics().density;

        trackWidth = 54f * density;
        trackHeight = 28f * density;
        thumbRadius = 11f * density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        checked = initialChecked;
        progress = initialChecked ? 1f : 0f;

        setClickable(true);
        setFocusable(true);
        setMinimumWidth((int) (58f * density));
        setMinimumHeight((int) (48f * density));
        setContentDescription(initialChecked ? "已开启" : "已关闭");
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        changeChecked(checked, true);
    }

    private void changeChecked(boolean value, boolean animate) {
        boolean changed = this.checked != value;
        this.checked = value;

        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        float target = value ? 1f : 0f;

        if (animate) {
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(190);
            animator.setInterpolator(new DecelerateInterpolator());

            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });

            animator.start();
        } else {
            progress = target;
            invalidate();
        }

        if (changed) {
            setContentDescription(value ? "已开启" : "已关闭");

            sendAccessibilityEvent(
                    android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            );

            if (listener != null) {
                listener.onCheckedChanged(this, value);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;

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

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blendColor(offTrackColor, accentColor, progress));

        canvas.drawRoundRect(
                track,
                trackHeight / 2f,
                trackHeight / 2f,
                paint
        );

        float left = track.left + thumbRadius + 2f;
        float right = track.right - thumbRadius - 2f;
        float thumbX = left + (right - left) * progress;

        paint.setColor(thumbColor);
        canvas.drawCircle(thumbX, cy, thumbRadius, paint);
    }

    private int blendColor(int from, int to, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));

        int a = (int) (((from >>> 24) & 0xFF)
                + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * amount);

        int r = (int) (((from >>> 16) & 0xFF)
                + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * amount);

        int g = (int) (((from >>> 8) & 0xFF)
                + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * amount);

        int b = (int) ((from & 0xFF)
                + ((to & 0xFF) - (from & 0xFF)) * amount);

        return (a << 24) | (r << 16) | (g << 8) | b;
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

                setScaleX(0.96f);
                setScaleY(0.96f);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;

                if (Math.abs(dx) > touchSlop) {
                    dragging = true;
                }

                if (dragging) {
                    float travel = trackWidth - thumbRadius * 2f - 4f;

                    progress = Math.max(0f, Math.min(1f,
                            startProgress + dx / travel));

                    invalidate();
                }

                return true;

            case MotionEvent.ACTION_UP:
                setScaleX(1f);
                setScaleY(1f);

                if (dragging) {
                    changeChecked(progress >= 0.5f, true);
                } else {
                    changeChecked(!checked, true);
                }

                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return true;

            case MotionEvent.ACTION_CANCEL:
                setScaleX(1f);
                setScaleY(1f);
                changeChecked(checked, true);
                return true;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        boolean target = !checked;
        changeChecked(target, true);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        return super.performClick();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
        info.setClickable(true);
    }
}
