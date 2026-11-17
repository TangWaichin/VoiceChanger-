package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 压缩器：超出阈值的部分按比例压缩，让音量更稳。
 */
public final class CompressorProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("threshold", "阈值", -60f, 0f, -18f, 1f, " dB", 0),
            ParamSpec.of("ratio", "压缩比", 1f, 20f, 3f, 0.5f, ":1", 1),
            ParamSpec.of("attack", "启动", 1f, 200f, 10f, 1f, " ms", 0),
            ParamSpec.of("release", "释放", 10f, 1000f, 150f, 5f, " ms", 0),
            ParamSpec.of("makeup", "补偿增益", 0f, 24f, 0f, 0.5f, " dB", 1));

    private float envelope = 0.0001f;
    private float gain = 0.0001f;

    public CompressorProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "compressor";
    }

    @Override
    public String displayName() {
        return "压缩";
    }

    @Override
    public String description() {
        return "压低过大的音量峰值并可用补偿增益抬回整体响度，声音更贴耳。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    public void reset() {
        envelope = 0.0001f;
        gain = 0.0001f;
    }

    @Override
    public void process(float[] buffer, int length) {
        float thresholdDb = param("threshold");
        float ratio = Math.max(1f, param("ratio"));
        float attack = (float) Math.exp(-1d / (0.001d * param("attack") * sampleRate()));
        float release = (float) Math.exp(-1d / (0.001d * param("release") * sampleRate()));
        float makeup = dbToGain(param("makeup"));
        float slope = 1f - 1f / ratio;
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            float abs = Math.abs(x) + 1e-7f;
            envelope = abs > envelope ? attack * envelope + (1f - attack) * abs
                    : release * envelope + (1f - release) * abs;
            float levelDb = 20f * (float) Math.log10(envelope);
            float over = Math.max(0f, levelDb - thresholdDb);
            float targetDb = -over * slope;
            float targetGain = (float) Math.pow(10d, targetDb / 20d);
            gain = attack * gain + (1f - attack) * targetGain;
            buffer[i] = x * gain * makeup;
        }
    }
}
