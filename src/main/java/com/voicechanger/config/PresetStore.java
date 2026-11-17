package com.voicechanger.config;

import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;
import com.voicechanger.logging.LogService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;

/**
 * 存档：软件所在目录下的 voice-changer.preset，保存所有调音参数与设备设置。
 */
public final class PresetStore {

    public static final String FILE_NAME = "voice-changer.preset";
    private static final String VERSION = "1";

    private final Path file;

    public PresetStore(Path appDir) {
        this.file = appDir.resolve(FILE_NAME);
    }

    public Path file() {
        return file;
    }

    public boolean exists() {
        return Files.isRegularFile(file);
    }

    /** 读取存档并套用到设置中；返回是否读取成功。 */
    public boolean load(AppSettings settings) {
        if (!exists()) {
            LogService.info("未找到存档，使用默认设置: " + file);
            return false;
        }
        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            LogService.error("读取存档失败: " + file, e);
            return false;
        }
        String version = properties.getProperty("version", VERSION);
        if (!VERSION.equals(version)) {
            LogService.warn("存档版本为 " + version + "，将按当前版本兼容读取");
        }
        settings.setLogDirectory(properties.getProperty("log.dir", ""));
        settings.setSourceDeviceName(properties.getProperty("device.source", ""));
        settings.setOutputDeviceName(properties.getProperty("device.output", ""));
        settings.setMonitorDeviceName(properties.getProperty("device.monitor", ""));
        settings.setMonitorEnabled(Boolean.parseBoolean(properties.getProperty("monitor.enabled", "false")));
        settings.setMonitorVolume(parseFloat(properties.getProperty("monitor.volume"), settings.monitorVolume()));
        settings.setLanguage(properties.getProperty("ui.language", settings.language()));
        int restored = 0;
        for (ProcessorState state : settings.states()) {
            String id = state.processorId();
            String enabledKey = "proc." + id + ".enabled";
            if (properties.containsKey(enabledKey)) {
                state.setEnabled(Boolean.parseBoolean(properties.getProperty(enabledKey)));
            }
            for (ParamSpec spec : settings.specsOf(id)) {
                String key = "proc." + id + "." + spec.id();
                if (properties.containsKey(key)) {
                    state.setValue(spec, parseFloat(properties.getProperty(key), spec.defaultValue()));
                    restored++;
                }
            }
        }
        String storedCode = properties.getProperty("tuning.code", "");
        LogService.info("已加载存档: " + file + "（恢复参数 " + restored + " 项，日志目录="
                + (settings.logDirectory().isBlank() ? "默认目录" : settings.logDirectory())
                + "，耳返=" + (settings.monitorEnabled() ? "开启" : "关闭") + "）"
                + (storedCode.isEmpty() ? "" : "，存档调音编码=" + abbreviate(storedCode)));
        return true;
    }

    /** 日志中只显示编码前缀，完整编码以存档 tuning.code 字段为准。 */
    private static String abbreviate(String code) {
        return code.length() <= 24 ? code : code.substring(0, 16) + "…";
    }

    public void save(AppSettings settings) {
        Properties properties = new Properties();
        properties.setProperty("version", VERSION);
        properties.setProperty("log.dir", settings.logDirectory());
        properties.setProperty("device.source", settings.sourceDeviceName());
        properties.setProperty("device.output", settings.outputDeviceName());
        properties.setProperty("device.monitor", settings.monitorDeviceName());
        properties.setProperty("monitor.enabled", Boolean.toString(settings.monitorEnabled()));
        properties.setProperty("monitor.volume", Float.toString(settings.monitorVolume()));
        properties.setProperty("ui.language", settings.language());
        // 同步保存调音编码，便于分享与核对；加载时仍以分项字段为准
        String tuningCode = TuningCodec.encode(settings);
        properties.setProperty("tuning.code", tuningCode);
        for (ProcessorState state : settings.states()) {
            String id = state.processorId();
            properties.setProperty("proc." + id + ".enabled", Boolean.toString(state.enabled()));
            for (Map.Entry<String, Float> entry : state.values().entrySet()) {
                properties.setProperty("proc." + id + "." + entry.getKey(), Float.toString(entry.getValue()));
            }
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (java.io.Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            properties.store(writer, "Voice Changer preset");
        } catch (IOException e) {
            LogService.error("写入存档失败: " + file, e);
            return;
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            LogService.info("存档已保存: " + file + "（调音编码 " + abbreviate(tuningCode) + "）");
        } catch (IOException e) {
            LogService.error("存档替换失败: " + file, e);
        }
    }

    private static float parseFloat(String text, float fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String readPreview(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
