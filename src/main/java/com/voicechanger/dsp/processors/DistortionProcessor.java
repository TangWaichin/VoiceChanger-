package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.Biquad;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 失真：软削波 + 音色滤波。
 */
public final class DistortionProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("drive", "驱动", 1f, 60f, 4f, 0.5f, "x", 1),
            ParamSpec.percent("tone", "亮度", 55f),
            ParamSpec.percent("mix", "干湿比", 45f));

    private final Biquad toneLow = new Biquad();
    private final Biquad toneHigh = new Biquad();
    private float lastTone = Float.NaN;

    /** tanh 查找表：定义域 [-8, 8]，之外饱和，线性插值误差约 1e-5，替代逐采样 Math.tanh。 */
    private static final int LUT_SIZE = 4096;
    private static final float LUT_RANGE = 8f;
    private static final float[] TANH_LUT = new float[LUT_SIZE];

    static {
        for (int i = 0; i < LUT_SIZE; i++) {
            TANH_LUT[i] = (float) Math.tanh(-LUT_RANGE + i * (2d * LUT_RANGE) / (LUT_SIZE - 1));
        }
    }

    private static float fastTanh(float x) {
        if (x >= LUT_RANGE) {
            return 1f;
        }
        if (x <= -LUT_RANGE) {
            return -1f;
        }
        float pos = (x + LUT_RANGE) * (LUT_SIZE - 1) / (2f * LUT_RANGE);
        int i = (int) pos;
        float frac = pos - i;
        return TANH_LUT[i] + (TANH_LUT[i + 1] - TANH_LUT[i]) * frac;
    }

    public DistortionProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "distortion";
    }

    @Override
    public String displayName() {
        return "失真";
    }

    @Override
    public String description() {
        return "过载削波带来沙哑、嘶吼的质感，驱动越大越脏。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected void onPrepare(int sampleRate, int maxFrames) {
        lastTone = Float.NaN;
        toneLow.reset();
        toneHigh.reset();
    }

    @Override
    public void reset() {
        onPrepare(sampleRate(), 0);
    }

    @Override
    public void process(float[] buffer, int length) {
        float tone = param("tone");
        if (tone != lastTone) {
            float brightness = tone / 100f;
            toneLow.lowShelf(sampleRate(), 900f, (brightness - 0.5f) * -2f * 8f);
            toneHigh.highShelf(sampleRate(), 4200f, (brightness - 0.5f) * 2f * 10f);
            lastTone = tone;
        }
        float drive = param("drive");
        float mix = param("mix") / 100f;
        float norm = 1f / (float) Math.tanh(drive);
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            float wet = fastTanh(x * drive) * norm;
            wet = toneLow.process(wet);
            wet = toneHigh.process(wet);
            buffer[i] = x * (1f - mix) + wet * mix;
        }
    }
}
