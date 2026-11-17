package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.DelayLine;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 颤音：周期性音高波动。
 */
public final class VibratoProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("rate", "速率", 0.1f, 12f, 5f, 0.1f, " Hz", 1),
            ParamSpec.of("depth", "深度", 0.5f, 8f, 2.5f, 0.1f, " ms", 1),
            ParamSpec.percent("mix", "干湿比", 100f));

    private DelayLine line;
    private double phase;
    private float baseDelay;

    public VibratoProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "vibrato";
    }

    @Override
    public String displayName() {
        return "颤音";
    }

    @Override
    public String description() {
        return "让音高周期性轻微摆动，唱歌与播音都更生动。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected void onPrepare(int sampleRate, int maxFrames) {
        line = new DelayLine(4096);
        phase = 0d;
        baseDelay = 0.012f * sampleRate;
    }

    @Override
    public void reset() {
        if (line != null) {
            line.clear();
        }
        phase = 0d;
    }

    @Override
    public void process(float[] buffer, int length) {
        if (line == null) {
            onPrepare(sampleRate(), length);
        }
        float mix = param("mix") / 100f;
        float depth = param("depth") * sampleRate() / 1000f;
        float step = param("rate") / sampleRate();
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            line.write(x);
            float lfo = (float) Math.sin(2d * Math.PI * phase);
            float wet = line.read(baseDelay + depth * (0.5f + 0.5f * lfo));
            buffer[i] = x * (1f - mix) + wet * mix;
            phase += step;
            if (phase >= 1d) {
                phase -= 1d;
            }
        }
    }
}
