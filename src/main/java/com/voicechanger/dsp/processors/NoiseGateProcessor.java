package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 噪声门：安静时压下底噪，只让超过阈值的语音通过。
 */
public final class NoiseGateProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("threshold", "阈值", -80f, 0f, -45f, 1f, " dB", 0),
            ParamSpec.of("attack", "启动", 1f, 100f, 5f, 1f, " ms", 0),
            ParamSpec.of("release", "释放", 20f, 1000f, 150f, 5f, " ms", 0));

    private float envelope;
    private float gain = 1f;

    public NoiseGateProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "gate";
    }

    @Override
    public String displayName() {
        return "噪声门";
    }

    @Override
    public String description() {
        return "低于阈值的底噪被压下去，说话时才放行，可减少房间噪音。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    public void reset() {
        envelope = 0f;
        gain = 1f;
    }

    @Override
    public void process(float[] buffer, int length) {
        float threshold = dbToGain(param("threshold"));
        float attack = (float) Math.exp(-1d / (0.001d * param("attack") * sampleRate()));
        float release = (float) Math.exp(-1d / (0.001d * param("release") * sampleRate()));
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            float abs = Math.abs(x);
            envelope = abs > envelope ? abs : envelope * 0.9995f + abs * 0.0005f;
            float target = envelope >= threshold ? 1f : 0f;
            float coeff = target > gain ? attack : release;
            gain = target + (gain - target) * coeff;
            buffer[i] = x * gain;
        }
    }
}
