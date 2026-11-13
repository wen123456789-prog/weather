package com.example.hyperweatherfx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** 全屏透明覆盖层，负责绘制雨、雪、冰雹粒子。不拦截任何触摸。 */
public class WeatherOverlayView extends View {

    private static final class P {
        float x, y, vx, vy, size, phase, alpha;
        boolean bounced;
    }

    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    private int type = WeatherMapper.NONE;
    private int level = 1;
    private boolean running = false;
    private long lastFrame = 0;

    private P[] ps = new P[0];
    private int count = 0;
    private float[] lines = new float[0];

    public WeatherOverlayView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setEffect(int newType, int newLevel) {
        if (newType == type && newLevel == level) return;
        type = newType;
        level = newLevel;
        rebuild();
        updateLoop();
    }

    public void setActive(boolean active) {
        running = active;
        lastFrame = 0;
        updateLoop();
    }

    private void updateLoop() {
        if (running && type != WeatherMapper.NONE) {
            postInvalidateOnAnimation();
        } else {
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    private void rebuild() {
        int w = getWidth();
        int h = getHeight();
        if (type == WeatherMapper.NONE || w <= 0 || h <= 0) {
            count = 0;
            return;
        }
        switch (type) {
            case WeatherMapper.RAIN:
                count = level == 1 ? 70 : level == 2 ? 150 : 280;
                break;
            case WeatherMapper.SNOW:
                count = level == 1 ? 45 : level == 2 ? 100 : 190;
                break;
            default:
                count = level == 1 ? 24 : level == 2 ? 40 : 64;
                break;
        }
        if (ps.length < count) {
            P[] n = new P[count];
            System.arraycopy(ps, 0, n, 0, ps.length);
            for (int i = ps.length; i < count; i++) n[i] = new P();
            ps = n;
        }
        for (int i = 0; i < count; i++) spawn(ps[i], true);
    }

    private void spawn(P p, boolean initial) {
        int w = getWidth();
        int h = getHeight();
        p.bounced = false;
        p.alpha = 1f;
        p.phase = rnd.nextFloat() * 6.2832f;
        switch (type) {
            case WeatherMapper.RAIN: {
                float base = level == 1 ? 1000f : level == 2 ? 1400f : 1800f;
                float speed = base * dp * (0.8f + rnd.nextFloat() * 0.4f);
                p.vy = speed;
                p.vx = -speed * 0.15f;
                p.size = (level == 1 ? 12f : level == 2 ? 16f : 22f) * dp * (0.7f + rnd.nextFloat() * 0.6f);
                p.x = rnd.nextFloat() * (w + h * 0.25f);
                break;
            }
            case WeatherMapper.SNOW: {
                float base = level == 1 ? 60f : level == 2 ? 90f : 130f;
                p.vy = base * dp * (0.6f + rnd.nextFloat() * 0.8f);
                p.vx = (rnd.nextFloat() - 0.5f) * 20f * dp;
                p.size = (level == 1 ? 1.4f : level == 2 ? 1.9f : 2.5f) * dp * (0.6f + rnd.nextFloat() * 0.9f);
                p.alpha = 0.55f + rnd.nextFloat() * 0.45f;
                p.x = rnd.nextFloat() * w;
                break;
            }
            default: {
                float speed = (1500f + rnd.nextFloat() * 700f) * dp;
                p.vy = speed;
                p.vx = -speed * 0.12f;
                p.size = (level == 1 ? 2.2f : level == 2 ? 3f : 3.8f) * dp * (0.7f + rnd.nextFloat() * 0.6f);
                p.x = rnd.nextFloat() * (w + h * 0.2f);
                break;
            }
        }
        p.y = initial ? rnd.nextFloat() * h : -rnd.nextFloat() * h * 0.15f - p.size;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (type == WeatherMapper.NONE || count == 0) return;

        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        int w = getWidth();
        int h = getHeight();
        switch (type) {
            case WeatherMapper.RAIN:
                drawRain(c, dt, h);
                break;
            case WeatherMapper.SNOW:
                drawSnow(c, dt, w, h);
                break;
            default:
                drawHail(c, dt, h);
                break;
        }
        if (running) postInvalidateOnAnimation();
    }

    private void drawRain(Canvas c, float dt, int h) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp * (level == 1 ? 1.0f : level == 2 ? 1.4f : 1.8f));
        paint.setColor(0xFFCFE4FF);
        paint.setAlpha(level == 1 ? 110 : level == 2 ? 140 : 170);

        if (lines.length < count * 4) lines = new float[count * 4];
        for (int i = 0; i < count; i++) {
            P p = ps[i];
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.y - p.size > h || p.x < -50f * dp) spawn(p, false);
            float m = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            lines[i * 4] = p.x;
            lines[i * 4 + 1] = p.y;
            lines[i * 4 + 2] = p.x - p.vx / m * p.size;
            lines[i * 4 + 3] = p.y - p.vy / m * p.size;
        }
        c.drawLines(lines, 0, count * 4, paint);
    }

    private void drawSnow(Canvas c, float dt, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        for (int i = 0; i < count; i++) {
            P p = ps[i];
            p.phase += dt * (1f + p.size * 0.1f);
            p.x += (p.vx + (float) Math.sin(p.phase) * 20f * dp) * dt;
            p.y += p.vy * dt;
            if (p.y - p.size > h) spawn(p, false);
            if (p.x < -10f * dp) p.x = w + 10f * dp;
            if (p.x > w + 10f * dp) p.x = -10f * dp;
            paint.setAlpha((int) (p.alpha * 255f));
            c.drawCircle(p.x, p.y, p.size, paint);
        }
    }

    private void drawHail(Canvas c, float dt, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFEAF6FF);
        for (int i = 0; i < count; i++) {
            P p = ps[i];
            if (p.bounced) {
                p.vy += 2600f * dp * dt;
                p.alpha -= dt * 2.2f;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (!p.bounced && p.y >= h - p.size) {
                p.y = h - p.size;
                p.vy = -p.vy * 0.28f;
                p.vx *= 0.5f;
                p.bounced = true;
            }
            if (p.alpha <= 0f || p.y > h + p.size || p.x < -50f * dp) {
                spawn(p, false);
            }
            paint.setAlpha((int) (Math.max(0f, p.alpha) * 230f));
            c.drawCircle(p.x, p.y, p.size, paint);
        }
    }
}
