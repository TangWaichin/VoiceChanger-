package com.voicechanger.audio;

/**
 * 耳返模块：把处理后的音频同时送到耳返设备，便于自己监听。
 */
public final class AudioMonitorModule extends AbstractAudioSink {

    @Override
    protected String threadName() {
        return "audio-monitor";
    }

    @Override
    protected String moduleName() {
        return "耳返模块";
    }
}
