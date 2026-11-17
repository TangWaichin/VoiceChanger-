package com.voicechanger.audio;

import com.voicechanger.dsp.TransformChain;

/**
 * 音频转换模块：接收拦截到的音频帧，执行已开启的调音选项后转交给发放/耳返模块。
 */
public final class AudioTransformModule {

    private final TransformChain chain;
    private volatile float inputPeak;
    private volatile float outputPeak;
    private volatile long inputStamp = System.nanoTime();
    private volatile long outputStamp = System.nanoTime();

    public AudioTransformModule(TransformChain chain) {
        this.chain = chain;
        chain.prepare(AudioFormatSpec.SAMPLE_RATE, 2048);
    }

    public void process(float[] buffer, int length) {
        measureInput(buffer, length);
        chain.process(buffer, length);
        measureOutput(buffer, length);
    }

    /** 只统计输入电平，不做调音处理（自定义接口旁路音频调节模块时使用）。 */
    public void measureInput(float[] buffer, int length) {
        float peak = 0f;
        for (int i = 0; i < length; i++) {
            float abs = Math.abs(buffer[i]);
            if (abs > peak) {
                peak = abs;
            }
        }
        inputPeak = inputPeak * 0.75f + peak * 0.25f;
        inputStamp = System.nanoTime();
    }

    /** 只统计输出电平。 */
    public void measureOutput(float[] buffer, int length) {
        float peak = 0f;
        for (int i = 0; i < length; i++) {
            float abs = Math.abs(buffer[i]);
            if (abs > peak) {
                peak = abs;
            }
        }
        outputPeak = outputPeak * 0.75f + peak * 0.25f;
        outputStamp = System.nanoTime();
    }

    public TransformChain chain() {
        return chain;
    }

    public float inputLevel() {
        return decay(inputPeak, inputStamp);
    }

    public float outputLevel() {
        return decay(outputPeak, outputStamp);
    }

    /** 无音频数据时按 250ms 时间常数自然衰减，避免电平表停留在最后一次读数。 */
    private static float decay(float value, long stamp) {
        long elapsed = System.nanoTime() - stamp;
        if (elapsed <= 0) {
            return value;
        }
        return (float) (value * Math.exp(-elapsed / 2.5e8d));
    }

    public void reset() {
        chain.reset();
    }

    public void reset(String id) {
        chain.reset(id);
    }
}
