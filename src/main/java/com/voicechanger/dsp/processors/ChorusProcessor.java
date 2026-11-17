package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.DelayLine;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 合唱：轻微延迟调制，让声音更饱满、有层次。
 */
public final class ChorusProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("rate", "速率", 0.05f, 5f, 0.8f, 0.05f, " Hz", 2),
            ParamSpec.of("depth", "深度", 0.5f, 10f, 3f, 0.1f, " ms", 1),
            ParamSpec.of("delay", "基础延迟", 5f, 40f, 16f, 1f, " ms", 0),
            ParamSpec.percent("mix", "干湿比", 35f));

    private DelayLine line;
    private double phase;

    public ChorusProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "chorus";
    }

    @Override
    public String displayName() {
        return "合唱";
    }

    @Override
    public String description() {
        return "叠加轻微失谐的副本，声音更宽、更有厚度。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected void onPrepare(int sampleRate, int maxFrames) {
        line = new DelayLine(4096);
        phase = 0d;
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
        float base = param("delay") * sampleRate() / 1000f;
        float depth = param("depth") * sampleRate() / 1000f;
        float step = param("rate") / sampleRate();
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            line.write(x);
            float lfo = (float) Math.sin(2d * Math.PI * phase);
            float wet = line.read(base + depth * (0.5f + 0.5f * lfo));
            buffer[i] = x * (1f - mix) + wet * mix;
            phase += step;
            if (phase >= 1d) {
                phase -= 1d;
            }
        }
    }
}
