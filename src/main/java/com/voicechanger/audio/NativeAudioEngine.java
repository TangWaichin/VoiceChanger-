package com.voicechanger.audio;

import com.voicechanger.dsp.ProcessorCatalog;
import com.voicechanger.dsp.ProcessorState;
import com.voicechanger.dsp.TransformChain;
import com.voicechanger.logging.LogService;

import javax.sound.sampled.LineUnavailableException;
import java.util.List;

/**
 * 原生音频引擎（C++ WASAPI）的 Java 侧封装。
 * 负责调用 DSP 链路，并把处理结果写回原生输出/耳返队列。
 */
final class NativeAudioEngine {

    private static final int BLOCK_FRAMES = 480;

    private final ProcessorCatalog catalog;
    private final List<ProcessorState> states;
    private final float[] monitorBuffer = new float[BLOCK_FRAMES * 2];

    private volatile AudioTransformModule transform;
    private volatile boolean running;
    private volatile boolean monitorEnabled;
    private volatile float monitorVolume = 1f;
    private volatile float monitorPeak;
    private volatile boolean customMode;
    private volatile CustomAudioBridge customBridge;
    private volatile String inputFormat = "";
    private volatile String driverFormat = "";

    NativeAudioEngine(ProcessorCatalog catalog, List<ProcessorState> states) {
        this.catalog = catalog;
        this.states = states;
    }

    void start(DeviceInfo input, DeviceInfo output, boolean monitorEnabled,
               DeviceInfo monitorDevice, float monitorVolume) throws LineUnavailableException {
        stop();
        AudioTransformModule module = new AudioTransformModule(new TransformChain(catalog, states));
        transform = module;
        this.monitorEnabled = monitorEnabled;
        this.monitorVolume = clamp(monitorVolume);
        monitorPeak = 0f;
        running = true;

        String error = NativeAudio.start(this::onCaptureBlock,
                nameOf(input), nameOf(output),
                monitorEnabled ? nameOf(monitorDevice) : "",
                monitorEnabled, BLOCK_FRAMES);
        if (error != null) {
            running = false;
            transform = null;
            throw new LineUnavailableException(error);
        }
        inputFormat = NativeAudio.captureInputFormat();
        driverFormat = NativeAudio.captureDriverFormat();
        LogService.info("原生引擎使用设备：采集=" + nameOf(input)
                + "，输出=" + nameOf(output)
                + (monitorEnabled ? "，耳返=" + nameOf(monitorDevice) : "")
                + "；输入格式=" + inputFormat + "，驱动格式=" + driverFormat);
    }

    private void onCaptureBlock(float[] block, int frames) {
        AudioTransformModule module = transform;
        if (!running || module == null || frames <= 0) {
            return;
        }
        if (customMode) {
            // 自定义接口模式：跳过调音模块，只统计输入电平并转交实体麦克风 API
            module.measureInput(block, frames);
            CustomAudioBridge bridge = customBridge;
            if (bridge != null) {
                bridge.pushMicrophone(block, frames);
            }
            return;
        }
        module.process(block, frames);
        NativeAudio.writeOutput(block, frames);
        writeMonitor(block, frames);
    }

    /** 自定义接口模式：把虚拟麦克风 API 收到的数据送到发放/耳返模块。 */
    void writeCustomOutput(float[] mono, int length) {
        AudioTransformModule module = transform;
        if (!running || module == null || length <= 0) {
            return;
        }
        module.measureOutput(mono, length);
        NativeAudio.writeOutput(mono, length);
        writeMonitor(mono, length);
    }

    private void writeMonitor(float[] mono, int count) {
        if (monitorEnabled) {
            int frames = Math.min(count, monitorBuffer.length);
            float volume = monitorVolume;
            float peak = 0f;
            for (int i = 0; i < frames; i++) {
                float value = mono[i] * volume;
                monitorBuffer[i] = value;
                float abs = Math.abs(value);
                if (abs > peak) {
                    peak = abs;
                }
            }
            NativeAudio.writeMonitor(monitorBuffer, frames);
            monitorPeak = monitorPeak * 0.75f + peak * 0.25f;
        } else {
            monitorPeak = 0f;
        }
    }

    void setCustomMode(boolean customMode) {
        this.customMode = customMode;
    }

    void setCustomBridge(CustomAudioBridge bridge) {
        this.customBridge = bridge;
    }

    void stop() {
        running = false;
        NativeAudio.stop();
        transform = null;
        monitorPeak = 0f;
        inputFormat = "";
        driverFormat = "";
    }

    String inputFormat() {
        return inputFormat;
    }

    String driverFormat() {
        return driverFormat;
    }

    boolean isRunning() {
        return running;
    }

    void setMonitorVolume(float volume) {
        monitorVolume = clamp(volume);
    }

    float inputLevel() {
        AudioTransformModule module = transform;
        return module == null ? 0f : module.inputLevel();
    }

    float outputLevel() {
        AudioTransformModule module = transform;
        return module == null ? 0f : module.outputLevel();
    }

    float monitorLevel() {
        return monitorEnabled ? monitorPeak : 0f;
    }

    void resetChain() {
        AudioTransformModule module = transform;
        if (module != null) {
            module.reset();
        }
    }

    void resetProcessor(String id) {
        AudioTransformModule module = transform;
        if (module != null) {
            module.reset(id);
        }
    }

    private static String nameOf(DeviceInfo device) {
        return device == null ? "" : device.mixerName();
    }

    private static float clamp(float volume) {
        if (volume < 0f) {
            return 0f;
        }
        if (volume > 1f) {
            return 1f;
        }
        return volume;
    }
}
