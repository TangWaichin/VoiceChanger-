package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.AudioProcessor;
import com.voicechanger.dsp.DelayLine;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 变调：双延迟头交叉淡化的移调器，semitones 为正升高音调。
 */
public final class PitchShiftProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("semitones", "音高", -12f, 12f, 0f, 1f, " 半音", 0),
            ParamSpec.of("grain", "颗粒长度", 30f, 120f, 60f, 5f, " ms", 0),
            ParamSpec.percent("mix", "干湿比", 100f));

    private DelayLine line;
    private float delaySamples;
    private float grainSamples;
    private float phase;

    public PitchShiftProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "pitch";
    }

    @Override
    public String displayName() {
        return "变调";
    }

    @Override
    public String description() {
        return "整体升降音高，颗粒越长越平滑，越短越灵敏。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected void onPrepare(int sampleRate, int maxFrames) {
        // 颗粒上限 120ms，第二读头最远到 1.5 倍颗粒长度（约 180ms），据此分配延迟线
        line = new DelayLine((int) Math.ceil(sampleRate * 0.18f) + 64);
        phase = 0f;
        grainSamples = Math.max(64f, param("grain") * sampleRate / 1000f);
        delaySamples = 0f;
    }

    @Override
    public void reset() {
        if (line != null) {
            line.clear();
        }
        phase = 0f;
    }

    @Override
    public void process(float[] buffer, int length) {
        if (line == null) {
            onPrepare(sampleRate(), length);
        }
        float semitones = param("semitones");
        float mix = param("mix") / 100f;
        grainSamples = Math.max(64f, param("grain") * sampleRate() / 1000f);
        float ratio = (float) Math.pow(2d, semitones / 12d);
        boolean bypass = Math.abs(semitones) < 0.01f || mix <= 0.001f;
        float step = 1f - ratio;
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            line.write(x);
            if (bypass) {
                continue;
            }
            delaySamples += step;
            if (delaySamples >= grainSamples) {
                delaySamples -= grainSamples;
            } else if (delaySamples < 0f) {
                delaySamples += grainSamples;
            }
            float d1 = delaySamples;
            float d2 = d1 + grainSamples * 0.5f;
            if (d2 >= grainSamples) {
                d2 -= grainSamples;
            }
            double w1Angle = Math.PI * d1 / grainSamples;
            float w1 = (float) (Math.sin(w1Angle) * Math.sin(w1Angle));
            float w2 = 1f - w1;
            float wet = line.read(d1) * w1 + line.read(d2) * w2;
            buffer[i] = x * (1f - mix) + wet * mix;
        }
    }
}
