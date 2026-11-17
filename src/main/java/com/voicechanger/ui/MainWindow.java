package com.voicechanger.ui;

import com.voicechanger.audio.AudioEngine;
import com.voicechanger.audio.DeviceInfo;
import com.voicechanger.audio.DeviceManager;
import com.voicechanger.config.AppSettings;
import com.voicechanger.config.PresetStore;
import com.voicechanger.dsp.ProcessorCatalog;
import com.voicechanger.dsp.ProcessorState;
import com.voicechanger.logging.LogService;
import com.voicechanger.util.I18n;
import com.voicechanger.util.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 主界面：左侧三分之二为调音选项，右上角为设置按钮。
 */
public final class MainWindow extends JFrame {

    /** 官网地址：点击右上角“官网”按钮后用系统默认浏览器打开。 */
    private static final String WEBSITE_URL = "http://tang-waichin-test.waichin.work/voicechanger/";

    private final AppSettings settings;
    private final PresetStore preset;
    private final AudioEngine engine;
    private final Path appDir;

    private final WebsiteButton websiteButton = new WebsiteButton();
    private final GearButton gearButton = new GearButton();
    private final GlobeButton globeButton = new GlobeButton();
    private final JLabel statusText = Ui.label("未启动", 18f, Ui.TEXT, Font.BOLD);
    private final JLabel statusDetail = Ui.label("选择虚拟麦克风后点击右侧按钮开始", 11.5f, Ui.MUTED);
    private final JLabel outputName = Ui.label("—", 11.5f, Ui.TEXT);
    private final LevelMeter inputMeter = new LevelMeter();
    private final LevelMeter outputMeter = new LevelMeter();
    private final LevelMeter monitorMeter = new LevelMeter();
    private final PillButton startButton = new PillButton("开始变声", true);
    private final PillButton resetDefaultsButton = new PillButton("一键恢复默认数值", false);
    private final PressInvertButton modeToggleButton = new PressInvertButton("切换自定义接口");
    private final java.util.List<JLabel> i18nLabels = new ArrayList<>();
    private final java.util.List<String> i18nKeys = new ArrayList<>();
    private JLabel pathsLabel;
    private SettingsMenu settingsMenu;
    private LanguageMenu languageMenu;
    private final java.util.List<ProcessorCard> processorCards = new java.util.ArrayList<>();
    private JScrollPane tuningScroll;
    private JLabel tuningTitle;
    private JLabel tuningHint;
    private JPanel centerCards;
    private java.awt.CardLayout centerCardsLayout;
    private CustomInterfacePanel customPanel;
    private boolean customMode;
    private Timer saveTimer;
    private String runningSignature = "";
    private boolean runningMonitor;
    private String statusKey = "未启动";
    private String detailKey = "选择虚拟麦克风后点击右侧按钮开始";

    public MainWindow(AppSettings settings, PresetStore preset, AudioEngine engine, Path appDir) {
        super("实时变声器 · Voice Changer");
        setIconImages(loadIcons());
        this.settings = settings;
        this.preset = preset;
        this.engine = engine;
        this.appDir = appDir;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setMinimumSize(new Dimension(980, 620));
        setSize(Math.min(1360, screen.width - 60), Math.min(880, screen.height - 60));
        setLocationRelativeTo(null);
        websiteButton.onClick(this::openWebsite);
        setContentPane(buildRoot());
        buildSettingsMenu();
        buildLanguageMenu();
        bindEvents();
        startMeterTimer();
        // 采集线程异常（如设备被拔出）时回到未启动状态，避免界面停留在“运行中”
        engine.setCaptureErrorHandler(() -> SwingUtilities.invokeLater(this::onCaptureLost));
        I18n.addListener(this::applyLanguage);
        applyLanguage();
        LogService.info("主界面已就绪，数据目录: " + appDir);
    }

    /** 加载窗口/任务栏图标（多尺寸，Windows 自动选用）。 */
    private static java.util.List<java.awt.Image> loadIcons() {
        java.util.List<java.awt.Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 32, 48, 256}) {
            java.net.URL url = MainWindow.class.getResource("/icon-" + size + ".png");
            if (url != null) {
                icons.add(new javax.swing.ImageIcon(url).getImage());
            }
        }
        return icons;
    }

    /** 登记可翻译标签，applyLanguage 时统一刷新。 */
    private JLabel translatable(String zhText, float size, Color color, int style) {
        JLabel label = Ui.label(I18n.t(zhText), size, color, style);
        i18nLabels.add(label);
        i18nKeys.add(zhText);
        return label;
    }

    private JLabel translatable(String zhText, float size, Color color) {
        return translatable(zhText, size, color, Font.PLAIN);
    }

    /** 语言切换：窗口标题 + 全部静态/动态文本。 */
    public void applyLanguage() {
        setTitle(I18n.isEnglish() ? "RealtimeVoiceChanger" : "实时麦克风变声");
        for (int i = 0; i < i18nLabels.size(); i++) {
            i18nLabels.get(i).setText(I18n.t(i18nKeys.get(i)));
        }
        statusText.setText(I18n.t(statusKey));
        statusDetail.setText(engine.isRunning()
                ? I18n.t("已输出到 ") + outputName.getText() : I18n.t(detailKey));
        startButton.setText(I18n.t(engine.isRunning() ? "停止变声" : "开始变声"));
        resetDefaultsButton.setText(I18n.t("一键恢复默认数值"));
        if (tuningTitle != null) {
            updateModeUi();
        }
        if (customPanel != null) {
            customPanel.applyLanguage();
        }
        if (pathsLabel != null) {
            pathsLabel.setText("<html>" + I18n.t("日志：") + escape(LogService.currentFile())
                    + "<br>" + I18n.t("存档：") + escape(preset.file()) + "</html>");
        }
        if (settingsMenu != null) {
            settingsMenu.applyLanguage();
        }
        if (hintsArea != null) {
            hintsArea.setText(I18n.t(HINTS_ZH));
        }
        updateFormatInfo();
        revalidate();
        repaint();
    }

    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(Ui.BG);
        root.setBorder(Ui.padding(22, 26, 22, 26));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        JLabel title = translatable("实时变声器", 22f, Ui.TEXT, Font.BOLD);
        JLabel subtitle = translatable("麦克风拦截 · 实时调音 · 虚拟麦克风输出", 11.5f, Ui.MUTED);
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(Box.createVerticalStrut(3));
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        JPanel websiteGroup = new JPanel(new BorderLayout(10, 0));
        websiteGroup.setOpaque(false);
        websiteGroup.add(translatable("官网", 11.5f, Ui.MUTED), BorderLayout.WEST);
        websiteGroup.add(websiteButton, BorderLayout.EAST);

        JPanel languageGroup = new JPanel(new BorderLayout(10, 0));
        languageGroup.setOpaque(false);
        languageGroup.add(translatable("语言", 11.5f, Ui.MUTED), BorderLayout.WEST);
        languageGroup.add(globeButton, BorderLayout.EAST);

        JPanel settingsGroup = new JPanel(new BorderLayout(10, 0));
        settingsGroup.setOpaque(false);
        settingsGroup.add(translatable("设置", 11.5f, Ui.MUTED), BorderLayout.WEST);
        settingsGroup.add(gearButton, BorderLayout.EAST);

        JPanel wrapper = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        wrapper.setOpaque(false);
        wrapper.add(websiteGroup);
        wrapper.add(languageGroup);
        wrapper.add(settingsGroup);
        header.add(wrapper, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(null) {
            @Override
            public void doLayout() {
                int width = getWidth();
                int height = getHeight();
                int gap = 18;
                int leftWidth = Math.max(320, Math.round((width - gap) * 0.6667f));
                if (getComponentCount() == 2) {
                    getComponent(0).setBounds(0, 0, leftWidth, height);
                    getComponent(1).setBounds(leftWidth + gap, 0, width - gap - leftWidth, height);
                }
            }
        };
        body.setOpaque(false);
        body.add(buildTuningArea());
        body.add(buildSidePanel());
        return body;
    }

    private JComponent buildTuningArea() {
        JPanel container = new JPanel(new BorderLayout(0, 12));
        container.setOpaque(false);

        JPanel caption = new JPanel(new BorderLayout());
        caption.setOpaque(false);
        JPanel captionLeft = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
        captionLeft.setOpaque(false);
        modeToggleButton.addActionListener(e -> toggleCustomMode());
        captionLeft.add(modeToggleButton);
        tuningTitle = translatable("调音台", 13f, Ui.TEXT, Font.BOLD);
        captionLeft.add(tuningTitle);
        resetDefaultsButton.setFont(Ui.font(10.5f));
        resetDefaultsButton.addActionListener(e -> resetTuningDefaults());
        captionLeft.add(resetDefaultsButton);
        caption.add(captionLeft, BorderLayout.WEST);
        tuningHint = translatable("开启的选项按顺序处理，关闭的选项自动跳过", 11f, Ui.MUTED);
        caption.add(tuningHint, BorderLayout.EAST);
        container.add(caption, BorderLayout.NORTH);

        JPanel columns = new ScrollablePanel();
        columns.setLayout(new java.awt.GridLayout(1, 2, 14, 0));
        columns.setOpaque(false);
        JPanel leftColumn = new JPanel();
        leftColumn.setLayout(new BoxLayout(leftColumn, BoxLayout.Y_AXIS));
        leftColumn.setOpaque(false);
        JPanel rightColumn = new JPanel();
        rightColumn.setLayout(new BoxLayout(rightColumn, BoxLayout.Y_AXIS));
        rightColumn.setOpaque(false);

        int leftLoad = 0;
        int rightLoad = 0;
        for (ProcessorCatalog.Entry entry : settings.catalog().entries()) {
            ProcessorCard card = new ProcessorCard(entry.name(), entry.description(),
                    settings.state(entry.id()), entry.params(), this::scheduleSave,
                    this::onEffectParamsSettled);
            processorCards.add(card);
            JPanel holder = new CardPanel(16);
            holder.setLayout(new BorderLayout());
            ((CardPanel) holder).setBorder(Ui.padding(2, 2, 2, 2));
            holder.add(card, BorderLayout.CENTER);

            int load = 88 + entry.params().size() * 62;
            if (leftLoad <= rightLoad) {
                leftColumn.add(holder);
                leftColumn.add(Box.createVerticalStrut(14));
                leftLoad += load;
            } else {
                rightColumn.add(holder);
                rightColumn.add(Box.createVerticalStrut(14));
                rightLoad += load;
            }
        }
        columns.add(leftColumn);
        columns.add(rightColumn);

        JScrollPane scroll = new JScrollPane(columns);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Ui.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(26);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        tuningScroll = scroll;
        customPanel = new CustomInterfacePanel(appDir);
        centerCardsLayout = new java.awt.CardLayout();
        centerCards = new JPanel(centerCardsLayout);
        centerCards.setOpaque(false);
        centerCards.add(scroll, "tuning");
        centerCards.add(customPanel, "custom");
        container.add(centerCards, BorderLayout.CENTER);
        return container;
    }

    private JComponent buildSidePanel() {
        CardPanel panel = new CardPanel(18);
        panel.setLayout(new BorderLayout());
        panel.setBorder(Ui.padding(22, 22, 20, 22));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JLabel caption = translatable("运行状态", 11.5f, Ui.MUTED, Font.BOLD);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        content.add(caption);
        content.add(Box.createVerticalStrut(8));
        statusText.setAlignmentX(LEFT_ALIGNMENT);
        content.add(statusText);
        content.add(Box.createVerticalStrut(5));
        statusDetail.setAlignmentX(LEFT_ALIGNMENT);
        content.add(statusDetail);
        content.add(Box.createVerticalStrut(18));

        startButton.setAlignmentX(LEFT_ALIGNMENT);
        startButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        content.add(startButton);
        content.add(Box.createVerticalStrut(22));

        content.add(meterRow("输入电平", inputMeter, new Dimension(Integer.MAX_VALUE, 6)));
        content.add(meterRow("输出电平", outputMeter, new Dimension(Integer.MAX_VALUE, 6)));
        content.add(meterRow("耳返电平", monitorMeter, new Dimension(Integer.MAX_VALUE, 6)));
        content.add(Box.createVerticalStrut(6));
        content.add(sectionDivider());

        JLabel outputCaption = translatable("当前输出设备", 11.5f, Ui.MUTED, Font.BOLD);
        outputCaption.setAlignmentX(LEFT_ALIGNMENT);
        content.add(outputCaption);
        content.add(Box.createVerticalStrut(4));
        outputName.setAlignmentX(LEFT_ALIGNMENT);
        content.add(outputName);
        content.add(Box.createVerticalStrut(12));

        JTextArea hints = new JTextArea(I18n.t(HINTS_ZH));
        hints.setEditable(false);
        hints.setOpaque(false);
        hints.setLineWrap(true);
        hints.setWrapStyleWord(true);
        hints.setFont(Ui.font(11f));
        hints.setForeground(Ui.MUTED);
        hints.setBorder(null);
        hints.setFocusable(false);
        hints.setCaret(new javax.swing.text.DefaultCaret() {
            @Override
            protected void adjustVisibility(java.awt.Rectangle nloc) {
                // 只读提示文本不滚动插入符
            }
        });
        hints.setAlignmentX(LEFT_ALIGNMENT);
        hintsArea = hints;
        content.add(hints);
        content.add(Box.createVerticalStrut(10));

        JLabel formatCaption = translatable("音频规格", 11.5f, Ui.MUTED, Font.BOLD);
        formatCaption.setAlignmentX(LEFT_ALIGNMENT);
        content.add(formatCaption);
        content.add(Box.createVerticalStrut(4));
        formatInfoLabel = Ui.label("<html>" + I18n.t("（未启动）") + "</html>", 10.5f, Ui.MUTED);
        formatInfoLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(formatInfoLabel);
        content.add(Box.createVerticalGlue());

        pathsLabel = Ui.label("<html>" + I18n.t("日志：") + escape(LogService.currentFile())
                + "<br>" + I18n.t("存档：") + escape(preset.file()) + "</html>",
                10f, new Color(0xA8A8A8));
        pathsLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(pathsLabel);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private static final String HINTS_ZH = "使用步骤\n"
            + "1. 在设置里选择真实的麦克风作为信号源。\n"
            + "2. 变声输出选择虚拟声卡的播放端（如 CABLE Input）。\n"
            + "3. 在会议/游戏软件中把麦克风选为虚拟声卡的录音端（如 CABLE Output）。\n"
            + "4. 耳返可选真实耳机，边调边听；耳返音量过大易啸叫。";

    private JTextArea hintsArea;
    private JLabel formatInfoLabel;

    private static String escape(Path path) {
        if (path == null) {
            return "—";
        }
        String text = path.toString();
        return text.length() > 58 ? "…" + text.substring(text.length() - 57) : text;
    }

    /** 刷新右侧“音频规格”：输入设备格式、驱动实际格式、适配后的引擎输出格式。 */
    private void updateFormatInfo() {
        if (formatInfoLabel == null) {
            return;
        }
        AudioEngine.FormatInfo info = engine.formatInfo();
        if (!engine.isRunning() || info.input() == null || info.input().isEmpty()) {
            formatInfoLabel.setText("<html>" + I18n.t("（未启动）") + "</html>");
            return;
        }
        formatInfoLabel.setText("<html>"
                + I18n.t("输入：") + escapeHtml(info.input()) + "<br>"
                + I18n.t("驱动：") + escapeHtml(info.driver()) + "<br>"
                + I18n.t("输出：") + escapeHtml(info.output()) + "</html>");
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel meterRow(String label, LevelMeter meter, Dimension size) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(translatable(label, 11.5f, Ui.MUTED), BorderLayout.WEST);
        meter.setPreferredSize(size);
        meter.setMaximumSize(size);
        meter.setAlignmentX(LEFT_ALIGNMENT);
        row.add(top);
        row.add(Box.createVerticalStrut(4));
        row.add(meter);
        row.add(Box.createVerticalStrut(12));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return row;
    }

    private JComponent sectionDivider() {
        JPanel line = new JPanel();
        line.setBackground(Ui.LINE);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setPreferredSize(new Dimension(1, 1));
        line.setAlignmentX(LEFT_ALIGNMENT);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(line, BorderLayout.CENTER);
        holder.setBorder(Ui.padding(10, 0, 12, 0));
        holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        holder.setAlignmentX(LEFT_ALIGNMENT);
        return holder;
    }

    private void buildSettingsMenu() {
        settingsMenu = new SettingsMenu(this, settings, appDir, this::saveNow,
                this::applyDeviceSettings, () -> gearButton.setActive(false),
                engine::setMonitorVolume, this::currentTuningCode, this::applyTuningCode);
        settingsMenu.refreshTuningCode();
    }

    /** 当前全部调音数据生成的十进制调音编码，与界面语言无关。 */
    private String currentTuningCode() {
        return com.voicechanger.config.TuningCodec.encode(settings);
    }

    /**
     * 应用输入的调音编码：写回所有调音状态、刷新调音面板、重置效果链并保存存档。
     * 返回 null 表示成功，否则返回给用户看的错误文本。
     */
    private String applyTuningCode(String code) {
        try {
            com.voicechanger.config.TuningCodec.decode(settings, code);
        } catch (IllegalArgumentException ex) {
            LogService.warn("调音编码应用失败: " + ex.getMessage());
            return I18n.t("调音编码无效，请检查后再试");
        }
        for (ProcessorCard card : processorCards) {
            card.refreshFromState();
        }
        if (engine.isRunning()) {
            engine.resetChain();
        }
        saveNow();
        if (settingsMenu != null) {
            settingsMenu.refreshTuningCode();
        }
        LogService.info("已应用新的调音编码: " + code);
        return null;
    }

    /** 一键恢复默认数值：仅重置全部模块参数（不动开关），刷新面板、重置效果链并保存。 */
    private void resetTuningDefaults() {
        for (ProcessorCatalog.Entry entry : settings.catalog().entries()) {
            ProcessorState state = settings.state(entry.id());
            state.resetToDefaults(entry.params());
        }
        for (ProcessorCard card : processorCards) {
            card.refreshFromState();
        }
        if (engine.isRunning()) {
            engine.resetChain();
        }
        saveNow();
        if (settingsMenu != null) {
            settingsMenu.refreshTuningCode();
        }
        LogService.info("已恢复全部调音参数为默认值");
    }

    /**
     * 切换默认调音面板与自定义调音接口：
     * 进入自定义模式时关闭音频调节模块并激活两个 API，面板切换为“自定义调音接口”；
     * 返回默认面板时重新开启音频调节模块并关闭两个 API。
     */
    private void toggleCustomMode() {
        boolean enable = !customMode;
        if (!enable) {
            // 先停止自定义调音脚本，再关闭两个 API，避免脚本在连接被关闭后继续运行
            customPanel.stopActiveFile();
        }
        if (!engine.setCustomMode(enable)) {
            LogService.warn("自定义调音接口启动失败，保持默认调音面板");
            showError(I18n.t("自定义调音接口启动失败：端口 47801/47802 可能被占用，请关闭占用程序后重试。"));
            return;
        }
        customMode = enable;
        // 两种链路切换时清理调音模块的延迟线残留，避免恢复默认面板时出现旧尾音
        engine.resetChain();
        if (customMode) {
            customPanel.refreshFiles();
            LogService.info("已切换到自定义调音接口：音频调节模块关闭");
        } else {
            LogService.info("已切换到默认调音面板：音频调节模块开启");
        }
        updateModeUi();
        revalidate();
        repaint();
    }

    /** 按当前模式刷新调音区标题、切换按钮、恢复默认按钮与提示的显示状态。 */
    private void updateModeUi() {
        resetDefaultsButton.setVisible(!customMode);
        tuningHint.setVisible(!customMode);
        tuningTitle.setText(I18n.t(customMode ? "自定义调音接口" : "调音台"));
        modeToggleButton.setText(I18n.t(customMode ? "切换默认面板" : "切换自定义接口"));
        if (centerCardsLayout != null && centerCards != null) {
            centerCardsLayout.show(centerCards, customMode ? "custom" : "tuning");
        }
    }

    private void buildLanguageMenu() {
        languageMenu = new LanguageMenu(this, this::onLanguageSelected,
                () -> globeButton.setActive(false));
    }

    /** 语言选择：更新设置、刷新界面并立即写入存档。 */
    private void onLanguageSelected(String language) {
        settings.setLanguage(language);
        I18n.setLanguage(language);
        saveNow();
        LogService.info("界面语言已切换为: " + (I18n.isEnglish() ? "English" : "中文"));
    }

    private void bindEvents() {
        gearButton.onClick(this::toggleSettings);
        globeButton.onClick(this::toggleLanguage);
        startButton.addActionListener(e -> {
            if (engine.isRunning()) {
                stopEngine();
            } else {
                startEngine();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    /** 打开/关闭设置菜单，同时切换齿轮按钮的黑白状态。 */
    public void toggleSettings() {
        if (settingsMenu == null) {
            return;
        }
        if (settingsMenu.isVisible()) {
            settingsMenu.setVisible(false);
            gearButton.setActive(false);
        } else {
            if (languageMenu != null) {
                languageMenu.setVisible(false);
            }
            globeButton.setActive(false);
            gearButton.setActive(true);
            settingsMenu.showBelow(gearButton);
        }
    }

    /** 打开/关闭语言菜单，逻辑与设置菜单一致。 */
    public void toggleLanguage() {
        if (languageMenu == null) {
            return;
        }
        if (languageMenu.isVisible()) {
            languageMenu.setVisible(false);
            globeButton.setActive(false);
        } else {
            if (settingsMenu != null) {
                settingsMenu.setVisible(false);
            }
            gearButton.setActive(false);
            globeButton.setActive(true);
            languageMenu.showBelow(globeButton);
        }
    }

    /** 用系统默认浏览器打开官网；失败时只记录日志，不打断音频链路。 */
    private void openWebsite() {
        try {
            if (!Desktop.isDesktopSupported()) {
                LogService.warn("当前系统不支持打开默认浏览器，官网地址: " + WEBSITE_URL);
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                LogService.warn("当前环境不支持浏览操作，官网地址: " + WEBSITE_URL);
                return;
            }
            desktop.browse(URI.create(WEBSITE_URL));
            LogService.info("已打开官网: " + WEBSITE_URL);
        } catch (Exception e) {
            LogService.error("打开官网失败: " + WEBSITE_URL, e);
        }
    }

    /** 供截图/自测使用：把调音台滚动到顶部。 */
    public void resetTuningScroll() {
        if (tuningScroll != null) {
            tuningScroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
        }
    }

    private void startMeterTimer() {
        Timer meterTimer = new Timer(90, e -> {
            inputMeter.setValue(engine.isRunning() ? engine.inputLevel() : 0f);
            outputMeter.setValue(engine.isRunning() ? engine.outputLevel() : 0f);
            monitorMeter.setValue(engine.isRunning() ? engine.monitorLevel() : 0f);
        });
        meterTimer.start();
    }

    private void startEngine() {
        List<DeviceInfo> inputs = DeviceManager.inputDevices();
        List<DeviceInfo> outputs = DeviceManager.outputDevices();
        DeviceInfo input = resolveInput(inputs);
        DeviceInfo output = resolveOutput(outputs);
        if (input == null) {
            showError(I18n.t("没有可用的录音设备，请检查麦克风连接。"));
            return;
        }
        if (output == null) {
            showError(I18n.t("没有可用的播放设备，无法把变声结果送回电脑。"));
            return;
        }
        DeviceInfo monitorDev = resolveMonitor(outputs);
        runningMonitor = settings.monitorEnabled() && monitorDev != null;
        try {
            engine.start(input, output, runningMonitor, monitorDev, settings.monitorVolume());
            runningSignature = signature();
            statusKey = "运行中";
            statusText.setText(I18n.t(statusKey));
            statusText.setForeground(new Color(0x1E8E3E));
            statusDetail.setText(I18n.t("已输出到 ") + output.label());
            outputName.setText(output.label());
            startButton.setText(I18n.t("停止变声"));
            updateFormatInfo();
            LogService.info("变声已开始，状态正常");
        } catch (Exception ex) {
            LogService.error("启动变声失败", ex);
            updateFormatInfo();
            showError(I18n.t("启动失败：") + ex.getMessage() + I18n.t("\n请检查设备是否被其他程序独占。"));
            statusKey = "启动失败";
            statusText.setText(I18n.t(statusKey));
            statusText.setForeground(new Color(0xC0392B));
            statusDetail.setText(String.valueOf(ex.getMessage()));
            // 启动失败时按钮与耳返状态需回到未运行，避免误显示“停止变声”
            runningMonitor = false;
            startButton.setText(I18n.t("开始变声"));
        }
    }

    private void stopEngine() {
        engine.stop();
        statusKey = "未启动";
        detailKey = "选择虚拟麦克风后点击右侧按钮开始";
        statusText.setText(I18n.t(statusKey));
        statusText.setForeground(Ui.TEXT);
        statusDetail.setText(I18n.t(detailKey));
        startButton.setText(I18n.t("开始变声"));
        inputMeter.setValue(0f);
        outputMeter.setValue(0f);
        monitorMeter.setValue(0f);
        updateFormatInfo();
        LogService.info("变声已停止");
    }

    /** 采集异常（设备被拔出等）：停止引擎并提示用户重新连接。 */
    private void onCaptureLost() {
        if (!engine.isRunning()) {
            return;
        }
        stopEngine();
        statusKey = "输入设备已断开";
        detailKey = "请检查麦克风连接后重新开始";
        statusText.setText(I18n.t(statusKey));
        statusText.setForeground(new Color(0xC0392B));
        statusDetail.setText(I18n.t(detailKey));
        showError(I18n.t("输入设备已断开，变声已停止。请检查麦克风连接后重新开始。"));
    }

    private DeviceInfo resolveInput(List<DeviceInfo> inputs) {
        DeviceInfo info = AppSettings.resolve(inputs, settings.sourceDeviceName());
        if (info == null) {
            info = AppSettings.pickDefault(inputs, null);
        }
        if (info != null) {
            settings.setSourceDeviceName(info.mixerName());
        }
        return info;
    }

    private DeviceInfo resolveOutput(List<DeviceInfo> outputs) {
        DeviceInfo info = AppSettings.resolve(outputs, settings.outputDeviceName());
        if (info == null) {
            info = AppSettings.pickDefault(outputs, "voicechanger");
            if (info == null) {
                info = AppSettings.pickDefault(outputs, "cable");
            }
            if (info == null) {
                info = AppSettings.pickDefault(outputs, null);
            }
        }
        if (info != null) {
            settings.setOutputDeviceName(info.mixerName());
        }
        return info;
    }

    private DeviceInfo resolveMonitor(List<DeviceInfo> outputs) {
        DeviceInfo info = AppSettings.resolve(outputs, settings.monitorDeviceName());
        if (info == null) {
            for (DeviceInfo candidate : outputs) {
                String text = candidate.name().toLowerCase();
                if (!text.contains("cable") && !text.contains("virtual")) {
                    info = candidate;
                    break;
                }
            }
        }
        if (info != null) {
            settings.setMonitorDeviceName(info.mixerName());
        }
        return info;
    }

    private void applyDeviceSettings() {
        if (!engine.isRunning()) {
            return;
        }
        if (signature().equals(runningSignature)) {
            return;
        }
        LogService.info("设备设置发生变化，重启音频引擎");
        stopEngine();
        SwingUtilities.invokeLater(this::startEngine);
    }

    private String signature() {
        return settings.sourceDeviceName() + "|" + settings.outputDeviceName() + "|"
                + settings.monitorEnabled() + "|" + settings.monitorDeviceName();
    }

    /**
     * 回声/混响的延迟时间在拖动后会发生跳变，稳定后清一次尾音避免杂音；
     * 其余模块参数本身连续可调，重置反而会造成断音，不处理。
     */
    private void onEffectParamsSettled(String id) {
        if (engine.isRunning() && ("echo".equals(id) || "reverb".equals(id))) {
            engine.resetProcessor(id);
        }
    }

    private void scheduleSave() {
        // 调音数据变化时实时刷新设置菜单中的调音编码
        if (settingsMenu != null) {
            settingsMenu.refreshTuningCode();
        }
        if (saveTimer == null) {
            saveTimer = new Timer(400, e -> {
                saveTimer.stop();
                preset.save(settings);
            });
            saveTimer.setRepeats(false);
        }
        saveTimer.restart();
    }

    private void saveNow() {
        preset.save(settings);
    }

    private void showError(String message) {
        javax.swing.JOptionPane.showMessageDialog(this, message, I18n.t("提示"),
                javax.swing.JOptionPane.WARNING_MESSAGE);
    }

    private void shutdown() {
        LogService.info("窗口关闭，正在停止音频引擎并保存存档");
        if (customPanel != null) {
            // 同步停止：随后立即 System.exit，异步清理来不及执行会导致脚本子进程残留
            customPanel.stopActiveFileAndWait();
        }
        engine.close();
        saveNow();
        dispose();
        System.exit(0);
    }

    public static void applyGlobalStyle() {
        javax.swing.UIManager.put("Label.font", Ui.font(12f));
        javax.swing.UIManager.put("ComboBox.font", Ui.font(12f));
        javax.swing.UIManager.put("Panel.background", Color.WHITE);
        javax.swing.UIManager.put("OptionPane.messageFont", Ui.font(12.5f));
    }
}
