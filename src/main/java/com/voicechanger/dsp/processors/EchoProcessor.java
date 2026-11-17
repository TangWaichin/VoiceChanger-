package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.DelayLine;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 回声：带反馈的延迟。
 */
public final class EchoProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("delay", "延迟时间", 40f, 800f, 220f, 5f, " ms", 0),
            ParamSpec.percent("feedback", "反馈", 35f),
            ParamSpec.percent("mix", "干湿比", 30f));

    private DelayLine line;

    public EchoProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "echo";
    }

    @Override
    public String displayName() {
        return "回声";
    }

    @Override
    public String description() {
        return "可调延迟与反馈的回声，适合营造空间感或特效。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected void onPrepare(int sampleRate, int maxFrames) {
        // 延迟上限 800ms，按采样率换算后分配，避免大延迟被环形缓冲截断
        line = new DelayLine((int) Math.ceil(sampleRate * 0.8f) + 64);
    }

    @Override
    public void reset() {
        if (line != null) {
            line.clear();
        }
    }

    @Override
    public void process(float[] buffer, int length) {
        if (line == null) {
            onPrepare(sampleRate(), length);
        }
        float delaySamples = Math.max(1f, param("delay") * sampleRate() / 1000f);
        float feedback = Math.min(0.95f, param("feedback") / 100f);
        float mix = param("mix") / 100f;
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            float delayed = line.read(delaySamples);
            line.write(x + delayed * feedback);
            buffer[i] = x * (1f - mix) + delayed * mix;
        }
    }
}
