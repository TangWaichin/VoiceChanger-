package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.Biquad;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 三段均衡：低架 / 中频峰 / 高架。
 */
public final class EqualizerProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("low", "低频", -15f, 15f, 0f, 0.5f, " dB", 1),
            ParamSpec.of("midFreq", "中频中心", 400f, 5000f, 1200f, 50f, " Hz", 0),
            ParamSpec.of("mid", "中频", -15f, 15f, 0f, 0.5f, " dB", 1),
            ParamSpec.of("high", "高频", -15f, 15f, 0f, 0.5f, " dB", 1));

    private final Biquad low = new Biquad();
    private final Biquad mid = new Biquad();
    private final Biquad high = new Biquad();
    private float lastLow = Float.NaN;
    private float lastMid = Float.NaN;
    private float lastMidFreq = Float.NaN;
    private float lastHigh = Float.NaN;

    public EqualizerProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "eq";
    }

    @Override
    public String displayName() {
        return "均衡";
    }

    @Override
    public String description() {
        return "调整低、中、高频比例：低频厚度、中频清晰度、高频空气感。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected void onPrepare(int sampleRate, int maxFrames) {
        lastLow = lastMid = lastMidFreq = lastHigh = Float.NaN;
        low.reset();
        mid.reset();
        high.reset();
    }

    @Override
    public void reset() {
        onPrepare(sampleRate(), 0);
    }

    @Override
    public void process(float[] buffer, int length) {
        float lowGain = param("low", 0f);
        float midGain = param("mid", 0f);
        float midFreq = param("midFreq", 1200f);
        float highGain = param("high", 0f);
        if (lowGain != lastLow) {
            low.lowShelf(sampleRate(), 160f, lowGain);
            lastLow = lowGain;
        }
        if (midGain != lastMid || midFreq != lastMidFreq) {
            mid.peaking(sampleRate(), midFreq, 1.1f, midGain);
            lastMid = midGain;
            lastMidFreq = midFreq;
        }
        if (highGain != lastHigh) {
            high.highShelf(sampleRate(), 5200f, highGain);
            lastHigh = highGain;
        }
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            x = low.process(x);
            x = mid.process(x);
            x = high.process(x);
            buffer[i] = x;
        }
    }
}
