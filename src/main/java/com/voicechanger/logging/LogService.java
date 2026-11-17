package com.voicechanger.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;

/**
 * 日志服务：日志目录可自定义，未自定义时使用用户数据目录。
 * 调用方只把日志行放入内存队列，由后台线程批量落盘，避免错误风暴或高频脚本输出引发磁盘 IO 抖动。
 */
public final class LogService {

    public static final String LOG_FILE_NAME = "voice-changer.log";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final long FLUSH_INTERVAL_MS = 200L;
    /** 队列上限：超出后丢弃最旧的行并计数，保证内存占用可控。 */
    private static final int MAX_PENDING_LINES = 4096;
    /** 无控制台（javaw 启动）时跳过 System.out，减少无意义的同步输出。 */
    private static final boolean CONSOLE_OUT = System.console() != null;

    private static Path logFile;
    private static BufferedWriter writer;
    private static final ArrayDeque<String> pending = new ArrayDeque<>();
    private static long droppedLines;
    private static volatile Thread writerThread;

    private LogService() {
    }

    /**
     * 启动时调用：确保日志文件存在并打开写入器。
     *
     * @param customDirectory 自定义目录，null/空表示使用默认数据目录
     * @return 实际使用的日志文件路径
     */
    public static synchronized Path initialize(Path appDir, String customDirectory) {
        Path dir = resolveDirectory(appDir, customDirectory);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("无法创建日志目录 " + dir + "，回退到默认目录: " + e.getMessage());
            dir = appDir;
            try {
                Files.createDirectories(dir);
            } catch (IOException ignored) {
                // 仍然失败则只能输出到控制台
                closeWriter();
                logFile = null;
                return null;
            }
        }
        Path file = dir.resolve(LOG_FILE_NAME);
        closeWriter();
        logFile = file;
        try {
            boolean created = !Files.exists(file);
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            startFlusher();
            if (created) {
                append("日志文件已创建: " + file);
                flush();
            }
        } catch (IOException e) {
            System.err.println("无法创建日志文件 " + file + ": " + e.getMessage());
            closeWriter();
            logFile = null;
        }
        return logFile;
    }

    private static Path resolveDirectory(Path appDir, String customDirectory) {
        if (customDirectory == null || customDirectory.isBlank()) {
            return appDir;
        }
        try {
            Path dir = Paths.get(customDirectory.trim());
            if (Files.isRegularFile(dir)) {
                dir = dir.getParent();
            }
            return dir == null ? appDir : dir.toAbsolutePath().normalize();
        } catch (Exception e) {
            System.err.println("日志路径无效，使用默认目录: " + e.getMessage());
            return appDir;
        }
    }

    public static synchronized Path currentFile() {
        return logFile;
    }

    public static void info(String message) {
        append("[INFO ] " + message);
    }

    public static void warn(String message) {
        append("[WARN ] " + message);
    }

    public static void error(String message) {
        append("[ERROR] " + message);
    }

    public static void error(String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder("[ERROR] ").append(message);
        if (throwable != null) {
            sb.append(" :: ").append(throwable.getClass().getSimpleName())
                    .append(": ").append(throwable.getMessage());
        }
        append(sb.toString());
        if (throwable != null) {
            for (StackTraceElement element : throwable.getStackTrace()) {
                append("        at " + element);
            }
        }
    }

    /** 把队列中剩余的日志落盘；退出钩子与关闭日志时调用。 */
    public static synchronized void flush() {
        drainAndFlush();
    }

    /** 后台线程按固定间隔把队列批量写入文件并 flush，保证日志延迟不超过一个周期。 */
    private static synchronized void startFlusher() {
        stopFlusher();
        // 用线程身份判断存活：旧线程被 interrupt 后即使醒来，也不会因为新线程存在而继续运行
        Thread thread = new Thread(() -> {
            while (writerThread == Thread.currentThread()) {
                try {
                    Thread.sleep(FLUSH_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                drainAndFlush();
            }
        }, "log-writer");
        thread.setDaemon(true);
        writerThread = thread;
        thread.start();
    }

    private static void stopFlusher() {
        Thread current = writerThread;
        writerThread = null;
        if (current != null) {
            current.interrupt();
        }
    }

    private static synchronized void drainAndFlush() {
        if (writer == null) {
            pending.clear();
            droppedLines = 0;
            return;
        }
        try {
            while (!pending.isEmpty()) {
                writer.write(pending.pollFirst());
            }
            if (droppedLines > 0) {
                writer.write(LocalDateTime.now().format(STAMP) + " [WARN ] 日志输出过快，已丢弃 "
                        + droppedLines + " 行" + System.lineSeparator());
                droppedLines = 0;
            }
            writer.flush();
        } catch (IOException e) {
            System.err.println("写日志失败: " + e.getMessage());
        }
    }

    private static synchronized void closeWriter() {
        stopFlusher();
        if (writer != null) {
            drainAndFlush();
            try {
                writer.close();
            } catch (IOException ignored) {
                // 关闭失败仅影响日志，不影响主流程
            }
            writer = null;
        }
        pending.clear();
        droppedLines = 0;
    }

    private static synchronized void append(String message) {
        String line = LocalDateTime.now().format(STAMP) + " " + message + System.lineSeparator();
        if (CONSOLE_OUT) {
            System.out.print(line);
        }
        if (writer == null) {
            return;
        }
        if (pending.size() >= MAX_PENDING_LINES) {
            // 队列满时丢弃最旧的行并计数，避免阻塞音频/界面线程
            pending.pollFirst();
            droppedLines++;
        }
        pending.addLast(line);
    }
}
