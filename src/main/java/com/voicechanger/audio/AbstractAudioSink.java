package com.voicechanger.audio;

import com.voicechanger.logging.LogService;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 播放类模块的公共实现：队列 + 独立写线程 + 采样率/声道适配 + 音量。
 */
public abstract class AbstractAudioSink implements AutoCloseable {

    private static final int QUEUE_BLOCKS = 12;

    private final ArrayBlockingQueue<float[]> queue = new ArrayBlockingQueue<>(QUEUE_BLOCKS);
    private SourceDataLine line;
    private AudioConverter.StreamResampler resampler;
    private AudioFormat format;
    private int deviceChannels = 1;
    private int deviceRate = AudioFormatSpec.SAMPLE_RATE;
    private int frameSize = 2;
    private volatile float volume = 1f;
    private volatile float level;
    private volatile boolean running;
    private Thread writer;
    private DeviceInfo device;

    protected abstract String threadName();

    protected abstract String moduleName();

    public synchronized void open(DeviceInfo device) throws LineUnavailableException {
        close();
        this.device = device;
        DeviceManager.OpenedLine opened = DeviceManager.openSourceLine(device, 16384);
        this.line = (SourceDataLine) opened.line();
        AudioFormat format = opened.format();
        this.format = format;
        this.deviceChannels = Math.max(1, format.getChannels());
        this.deviceRate = Math.round(format.getSampleRate());
        this.frameSize = Math.max(1, format.getFrameSize());
        this.resampler = new AudioConverter.StreamResampler(AudioFormatSpec.SAMPLE_RATE, deviceRate);
        queue.clear();
        line.start();
        LogService.info(moduleName() + "已打开设备: " + device.label() + " / 格式 "
                + AudioFormatSpec.describe(format));
    }

    public synchronized void start() {
        if (line == null) {
            throw new IllegalStateException(moduleName() + "尚未打开设备");
        }
        if (running) {
            return;
        }
        running = true;
        writer = new Thread(this::loop, threadName());
        writer.setDaemon(true);
        writer.start();
    }

    private void loop() {
        while (running) {
            float[] block;
            try {
                block = queue.poll(60, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (block == null) {
                continue;
            }
            writeBlock(block);
        }
    }

    // 复用缓冲：播放链路每 10ms 触发一次，避免每块新建数组造成 GC 压力
    private float[] scaledBuf = new float[0];
    private float[] resampleBuf = new float[0];
    private float[] channelBuf = new float[0];
    private byte[] pcmBuf = new byte[0];

    private void writeBlock(float[] mono) {
        // 快照当前设备参数：close() 可能并发清空字段，避免中途变为 null 导致异常
        AudioConverter.StreamResampler localResampler = resampler;
        AudioFormat localFormat = format;
        SourceDataLine localLine = line;
        int channels = Math.max(1, deviceChannels);
        int localFrameSize = Math.max(1, frameSize);
        if (localFormat == null || localLine == null) {
            return;
        }
        float gain = volume;
        int n = mono.length;
        scaledBuf = ensure(scaledBuf, n);
        float peak = 0f;
        for (int i = 0; i < n; i++) {
            float v = mono[i] * gain;
            if (v > 1f) {
                v = 1f;
            } else if (v < -1f) {
                v = -1f;
            }
            scaledBuf[i] = v;
            float abs = Math.abs(v);
            if (abs > peak) {
                peak = abs;
            }
        }
        level = level * 0.7f + peak * 0.3f;
        float[] src = scaledBuf;
        int srcLen = n;
        if (localResampler != null && !localResampler.isIdentity()) {
            resampleBuf = ensure(resampleBuf, localResampler.maxOutputFrames(n));
            srcLen = localResampler.process(scaledBuf, n, resampleBuf);
            src = resampleBuf;
        }
        int frames = srcLen * channels;
        if (channels > 1) {
            channelBuf = ensure(channelBuf, frames);
            AudioConverter.monoToChannels(src, srcLen, channels, channelBuf);
            src = channelBuf;
        }
        int bytesPerSample = Math.max(1, localFrameSize / channels);
        int bytes = frames * bytesPerSample;
        pcmBuf = ensure(pcmBuf, bytes);
        AudioConverter.monoToPcm(localFormat, src, frames, pcmBuf);
        try {
            localLine.write(pcmBuf, 0, bytes);
        } catch (Exception e) {
            LogService.error(moduleName() + "写入失败", e);
        }
    }

    private static float[] ensure(float[] buf, int length) {
        return buf.length >= length ? buf : new float[length];
    }

    private static byte[] ensure(byte[] buf, int length) {
        return buf.length >= length ? buf : new byte[length];
    }

    /**
     * 将处理后的单声道数据送入播放队列，队列满时丢弃最旧的数据以保证实时性。
     */
    public void push(float[] mono, int length) {
        if (!running || line == null || mono == null || length <= 0 || length > mono.length) {
            return;
        }
        float[] copy = new float[length];
        System.arraycopy(mono, 0, copy, 0, length);
        if (!queue.offer(copy)) {
            queue.poll();
            queue.offer(copy);
        }
    }

    public void setVolume(float volume) {
        // 与原生引擎、存档的钳位范围保持一致：0~1
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    public float volume() {
        return volume;
    }

    public float level() {
        return level;
    }

    public synchronized boolean isOpen() {
        return line != null;
    }

    public synchronized void stop() {
        running = false;
        if (writer != null) {
            try {
                writer.join(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writer = null;
        }
        queue.clear();
        if (line != null) {
            try {
                line.stop();
                line.flush();
            } catch (Exception ignored) {
                // 设备可能已拔出
            }
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
            LogService.info(moduleName() + "已释放设备: " + device.label());
            device = null;
        }
        resampler = null;
        format = null;
        frameSize = 2;
    }
}
