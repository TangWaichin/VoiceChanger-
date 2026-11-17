package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 机器人音：环形调制，混合后得到金属/机械音色。
 */
public final class RobotProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("freq", "载波频率", 20f, 400f, 90f, 1f, " Hz", 0),
            ParamSpec.percent("square", "方波比例", 30f),
            ParamSpec.percent("mix", "干湿比", 70f));

    private double phase;

    public RobotProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "robot";
    }

    @Override
    public String displayName() {
        return "机器人";
    }

    @Override
    public String description() {
        return "环形调制制造机械感，方波比例越高越硬朗。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    public void reset() {
        phase = 0d;
    }

    @Override
    public void process(float[] buffer, int length) {
        float freq = param("freq");
        float square = param("square") / 100f;
        float mix = param("mix") / 100f;
        double step = freq / sampleRate();
        for (int i = 0; i < length; i++) {
            float sine = (float) Math.sin(2d * Math.PI * phase);
            float sq = sine >= 0f ? 1f : -1f;
            float carrier = sine * (1f - square) + sq * square;
            float x = buffer[i];
            buffer[i] = x * (1f - mix) + x * carrier * mix;
            phase += step;
            if (phase >= 1d) {
                phase -= 1d;
            }
        }
    }
}
