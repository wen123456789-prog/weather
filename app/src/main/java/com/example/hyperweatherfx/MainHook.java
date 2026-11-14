package com.example.hyperweatherfx;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 注入澎湃OS桌面（com.miui.home），在桌面 Activity 上叠加天气特效。 */
public class MainHook implements IXposedHookLoadPackage {

    static final String TAG = "[HyperWeatherFx] ";
    private static final String TARGET_PKG = "com.miui.home";
    private static final String LAUNCHER_CLASS = "com.miui.home.launcher.Launcher";

    private static Class<?> launcherClass;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;
        try {
            launcherClass = XposedHelpers.findClassIfExists(LAUNCHER_CLASS, lpparam.classLoader);
            XposedBridge.log(TAG + "已注入桌面，Launcher 类"
                    + (launcherClass != null ? "已找到" : "未找到，使用类名前缀兜底"));

            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Activity a = (Activity) param.thisObject;
                        if (isLauncher(a)) Controller.INSTANCE.onResume(a);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "onResume 出错: " + t);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Activity a = (Activity) param.thisObject;
                        if (isLauncher(a)) Controller.INSTANCE.onPause(a);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "onPause 出错: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "注入失败: " + t);
        }
    }

    private static boolean isLauncher(Activity a) {
        if (launcherClass != null) return launcherClass.isInstance(a);
        return a.getClass().getName().startsWith(LAUNCHER_CLASS);
    }

    /** 运行在桌面进程里：管理覆盖层、读设置、读系统天气。 */
    private static final class Controller {
        static final Controller INSTANCE = new Controller();

        private static final Uri WEATHER_URI = Uri.parse("content://weather/actualWeatherData/1");
        private static final long FETCH_INTERVAL_MS = 5 * 60 * 1000L;
        private static final long TICK_MS = 4000L;

        private final Handler main = new Handler(Looper.getMainLooper());
        private final Handler io;

        // 仅主线程访问
        private WeakReference<Activity> actRef = new WeakReference<>(null);
        private WeakReference<Activity> overlayActRef = new WeakReference<>(null);
        private WeatherOverlayView overlay;
        private boolean overlayInWindow;
        private boolean resumed;
        private volatile Context appCtx;

        // 仅 io 线程访问
        private XSharedPreferences prefs;
        private int autoType = WeatherMapper.NONE;
        private int autoLevel = 1;
        private long lastFetch = 0;
        private int lastCode = -2;
        private boolean errLogged = false;

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!resumed) return;
                evaluate();
                main.postDelayed(this, TICK_MS);
            }
        };

        private Controller() {
            HandlerThread ht = new HandlerThread("HyperWeatherFx-io");
            ht.setDaemon(true);
            ht.start();
            io = new Handler(ht.getLooper());
        }

        void onResume(Activity a) {
            actRef = new WeakReference<>(a);
            appCtx = a.getApplicationContext();
            resumed = true;
            ensureOverlay(a);
            if (overlay != null) overlay.setActive(true);
            main.removeCallbacks(tick);
            tick.run();
        }

        void onPause(Activity a) {
            if (actRef.get() != a) return;
            resumed = false;
            main.removeCallbacks(tick);
            if (overlay != null) overlay.setActive(false);
        }

        /**
         * 优先把覆盖层作为桌面窗口的子窗口添加：它在桌面窗口之上，
         * 能盖住用独立图层绘制的小组件（图库、时钟等）。失败时退回到普通添加方式。
         */
        private void ensureOverlay(Activity a) {
            if (overlay != null && overlayActRef.get() == a && overlay.isAttachedToWindow()) {
                if (!overlayInWindow) overlay.bringToFront();
                return;
            }
            removeOverlay();

            WeatherOverlayView v = new WeatherOverlayView(a);
            v.setScanRoot(a.getWindow().getDecorView());
            try {
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                lp.setTitle("HyperWeatherFx");
                a.getWindowManager().addView(v, lp);
                overlay = v;
                overlayInWindow = true;
                overlayActRef = new WeakReference<>(a);
                XposedBridge.log(TAG + "覆盖层已作为独立窗口添加（盖在小组件之上）");
                return;
            } catch (Throwable t) {
                XposedBridge.log(TAG + "独立窗口添加失败，改用普通方式: " + t);
            }

            try {
                ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
                decor.addView(v, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                overlay = v;
                overlayInWindow = false;
                overlayActRef = new WeakReference<>(a);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "添加覆盖层失败: " + t);
                overlay = null;
            }
        }

        private void removeOverlay() {
            WeatherOverlayView o = overlay;
            overlay = null;
            if (o == null) return;
            try {
                if (overlayInWindow) {
                    Activity oa = overlayActRef.get();
                    if (oa != null) oa.getWindowManager().removeViewImmediate(o);
                } else if (o.getParent() instanceof ViewGroup) {
                    ((ViewGroup) o.getParent()).removeView(o);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "移除旧覆盖层出错: " + t);
            }
            overlayActRef = new WeakReference<>(null);
        }

        private void evaluate() {
            io.post(() -> {
                final int[] eff = compute();
                main.post(() -> {
                    if (resumed && overlay != null) overlay.setEffect(eff[0], eff[1]);
                });
            });
        }

        /** io 线程：读设置，必要时读系统天气，返回 {类型, 强度}。 */
        private int[] compute() {
            try {
                if (prefs == null) {
                    prefs = new XSharedPreferences(Prefs.MODULE_PKG, Prefs.FILE);
                } else {
                    prefs.reload();
                }
                if (!prefs.getBoolean(Prefs.KEY_ENABLED, true)) {
                    return new int[]{WeatherMapper.NONE, 1};
                }
                if (!prefs.getBoolean(Prefs.KEY_AUTO, true)) {
                    int t = prefs.getInt(Prefs.KEY_TYPE, 0);
                    int l = prefs.getInt(Prefs.KEY_LEVEL, 1);
                    if (t < 0 || t > 4) t = 0;
                    if (l < 1) l = 1;
                    int maxL = WeatherMapper.maxLevel(t);
                    if (l > maxL) l = maxL;
                    return new int[]{t, l};
                }
                long now = SystemClock.elapsedRealtime();
                if (lastFetch == 0 || now - lastFetch > FETCH_INTERVAL_MS) {
                    lastFetch = now;
                    fetchWeather();
                }
                return new int[]{autoType, autoLevel};
            } catch (Throwable t) {
                XposedBridge.log(TAG + "compute 出错: " + t);
                return new int[]{WeatherMapper.NONE, 1};
            }
        }

        private void fetchWeather() {
            Context ctx = appCtx;
            if (ctx == null) return;
            Cursor c = null;
            try {
                c = ctx.getContentResolver().query(WEATHER_URI,
                        new String[]{"weather_type", "description"}, null, null, null);
                if (c != null && c.moveToFirst()) {
                    int code = 99;
                    int i1 = c.getColumnIndex("weather_type");
                    if (i1 >= 0 && !c.isNull(i1)) {
                        try {
                            code = c.getInt(i1);
                        } catch (Throwable ignored) {
                            code = 99;
                        }
                    }
                    String desc = "";
                    int i2 = c.getColumnIndex("description");
                    if (i2 >= 0 && !c.isNull(i2)) desc = c.getString(i2);

                    int[] eff = WeatherMapper.map(code, desc);
                    autoType = eff[0];
                    autoLevel = eff[1];
                    errLogged = false;
                    if (code != lastCode) {
                        lastCode = code;
                        XposedBridge.log(TAG + "系统天气 code=" + code + " desc=" + desc
                                + " -> type=" + eff[0] + " level=" + eff[1]);
                    }
                } else {
                    autoType = WeatherMapper.NONE;
                    if (!errLogged) {
                        errLogged = true;
                        XposedBridge.log(TAG + "读取系统天气：没有数据");
                    }
                }
            } catch (Throwable t) {
                if (!errLogged) {
                    errLogged = true;
                    XposedBridge.log(TAG + "读取系统天气失败: " + t);
                }
            } finally {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable ignored) {
                        // ignore
                    }
                }
            }
        }
    }
}
