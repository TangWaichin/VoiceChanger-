package com.voicechanger.audio;

import javax.sound.sampled.AudioFormat;

/**
 * 引擎内部统一的工作格式：设备各自的采样率/声道会被转换到这个格式后再处理。
 */
public final class AudioFormatSpec {

    public static final int SAMPLE_RATE = 48000;
    public static final int CHANNELS = 1;
    public static final int BITS = 16;

    private AudioFormatSpec() {
    }

    public static AudioFormat std() {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, BITS, CHANNELS,
                CHANNELS * BITS / 8, SAMPLE_RATE, false);
    }

    /**
     * 把任意格式归一为 16bit / 小端 / 有符号 PCM，便于统一按 short 处理。
     */
    public static AudioFormat normalized(AudioFormat format) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), 16,
                format.getChannels(), format.getChannels() * 2, format.getSampleRate(), false);
    }

    /** 音频格式的可读描述（与界面语言无关），如 "48000 Hz / 2 ch / 32bit float"。 */
    public static String describe(AudioFormat format) {
        if (format == null) {
            return "unknown";
        }
        int rate = Math.round(format.getSampleRate());
        int channels = Math.max(1, format.getChannels());
        int bits = format.getSampleSizeInBits();
        String kind = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding())
                ? bits + "bit float" : bits + "bit PCM";
        return rate + " Hz / " + channels + " ch / " + kind;
    }
}
