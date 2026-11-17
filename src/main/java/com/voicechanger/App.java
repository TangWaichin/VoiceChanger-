package com.voicechanger;

import com.voicechanger.audio.AudioEngine;
import com.voicechanger.audio.DeviceInfo;
import com.voicechanger.audio.DeviceManager;
import com.voicechanger.config.AppSettings;
import com.voicechanger.config.PresetStore;
import com.voicechanger.dsp.ProcessorCatalog;
import com.voicechanger.logging.LogService;
import com.voicechanger.ui.MainWindow;
import com.voicechanger.util.AppPaths;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;
import java.util.List;

/**
 * 程序入口：先根据存档确定日志位置，再初始化模块与界面。
 */
public final class App {

    public static final String VERSION = "1.0.0.0";

    private App() {
    }

    public static void main(String[] args) {
        Path appDir = AppPaths.baseDir();
        // 存档/日志放用户数据目录；旧版程序目录数据首次启动自动迁移（不删除旧文件）
        Path dataDir = com.voicechanger.config.UserDataDir.resolve();
        java.util.List<String> migrated = com.voicechanger.config.UserDataDir.migrateLegacy(appDir, dataDir);
        LogService.initialize(dataDir, "");
        LogService.info("==== 实时变声器 v" + VERSION + " 启动 ====");
        LogService.info("软件目录: " + appDir);
        LogService.info("数据目录: " + dataDir);
        for (String message : migrated) {
            LogService.info(message);
        }
        LogService.info("Java 版本: " + System.getProperty("java.version")
                + " / " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                LogService.error("未捕获异常 [" + thread.getName() + "]", throwable));

        ProcessorCatalog catalog = ProcessorCatalog.load(appDir);
        AppSettings settings = new AppSettings(catalog);
        PresetStore preset = new PresetStore(dataDir);
        boolean loaded = preset.load(settings);
        com.voicechanger.util.I18n.setLanguage(settings.language());

        LogService.initialize(dataDir, settings.logDirectory());
        LogService.info("日志文件: " + LogService.currentFile());
        if (loaded) {
            LogService.info("已按存档恢复：信号源=" + fallback(settings.sourceDeviceName())
                    + "，输出=" + fallback(settings.outputDeviceName())
                    + "，耳返=" + (settings.monitorEnabled() ? "开启" : "关闭")
                    + "，耳返音量=" + Math.round(settings.monitorVolume() * 100) + "%");
        } else {
            preset.save(settings);
            LogService.info("首次运行已创建默认存档: " + preset.file());
        }

        List<DeviceInfo> inputs = DeviceManager.inputDevices();
        List<DeviceInfo> outputs = DeviceManager.outputDevices();
        LogService.info("检测到录音设备 " + inputs.size() + " 个，播放设备 " + outputs.size() + " 个");
        for (DeviceInfo info : inputs) {
            LogService.info("  [录音] " + info.label());
        }
        for (DeviceInfo info : outputs) {
            LogService.info("  [播放] " + info.label());
        }
        if (!DeviceManager.hasVirtualCable(outputs)) {
            LogService.warn("未检测到虚拟声卡，需安装 VB-CABLE/VoiceMeeter 才能把变声结果作为麦克风使用");
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LogService.warn("外观初始化失败，使用默认外观: " + e.getMessage());
        }
        MainWindow.applyGlobalStyle();

        AudioEngine engine = new AudioEngine(catalog, settings.states());
        Runtime.getRuntime().addShutdownHook(new Thread(engine::close, "engine-shutdown"));
        Runtime.getRuntime().addShutdownHook(new Thread(LogService::flush, "log-flush"));
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow(settings, preset, engine, dataDir);
            window.setVisible(true);
        });
    }

    private static String fallback(String text) {
        return text == null || text.isBlank() ? "未设置" : text;
    }
}
