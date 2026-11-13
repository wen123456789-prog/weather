package com.example.hyperweatherfx;

/** 模块设置的键名，设置界面和桌面进程共用。 */
public final class Prefs {
    public static final String MODULE_PKG = "com.example.hyperweatherfx";
    public static final String FILE = "settings";

    /** boolean：总开关，默认 true */
    public static final String KEY_ENABLED = "enabled";
    /** boolean：自动跟随系统天气，默认 true */
    public static final String KEY_AUTO = "auto";
    /** int：手动模式的特效类型 0无 1雨 2雪 3冰雹 */
    public static final String KEY_TYPE = "manual_type";
    /** int：手动模式的强度 1小 2中 3大 */
    public static final String KEY_LEVEL = "manual_level";

    private Prefs() {}
}
