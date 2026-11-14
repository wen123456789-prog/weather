package com.example.hyperweatherfx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Random;

import de.robv.android.xposed.XposedBridge;

/**
 * 全屏透明覆盖层：雨、雪、冰雹、雾。
 * 下雨时会找到桌面上的图标和文件夹，让雨滴砸在上面溅起水花，并有水滴/水流从图标上淌下。
 * 不拦截任何触摸。
 */
public class WeatherOverlayView extends View {

    // ---------- 各档位参数（下标 = 强度，从 1 开始） ----------
    private static final int[] RAIN_COUNT = {0, 70, 150, 280, 420, 480};
    private static final float[] RAIN_SPEED = {0, 1000f, 1400f, 1800f, 2300f, 2500f};
    private static final float[] RAIN_SLANT = {0, 0.15f, 0.15f, 0.15f, 0.20f, 0.28f};
    private static final float[] RAIN_LEN = {0, 12f, 16f, 22f, 28f, 30f};
    private static final float[] RAIN_WIDTH = {0, 1.0f, 1.4f, 1.8f, 2.1f, 2.3f};
    private static final int[] RAIN_ALPHA = {0, 110, 140, 170, 190, 205};
    /** 雨滴落到屏幕底部时溅起水花的概率 */
    private static final float[] RAIN_BOTTOM_P = {0, 0.18f, 0.22f, 0.26f, 0.28f, 0.30f};
    /** 雨滴砸到图标时溅起水花的概率 */
    private static final float[] RAIN_HIT_P = {0, 0.25f, 0.30f, 0.35f, 0.40f, 0.40f};

    /** 每秒新出现的水流条数（小雨为 0，小雨用水滴） */
    private static final float[] STREAM_RATE = {0, 0f, 0.8f, 2.5f, 5f, 7f};
    private static final float[] STREAM_SPEED = {0, 0f, 130f, 170f, 220f, 260f};
    private static final float[] STREAM_LEN = {0, 0f, 28f, 38f, 50f, 56f};
    private static final float[] STREAM_WIDTH = {0, 0f, 1.3f, 1.7f, 2.1f, 2.4f};
    private static final int[] STREAM_MAX = {0, 0, 10, 20, 32, 44};
    /** 小雨：雨滴砸在图标左右两侧时，聚成水珠的概率 */
    private static final float SIDE_BEAD_P = 0.10f;
    /** 水珠沿图标侧边流下时，离图标边缘的距离（占宽度比例） */
    private static final float SIDE_INSET = 0.17f;

    private static final int[] SNOW_COUNT = {0, 45, 100, 190, 340};
    private static final float[] SNOW_SPEED = {0, 60f, 90f, 130f, 260f};
    private static final float[] SNOW_SIZE = {0, 1.4f, 1.9f, 2.5f, 2.6f};
    private static final float[] SNOW_WIND = {0, 0f, 0f, 0f, -230f};
    private static final float[] SNOW_SWAY = {0, 20f, 20f, 20f, 45f};

    private static final int[] HAIL_COUNT = {0, 24, 40, 64};
    private static final float[] HAIL_SIZE = {0, 2.2f, 3f, 3.8f};

    private static final int[] FOG_BLOBS = {0, 6, 9, 12};
    private static final int[] FOG_ALPHA = {0, 0x38, 0x48, 0x5A};
    private static final int[] FOG_VEIL = {0, 0x10, 0x1C, 0x2C};

    private static final int MAX_Q = 400;
    private static final int MAX_S = 48;
    private static final int MAX_B = 24;
    private static final int MAX_ICONS = 120;
    private static final long SCAN_MS = 100L;

    // ---------- 数据结构 ----------
    /** 下落的粒子：雨、雪、冰雹 */
    private static final class P {
        float x, y, vx, vy, size, phase, alpha;
        boolean bounced;
    }

    /** 溅起的水花(kind 0) 或 从图标滴落的水滴(kind 1) */
    private static final class Q {
        boolean on;
        int kind;
        float x, y, vx, vy, life, maxLife, size;
        Icon src; // 水滴从哪个图标滴下来的，穿过它自己时不算撞击
    }

    /** 桌面上的一个图标或文件夹，坐标是相对整个窗口的 */
    private static final class Icon {
        View v;
        int l, t, r, b;
        boolean alive;
        boolean full;
    }

    /** 从图标上流下的水流 */
    private static final class S {
        boolean on;
        Icon host;
        float relX, head, tail, speed, lenMax, width;
        boolean detached;
    }

    /** 小雨：雨滴砸在图标侧边后聚成的水珠，沿侧边慢慢流下，再滴落 */
    private static final class B {
        boolean on;
        Icon host;
        float relX0, relX;  // 砸中位置 -> 侧边位置（占图标宽度的比例）
        float y0, y;        // 距离图标顶部的偏移
        float age, speed, size;
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

    private final Q[] qs = new Q[MAX_Q];
    private int qCursor = 0;
    private final S[] ss = new S[MAX_S];
    private final B[] bs = new B[MAX_B];
    private float streamAcc = 0f;

    // 图标扫描
    private View scanRoot;
    private final Icon[] icons = new Icon[MAX_ICONS];
    private int iconN = 0;
    private IdentityHashMap<View, Icon> iconMap = new IdentityHashMap<>();
    private IdentityHashMap<View, Icon> scanNext;
    private int scanN;
    private long lastScan = 0;
    private final Rect tmpRect = new Rect();
    private final HashMap<Class<?>, Boolean> classCache = new HashMap<>();
    private boolean loggedFound = false;
    private int zeroScans = 0;
    private boolean loggedNone = false;

    // 雾
    private int fogCount = 0;
    private float[] fx = new float[0];
    private float[] fy = new float[0];
    private float[] fr = new float[0];
    private float[] fvx = new float[0];
    private Paint[] fogPaints = new Paint[0];

    // 闪电（雷暴雨）
    private float flashTimer = 2f;
    private float flashAge = -1f;
    private final float[] boltLines = new float[12 * 4];

    public WeatherOverlayView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < MAX_Q; i++) qs[i] = new Q();
        for (int i = 0; i < MAX_S; i++) ss[i] = new S();
        for (int i = 0; i < MAX_B; i++) bs[i] = new B();
    }

    // ---------- 对外接口 ----------

    public void setEffect(int newType, int newLevel) {
        int lv = Math.max(1, Math.min(WeatherMapper.maxLevel(newType), newLevel));
        if (newType == type && lv == level) return;
        type = newType;
        level = lv;
        resetDynamics();
        rebuild();
        updateLoop();
    }

    /** 告诉覆盖层去哪棵视图树里找桌面图标（覆盖层是独立窗口时必须设置） */
    public void setScanRoot(View root) {
        scanRoot = root;
    }

    public void setActive(boolean active) {
        running = active;
        lastFrame = 0;
        lastScan = 0;
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        iconMap.clear();
        for (int i = 0; i < MAX_ICONS; i++) icons[i] = null;
        iconN = 0;
        resetDynamics();
    }

    private void resetDynamics() {
        for (int i = 0; i < MAX_Q; i++) qs[i].on = false;
        for (int i = 0; i < MAX_S; i++) ss[i].on = false;
        for (int i = 0; i < MAX_B; i++) bs[i].on = false;
        streamAcc = 0f;
        flashAge = -1f;
        flashTimer = 1.5f + rnd.nextFloat() * 3f;
        lastScan = 0;
    }

    // ---------- 粒子初始化 ----------

    private void rebuild() {
        int w = getWidth();
        int h = getHeight();
        count = 0;
        fogCount = 0;
        if (type == WeatherMapper.NONE || w <= 0 || h <= 0) return;

        switch (type) {
            case WeatherMapper.RAIN:
                count = RAIN_COUNT[level];
                break;
            case WeatherMapper.SNOW:
                count = SNOW_COUNT[level];
                break;
            case WeatherMapper.HAIL:
                count = HAIL_COUNT[level];
                break;
            default:
                buildFog(w, h);
                return;
        }
        if (ps.length < count) {
            P[] n = new P[count];
            System.arraycopy(ps, 0, n, 0, ps.length);
            for (int i = ps.length; i < count; i++) n[i] = new P();
            ps = n;
        }
        for (int i = 0; i < count; i++) spawn(ps[i], true);
    }

    private void buildFog(int w, int h) {
        fogCount = FOG_BLOBS[level];
        fx = new float[fogCount];
        fy = new float[fogCount];
        fr = new float[fogCount];
        fvx = new float[fogCount];
        fogPaints = new Paint[fogCount];
        int a = FOG_ALPHA[level];
        int[] colors = {Color.argb(a, 236, 241, 246), Color.argb(0, 236, 241, 246)};
        for (int i = 0; i < fogCount; i++) {
            fr[i] = w * (0.55f + rnd.nextFloat() * 0.6f);
            fx[i] = rnd.nextFloat() * (w + 2f * fr[i]) - fr[i];
            fy[i] = rnd.nextFloat() * h;
            fvx[i] = (rnd.nextBoolean() ? 1f : -1f) * (6f + rnd.nextFloat() * 14f) * dp;
            Paint pa = new Paint(Paint.ANTI_ALIAS_FLAG);
            pa.setShader(new RadialGradient(0f, 0f, fr[i], colors, null, Shader.TileMode.CLAMP));
            fogPaints[i] = pa;
        }
    }

    private void spawn(P p, boolean initial) {
        int w = getWidth();
        int h = getHeight();
        p.bounced = false;
        p.alpha = 1f;
        p.phase = rnd.nextFloat() * 6.2832f;
        switch (type) {
            case WeatherMapper.RAIN: {
                float speed = RAIN_SPEED[level] * dp * (0.8f + rnd.nextFloat() * 0.4f);
                float slant = RAIN_SLANT[level];
                p.vy = speed;
                p.vx = -speed * slant;
                p.size = RAIN_LEN[level] * dp * (0.7f + rnd.nextFloat() * 0.6f);
                p.x = rnd.nextFloat() * (w + h * slant * 1.1f);
                break;
            }
            case WeatherMapper.SNOW: {
                p.vy = SNOW_SPEED[level] * dp * (0.6f + rnd.nextFloat() * 0.8f);
                p.vx = (rnd.nextFloat() - 0.5f) * 20f * dp + SNOW_WIND[level] * dp;
                p.size = SNOW_SIZE[level] * dp * (0.6f + rnd.nextFloat() * 0.9f);
                p.alpha = 0.55f + rnd.nextFloat() * 0.45f;
                p.x = rnd.nextFloat() * w;
                break;
            }
            default: {
                float speed = (1500f + rnd.nextFloat() * 700f) * dp;
                p.vy = speed;
                p.vx = -speed * 0.12f;
                p.size = HAIL_SIZE[level] * dp * (0.7f + rnd.nextFloat() * 0.6f);
                p.x = rnd.nextFloat() * (w + h * 0.2f);
                break;
            }
        }
        p.y = initial ? rnd.nextFloat() * h : -rnd.nextFloat() * h * 0.15f - p.size;
    }

    // ---------- 绘制入口 ----------

    @Override
    protected void onDraw(Canvas c) {
        if (type == WeatherMapper.NONE) return;
        if (type == WeatherMapper.FOG ? fogCount == 0 : count == 0) return;

        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        int w = getWidth();
        int h = getHeight();
        switch (type) {
            case WeatherMapper.RAIN:
                if (now - lastScan >= SCAN_MS) {
                    lastScan = now;
                    scanIcons();
                }
                drawLightning(c, dt, w, h);
                drawRain(c, dt, h);
                drawStreamsAndBeads(c, dt);
                drawParticles(c, dt, h);
                break;
            case WeatherMapper.SNOW:
                drawSnow(c, dt, w, h);
                break;
            case WeatherMapper.HAIL:
                drawHail(c, dt, h);
                break;
            default:
                drawFog(c, dt, w, h);
                break;
        }
        if (running) postInvalidateOnAnimation();
    }

    // ---------- 雨 ----------

    private void drawRain(Canvas c, float dt, int h) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp * RAIN_WIDTH[level]);
        paint.setColor(0xFFCFE4FF);
        paint.setAlpha(RAIN_ALPHA[level]);

        if (lines.length < count * 4) lines = new float[count * 4];
        for (int i = 0; i < count; i++) {
            P p = ps[i];
            p.x += p.vx * dt;
            p.y += p.vy * dt;

            boolean respawn = false;
            if (p.x < -50f * dp) {
                respawn = true;
            } else {
                Icon hit = findIcon(p.x, p.y, null);
                if (hit != null) {
                    // 砸到图标或文件夹
                    if (rnd.nextFloat() < RAIN_HIT_P[level]) {
                        float sy = (p.y - hit.t < p.vy * dt * 1.5f) ? hit.t : p.y;
                        emitSplash(p.x, sy, 2, 0.7f);
                    }
                    // 小雨：砸在图标左右两侧附近，水会聚到侧边，变成水珠流下
                    if (level == 1 && hit.full) {
                        float iw = hit.r - hit.l;
                        if (iw > 0f) {
                            float rx = (p.x - hit.l) / iw;
                            if ((rx < 0.3f || rx > 0.7f) && rnd.nextFloat() < SIDE_BEAD_P) {
                                spawnSideBead(hit, rx, rx < 0.5f ? SIDE_INSET : 1f - SIDE_INSET,
                                        Math.max(0f, p.y - hit.t));
                            }
                        }
                    }
                    respawn = true;
                } else if (p.y >= h) {
                    // 砸到屏幕底部
                    if (rnd.nextFloat() < RAIN_BOTTOM_P[level]) {
                        emitSplash(p.x, h - dp, 2, 1f);
                    }
                    respawn = true;
                }
            }
            if (respawn) spawn(p, false);

            float m = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            lines[i * 4] = p.x;
            lines[i * 4 + 1] = p.y;
            lines[i * 4 + 2] = p.x - p.vx / m * p.size;
            lines[i * 4 + 3] = p.y - p.vy / m * p.size;
        }
        c.drawLines(lines, 0, count * 4, paint);
    }

    private Icon findIcon(float x, float y, Icon skip) {
        for (int k = 0; k < iconN; k++) {
            Icon ic = icons[k];
            if (ic == skip) continue;
            if (x >= ic.l && x <= ic.r && y >= ic.t && y <= ic.b) return ic;
        }
        return null;
    }

    private Q freeQ() {
        for (int k = 0; k < MAX_Q; k++) {
            qCursor++;
            if (qCursor >= MAX_Q) qCursor = 0;
            if (!qs[qCursor].on) return qs[qCursor];
        }
        return null;
    }

    /** 在 (x, y) 溅起 n 颗水珠，power 越大溅得越高 */
    private void emitSplash(float x, float y, int n, float power) {
        for (int j = 0; j < n; j++) {
            Q q = freeQ();
            if (q == null) return;
            double ang = -Math.PI / 2 + (rnd.nextFloat() - 0.5f) * 2.0f;
            float sp = (120f + rnd.nextFloat() * 200f) * power * dp;
            q.on = true;
            q.kind = 0;
            q.x = x;
            q.y = y;
            q.vx = (float) Math.cos(ang) * sp;
            q.vy = (float) Math.sin(ang) * sp;
            q.maxLife = 0.22f + rnd.nextFloat() * 0.2f;
            q.life = q.maxLife;
            q.size = (0.8f + rnd.nextFloat() * 0.9f) * dp;
        }
    }

    /** 从图标下沿滴落一滴水 */
    private void spawnDrip(float x, float y, float vy, Icon src) {
        Q q = freeQ();
        if (q == null) return;
        q.on = true;
        q.kind = 1;
        q.src = src;
        q.x = x;
        q.y = y;
        q.vx = 0f;
        q.vy = vy;
        q.life = 4f;
        q.maxLife = 4f;
        q.size = 1.6f * dp;
    }

    private void drawParticles(Canvas c, float dt, int h) {
        // 水花
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFDDEBFF);
        for (int i = 0; i < MAX_Q; i++) {
            Q q = qs[i];
            if (!q.on || q.kind != 0) continue;
            q.vy += 1500f * dp * dt;
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.life -= dt;
            if (q.life <= 0f) {
                q.on = false;
                continue;
            }
            paint.setAlpha((int) (200f * q.life / q.maxLife));
            c.drawCircle(q.x, q.y, q.size, paint);
        }
        // 从图标滴落的水滴
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(1.6f * dp);
        paint.setColor(0xFFDDEBFF);
        paint.setAlpha(210);
        for (int i = 0; i < MAX_Q; i++) {
            Q q = qs[i];
            if (!q.on || q.kind != 1) continue;
            q.vy += 2400f * dp * dt;
            q.y += q.vy * dt;
            if (findIcon(q.x, q.y, q.src) != null) {
                emitSplash(q.x, q.y, 2, 0.6f);
                q.on = false;
                continue;
            }
            if (q.y >= h) {
                emitSplash(q.x, h - dp, 3, 0.9f);
                q.on = false;
                continue;
            }
            float len = Math.min(14f * dp, 3f * dp + q.vy * 0.012f);
            c.drawLine(q.x, q.y - len, q.x, q.y, paint);
        }
    }

    // ---------- 图标上的水流和水珠 ----------

    private Icon pickIcon() {
        if (iconN == 0) return null;
        for (int k = 0; k < 6; k++) {
            Icon ic = icons[rnd.nextInt(iconN)];
            if (ic.full) return ic;
        }
        return null;
    }

    private void spawnStream() {
        Icon ic = pickIcon();
        if (ic == null) return;
        int active = 0;
        S free = null;
        for (int i = 0; i < MAX_S; i++) {
            if (ss[i].on) active++;
            else if (free == null) free = ss[i];
        }
        if (free == null || active >= STREAM_MAX[level]) return;
        free.on = true;
        free.host = ic;
        free.relX = 0.2f + rnd.nextFloat() * 0.6f;
        free.head = 0f;
        free.tail = 0f;
        free.detached = false;
        free.speed = STREAM_SPEED[level] * dp * (0.75f + rnd.nextFloat() * 0.5f);
        free.lenMax = STREAM_LEN[level] * dp * (0.8f + rnd.nextFloat() * 0.4f);
        free.width = STREAM_WIDTH[level] * dp;
        emitSplash(ic.l + free.relX * (ic.r - ic.l), ic.t, 3, 0.8f);
    }

    private void spawnSideBead(Icon host, float relX0, float relXSide, float y0) {
        B free = null;
        for (int i = 0; i < MAX_B; i++) {
            if (bs[i].on && bs[i].host == host) return; // 一个图标同时只留一颗，免得太乱
            if (!bs[i].on && free == null) free = bs[i];
        }
        if (free == null) return;
        free.on = true;
        free.host = host;
        free.relX0 = relX0;
        free.relX = relXSide;
        free.y0 = y0;
        free.y = y0;
        free.age = 0f;
        free.speed = (14f + rnd.nextFloat() * 10f) * dp;
        free.size = (1.8f + rnd.nextFloat() * 1.0f) * dp;
    }

    private void drawStreamsAndBeads(Canvas c, float dt) {
        if (iconN > 0 && STREAM_RATE[level] > 0f) {
            streamAcc = Math.min(3f, streamAcc + STREAM_RATE[level] * dt);
            while (streamAcc >= 1f) {
                streamAcc -= 1f;
                spawnStream();
            }
        }

        // 水流：从图标顶部淌到底部，淌出去后变成一滴水落下
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFD6E8FF);
        paint.setAlpha(175);
        for (int i = 0; i < MAX_S; i++) {
            S s = ss[i];
            if (!s.on) continue;
            Icon host = s.host;
            if (host == null || !host.alive) {
                s.on = false;
                continue;
            }
            float height = host.b - host.t;
            float x = host.l + s.relX * (host.r - host.l);
            if (!s.detached) {
                s.head += s.speed * dt;
                if (s.head >= height) {
                    s.head = height;
                    s.detached = true;
                    spawnDrip(x, host.b + dp, s.speed * 0.25f, null);
                }
                s.tail = Math.max(0f, s.head - s.lenMax);
            } else {
                s.tail += s.speed * dt;
                if (s.tail >= height) {
                    s.on = false;
                    continue;
                }
            }
            paint.setStrokeWidth(s.width);
            c.drawLine(x, host.t + s.tail, x, host.t + s.head, paint);
        }

        // 小雨的水珠：雨滴砸在图标侧边后聚成水珠，沿侧边慢慢流下，越流越大，最后滴落
        for (int i = 0; i < MAX_B; i++) {
            B b = bs[i];
            if (!b.on) continue;
            Icon host = b.host;
            if (host == null || !host.alive) {
                b.on = false;
                continue;
            }
            float height = host.b - host.t;
            float yEnd = height * 0.74f;
            float progress = Math.min(1f, b.y / Math.max(1f, yEnd));
            b.age += dt;
            b.y += (b.speed + 45f * dp * progress) * dt;
            float slide = Math.min(1f, b.age / 0.35f); // 先从砸中的位置滑向侧边
            float rel = b.relX0 + (b.relX - b.relX0) * slide;
            float x = host.l + rel * (host.r - host.l);
            if (b.y >= yEnd) {
                spawnDrip(x, host.t + b.y + b.size, 30f * dp, host);
                b.on = false;
                continue;
            }
            float r = b.size * (0.45f + 0.55f * progress);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(0.9f * dp);
            paint.setColor(0xFFD6E8FF);
            paint.setAlpha(70);
            c.drawLine(x, host.t + b.y0, x, host.t + b.y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(210);
            c.drawCircle(x, host.t + b.y, r, paint);
        }
    }

    // ---------- 扫描桌面上的图标 ----------

    private void scanIcons() {
        View root = scanRoot != null ? scanRoot : getRootView();
        scanNext = new IdentityHashMap<>();
        int prevN = iconN;
        scanN = 0;
        walk(root, 1f, 0);

        for (Icon old : iconMap.values()) {
            if (!scanNext.containsKey(old.v)) old.alive = false;
        }
        iconMap = scanNext;
        scanNext = null;
        iconN = scanN;
        for (int i = scanN; i < prevN && i < MAX_ICONS; i++) icons[i] = null;

        if (iconN > 0) {
            zeroScans = 0;
            if (!loggedFound) {
                loggedFound = true;
                XposedBridge.log(MainHook.TAG + "找到桌面图标 " + iconN + " 个");
            }
        } else {
            zeroScans++;
            if (zeroScans == 30 && !loggedNone) {
                loggedNone = true;
                XposedBridge.log(MainHook.TAG + "一直没找到桌面图标，雨滴撞击和水流不会出现");
            }
        }
    }

    private void walk(View v, float alphaAcc, int depth) {
        if (v == this || depth > 30 || v.getVisibility() != View.VISIBLE) return;
        float a = alphaAcc * v.getAlpha();
        if (a < 0.1f) return;

        if (isIconView(v)) {
            if (scanN < MAX_ICONS && v.getWidth() > 0 && v.getHeight() > 0
                    && v.getGlobalVisibleRect(tmpRect)) {
                Icon ic = iconMap.get(v);
                if (ic == null) {
                    ic = new Icon();
                    ic.v = v;
                }
                ic.l = tmpRect.left;
                ic.t = tmpRect.top;
                ic.r = tmpRect.right;
                ic.b = tmpRect.bottom;
                ic.alive = true;
                ic.full = (tmpRect.bottom - tmpRect.top) >= 0.6f * v.getHeight()
                        && (tmpRect.right - tmpRect.left) >= 0.6f * v.getWidth();
                scanNext.put(v, ic);
                icons[scanN++] = ic;
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                View ch = g.getChildAt(i);
                if (ch != null) walk(ch, a, depth + 1);
            }
        }
    }

    private boolean isIconView(View v) {
        Class<?> k = v.getClass();
        Boolean cached = classCache.get(k);
        if (cached != null) return cached;
        String n = k.getName();
        boolean match = n.startsWith("com.miui.home")
                && (n.contains("ShortcutIcon") || n.contains("FolderIcon") || n.contains("ItemIcon"));
        if (!match && n.startsWith("com.miui.home") && n.contains("Icon")) {
            // 方便排查：把名字里带 Icon 却没被识别的类记到日志里
            XposedBridge.log(MainHook.TAG + "未识别的类(含Icon): " + n);
        }
        classCache.put(k, match);
        return match;
    }

    // ---------- 雷暴雨的闪电 ----------

    private float flashIntensity(float a) {
        if (a < 0.05f) return a / 0.05f * 0.55f;
        if (a < 0.13f) return 0.55f - (a - 0.05f) / 0.08f * 0.40f;
        if (a < 0.19f) return 0.15f + (a - 0.13f) / 0.06f * 0.30f;
        if (a < 0.6f) return 0.45f * (1f - (a - 0.19f) / 0.41f);
        return 0f;
    }

    private void startFlash(int w, int h) {
        flashAge = 0f;
        float x = w * (0.15f + rnd.nextFloat() * 0.7f);
        float endY = h * (0.45f + rnd.nextFloat() * 0.3f);
        float y = 0f;
        int segs = boltLines.length / 4;
        for (int i = 0; i < segs; i++) {
            float nx = x + (rnd.nextFloat() - 0.5f) * 0.12f * w;
            float ny = (i + 1) * endY / segs;
            boltLines[i * 4] = x;
            boltLines[i * 4 + 1] = y;
            boltLines[i * 4 + 2] = nx;
            boltLines[i * 4 + 3] = ny;
            x = nx;
            y = ny;
        }
    }

    private void drawLightning(Canvas c, float dt, int w, int h) {
        if (level != 5) return;
        if (flashAge < 0f) {
            flashTimer -= dt;
            if (flashTimer <= 0f) startFlash(w, h);
            return;
        }
        flashAge += dt;
        if (flashAge > 0.6f) {
            flashAge = -1f;
            flashTimer = 2.5f + rnd.nextFloat() * 5f;
            return;
        }
        float f = flashIntensity(flashAge);
        c.drawColor(Color.argb((int) (f * 150f), 225, 235, 255));
        if (flashAge < 0.22f) {
            float k = Math.min(1f, f * 2.2f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFFDDE8FF);
            paint.setStrokeWidth(14f * dp);
            paint.setAlpha((int) (60f * k));
            c.drawLines(boltLines, 0, boltLines.length, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setStrokeWidth(3.5f * dp);
            paint.setAlpha((int) (255f * k));
            c.drawLines(boltLines, 0, boltLines.length, paint);
        }
    }

    // ---------- 雪 ----------

    private void drawSnow(Canvas c, float dt, int w, int h) {
        if (level == 4) c.drawColor(0x20FFFFFF); // 暴雪：整体泛白
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        float sway = SNOW_SWAY[level] * dp;
        for (int i = 0; i < count; i++) {
            P p = ps[i];
            p.phase += dt * (1f + p.size * 0.1f);
            p.x += (p.vx + (float) Math.sin(p.phase) * sway) * dt;
            p.y += p.vy * dt;
            if (p.y - p.size > h) spawn(p, false);
            if (p.x < -10f * dp) p.x = w + 10f * dp;
            if (p.x > w + 10f * dp) p.x = -10f * dp;
            paint.setAlpha((int) (p.alpha * 255f));
            c.drawCircle(p.x, p.y, p.size, paint);
        }
    }

    // ---------- 冰雹 ----------

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

    // ---------- 雾 ----------

    private void drawFog(Canvas c, float dt, int w, int h) {
        c.drawColor(Color.argb(FOG_VEIL[level], 235, 240, 245));
        for (int i = 0; i < fogCount; i++) {
            fx[i] += fvx[i] * dt;
            if (fx[i] > w + fr[i]) fx[i] = -fr[i];
            else if (fx[i] < -fr[i]) fx[i] = w + fr[i];
            c.save();
            c.translate(fx[i], fy[i]);
            c.drawCircle(0f, 0f, fr[i], fogPaints[i]);
            c.restore();
        }
    }
}
