package com.example.hyperweatherfx;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.WeakHashMap;

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
    /** 雪花碰到图标/文件夹/小组件顶部时，有多大比例会落在上面消失；其余的从它们前面穿过。0 = 全部穿过，1 = 全部停在顶上 */
    private static final float SNOW_LAND_P = 0.25f;
    /** 穿过图标/小组件的雪花，在它们上面显示时的透明度倍数（小一点，免得盖住内容） */
    private static final float SNOW_INSIDE_ALPHA = 0.7f;

    private static final int[] HAIL_COUNT = {0, 24, 40, 64};
    private static final float[] HAIL_SIZE = {0, 2.2f, 3f, 3.8f};

    private static final int[] FOG_BLOBS = {0, 6, 9, 12};
    private static final int[] FOG_ALPHA = {0, 0x38, 0x48, 0x5A};
    private static final int[] FOG_VEIL = {0, 0x10, 0x1C, 0x2C};

    // ---------- 积雪 ----------
    /** 积雪从无到积满所需的秒数（按雪的强度：小雪最慢，暴雪最快） */
    private static final float[] PILE_FILL_SECONDS = {0, 240f, 150f, 90f, 45f};
    /** 积雪最高能积到「满高」的多少（小雪积得薄一些） */
    private static final float[] PILE_CAP = {0, 0.55f, 0.8f, 1f, 1f};
    /** 雪停（或换成别的天气）后，积雪化完所需的秒数 */
    private static final float PILE_MELT_SECONDS = 40f;
    /** 满高时的雪堆高度（dp）：应用/文件夹、小组件、屏幕底部 */
    private static final float PILE_MAX_ICON_DP = 15f;
    private static final float PILE_MAX_WIDGET_DP = 20f;
    private static final float PILE_MAX_BOTTOM_DP = 34f;
    /** 应用图标：图案相对整个图标格子，左右各缩进多少（占格子宽度比例） */
    private static final float ICON_GFX_X = 0.166f;
    /** 应用图标：图案顶部离格子顶部多少 dp */
    private static final float ICON_GFX_TOP_DP = 6f;
    /** 小组件：卡片相对整个小组件视图，左右、上下各缩进多少 dp（雪才能贴着卡片边缘） */
    private static final float WIDGET_MARGIN_X_DP = 11f;
    private static final float WIDGET_MARGIN_TOP_DP = 6f;
    /** 小组件视图底部比卡片多出来的一截（桌面给标题留的位置），雪霜边要把它去掉 */
    private static final float WIDGET_MARGIN_BOTTOM_DP = 39f;
    /** 小组件卡片的圆角半径（dp） */
    private static final float WIDGET_RADIUS_DP = 22f;
    /** 图标/小组件表面的雪粒：最多多少颗，以及大约每多少平方 dp 一颗（调大这个数 = 雪粒更稀、更省电） */
    private static final int GRAIN_MAX = 320;
    private static final float GRAIN_AREA_DP2 = 45f;

    // ---------- 跟随手机倾斜 ----------
    /** 倾斜对雨雪方向的影响：0 = 关闭，1 = 完全按真实重力方向，越大斜得越厉害 */
    private static final float TILT_STRENGTH = 1.0f;
    /** 最多按倾斜多少算（0.75 约等于倾斜 49 度），再歪就不再继续斜了 */
    private static final float TILT_MAX = 0.75f;
    /** 手机基本放平时的死区，避免抖动 */
    private static final float TILT_DEADZONE = 0.04f;
    /** 方向变化的平滑速度，越大跟得越快 */
    private static final float TILT_SMOOTH = 6f;
    /** 雪比雨轻，倾斜时飘得更明显 */
    private static final float SNOW_TILT_BOOST = 2.5f;

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
        boolean lands; // 雪花：碰到图标顶部时是落在上面（true）还是穿过去（false）
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
        View gfx; // 图标内部真正的图案视图，用于每帧同步位置
        int l, t, r, b;
        boolean alive;
        boolean full;
        boolean widget; // true = 小组件，false = 应用图标或文件夹
        boolean hasGfx; // 是否找到了图标里真正的图案（ImageView）
        int gl, gt, gr, gb; // 图案的位置
    }

    /** 某个图标/小组件顶上的积雪 */
    private static final class Pile {
        float fill;  // 0..1，积雪厚度比例
        float seed;  // 决定雪堆形状的随机数，保证同一个图标上形状固定
        float[] grains; // 表面雪粒：每颗 4 个数 (横向位置, 纵向位置, 出现时机, 大小)，都是 0..1
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
    private final Rect gfxRect = new Rect();
    private View bestView;
    private int bestArea;
    private final HashMap<Class<?>, Integer> classCache = new HashMap<>(); // 0无 1图标 2小组件
    private boolean loggedFound = false;
    private int zeroScans = 0;
    private boolean loggedNone = false;

    // 积雪
    private final WeakHashMap<View, Pile> piles = new WeakHashMap<>();
    private float bottomFill = 0f;
    private boolean pileLive = false;
    private final Path path = new Path();
    private final float[] px = new float[48];
    private final float[] py = new float[48];
    private final float[] puffX = new float[48];
    private final float[] puffY = new float[48];
    private final float[] puffR = new float[48];
    // 雪霜边：三种大小的雪粒，各自一组坐标
    private final float[][] rim = new float[3][2048];
    private final int[] rimN = new int[3];
    private float ptX, ptY, ptNX, ptNY; // locateOnRim 的输出
    private final Path clip = new Path();

    // 雾
    private int fogCount = 0;
    private float[] fx = new float[0];
    private float[] fy = new float[0];
    private float[] fr = new float[0];
    private float[] fvx = new float[0];
    private Paint[] fogPaints = new Paint[0];

    // 倾斜（重力传感器）
    private SensorManager sensorManager;
    private Sensor tiltSensor;
    private boolean sensorOn = false;
    private float tiltTarget = 0f; // 传感器给出的目标方向，-1..1，正数 = 往屏幕右边斜
    private float tilt = 0f;       // 平滑后实际使用的方向，-1..1
    private final SensorEventListener tiltListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            // 传感器 x 轴读数：手机右边缘朝下倾斜时为负。取反后，正数 = 重力朝屏幕右边
            float t = -e.values[0] / 9.81f;
            if (t > TILT_MAX) t = TILT_MAX;
            if (t < -TILT_MAX) t = -TILT_MAX;
            if (Math.abs(t) < TILT_DEADZONE) t = 0f;
            tiltTarget = t * TILT_STRENGTH;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // 不需要
        }
    };

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
        try {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                tiltSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
                if (tiltSensor == null) {
                    tiltSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                }
            }
        } catch (Throwable t) {
            tiltSensor = null;
        }
    }

    // ---------- 对外接口 ----------

    public void setEffect(int newType, int newLevel) {
        int lv = Math.max(1, Math.min(WeatherMapper.maxLevel(newType), newLevel));
        if (newType == type && lv == level) return;
        type = newType;
        level = lv;
        resetDynamics();
        rebuild();
        updateSensor();
        updateLoop();
    }

    /** 只有在桌面上、并且正在下雨/下雪/冰雹时才打开传感器，省电。 */
    private void updateSensor() {
        boolean need = running && tiltSensor != null && TILT_STRENGTH > 0f
                && (type == WeatherMapper.RAIN || type == WeatherMapper.SNOW
                || type == WeatherMapper.HAIL);
        if (need == sensorOn) return;
        try {
            if (need) {
                sensorOn = sensorManager.registerListener(tiltListener, tiltSensor,
                        SensorManager.SENSOR_DELAY_UI);
            } else {
                sensorManager.unregisterListener(tiltListener);
                sensorOn = false;
                tiltTarget = 0f;
            }
        } catch (Throwable t) {
            sensorOn = false;
        }
    }

    /** 告诉覆盖层去哪棵视图树里找桌面图标（覆盖层是独立窗口时必须设置） */
    public void setScanRoot(View root) {
        scanRoot = root;
    }

    public void setActive(boolean active) {
        running = active;
        lastFrame = 0;
        lastScan = 0;
        updateSensor();
        updateLoop();
    }

    private void updateLoop() {
        if (running && (type != WeatherMapper.NONE || pileLive)) {
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
        running = false;
        updateSensor();
        iconMap.clear();
        for (int i = 0; i < MAX_ICONS; i++) icons[i] = null;
        iconN = 0;
        piles.clear();
        bottomFill = 0f;
        pileLive = false;
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

    /**
     * 雨滴/冰雹出生的横坐标：要保证斜着飘过整个屏幕高度之后，刚好能铺满屏幕宽度。
     * ratio = 横向速度 / 纵向速度（正数 = 往右飘）。
     */
    private float spawnX(int w, int h, float ratio) {
        float drift = ratio * h;
        return drift >= 0f
                ? rnd.nextFloat() * (w + drift) - drift
                : rnd.nextFloat() * (w - drift);
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
                p.x = spawnX(w, h, -slant + tilt);
                break;
            }
            case WeatherMapper.SNOW: {
                p.vy = SNOW_SPEED[level] * dp * (0.6f + rnd.nextFloat() * 0.8f);
                p.vx = (rnd.nextFloat() - 0.5f) * 20f * dp + SNOW_WIND[level] * dp;
                p.size = SNOW_SIZE[level] * dp * (0.6f + rnd.nextFloat() * 0.9f);
                p.alpha = 0.55f + rnd.nextFloat() * 0.45f;
                p.lands = rnd.nextFloat() < SNOW_LAND_P;
                p.x = rnd.nextFloat() * w;
                break;
            }
            default: {
                float speed = (1500f + rnd.nextFloat() * 700f) * dp;
                p.vy = speed;
                p.vx = -speed * 0.12f;
                p.size = HAIL_SIZE[level] * dp * (0.7f + rnd.nextFloat() * 0.6f);
                p.x = spawnX(w, h, -0.12f + tilt);
                break;
            }
        }
        p.y = initial ? rnd.nextFloat() * h : -rnd.nextFloat() * h * 0.15f - p.size;
    }

    // ---------- 绘制入口 ----------

    @Override
    protected void onDraw(Canvas c) {
        boolean typeOk = type != WeatherMapper.NONE
                && (type == WeatherMapper.FOG ? fogCount > 0 : count > 0);
        if (!typeOk && !pileLive) return;

        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        tilt += (tiltTarget - tilt) * Math.min(1f, dt * TILT_SMOOTH);

        int w = getWidth();
        int h = getHeight();

        boolean needScan = type == WeatherMapper.RAIN || type == WeatherMapper.SNOW || pileLive;
        if (needScan && now - lastScan >= SCAN_MS) {
            lastScan = now;
            scanIcons();
        }

        // 每帧同步图标和内部图案的位置，避免拖动时冰霜边框滞后
        if (needScan) {
            refreshIconBounds();
        }

        if (typeOk) {
            switch (type) {
                case WeatherMapper.RAIN:
                    drawLightning(c, dt, w, h);
                    drawRain(c, dt, w, h);
                    drawStreamsAndBeads(c, dt);
                    drawParticles(c, dt, h);
                    break;
                case WeatherMapper.SNOW:
                    drawSnow(c, dt, w, h);
                    break;
                case WeatherMapper.HAIL:
                    drawHail(c, dt, w, h);
                    break;
                default:
                    drawFog(c, dt, w, h);
                    break;
            }
        }
        if (type == WeatherMapper.SNOW || pileLive) drawPiles(c, dt, w, h);

        if (running) postInvalidateOnAnimation();
    }

    // ---------- 雨 ----------

    private void drawRain(Canvas c, float dt, int w, int h) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp * RAIN_WIDTH[level]);
        paint.setColor(0xFFCFE4FF);
        paint.setAlpha(RAIN_ALPHA[level]);

        if (lines.length < count * 4) lines = new float[count * 4];
        for (int i = 0; i < count; i++) {
            P p = ps[i];
            float vxe = p.vx + tilt * p.vy; // 加上手机倾斜带来的横向速度
            p.x += vxe * dt;
            p.y += p.vy * dt;

            boolean respawn = false;
            if (p.x < -50f * dp || p.x > w + 50f * dp) {
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

            float m = (float) Math.sqrt(vxe * vxe + p.vy * p.vy);
            lines[i * 4] = p.x;
            lines[i * 4 + 1] = p.y;
            lines[i * 4 + 2] = p.x - vxe / m * p.size;
            lines[i * 4 + 3] = p.y - p.vy / m * p.size;
        }
        c.drawLines(lines, 0, count * 4, paint);
    }

    /** 雨用：只找应用图标和文件夹（不含小组件） */
    private Icon findIcon(float x, float y, Icon skip) {
        for (int k = 0; k < iconN; k++) {
            Icon ic = icons[k];
            if (ic == null || !ic.alive || ic == skip || ic.widget) continue;
            if (x >= ic.l && x <= ic.r && y >= ic.t && y <= ic.b) return ic;
        }
        return null;
    }

    /** 雪用：图标、文件夹、小组件都算 */
    private Icon findTarget(float x, float y) {
        for (int k = 0; k < iconN; k++) {
            Icon ic = icons[k];
            if (ic != null && ic.alive
                    && x >= ic.l && x <= ic.r
                    && y >= ic.t && y <= ic.b) return ic;
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
            if (ic != null && ic.alive && ic.full && !ic.widget) return ic;
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

    /**
     * 每帧刷新已经识别的图标和内部图案的位置。
     * 全量扫描仍按原来的间隔执行，避免每帧递归遍历整棵桌面视图树。
     */
    private void refreshIconBounds() {
        for (int k = 0; k < iconN; k++) {
            Icon ic = icons[k];

            if (ic == null || ic.v == null
                    || !ic.v.isAttachedToWindow()
                    || !ic.v.getGlobalVisibleRect(tmpRect)) {
                if (ic != null) ic.alive = false;
                continue;
            }

            ic.l = tmpRect.left;
            ic.t = tmpRect.top;
            ic.r = tmpRect.right;
            ic.b = tmpRect.bottom;
            ic.alive = true;

            ic.full = tmpRect.height() >= 0.6f * ic.v.getHeight()
                    && tmpRect.width() >= 0.6f * ic.v.getWidth();

            if (ic.gfx != null
                    && ic.gfx.isAttachedToWindow()
                    && ic.gfx.getGlobalVisibleRect(gfxRect)) {
                ic.hasGfx = true;
                ic.gl = gfxRect.left;
                ic.gt = gfxRect.top;
                ic.gr = gfxRect.right;
                ic.gb = gfxRect.bottom;
            } else {
                ic.hasGfx = false;
            }
        }
    }

    private void walk(View v, float alphaAcc, int depth) {
        if (v == this || depth > 30 || v.getVisibility() != View.VISIBLE) return;
        float a = alphaAcc * v.getAlpha();
        if (a < 0.1f) return;

        int kind = classify(v);
        if (kind != 0) {
            boolean ok = scanN < MAX_ICONS && v.getWidth() > 0 && v.getHeight() > 0
                    && v.getGlobalVisibleRect(tmpRect);
            if (!ok) return;
            // 小组件如果大得几乎占满屏幕，多半是外层容器，不当作小组件，继续往里找
            boolean oversize = kind == 2
                    && (float) tmpRect.width() * tmpRect.height() > 0.7f * getWidth() * getHeight();
            if (!oversize) {
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
                ic.widget = kind == 2;
                ic.full = (tmpRect.bottom - tmpRect.top) >= 0.6f * v.getHeight()
                        && (tmpRect.right - tmpRect.left) >= 0.6f * v.getWidth();

                ic.hasGfx = false;
                ic.gfx = null;

                if (kind == 1) {
                    // 找图标里真正的图案：最大的那个 ImageView，雪就按它的位置和大小来画
                    bestView = null;
                    bestArea = 0;
                    findGraphic(v, 0);
                    if (bestView != null
                            && bestArea >= 0.2f * v.getWidth() * v.getHeight()
                            && bestView.getGlobalVisibleRect(gfxRect)) {
                        ic.hasGfx = true;
                        ic.gfx = bestView;
                        ic.gl = gfxRect.left;
                        ic.gt = gfxRect.top;
                        ic.gr = gfxRect.right;
                        ic.gb = gfxRect.bottom;
                    }
                }
                scanNext.put(v, ic);
                icons[scanN++] = ic;
                return;
            }
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

    /** 在图标里（最多往下 4 层）找面积最大的 ImageView，结果放在 bestView/bestArea。 */
    private void findGraphic(View v, int depth) {
        if (!(v instanceof ViewGroup) || depth > 3) return;
        ViewGroup g = (ViewGroup) v;
        int n = g.getChildCount();
        for (int i = 0; i < n; i++) {
            View ch = g.getChildAt(i);
            if (ch == null || ch.getVisibility() != View.VISIBLE) continue;
            if (ch instanceof ImageView) {
                int area = ch.getWidth() * ch.getHeight();
                if (area > bestArea) {
                    bestArea = area;
                    bestView = ch;
                }
            } else if (ch instanceof ViewGroup) {
                findGraphic(ch, depth + 1);
            }
        }
    }

    /** 0 = 不关心，1 = 应用图标或文件夹，2 = 小组件。结果按类缓存。 */
    private int classify(View v) {
        Class<?> k = v.getClass();
        Integer cached = classCache.get(k);
        if (cached != null) return cached;
        String n = k.getName();
        int kind = 0;
        if (n.startsWith("com.miui.home")) {
            if (n.contains("ShortcutIcon") || n.contains("FolderIcon") || n.contains("ItemIcon")) {
                kind = 1;
            } else if (v instanceof AppWidgetHostView || (n.contains("Widget") && !n.contains("Icon"))) {
                kind = 2;
            }
        } else if (v instanceof AppWidgetHostView) {
            kind = 2;
        }
        if (kind == 2) {
            XposedBridge.log(MainHook.TAG + "识别为小组件: " + n);
        } else if (kind == 0 && n.startsWith("com.miui.home") && n.contains("Icon")) {
            // 方便排查：把名字里带 Icon 却没被识别的类记到日志里
            XposedBridge.log(MainHook.TAG + "未识别的类(含Icon): " + n);
        }
        classCache.put(k, kind);
        return kind;
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
        float bottomTop = h - PILE_MAX_BOTTOM_DP * dp * bottomFill * 0.6f;
        for (int i = 0; i < count; i++) {
            P p = ps[i];
            p.phase += dt * (1f + p.size * 0.1f);
            p.x += (p.vx + tilt * p.vy * SNOW_TILT_BOOST + (float) Math.sin(p.phase) * sway) * dt;
            p.y += p.vy * dt;
            // 落到屏幕底部的雪堆上就消失，从上面重新飘下；
            // 碰到图标/文件夹/小组件时，一部分落在顶上消失，其余的从前面穿过去
            boolean inside = false;
            if (p.y >= bottomTop) {
                spawn(p, false);
            } else if (findTarget(p.x, p.y) != null) {
                if (p.lands) spawn(p, false);
                else inside = true;
            }
            if (p.x < -10f * dp) p.x = w + 10f * dp;
            if (p.x > w + 10f * dp) p.x = -10f * dp;
            paint.setAlpha((int) (p.alpha * 255f * (inside ? SNOW_INSIDE_ALPHA : 1f)));
            c.drawCircle(p.x, p.y, p.size, paint);
        }
    }

    // ---------- 积雪 ----------

    private void drawPiles(Canvas c, float dt, int w, int h) {
        boolean snowing = type == WeatherMapper.SNOW;
        if (snowing) {
            float cap = PILE_CAP[level];
            float grow = dt / PILE_FILL_SECONDS[level];
            for (int k = 0; k < iconN; k++) {
                Icon ic = icons[k];
                if (ic == null || !ic.alive || !ic.full) continue;
                Pile pl = piles.get(ic.v);
                if (pl == null) {
                    pl = new Pile();
                    pl.seed = rnd.nextFloat() * 6.2832f;
                    piles.put(ic.v, pl);
                }
                if (pl.fill < cap) pl.fill = Math.min(cap, pl.fill + grow);
            }
            if (bottomFill < cap) bottomFill = Math.min(cap, bottomFill + grow);
        } else {
            float melt = dt / PILE_MELT_SECONDS;
            Iterator<Pile> it = piles.values().iterator();
            while (it.hasNext()) {
                Pile pl = it.next();
                pl.fill -= melt;
                if (pl.fill <= 0f) it.remove();
            }
            bottomFill = Math.max(0f, bottomFill - melt);
        }

        for (int k = 0; k < iconN; k++) {
            Icon ic = icons[k];
            if (ic == null || !ic.alive || !ic.full) continue;
            Pile pl = piles.get(ic.v);
            if (pl == null || pl.fill <= 0.001f) continue;
            float iw = ic.r - ic.l;
            if (ic.widget) {
                // 小组件：先估出里面那张卡片的位置，雪霜边描在卡片四周，雪帽盖在卡片顶上
                float mx = WIDGET_MARGIN_X_DP * dp;
                float cl = ic.l + mx;
                float cr = ic.r - mx;
                float ct = ic.t + WIDGET_MARGIN_TOP_DP * dp;
                float cb = ic.b - WIDGET_MARGIN_BOTTOM_DP * dp;
                float rad = WIDGET_RADIUS_DP * dp;
                drawSurface(c, pl, cl, ct, cr, cb, rad, 0.14f, 0.8f);
                drawRim(c, cl, ct, cr, cb, rad, pl.fill, pl.seed);
                drawCap(c, cl + rad * 0.6f, cr - rad * 0.6f, ct + dp,
                        PILE_MAX_WIDGET_DP * dp * pl.fill, pl.seed, true, 14);
            } else {
                // 应用图标/文件夹：优先用图标里真正的图案位置，找不到才按格子估一个正方形
                float gl;
                float gt;
                float gr;
                float gb;
                if (ic.hasGfx) {
                    gl = ic.gl;
                    gt = ic.gt;
                    gr = ic.gr;
                    gb = ic.gb;
                } else {
                    gl = ic.l + iw * ICON_GFX_X;
                    gr = ic.r - iw * ICON_GFX_X;
                    gt = ic.t + ICON_GFX_TOP_DP * dp;
                    gb = gt + (gr - gl);
                }
                float gw = gr - gl;
                float gh = gb - gt;
                float irad = Math.min(gw, gh) * 0.23f;
                drawSurface(c, pl, gl, gt, gr, gb, irad, 0.5f, 1f);
                drawRim(c, gl, gt, gr, gb, irad, pl.fill, pl.seed);
                drawCap(c, gl + gw * 0.04f, gr - gw * 0.04f, gt + dp,
                        PILE_MAX_ICON_DP * dp * pl.fill, pl.seed, true, gw > 1.8f * gh ? 14 : 9);
            }
        }
        if (bottomFill > 0.001f) {
            drawCap(c, 0f, w, h, PILE_MAX_BOTTOM_DP * dp * bottomFill, 1.7f, false, 26);
        }
        pileLive = bottomFill > 0.001f || !piles.isEmpty();
    }

    /**
     * 在图标/小组件表面铺一层雪：顶部一层渐渐变淡的白霜，加上一些细小的雪粒（顶部和两侧更密）。
     * 只画在图标自己的圆角范围内，雪越积越多。
     */
    private void drawSurface(Canvas c, Pile pl, float l, float t, float r, float b, float rad,
                             float veilFrac, float veilStrength) {
        float w = r - l;
        float h = b - t;
        if (w <= 8f * dp || h <= 8f * dp) return;
        float fill = pl.fill;

        if (pl.grains == null) {
            float[] g = new float[GRAIN_MAX * 4];
            for (int k = 0; k < GRAIN_MAX; k++) {
                float u = hash(pl.seed, k, 11);
                float v = hash(pl.seed, k, 12);
                v = v * v;                               // 偏向顶部
                if (hash(pl.seed, k, 13) < 0.35f) {      // 一部分贴着左右两侧
                    float e = hash(pl.seed, k, 14);
                    u = e < 0.5f ? u * u * 0.25f : 1f - u * u * 0.25f;
                }
                g[k * 4] = u;
                g[k * 4 + 1] = v;
                g[k * 4 + 2] = hash(pl.seed, k, 15) * (0.35f + 0.65f * v); // 顶部的先出现
                g[k * 4 + 3] = hash(pl.seed, k, 16);
            }
            pl.grains = g;
        }

        rad = Math.min(rad, Math.min(w, h) * 0.5f);
        clip.reset();
        clip.addRoundRect(l, t, r, b, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(clip);

        // 顶部白霜：叠 6 层越来越短的半透明白条，上浓下淡
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF4F8FF);
        paint.setAlpha((int) (32f * fill * veilStrength));
        for (int j = 1; j <= 6; j++) {
            c.drawRect(l, t, r, t + h * veilFrac * j / 6f, paint);
        }

        // 雪粒
        int n = (int) Math.min(GRAIN_MAX, (w * h) / (dp * dp) / GRAIN_AREA_DP2);
        rimN[0] = 0;
        rimN[1] = 0;
        float[] g = pl.grains;
        for (int k = 0; k < n; k++) {
            if (fill * 1.1f < g[k * 4 + 2]) continue;
            int gi = g[k * 4 + 3] < 0.65f ? 0 : 1;
            int idx = rimN[gi];
            if (idx + 2 > 2048) continue;
            rim[gi][idx] = l + g[k * 4] * w;
            rim[gi][idx + 1] = t + g[k * 4 + 1] * h;
            rimN[gi] = idx + 2;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFF8FBFF);
        paint.setAlpha(235);
        float scale = 0.6f + 0.4f * fill;
        if (rimN[0] > 0) {
            paint.setStrokeWidth(1.3f * dp * scale);
            c.drawPoints(rim[0], 0, rimN[0], paint);
        }
        if (rimN[1] > 0) {
            paint.setStrokeWidth(2.2f * dp * scale);
            c.drawPoints(rim[1], 0, rimN[1], paint);
        }
        c.restore();
    }

    /** 伪随机数：同一个 (seed, k, salt) 永远得到同一个 0..1 的值，保证雪霜形状固定不闪。 */
    private static float hash(float seed, int k, int salt) {
        float v = (float) Math.sin(seed * 12.9898 + k * 78.233 + salt * 37.719) * 43758.5453f;
        return v - (float) Math.floor(v);
    }
        /**
     * 圆角矩形周长上第 s 个位置（从左上角圆角之后的上边开始，顺时针）。
     * 结果写进 ptX/ptY（位置）和 ptNX/ptNY（朝外的方向）。
     */
    private void locateOnRim(float s, float l, float t, float r, float b, float rad,
                             float sw, float sh, float arc) {
        float cx, cy, a;
        if (s < sw) {                       // 上边
            ptX = l + rad + s; ptY = t; ptNX = 0f; ptNY = -1f;
            return;
        }
        s -= sw;
        if (s < arc) {                      // 右上圆角
            cx = r - rad; cy = t + rad; a = -1.5708f + s / rad;
            ptNX = (float) Math.cos(a); ptNY = (float) Math.sin(a);
            ptX = cx + rad * ptNX; ptY = cy + rad * ptNY;
            return;
        }
        s -= arc;
        if (s < sh) {                       // 右边
            ptX = r; ptY = t + rad + s; ptNX = 1f; ptNY = 0f;
            return;
        }
        s -= sh;
        if (s < arc) {                      // 右下圆角
            cx = r - rad; cy = b - rad; a = s / rad;
            ptNX = (float) Math.cos(a); ptNY = (float) Math.sin(a);
            ptX = cx + rad * ptNX; ptY = cy + rad * ptNY;
            return;
        }
        s -= arc;
        if (s < sw) {                       // 下边
            ptX = r - rad - s; ptY = b; ptNX = 0f; ptNY = 1f;
            return;
        }
        s -= sw;
        if (s < arc) {                      // 左下圆角
            cx = l + rad; cy = b - rad; a = 1.5708f + s / rad;
            ptNX = (float) Math.cos(a); ptNY = (float) Math.sin(a);
            ptX = cx + rad * ptNX; ptY = cy + rad * ptNY;
            return;
        }
        s -= arc;
        if (s < sh) {                       // 左边
            ptX = l; ptY = b - rad - s; ptNX = -1f; ptNY = 0f;
            return;
        }
        s -= sh;                            // 左上圆角
        cx = l + rad; cy = t + rad; a = 3.1416f + s / rad;
        ptNX = (float) Math.cos(a); ptNY = (float) Math.sin(a);
        ptX = cx + rad * ptNX; ptY = cy + rad * ptNY;
    }

    /** 沿圆角矩形四周撒一圈雪粒，像结了一层霜。fill 越大雪粒越多越大，顶部先长。 */
    private void drawRim(Canvas c, float l, float t, float r, float b, float rad,
                         float fill, float seed) {
        float w = r - l;
        float h = b - t;
        if (w <= 8f * dp || h <= 8f * dp) return;
        rad = Math.min(rad, Math.min(w, h) * 0.5f);
        float sw = w - 2f * rad;
        float sh = h - 2f * rad;
        float arc = 1.5708f * rad;
        float perim = 2f * sw + 2f * sh + 4f * arc;
        float step = Math.max(1.4f * dp, perim / 900f);
        int n = (int) (perim / step);

        rimN[0] = 0;
        rimN[1] = 0;
        rimN[2] = 0;
        for (int k = 0; k < n; k++) {
            locateOnRim(perim * k / n, l, t, r, b, rad, sw, sh, arc);
            float thr = hash(seed, k, 1);
            if (ptNY < -0.5f) thr *= 0.6f;        // 顶部先积
            if (fill * 1.2f < thr) continue;
            float off = (hash(seed, k, 2) - 0.35f) * 3f * dp;
            float g = hash(seed, k, 3);
            int gi = g < 0.5f ? 0 : (g < 0.85f ? 1 : 2);
            int idx = rimN[gi];
            if (idx + 2 > 2048) continue;
            rim[gi][idx] = ptX + ptNX * off;
            rim[gi][idx + 1] = ptY + ptNY * off;
            rimN[gi] = idx + 2;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFF4F8FF);
        float scale = 0.6f + 0.4f * fill;
        // 先描一圈连续的细白边当底，再撒雪粒，这样看起来是一整圈霜，不是一串点
        paint.setStrokeWidth(2.2f * dp * scale);
        paint.setAlpha((int) (200f * Math.min(1f, fill * 1.3f)));
        c.drawRoundRect(l, t, r, b, rad, rad, paint);
        paint.setAlpha(225);
        if (rimN[0] > 0) {
            paint.setStrokeWidth(1.6f * dp * scale);
            c.drawPoints(rim[0], 0, rimN[0], paint);
        }
        if (rimN[1] > 0) {
            paint.setStrokeWidth(2.8f * dp * scale);
            c.drawPoints(rim[1], 0, rimN[1], paint);
        }
        if (rimN[2] > 0) {
            paint.setStrokeWidth(4.2f * dp * scale);
            c.drawPoints(rim[2], 0, rimN[2], paint);
        }
    }

    /**
     * 画一顶蓬松的雪帽：底边在 baseY，左右范围 left..right，最高 maxH。
     * 由一个实心底座和一排大小不一的雪团叠成，轮廓是起伏的。
     */
    private void drawCap(Canvas c, float left, float right, float baseY, float maxH,
                         float seed, boolean taper, int m) {
        float width = right - left;
        if (width <= 0f || maxH < 0.5f * dp) return;
        if (m > 40) m = 40;

        // 实心底座
        for (int i = 0; i <= m; i++) {
            float u = (float) i / m;
            float env = taper ? (float) Math.pow(Math.sin(Math.PI * u), 0.5) : 1f;
            px[i] = left + width * u;
            py[i] = baseY - maxH * 0.55f * env;
        }
        float base = baseY + 1.5f * dp;
        path.reset();
        path.moveTo(px[0], base);
        path.lineTo(px[0], py[0]);
        for (int i = 1; i <= m; i++) {
            float mx = (px[i - 1] + px[i]) * 0.5f;
            float my = (py[i - 1] + py[i]) * 0.5f;
            path.quadTo(px[i - 1], py[i - 1], mx, my);
        }
        path.lineTo(px[m], py[m]);
        path.lineTo(px[m], base);
        path.close();

        // 雪团
        for (int i = 0; i < m; i++) {
            float u = (i + 0.5f) / m;
            float env = taper ? (float) Math.pow(Math.sin(Math.PI * u), 0.5) : 1f;
            float noise = 0.70f + 0.18f * (float) Math.sin(seed + u * 7f)
                    + 0.12f * (float) Math.sin(seed * 1.7f + u * 19f);
            float hgt = maxH * env * noise;
            float rr = Math.max(0.9f * dp, hgt * 0.62f);
            puffX[i] = left + width * u;
            puffY[i] = baseY - hgt + rr;
            puffR[i] = rr;
        }

        paint.setStyle(Paint.Style.FILL);
        // 先画一层偏蓝灰的阴影，再画白色雪面，有立体感
        paint.setColor(0xFFAFC4DA);
        paint.setAlpha(150);
        c.save();
        c.translate(0f, 1.6f * dp);
        c.drawPath(path, paint);
        for (int i = 0; i < m; i++) c.drawCircle(puffX[i], puffY[i], puffR[i], paint);
        c.restore();
        paint.setColor(0xFFF6FAFF);
        paint.setAlpha(245);
        c.drawPath(path, paint);
        for (int i = 0; i < m; i++) c.drawCircle(puffX[i], puffY[i], puffR[i], paint);
    }

    // ---------- 冰雹 ----------

    private void drawHail(Canvas c, float dt, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFEAF6FF);
        for (int i = 0; i < count; i++) {
            P p = ps[i];
            if (p.bounced) {
                p.vy += 2600f * dp * dt;
                p.alpha -= dt * 2.2f;
            }
            p.x += (p.bounced ? p.vx : p.vx + tilt * p.vy) * dt;
            p.y += p.vy * dt;
            if (!p.bounced && p.y >= h - p.size) {
                p.y = h - p.size;
                p.vy = -p.vy * 0.28f;
                p.vx *= 0.5f;
                p.bounced = true;
            }
            if (p.alpha <= 0f || p.y > h + p.size || p.x < -50f * dp || p.x > w + 50f * dp) {
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
