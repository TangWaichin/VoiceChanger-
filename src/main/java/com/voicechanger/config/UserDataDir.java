package com.voicechanger.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据目录（Windows 为 %APPDATA%\VoiceChanger）：存放存档与默认日志，
 * 避免升级覆盖程序目录时丢失用户数据。程序目录仅用于 native 库与插件。
 */
public final class UserDataDir {

    private UserDataDir() {
    }

    public static Path resolve() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve("VoiceChanger");
    }

    /**
     * 旧版数据保存在程序目录，首次启动复制到数据目录（不删除旧文件，已存在则不覆盖）。
     * 返回迁移过程描述，供调用方写入日志。
     */
    public static List<String> migrateLegacy(Path appDir, Path dataDir) {
        List<String> migrated = new ArrayList<>();
        copyIfAbsent(appDir.resolve(PresetStore.FILE_NAME),
                dataDir.resolve(PresetStore.FILE_NAME), "旧版存档", migrated);
        copyIfAbsent(appDir.resolve(com.voicechanger.logging.LogService.LOG_FILE_NAME),
                dataDir.resolve(com.voicechanger.logging.LogService.LOG_FILE_NAME), "旧版日志", migrated);
        return migrated;
    }

    private static void copyIfAbsent(Path from, Path to, String name, List<String> migrated) {
        if (from.equals(to) || !Files.isRegularFile(from) || Files.exists(to)) {
            return;
        }
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to);
            migrated.add(name + "已迁移: " + from + " -> " + to);
        } catch (IOException e) {
            migrated.add(name + "迁移失败: " + e.getMessage());
        }
    }
}
