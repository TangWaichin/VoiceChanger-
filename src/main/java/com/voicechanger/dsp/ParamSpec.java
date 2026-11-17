package com.voicechanger.dsp;

/**
 * 参数定义：范围、默认值、单位、显示精度。
 */
public record ParamSpec(String id, String label, float min, float max, float defaultValue,
                        float step, String unit, int decimals) {

    public static ParamSpec of(String id, String label, float min, float max, float defaultValue,
                               float step, String unit, int decimals) {
        return new ParamSpec(id, label, min, max, defaultValue, step, unit, decimals);
    }

    public static ParamSpec percent(String id, String label, float defaultValue) {
        return new ParamSpec(id, label, 0f, 100f, defaultValue, 1f, "%", 0);
    }

    public float clamp(float value) {
        if (Float.isNaN(value)) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    public float normalize(float value) {
        return max <= min ? 0f : (clamp(value) - min) / (max - min);
    }

    public float denormalize(float ratio) {
        return clamp(min + (max - min) * Math.max(0f, Math.min(1f, ratio)));
    }

    public int steps() {
        if (step <= 0f) {
            return 1000;
        }
        return Math.max(1, Math.round((max - min) / step));
    }
}
