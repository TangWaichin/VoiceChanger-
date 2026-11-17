package com.voicechanger.ui;

import com.voicechanger.audio.CustomAudioBridge;
import com.voicechanger.logging.LogService;
import com.voicechanger.util.I18n;
import com.voicechanger.util.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 自定义调音接口面板：API 说明与复制、自定义调音文件导入、文件列表与后台运行。
 * 仅在“自定义接口”模式下显示，用于绕过音频调节模块在外部程序中处理音频。
 */
public final class CustomInterfacePanel extends JPanel {

    private static final String MIC_API_CODE = "import socket\n"
            + "mic = socket.create_connection((\"127.0.0.1\", " + CustomAudioBridge.MIC_API_PORT + "))\n"
            + "block = mic.recv(960)   # 480 frames, 48kHz mono PCM16LE";

    private static final String VIRTUAL_MIC_API_CODE = "import socket\n"
            + "out = socket.create_connection((\"127.0.0.1\", " + CustomAudioBridge.VIRTUAL_MIC_API_PORT + "))\n"
            + "out.sendall(pcm)        # 480 frames, 48kHz mono PCM16LE";

    /** 自定义脚本每秒最多写入的日志行数。 */
    private static final int MAX_LOG_LINES_PER_SECOND = 200;
    /** 自定义调音文件列表自动刷新间隔（秒）。 */
    private static final int REFRESH_INTERVAL_SECONDS = 5;

    private final Path customDir;
    private final JPanel fileList = new JPanel();
    private final JLabel statusLabel = Ui.label(" ", 10.5f, Ui.MUTED);
    private final JLabel refreshHint = Ui.label(I18n.t("每 5 秒自动刷新"), 10f, Ui.MUTED);
    private final List<JLabel> i18nLabels = new ArrayList<>();
    private final List<String> i18nKeys = new ArrayList<>();
    private final JButton importButton = new PillButton("导入自定义调音文件", false);
    private final JButton openFolderButton = new PillButton("前往文件存放地址", false);

    private Process activeProcess;
    private Path activeFile;
    /** 上一次列表内容的文件签名，用于自动刷新时判断是否有变化。 */
    private String lastSignature = "";
    private Timer autoRefreshTimer;

    public CustomInterfacePanel(Path dataDir) {
        this.customDir = dataDir.resolve("custom");
        setOpaque(false);
        setLayout(new BorderLayout(0, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);

        JPanel apiArea = new JPanel(new GridLayout(2, 1, 0, 8));
        apiArea.setOpaque(false);
        apiArea.setAlignmentX(LEFT_ALIGNMENT);
        apiArea.add(buildApiCard("实体麦克风api接口", MIC_API_CODE));
        apiArea.add(buildApiCard("虚拟麦克风api接口", VIRTUAL_MIC_API_CODE));
        top.add(apiArea);
        top.add(Box.createVerticalStrut(12));

        JPanel importRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        importRow.setOpaque(false);
        importRow.setAlignmentX(LEFT_ALIGNMENT);
        styleSecondaryButton(importButton);
        importButton.addActionListener(e -> importFile());
        styleSecondaryButton(openFolderButton);
        openFolderButton.addActionListener(e -> openCustomFolder());
        importRow.add(importButton);
        importRow.add(openFolderButton);
        top.add(importRow);

        add(top, BorderLayout.NORTH);
        add(buildListArea(), BorderLayout.CENTER);
        refreshFiles();
        // 面板可见时每 5 秒检查一次目录内容，有变化才重建列表
        autoRefreshTimer = new Timer(REFRESH_INTERVAL_SECONDS * 1000, e -> {
            if (isShowing()) {
                refreshIfChanged();
            }
        });
        autoRefreshTimer.setCoalesce(true);
        autoRefreshTimer.start();
    }

    private static void styleSecondaryButton(JButton button) {
        button.setFont(Ui.font(11f, Font.BOLD));
        button.setBorder(Ui.padding(8, 14, 8, 14));
    }

    /** API 说明卡片：左侧标题，右侧“一键复制api”，下方为正式 API 代码。 */
    private JPanel buildApiCard(String titleKey, String code) {
        CardPanel card = new CardPanel(14);
        card.setLayout(new BorderLayout(10, 6));
        card.setBorder(Ui.padding(10, 14, 10, 12));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = translatable(titleKey, 11f, Ui.TEXT, Font.BOLD);
        header.add(title, BorderLayout.WEST);
        JButton copy = new PillButton("一键复制api", false);
        copy.setFont(Ui.font(10f, Font.BOLD));
        copy.setBorder(Ui.padding(5, 12, 5, 12));
        copy.addActionListener(e -> copyApi(code));
        header.add(copy, BorderLayout.EAST);

        JTextArea codeArea = new JTextArea(code);
        codeArea.setEditable(false);
        codeArea.setFocusable(false);
        codeArea.setLineWrap(false);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10).deriveFont(10.5f));
        codeArea.setForeground(new Color(0x4A4A4A));
        codeArea.setBackground(new Color(0xFAFAFA));
        codeArea.setBorder(Ui.padding(6, 8, 6, 8));

        card.add(header, BorderLayout.NORTH);
        card.add(codeArea, BorderLayout.CENTER);
        return card;
    }

    /** 自定义调音文件列表区：文件名左侧“选择”按钮，同时只激活一个文件。 */
    private JPanel buildListArea() {
        CardPanel card = new CardPanel(16);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(Ui.padding(14, 16, 14, 16));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(translatable("自定义调音文件", 11.5f, Ui.MUTED, Font.BOLD), BorderLayout.WEST);
        JPanel headerInfo = new JPanel();
        headerInfo.setLayout(new BoxLayout(headerInfo, BoxLayout.Y_AXIS));
        headerInfo.setOpaque(false);
        refreshHint.setAlignmentX(RIGHT_ALIGNMENT);
        statusLabel.setAlignmentX(RIGHT_ALIGNMENT);
        headerInfo.add(refreshHint);
        headerInfo.add(statusLabel);
        header.add(headerInfo, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        fileList.setLayout(new BoxLayout(fileList, BoxLayout.Y_AXIS));
        fileList.setOpaque(false);
        fileList.setBorder(Ui.padding(2, 0, 2, 0));

        JScrollPane scroll = new JScrollPane(fileList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    /** 重新扫描自定义调音文件目录并刷新列表。 */
    public void refreshFiles() {
        List<Path> files = listCustomFiles();
        lastSignature = signature(files);
        rebuild(files);
    }

    /** 自动刷新：只有目录内容（文件名/大小/修改时间）变化时才重建列表。 */
    private void refreshIfChanged() {
        List<Path> files = listCustomFiles();
        String signature = signature(files);
        if (signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;
        stopActiveFileIfMissing(files);
        rebuild(files);
    }

    /** 运行中的文件被删除/改名后停止脚本，避免继续运行一个已不存在的入口。 */
    private void stopActiveFileIfMissing(List<Path> files) {
        Path active = activeFile;
        if (active == null || files.contains(active)) {
            return;
        }
        LogService.warn("正在运行的自定义调音文件已被移除: " + active);
        stopActiveFile();
        setStatus(I18n.t("自定义调音文件已被移除，已停止运行: ") + active.getFileName());
    }

    private void rebuild(List<Path> files) {
        fileList.removeAll();
        if (files.isEmpty()) {
            JLabel empty = Ui.label(I18n.t("没有可用的自定义调音文件"), 11f, Ui.MUTED);
            empty.setAlignmentX(LEFT_ALIGNMENT);
            fileList.add(Box.createVerticalStrut(2));
            fileList.add(empty);
        } else {
            for (Path file : files) {
                fileList.add(buildFileRow(file));
                fileList.add(Box.createVerticalStrut(6));
            }
        }
        fileList.revalidate();
        fileList.repaint();
    }

    /** 文件签名：名称 + 大小 + 修改时间，任一项变化都会触发刷新。 */
    private static String signature(List<Path> files) {
        StringBuilder builder = new StringBuilder();
        for (Path file : files) {
            builder.append(file.getFileName()).append('|');
            try {
                builder.append(Files.size(file)).append('|')
                        .append(Files.getLastModifiedTime(file).toMillis());
            } catch (IOException e) {
                builder.append('?');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private List<Path> listCustomFiles() {
        if (!Files.isDirectory(customDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(customDir)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            LogService.warn("读取自定义调音文件目录失败: " + e.getMessage());
            return List.of();
        }
    }

    private JPanel buildFileRow(Path file) {
        boolean active = activeFile != null && activeFile.equals(file);
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        PillButton select = new PillButton(I18n.t(active ? "运行中" : "选择"), active);
        select.setFont(Ui.font(10.5f, Font.BOLD));
        select.setBorder(Ui.padding(5, 14, 5, 14));
        select.addActionListener(e -> selectFile(file));

        JLabel name = Ui.label(file.getFileName().toString(), 11.5f, active ? Ui.TEXT : new Color(0x4A4A4A),
                active ? Font.BOLD : Font.PLAIN);
        name.setToolTipText(file.toString());

        row.add(select, BorderLayout.WEST);
        row.add(name, BorderLayout.CENTER);
        return row;
    }

    private void copyApi(String code) {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(code), null);
        setStatus(I18n.t("api已复制到剪贴板"));
        LogService.info("已复制自定义调音接口 API 代码");
    }

    private void importFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t("导入自定义调音文件"));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        try {
            Files.createDirectories(customDir);
            Path target = customDir.resolve(source.getFileName().toString());
            if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            LogService.info("自定义调音文件已导入: " + source + " -> " + target);
            refreshFiles();
            setStatus(I18n.t("自定义调音文件已导入: ") + target.getFileName());
        } catch (IOException e) {
            LogService.error("自定义调音文件导入失败", e);
            setStatus(I18n.t("自定义调音文件导入失败: ") + e.getMessage());
        }
    }

    private void openCustomFolder() {
        try {
            Files.createDirectories(customDir);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(customDir.toFile());
            }
        } catch (IOException e) {
            LogService.error("打开自定义调音文件目录失败", e);
            setStatus(I18n.t("文件资源管理器打开失败: ") + e.getMessage());
        }
    }

    /** 激活并后台运行选中的自定义调音文件；切换文件时先停止上一个。 */
    private void selectFile(Path file) {
        stopActiveFile();
        activeFile = file;
        try {
            activeProcess = startProcess(file);
            pipeProcessOutput(activeProcess.getInputStream(), file.getFileName().toString());
            watchProcess(activeProcess, file);
            LogService.info("已启动自定义调音文件: " + file);
            refreshFiles();
            setStatus(I18n.t("运行中：") + file.getFileName());
        } catch (IOException e) {
            LogService.error("自定义调音文件启动失败: " + file, e);
            activeProcess = null;
            activeFile = null;
            refreshFiles();
            setStatus(I18n.t("自定义调音文件启动失败: ") + file.getFileName());
        }
    }

    private Process startProcess(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        ProcessBuilder builder;
        if (name.endsWith(".py")) {
            builder = prepare(new ProcessBuilder("python", file.getFileName().toString()));
            try {
                return builder.start();
            } catch (IOException first) {
                return prepare(new ProcessBuilder("py", file.getFileName().toString())).start();
            }
        }
        if (name.endsWith(".bat") || name.endsWith(".cmd")) {
            // call + 引号形式可兼容含空格的文件名，并让脚本退出码原样传回
            builder = new ProcessBuilder("cmd", "/c", "call \"" + file.getFileName() + "\"");
        } else if (name.endsWith(".jar")) {
            builder = new ProcessBuilder("java", "-jar", file.getFileName().toString());
        } else {
            builder = new ProcessBuilder(file.getFileName().toString());
        }
        return prepare(builder).start();
    }

    private ProcessBuilder prepare(ProcessBuilder builder) {
        builder.directory(customDir.toFile());
        builder.redirectErrorStream(true);
        return builder;
    }

    /** 监听脚本进程退出：自行结束或崩溃时把列表状态从“运行中”恢复为“选择”。 */
    private void watchProcess(Process process, Path file) {
        Thread thread = new Thread(() -> {
            try {
                int code = process.waitFor();
                SwingUtilities.invokeLater(() -> {
                    if (activeProcess != process) {
                        return;
                    }
                    activeProcess = null;
                    activeFile = null;
                    refreshFiles();
                    setStatus(I18n.t("自定义调音文件已停止: ") + file.getFileName());
                    LogService.info("自定义调音文件已退出（退出码 " + code + "）: " + file);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "custom-tuning-watch");
        thread.setDaemon(true);
        thread.start();
    }

    private void pipeProcessOutput(InputStream stream, String fileName) {
        Thread thread = new Thread(() -> {
            long windowStart = System.nanoTime();
            int logged = 0;
            int dropped = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, scriptCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    long now = System.nanoTime();
                    if (now - windowStart >= 1_000_000_000L) {
                        if (dropped > 0) {
                            LogService.warn("[自定义调音] " + fileName + ": 输出过快，已丢弃 " + dropped + " 行");
                        }
                        windowStart = now;
                        logged = 0;
                        dropped = 0;
                    }
                    // 限速：每个脚本每秒最多写入 200 行日志，避免高频输出放大磁盘 IO
                    if (logged < MAX_LOG_LINES_PER_SECOND) {
                        LogService.info("[自定义调音] " + fileName + ": " + line);
                        logged++;
                    } else {
                        dropped++;
                    }
                }
            } catch (IOException ignored) {
                // 进程结束时流会关闭
            }
        }, "custom-tuning-log");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 自定义脚本输出编码：Windows 下 cmd / python 按控制台代码页（中文系统 GBK）输出，
     * 固定 UTF-8 会把中文日志读成乱码；优先使用 JDK 提供的 native.encoding。
     */
    private static Charset scriptCharset() {
        String name = System.getProperty("native.encoding");
        if (name != null && !name.isBlank()) {
            try {
                return Charset.forName(name);
            } catch (Exception ignored) {
                // 无法识别时退回默认字符集
            }
        }
        return Charset.defaultCharset();
    }

    /** 停止当前运行的自定义调音文件（切回默认面板或关闭软件时调用），连同其子进程一起终止。 */
    public void stopActiveFile() {
        Process process = activeProcess;
        activeProcess = null;
        if (process != null) {
            LogService.info("已停止自定义调音文件: " + activeFile);
            // 先记录再终止：父进程退出后子进程会被重新挂载，无法再通过 descendants 找到
            List<ProcessHandle> children = process.descendants().toList();
            children.forEach(ProcessHandle::destroy);
            process.destroy();
            terminateAsync(process, children);
        }
        if (activeFile != null) {
            activeFile = null;
            refreshFiles();
            setStatus(I18n.t("尚未选择自定义调音文件"));
        }
    }

    /** 同步停止当前运行的自定义调音文件，供退出流程调用，确保脚本与子进程不会残留。 */
    public void stopActiveFileAndWait() {
        Process process = activeProcess;
        activeProcess = null;
        if (process != null) {
            LogService.info("正在停止自定义调音文件: " + activeFile);
            List<ProcessHandle> children = process.descendants().toList();
            children.forEach(ProcessHandle::destroy);
            process.destroy();
            try {
                if (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            for (ProcessHandle child : children) {
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
            }
        }
        activeFile = null;
    }

    /** 后台等待优雅退出，超时或仍有残留子进程时强制终止，避免阻塞界面线程。 */
    private static void terminateAsync(Process process, List<ProcessHandle> children) {
        Thread thread = new Thread(() -> {
            try {
                if (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            } finally {
                for (ProcessHandle child : children) {
                    if (child.isAlive()) {
                        child.destroyForcibly();
                    }
                }
            }
        }, "custom-tuning-stop");
        thread.setDaemon(true);
        thread.start();
    }

    private void setStatus(String text) {
        statusLabel.setText(text == null || text.isBlank() ? " " : text);
    }

    /** 语言切换：静态文案与列表行（选择/运行中）一起刷新。 */
    public void applyLanguage() {
        for (int i = 0; i < i18nLabels.size(); i++) {
            i18nLabels.get(i).setText(I18n.t(i18nKeys.get(i)));
        }
        importButton.setText(I18n.t("导入自定义调音文件"));
        openFolderButton.setText(I18n.t("前往文件存放地址"));
        refreshHint.setText(I18n.t("每 5 秒自动刷新"));
        refreshFiles();
        if (activeFile != null) {
            setStatus(I18n.t("运行中：") + activeFile.getFileName());
        } else {
            setStatus(I18n.t("尚未选择自定义调音文件"));
        }
    }

    private JLabel translatable(String zhText, float size, Color color, int style) {
        JLabel label = Ui.label(I18n.t(zhText), size, color, style);
        i18nLabels.add(label);
        i18nKeys.add(zhText);
        return label;
    }
}
