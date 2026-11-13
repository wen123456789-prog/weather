package com.example.hyperweatherfx;

/** 把小米天气的天气现象代码映射成 {特效类型, 强度}。 */
public final class WeatherMapper {
    public static final int NONE = 0;
    public static final int RAIN = 1;
    public static final int SNOW = 2;
    public static final int HAIL = 3;

    private WeatherMapper() {}

    public static int[] map(int code, String desc) {
        // 描述里明确写了冰雹（例如“雷阵雨伴有冰雹”）时优先按冰雹处理
        if (desc != null && desc.contains("冰雹")) return new int[]{HAIL, 2};

        switch (code) {
            case 22: // 冰雹
                return new int[]{HAIL, 2};

            case 11: // 小雨
            case 25: // 冻雨
                return new int[]{RAIN, 1};
            case 8:  // 阵雨
            case 10: // 中雨
                return new int[]{RAIN, 2};
            case 7:  // 雷阵雨
            case 9:  // 大雨
            case 6:  // 暴雨
            case 5:  // 大暴雨
            case 4:  // 特大暴雨
                return new int[]{RAIN, 3};

            case 17: // 小雪
            case 12: // 雨夹雪
                return new int[]{SNOW, 1};
            case 14: // 阵雪
            case 16: // 中雪
                return new int[]{SNOW, 2};
            case 15: // 大雪
            case 13: // 暴雪
                return new int[]{SNOW, 3};

            case 0: case 1: case 2: case 3:      // 晴 多云 阴 雾
            case 18: case 19: case 20: case 21:  // 沙尘类
            case 23: case 24:                    // 浮尘 霾
                return new int[]{NONE, 1};

            default:
                return fromText(desc);
        }
    }

    private static int[] fromText(String d) {
        if (d == null || d.isEmpty()) return new int[]{NONE, 1};
        if (d.contains("冰雹")) return new int[]{HAIL, 2};
        if (d.contains("雪")) {
            if (d.contains("暴雪") || d.contains("大雪")) return new int[]{SNOW, 3};
            if (d.contains("中雪") || d.contains("阵雪")) return new int[]{SNOW, 2};
            return new int[]{SNOW, 1};
        }
        if (d.contains("雨")) {
            if (d.contains("暴雨") || d.contains("大雨") || d.contains("雷")) return new int[]{RAIN, 3};
            if (d.contains("中雨") || d.contains("阵雨")) return new int[]{RAIN, 2};
            return new int[]{RAIN, 1};
        }
        return new int[]{NONE, 1};
    }
}
