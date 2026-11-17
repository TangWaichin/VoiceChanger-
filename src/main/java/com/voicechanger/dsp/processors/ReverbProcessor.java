package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 混响：梳状 + 全通网络（Freeverb 简化版）。
 */
public final class ReverbProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.percent("size", "房间大小", 55f),
            ParamSpec.percent("damping", "阻尼", 40f),
            ParamSpec.percent("mix", "干湿比", 25f));

    private static final int[] COMB_LENGTHS = {1116, 1188, 1277, 1356};
    private static final int[] ALLPASS_LENGTHS = {556, 441};

    private float[][] combs;
    private int[] combIndex;
    private float[] combFilterStore;
    private float[][] allpasses;
    private int[] allpassIndex;

    public ReverbProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "reverb";
    }

    @Override
    public String displayName() {
        return "混响";
    }

    @Override
    public String description() {
        return "模拟房间反射，房间越大尾音越长，阻尼越大高频衰减越快。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected void onPrepare(int sampleRate, int maxFrames) {
        float scale = sampleRate / 44100f;
        combs = new float[COMB_LENGTHS.length][];
        combIndex = new int[COMB_LENGTHS.length];
        combFilterStore = new float[COMB_LENGTHS.length];
        for (int i = 0; i < COMB_LENGTHS.length; i++) {
            combs[i] = new float[Math.max(8, Math.round(COMB_LENGTHS[i] * scale))];
        }
        allpasses = new float[ALLPASS_LENGTHS.length][];
        allpassIndex = new int[ALLPASS_LENGTHS.length];
        for (int i = 0; i < ALLPASS_LENGTHS.length; i++) {
            allpasses[i] = new float[Math.max(8, Math.round(ALLPASS_LENGTHS[i] * scale))];
        }
    }

    @Override
    public void reset() {
        onPrepare(sampleRate(), 0);
    }

    @Override
    public void process(float[] buffer, int length) {
        if (combs == null) {
            onPrepare(sampleRate(), length);
        }
        float size = param("size") / 100f;
        float damping = param("damping") / 100f;
        float mix = param("mix") / 100f;
        float feedback = 0.7f + size * 0.28f;
        float damp = damping * 0.4f;
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            float input = x * 0.015f;
            float out = 0f;
            for (int c = 0; c < combs.length; c++) {
                float[] buf = combs[c];
                int idx = combIndex[c];
                float delayed = buf[idx];
                combFilterStore[c] = delayed * (1f - damp) + combFilterStore[c] * damp;
                buf[idx] = input + combFilterStore[c] * feedback;
                if (++idx >= buf.length) {
                    idx = 0;
                }
                combIndex[c] = idx;
                out += delayed;
            }
            for (int a = 0; a < allpasses.length; a++) {
                float[] buf = allpasses[a];
                int idx = allpassIndex[a];
                float delayed = buf[idx];
                float y = -out + delayed;
                buf[idx] = out + delayed * 0.5f;
                if (++idx >= buf.length) {
                    idx = 0;
                }
                allpassIndex[a] = idx;
                out = y;
            }
            buffer[i] = x * (1f - mix) + out * mix;
        }
    }
}
