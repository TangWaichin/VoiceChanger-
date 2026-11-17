package com.voicechanger.audio;

import com.voicechanger.dsp.ProcessorCatalog;
import com.voicechanger.dsp.ProcessorState;
import com.voicechanger.dsp.TransformChain;
import com.voicechanger.logging.LogService;

import javax.sound.sampled.LineUnavailableException;
import java.util.List;

/**
 * 音频引擎：串联拦截 -> 转换 -> 发放 / 耳返 三个模块。
 */
public final class AudioEngine implements AutoCloseable {

    /** 界面展示用的音频规格：输入设备格式、驱动实际格式、适配后的引擎输出格式。 */
    public record FormatInfo(String backend, String input, String driver, String output) {
        public static final FormatInfo EMPTY = new FormatInfo("", "—", "—", "—");
    }

    private static final String OUTPUT_FORMAT = "48000 Hz / 1 ch / 16bit PCM";

    private final ProcessorCatalog catalog;
    private final List<ProcessorState> states;
    private final NativeAudioEngine nativeEngine;
    private final AudioCaptureModule capture = new AudioCaptureModule();
    private final AudioOutputModule output = new AudioOutputModule();
    private final AudioMonitorModule monitor = new AudioMonitorModule();
    private final CustomAudioBridge customBridge = new CustomAudioBridge();
    private AudioTransformModule transform;
    private volatile boolean nativeActive;
    private volatile boolean running;
    private volatile boolean customMode;
    private volatile DeviceInfo inputDevice;
    private volatile DeviceInfo outputDevice;
    private volatile DeviceInfo monitorDevice;
    private volatile FormatInfo formatInfo = FormatInfo.EMPTY;
    private volatile Runnable captureErrorHandler;

    public AudioEngine(ProcessorCatalog catalog, List<ProcessorState> states) {
        this.catalog = catalog;
        this.states = states;
        this.nativeEngine = NativeAudio.isAvailable() ? new NativeAudioEngine(catalog, states) : null;
        capture.setErrorHandler(cause -> notifyCaptureError());
    }

    /** 采集异常（如设备被拔出）时的界面回调，由 MainWindow 注册。 */
    public void setCaptureErrorHandler(Runnable handler) {
        this.captureErrorHandler = handler;
    }

    private void notifyCaptureError() {
        Runnable handler = captureErrorHandler;
        if (handler != null) {
            handler.run();
        }
    }

    public synchronized void start(DeviceInfo input, DeviceInfo out, boolean monitorEnabled,
                                    DeviceInfo monitorDev, float monitorVolume) throws LineUnavailableException {
        if (running) {
            stop();
        }
        LogService.info("启动音频引擎：输入=" + (input == null ? "未选择" : input.label())
                + "，输出=" + (out == null ? "未选择" : out.label())
                + "，耳返=" + (monitorEnabled ? "开启" : "关闭")
                + (monitorEnabled && monitorDev != null ? " (" + monitorDev.label() + ")" : "")
                + "，耳返音量=" + Math.round(monitorVolume * 100) + "%");
        if (nativeEngine != null) {
            try {
                nativeEngine.setCustomBridge(customBridge);
                nativeEngine.setCustomMode(customMode);
                nativeEngine.start(input, out, monitorEnabled, monitorDev, monitorVolume);
                nativeActive = true;
                running = true;
                inputDevice = input;
                outputDevice = out;
                monitorDevice = monitorEnabled ? monitorDev : null;
                formatInfo = new FormatInfo("WASAPI", nativeEngine.inputFormat(),
                        nativeEngine.driverFormat(), OUTPUT_FORMAT);
                LogService.info("音频后端：C++ WASAPI 原生引擎");
                return;
            } catch (LineUnavailableException | RuntimeException e) {
                nativeActive = false;
                LogService.warn("原生音频后端启动失败，回退 Java Sound：" + e.getMessage());
                nativeEngine.stop();
            }
        }
        transform = new AudioTransformModule(new TransformChain(catalog, states));
        startJavaSound(input, out, monitorEnabled, monitorDev, monitorVolume);
    }

    /**
     * Java Sound 后端统一启动：首次启动与原生回退共用，避免两处链路逻辑分叉。
     */
    private void startJavaSound(DeviceInfo in, DeviceInfo out, boolean monitorEnabled,
                                DeviceInfo monitorDev, float monitorVolume) throws LineUnavailableException {
        transform = new AudioTransformModule(new TransformChain(catalog, states));
        try {
            output.open(out);
            monitor.setVolume(monitorVolume);
            if (monitorEnabled) {
                monitor.open(monitorDev);
            }
            capture.open(in);
        } catch (LineUnavailableException | RuntimeException e) {
            output.close();
            monitor.close();
            capture.close();
            throw e;
        }
        output.start();
        try {
            if (monitorEnabled) {
                monitor.start();
            }
            capture.start(this::onCapturedFrames);
        } catch (RuntimeException e) {
            // 启动后半段失败时回收已启动的播放/耳返线程，避免“界面失败但后台仍在跑”
            output.close();
            monitor.close();
            capture.close();
            throw e;
        }
        running = true;
        inputDevice = in;
        outputDevice = out;
        monitorDevice = monitorEnabled ? monitorDev : null;
        formatInfo = javaSoundFormatInfo();
    }

    /** 采集回调：默认链路走调节模块，自定义模式旁路并转交实体麦克风 API。 */
    private void onCapturedFrames(float[] frames, int length) {
        if (customMode) {
            // 自定义接口模式：旁路音频调节模块，麦克风数据转交实体麦克风 API
            transform.measureInput(frames, length);
            customBridge.pushMicrophone(frames, length);
            return;
        }
        transform.process(frames, length);
        output.push(frames, length);
        if (monitor.isOpen()) {
            monitor.push(frames, length);
        }
    }

    /** Java Sound 回退路径的格式信息。 */
    private FormatInfo javaSoundFormatInfo() {
        String text = AudioFormatSpec.describe(capture.format());
        return new FormatInfo("Java Sound", text, "Java Sound / " + text, OUTPUT_FORMAT);
    }

    public synchronized void stop() {
        if (nativeActive) {
            nativeEngine.stop();
            nativeActive = false;
        }
        capture.close();
        output.close();
        monitor.close();
        formatInfo = FormatInfo.EMPTY;
        if (running) {
            LogService.info("音频引擎已停止");
        }
        running = false;
    }

    public synchronized void restartMonitor(boolean enabled, DeviceInfo device, float volume)
            throws LineUnavailableException {
        if (nativeActive) {
            DeviceInfo in = inputDevice;
            DeviceInfo out = outputDevice;
            nativeEngine.stop();
            nativeActive = false;
            running = false;
            try {
                nativeEngine.start(in, out, enabled, device, volume);
                nativeActive = true;
                running = true;
                monitorDevice = enabled ? device : null;
                formatInfo = new FormatInfo("WASAPI", nativeEngine.inputFormat(),
                        nativeEngine.driverFormat(), OUTPUT_FORMAT);
                return;
            } catch (LineUnavailableException | RuntimeException e) {
                LogService.warn("原生引擎重启耳返失败，回退 Java Sound：" + e.getMessage());
                // 原生引擎已停，直接按新的耳返参数启动 Java Sound 链路
                try {
                    startJavaSound(in, out, enabled, device, volume);
                } catch (LineUnavailableException | RuntimeException failure) {
                    // 回退也失败：彻底回收，保证 running/formatInfo 与实际状态一致
                    capture.close();
                    output.close();
                    monitor.close();
                    running = false;
                    monitorDevice = null;
                    formatInfo = FormatInfo.EMPTY;
                    throw failure;
                }
                return;
            }
        }
        monitor.stop();
        monitor.close();
        monitor.setVolume(volume);
        monitorDevice = null;
        if (enabled) {
            monitor.open(device);
            monitor.start();
            monitorDevice = device;
        }
    }

    public void setMonitorVolume(float volume) {
        monitor.setVolume(volume);
        if (nativeActive) {
            nativeEngine.setMonitorVolume(volume);
        }
    }

    /**
     * 开启/关闭自定义调音接口：
     * 开启时启动两个本地 API 并旁路音频调节模块，音频拦截模块的数据转交实体麦克风 API，
     * 虚拟麦克风 API 收到的数据直接进入音频发放模块。
     *
     * @return 是否切换成功；端口被占用导致启用失败时返回 false 且保持原模式
     */
    public synchronized boolean setCustomMode(boolean enabled) {
        if (customMode == enabled) {
            return true;
        }
        if (enabled) {
            if (!customBridge.start(this::deliverCustomOutput)) {
                LogService.warn("自定义调音接口未能启动，继续使用默认调音面板");
                return false;
            }
            customMode = true;
            if (nativeEngine != null) {
                nativeEngine.setCustomMode(true);
            }
            LogService.info("已启用自定义调音接口：音频调节模块关闭，"
                    + "实体麦克风 API 端口 " + CustomAudioBridge.MIC_API_PORT
                    + "，虚拟麦克风 API 端口 " + CustomAudioBridge.VIRTUAL_MIC_API_PORT);
            return true;
        }
        customMode = false;
        if (nativeEngine != null) {
            nativeEngine.setCustomMode(false);
        }
        customBridge.stop();
        LogService.info("已关闭自定义调音接口：恢复默认调音链路");
        return true;
    }

    public boolean isCustomMode() {
        return customMode;
    }

    /** 虚拟麦克风 API 收到外部处理后的音频：送到音频发放模块与耳返。 */
    private void deliverCustomOutput(float[] mono, int length) {
        if (nativeActive) {
            nativeEngine.writeCustomOutput(mono, length);
            return;
        }
        if (!running) {
            return;
        }
        if (transform != null) {
            transform.measureOutput(mono, length);
        }
        output.push(mono, length);
        if (monitor.isOpen()) {
            monitor.push(mono, length);
        }
    }

    public boolean isRunning() {
        return running;
    }

    /** 当前音频规格信息（未启动时各项为 "—"）。 */
    public FormatInfo formatInfo() {
        return formatInfo;
    }

    public float inputLevel() {
        if (nativeActive) {
            return nativeEngine.inputLevel();
        }
        return transform == null ? 0f : transform.inputLevel();
    }

    public float outputLevel() {
        if (nativeActive) {
            return nativeEngine.outputLevel();
        }
        return transform == null ? 0f : transform.outputLevel();
    }

    public float monitorLevel() {
        if (nativeActive) {
            return nativeEngine.monitorLevel();
        }
        return monitor.isOpen() ? monitor.level() : 0f;
    }

    public void resetChain() {
        if (nativeActive) {
            nativeEngine.resetChain();
        }
        if (transform != null) {
            transform.reset();
        }
    }

    /** 单模块重置：参数调整稳定后清除该模块的延迟线残留，主链路不中断。 */
    public void resetProcessor(String id) {
        if (nativeActive) {
            nativeEngine.resetProcessor(id);
        } else if (transform != null) {
            transform.reset(id);
        }
    }

    public DeviceInfo inputDevice() {
        return inputDevice;
    }

    public DeviceInfo outputDevice() {
        return outputDevice;
    }

    public DeviceInfo monitorDevice() {
        return monitorDevice;
    }

    @Override
    public void close() {
        try {
            stop();
        } finally {
            customBridge.close();
        }
    }
}
