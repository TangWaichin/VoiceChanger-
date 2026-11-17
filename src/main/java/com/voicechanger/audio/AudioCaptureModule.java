package com.voicechanger.audio;

import com.voicechanger.logging.LogService;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * 音频拦截模块：从选定的输入设备实时采集，转换为引擎统一格式后交给下游。
 */
public final class AudioCaptureModule implements AutoCloseable {

    public interface FrameListener {
        void onFrames(float[] mono, int length);
    }

    /** 采集异常（如设备被拔出）通知，在采集线程触发。 */
    public interface ErrorHandler {
        void onError(Throwable cause);
    }

    private static final int BLOCK_FRAMES = 480;

    private TargetDataLine line;
    private Thread worker;
    private volatile boolean running;
    private AudioConverter.StreamResampler resampler;
    private final ChannelDownmixer downmixer = new ChannelDownmixer();
    private AudioFormat format;
    private int deviceChannels = 1;
    private FrameListener listener;
    private volatile ErrorHandler errorHandler;
    private DeviceInfo device;

    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    public synchronized void open(DeviceInfo device) throws LineUnavailableException {
        close();
        this.device = device;
        int bufferBytes = BLOCK_FRAMES * 8 * 4;
        DeviceManager.OpenedLine opened = DeviceManager.openTargetLine(device, bufferBytes);
        this.line = (TargetDataLine) opened.line();
        this.format = opened.format();
        deviceChannels = Math.max(1, format.getChannels());
        downmixer.prepare(deviceChannels);
        resampler = new AudioConverter.StreamResampler((int) format.getSampleRate(),
                AudioFormatSpec.SAMPLE_RATE);
        LogService.info("音频拦截模块已打开设备: " + device.label() + " / 格式 "
                + AudioFormatSpec.describe(format));
    }

    /** 实际打开的采集格式（用于界面显示）。 */
    public synchronized AudioFormat format() {
        return format;
    }

    public synchronized void start(FrameListener listener) {
        if (line == null) {
            throw new IllegalStateException("请先打开输入设备");
        }
        this.listener = listener;
        running = true;
        worker = new Thread(this::loop, "audio-capture");
        worker.setDaemon(true);
        worker.start();
        line.start();
    }

    private void loop() {
        int frames = BLOCK_FRAMES;
        AudioFormat localFormat = format;
        TargetDataLine input = line;
        if (localFormat == null || input == null) {
            return;
        }
        int frameSize = Math.max(1, localFormat.getFrameSize());
        int channels = Math.max(1, deviceChannels);
        // 复用缓冲：listener 同步消费（下游推送时已自行复制），块间复用安全
        byte[] buffer = new byte[frames * frameSize];
        float[] channelBuf = new float[frames * channels];
        float[] mono = new float[frames];
        float[] converted = resampler == null ? null
                : new float[resampler.maxOutputFrames(frames)];
        // 保留上一次读取剩余的不足一帧字节，避免 read 返回非整帧时丢样本
        int carry = 0;
        while (running) {
            int read;
            try {
                read = input.read(buffer, carry, buffer.length - carry);
            } catch (RuntimeException e) {
                // 设备被拔出或线路被关闭：结束采集线程并通知上层，避免静默死亡
                if (running) {
                    LogService.error("音频拦截模块读取失败，采集已停止", e);
                    ErrorHandler handler = errorHandler;
                    if (handler != null) {
                        handler.onError(e);
                    }
                }
                break;
            }
            if (read < 0) {
                if (running) {
                    LogService.warn("音频输入流已关闭，采集已停止");
                    ErrorHandler handler = errorHandler;
                    if (handler != null) {
                        handler.onError(new java.io.IOException("音频输入流已关闭"));
                    }
                }
                break;
            }
            if (read == 0) {
                // 设备暂时没有数据时短暂让出 CPU，避免空转
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            int filled = carry + read;
            int usable = filled - (filled % frameSize);
            if (usable > 0) {
                int monoLen;
                if (channels == 1) {
                    monoLen = AudioConverter.pcmToMono(localFormat, buffer, 0, usable, mono);
                } else {
                    int got = AudioConverter.pcmToChannels(localFormat, buffer, 0, usable, channelBuf);
                    for (int f = 0; f < got; f++) {
                        mono[f] = downmixer.process(channelBuf, f * channels, channels);
                    }
                    monoLen = got;
                }
                if (monoLen > 0) {
                    if (resampler == null || resampler.isIdentity()) {
                        if (listener != null) {
                            listener.onFrames(mono, monoLen);
                        }
                    } else {
                        int n = resampler.process(mono, monoLen, converted);
                        if (n > 0 && listener != null) {
                            listener.onFrames(converted, n);
                        }
                    }
                }
            }
            carry = filled - usable;
            if (carry > 0) {
                System.arraycopy(buffer, usable, buffer, 0, carry);
            }
        }
    }

    public synchronized void stop() {
        running = false;
        if (line != null) {
            try {
                line.stop();
                line.flush();
            } catch (Exception ignored) {
                // 设备可能已被移除
            }
        }
        if (worker != null) {
            try {
                worker.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
    }

    @Override
    public synchronized void close() {
        stop();
        if (line != null) {
            line.close();
            line = null;
        }
        if (device != null) {
            LogService.info("音频拦截模块已释放设备: " + device.label());
            device = null;
        }
    }

    public static boolean hasInputDevice() {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info info : mixers) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, AudioFormatSpec.std()))) {
                return true;
            }
        }
        return false;
    }
}
