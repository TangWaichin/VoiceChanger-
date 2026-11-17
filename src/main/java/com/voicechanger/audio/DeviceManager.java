package com.voicechanger.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

/**
 * 枚举可用设备并按可用格式打开音频线路。
 */
public final class DeviceManager {

    private DeviceManager() {
    }

    public record OpenedLine(javax.sound.sampled.DataLine line, AudioFormat format) {
    }

    public static List<DeviceInfo> inputDevices() {
        return scan(true);
    }

    public static List<DeviceInfo> outputDevices() {
        return scan(false);
    }

    private static List<DeviceInfo> scan(boolean input) {
        List<DeviceInfo> result = new ArrayList<>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (int i = 0; i < mixers.length; i++) {
            Mixer.Info info = mixers[i];
            Mixer mixer = AudioSystem.getMixer(info);
            if (!hasLine(mixer, input)) {
                continue;
            }
            String name = info.getName();
            String description = info.getDescription();
            result.add(new DeviceInfo(i, name, describe(description, input), name));
        }
        return result;
    }

    private static String describe(String description, boolean input) {
        if (description == null || description.isBlank()
                || description.equalsIgnoreCase("No details available")) {
            return input ? "录音设备" : "播放设备";
        }
        String text = description.replace("Direct Audio Device: ", "").replace("DirectSound ", "");
        return text.isBlank() ? (input ? "录音设备" : "播放设备") : text;
    }

    private static boolean hasLine(Mixer mixer, boolean input) {
        Line.Info[] infos = input ? mixer.getTargetLineInfo() : mixer.getSourceLineInfo();
        if (infos.length == 0) {
            return false;
        }
        Class<?> lineClass = input ? TargetDataLine.class : SourceDataLine.class;
        for (AudioFormat format : candidateFormats()) {
            if (mixer.isLineSupported(new DataLine.Info(lineClass, format))) {
                return true;
            }
        }
        return mixer.isLineSupported(new DataLine.Info(lineClass, null));
    }

    private static List<AudioFormat> candidateFormats() {
        return List.of(
                AudioFormatSpec.std(),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 2, 4, 48000, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 24, 1, 3, 48000, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 24, 2, 6, 48000, false),
                new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 48000, 32, 1, 4, 48000, false),
                new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 48000, 32, 2, 8, 48000, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 24, 1, 3, 44100, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 24, 2, 6, 44100, false),
                new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 32, 1, 4, 44100, false),
                new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 44100, 32, 2, 8, 44100, false));
    }

    public static Mixer findMixer(int id) {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        if (id < 0 || id >= mixers.length) {
            throw new IllegalArgumentException("无效的设备序号: " + id);
        }
        return AudioSystem.getMixer(mixers[id]);
    }

    public static OpenedLine openTargetLine(DeviceInfo device, int bufferBytes) throws LineUnavailableException {
        Mixer mixer = findMixer(device.id());
        for (AudioFormat format : candidateFormats()) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (mixer.isLineSupported(info)) {
                TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                line.open(format, Math.max(bufferBytes, 4096));
                return new OpenedLine(line, format);
            }
        }
        DataLine.Info fallback = new DataLine.Info(TargetDataLine.class, null);
        TargetDataLine line = (TargetDataLine) mixer.getLine(fallback);
        line.open();
        AudioFormat actual = line.getFormat();
        if (!isSupportedPcm(actual)) {
            // 采集/播放链路支持 8/16/24/32bit 整型与 32bit 浮点，其他格式明确报错而不是静默错配
            line.close();
            throw new LineUnavailableException("设备格式不受支持: " + actual);
        }
        return new OpenedLine(line, actual);
    }

    public static OpenedLine openSourceLine(DeviceInfo device, int bufferBytes) throws LineUnavailableException {
        Mixer mixer = findMixer(device.id());
        for (AudioFormat format : candidateFormats()) {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (mixer.isLineSupported(info)) {
                SourceDataLine line = (SourceDataLine) mixer.getLine(info);
                line.open(format, Math.max(bufferBytes, 8192));
                return new OpenedLine(line, format);
            }
        }
        DataLine.Info fallback = new DataLine.Info(SourceDataLine.class, null);
        SourceDataLine line = (SourceDataLine) mixer.getLine(fallback);
        line.open();
        AudioFormat actual = line.getFormat();
        if (!isSupportedPcm(actual)) {
            line.close();
            throw new LineUnavailableException("设备格式不受支持: " + actual);
        }
        return new OpenedLine(line, actual);
    }

    /**
     * 是否存在“虚拟声卡/虚拟麦克风”类设备，用于界面提示。
     */
    public static boolean hasVirtualCable(List<DeviceInfo> outputs) {
        for (DeviceInfo info : outputs) {
            String text = (info.name() + " " + String.valueOf(info.detail())).toLowerCase();
            if (text.contains("cable") || text.contains("virtual") || text.contains("voicemeeter")
                    || text.contains("vb-audio")) {
                return true;
            }
        }
        return false;
    }

    /** 是否为 VoiceChanger 自带的虚拟音频端点。 */
    public static boolean isVoiceChangerVirtual(DeviceInfo info) {
        if (info == null) {
            return false;
        }
        String text = (info.name() + " " + String.valueOf(info.detail()) + " " + info.mixerName())
                .toLowerCase();
        return text.contains("voicechanger") && text.contains("virtual");
    }

    /** 是否已检测到 VoiceChanger 自带虚拟声卡的播放或录音端点。 */
    public static boolean hasVoiceChangerVirtual(List<DeviceInfo> devices) {
        for (DeviceInfo info : devices) {
            if (isVoiceChangerVirtual(info)) {
                return true;
            }
        }
        return false;
    }

    /** 是否为 VB-CABLE（VB-Audio）虚拟音频端点。 */
    public static boolean isVbCable(DeviceInfo info) {
        if (info == null) {
            return false;
        }
        String text = (info.name() + " " + String.valueOf(info.detail()) + " " + info.mixerName())
                .toLowerCase();
        return text.contains("vb-audio") || text.contains("vb cable") || text.contains("vbcable");
    }

    /** 是否已检测到 VB-CABLE 的播放或录音端点。 */
    public static boolean hasVbCable(List<DeviceInfo> devices) {
        for (DeviceInfo info : devices) {
            if (isVbCable(info)) {
                return true;
            }
        }
        return false;
    }

    static boolean isAudioLine(Line line) {
        return line instanceof SourceDataLine || line instanceof TargetDataLine;
    }

    private static boolean isSupportedPcm(AudioFormat format) {
        if (format.isBigEndian()) {
            return false;
        }
        int bits = format.getSampleSizeInBits();
        if (AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding())) {
            return bits == 32;
        }
        if (AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding())) {
            return bits == 8;
        }
        return AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && (bits == 8 || bits == 16 || bits == 24 || bits == 32);
    }
}
