package com.voicechanger.audio;

import com.voicechanger.logging.LogService;
import com.voicechanger.util.AppPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * C++/WASAPI 原生音频引擎的 JNI 入口。
 * 找不到 VoiceChangerNative.dll 时保持不可用，调用方回退到 Java Sound 实现。
 */
public final class NativeAudio {

    public interface Listener {
        /** 采集线程回调，block 为 48kHz 单声道数据，回调返回后内存会被复用。 */
        void onCaptureBlock(float[] block, int frames);
    }

    private static final String DLL_NAME = "VoiceChangerNative.dll";
    private static String loadError = "";
    private static final boolean AVAILABLE = loadLibrary();

    private NativeAudio() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static String loadError() {
        return loadError;
    }

    private static boolean loadLibrary() {
        List<Path> candidates = new ArrayList<>();
        String explicit = System.getProperty("voicechanger.native");
        if (explicit != null && !explicit.isBlank()) {
            candidates.add(Path.of(explicit));
        }
        Path base = AppPaths.baseDir();
        candidates.add(base.resolve("native").resolve(DLL_NAME));
        Path parent = base.getParent();
        if (parent != null) {
            candidates.add(parent.resolve("native").resolve(DLL_NAME));
        }
        Path workDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        candidates.add(workDir.resolve("native").resolve(DLL_NAME));
        candidates.add(workDir.resolve("build").resolve("native").resolve(DLL_NAME));

        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                System.load(candidate.toString());
                if (nativeProbe()) {
                    LogService.info("原生音频引擎已加载: " + candidate);
                    return true;
                }
                loadError = "原生模块探测失败: " + candidate;
            } catch (Throwable t) {
                loadError = t.getClass().getSimpleName() + ": " + t.getMessage();
                LogService.error("原生音频引擎加载失败: " + candidate, t);
            }
        }
        LogService.warn("未找到可用的 " + DLL_NAME + "，使用 Java Sound 音频后端"
                + (loadError.isBlank() ? "" : "（" + loadError + "）"));
        return false;
    }

    /** 启动原生引擎；成功返回 null，失败返回错误说明。 */
    public static String start(Listener listener, String captureName, String renderName,
                               String monitorName, boolean monitorEnabled, int blockFrames) {
        return nativeStart(listener, captureName, renderName, monitorName, monitorEnabled, blockFrames);
    }

    public static String[] captureDevices() {
        return AVAILABLE ? nativeListDevices(true) : new String[0];
    }

    public static String[] renderDevices() {
        return AVAILABLE ? nativeListDevices(false) : new String[0];
    }

    public static void stop() {
        if (AVAILABLE) {
            nativeStop();
        }
    }

    public static void writeOutput(float[] data, int length) {
        if (AVAILABLE) {
            nativeWriteOutput(data, length);
        }
    }

    public static void writeMonitor(float[] data, int length) {
        if (AVAILABLE) {
            nativeWriteMonitor(data, length);
        }
    }

    /** 采集设备的原始混音格式（如 "48000 Hz / 2 ch / 32bit float"），未启动时为空。 */
    public static String captureInputFormat() {
        return AVAILABLE ? nativeCaptureInputFormat() : "";
    }

    /** 驱动实际打开的采集流格式（含 direct/AUTOCONVERT/engine 标记），未启动时为空。 */
    public static String captureDriverFormat() {
        return AVAILABLE ? nativeCaptureDriverFormat() : "";
    }

    private static native boolean nativeProbe();

    private static native String[] nativeListDevices(boolean capture);

    private static native String nativeStart(Listener listener, String captureName,
                                             String renderName, String monitorName,
                                             boolean monitorEnabled, int blockFrames);

    private static native void nativeStop();

    private static native void nativeWriteOutput(float[] data, int length);

    private static native void nativeWriteMonitor(float[] data, int length);

    private static native String nativeCaptureInputFormat();

    private static native String nativeCaptureDriverFormat();
}
