package com.voicechanger.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 解析“软件所在位置的文件夹”。存档、默认日志都放在这里。
 */
public final class AppPaths {

    private AppPaths() {
    }

    public static Path baseDir() {
        try {
            var source = AppPaths.class.getProtectionDomain().getCodeSource();
            if (source != null) {
                Path location = Paths.get(source.getLocation().toURI());
                if (Files.isRegularFile(location)) {
                    return location.toAbsolutePath().normalize().getParent();
                }
            }
        } catch (Exception ignored) {
            // 回退到工作目录
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
}
