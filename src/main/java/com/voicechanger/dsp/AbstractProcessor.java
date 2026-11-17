package com.voicechanger.dsp;

/**
 * 处理器基类：统一持有状态并把参数读取简化。
 */
public abstract class AbstractProcessor implements AudioProcessor {

    protected final ProcessorState state;
    private int sampleRate = 48000;

    protected AbstractProcessor(ProcessorState state) {
        this.state = state;
    }

    @Override
    public void prepare(int sampleRate, int maxFrames) {
        this.sampleRate = sampleRate;
        onPrepare(sampleRate, maxFrames);
    }

    protected void onPrepare(int sampleRate, int maxFrames) {
    }

    protected int sampleRate() {
        return sampleRate;
    }

    /** 读取当前参数值，缺省时用定义中的默认值。 */
    protected float param(String paramId) {
        for (ParamSpec spec : params()) {
            if (spec.id().equals(paramId)) {
                return state.value(paramId, spec.defaultValue());
            }
        }
        return 0f;
    }

    protected float param(String paramId, float fallback) {
        return state.value(paramId, fallback);
    }

    protected boolean flag(String paramId) {
        return param(paramId) >= 0.5f;
    }

    protected float dbToGain(float db) {
        return (float) Math.pow(10d, db / 20d);
    }
}
