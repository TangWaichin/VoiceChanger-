package com.voicechanger.audio;

/**
 * 音频发放模块：把处理后的音频写入“虚拟麦克风”输出设备，供其他软件作为麦克风使用。
 */
public final class AudioOutputModule extends AbstractAudioSink {

    @Override
    protected String threadName() {
        return "audio-output";
    }

    @Override
    protected String moduleName() {
        return "音频发放模块";
    }
}
