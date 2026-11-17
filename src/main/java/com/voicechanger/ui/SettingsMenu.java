package com.voicechanger.ui;

import com.voicechanger.audio.DeviceInfo;
import com.voicechanger.config.AppSettings;
import com.voicechanger.logging.LogService;
import com.voicechanger.util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 设置菜单：信号源、虚拟麦克风输出、耳返、日志地址。
 */
public final class SettingsMenu extends JDialog {

    private final AppSettings settings;
    private final Runnable onPersist;
    private final Runnable onDevicesApplied;
    private final Runnable onHide;
    private final java.util.function.Consumer<Float> onMonitorVolume;
    private final java.util.function.Supplier<String> tuningCodeSupplier;
    /** 应用编码回调：返回 null 表示成功，否则返回用于提示的错误文本。 */
    private final java.util.function.Function<String, String> onApplyTuningCode;
    private final java.nio.file.Path appDir;

    private final JComboBox<DeviceInfo> sourceCombo = new JComboBox<>();
    private final JComboBox<DeviceInfo> outputCombo = new JComboBox<>();
    private final JComboBox<DeviceInfo> monitorCombo = new JComboBox<>();
    private final ToggleSwitch monitorToggle = new ToggleSwitch();
    private final JSlider monitorVolume = FlatSliderUI.create(0, 100, 80);
    private final JLabel monitorVolumeValue = Ui.label("80%", 12f, Ui.MUTED);
    private final JTextField logField = new JTextField();
    private final JLabel deviceHint = Ui.label(" ", 11f, Ui.MUTED);
    private final java.util.List<JLabel> i18nLabels = new java.util.ArrayList<>();
    private final java.util.List<String> i18nKeys = new java.util.ArrayList<>();
    private JButton refreshButton;
    private JButton applyButton;
    private JButton browseButton;
    private final JTextField currentCodeField = new JTextField();
    private final JTextField inputCodeField = new JTextField();
    private final JLabel codeHint = Ui.label(" ", 10.5f, Ui.MUTED);
    private JButton copyCodeButton;
    private JButton applyCodeButton;
    private JButton virtualDriverButton;
    private JLabel virtualDriverStatus;
    private JButton vbCableButton;
    private JLabel vbCableStatus;
    private JLabel vbCableNotice;
    /** 是否已检测到 VoiceChanger 自带虚拟声卡的两个端点。 */
    private boolean voiceChangerDriverInstalled;
    /** 是否已检测到 VB-CABLE 的播放或录音端点。 */
    private boolean vbCableInstalled;
    /** 耳返音量拖动时防抖写盘，避免每一格都同步保存存档。 */
    private final Timer volumeSaveTimer;
    /** 是否检测到虚拟声卡（中英文提示文案据此切换）。 */
    private boolean hasVirtualCable = true;

    public SettingsMenu(Window owner, AppSettings settings, java.nio.file.Path appDir,
                        Runnable onPersist, Runnable onDevicesApplied, Runnable onHide,
                        java.util.function.Consumer<Float> onMonitorVolume,
                        java.util.function.Supplier<String> tuningCodeSupplier,
                        java.util.function.Function<String, String> onApplyTuningCode) {
        super(owner, "设置", ModalityType.MODELESS);
        this.settings = settings;
        this.appDir = appDir;
        this.onPersist = onPersist;
        this.onDevicesApplied = onDevicesApplied;
        this.onHide = onHide;
        this.onMonitorVolume = onMonitorVolume;
        this.tuningCodeSupplier = tuningCodeSupplier;
        this.onApplyTuningCode = onApplyTuningCode;
        setUndecorated(true);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE_STRONG),
                Ui.padding(20, 20, 18, 20)));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(translatable("设置", 16f, Ui.TEXT, Font.BOLD), BorderLayout.WEST);
        titleRow.add(translatable("自动保存", 11f, Ui.MUTED, Font.PLAIN), BorderLayout.EAST);
        root.add(titleRow, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int row = 0;
        row = addSection(form, "音频链路", row);
        row = addField(form, "音频信号源", sourceCombo, row);
        row = addField(form, "变声输出（虚拟麦克风）", outputCombo, row);
        row = addHint(form, "在会议/游戏软件中选择同一虚拟声卡的录音端作为麦克风。", row);

        row = addSection(form, "虚拟声卡", row);
        JPanel driverRow = new JPanel(new BorderLayout(10, 0));
        driverRow.setOpaque(false);
        virtualDriverStatus = Ui.label(" ", 11f, Ui.MUTED);
        virtualDriverButton = pillButton("安装虚拟声卡");
        virtualDriverButton.addActionListener(e -> onVirtualDriverButton());
        driverRow.add(virtualDriverStatus, BorderLayout.WEST);
        driverRow.add(virtualDriverButton, BorderLayout.EAST);
        row = addFull(form, driverRow, row);

        JPanel vbCableRow = new JPanel(new BorderLayout(10, 0));
        vbCableRow.setOpaque(false);
        vbCableStatus = Ui.label(" ", 11f, Ui.MUTED);
        vbCableButton = pillButton("安装 VB-CABLE");
        vbCableButton.addActionListener(e -> onVbCableButton());
        vbCableRow.add(vbCableStatus, BorderLayout.WEST);
        vbCableRow.add(vbCableButton, BorderLayout.EAST);
        row = addFull(form, vbCableRow, row);

        vbCableNotice = new JLabel();
        vbCableNotice.setFont(Ui.font(10.5f));
        vbCableNotice.setForeground(Ui.MUTED);
        vbCableNotice.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        vbCableNotice.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openVbCableSite();
            }
        });
        row = addFull(form, vbCableNotice, row);

        row = addSection(form, "耳返", row);
        JPanel monitorRow = new JPanel(new BorderLayout());
        monitorRow.setOpaque(false);
        monitorRow.add(translatable("开启耳返监听", 12f, Ui.TEXT, Font.PLAIN), BorderLayout.WEST);
        monitorRow.add(monitorToggle, BorderLayout.EAST);
        row = addFull(form, monitorRow, row);
        row = addField(form, "耳返设备", monitorCombo, row);
        row = addVolume(form, row);

        row = addSection(form, "调音编码", row);
        row = addTuningCodeRows(form, row);

        row = addSection(form, "日志", row);
        row = addLogPath(form, row);

        root.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        refreshButton = pillButton("刷新设备");
        refreshButton.addActionListener(e -> refreshDeviceLists());
        applyButton = pillButton("应用并保存");
        applyButton.setForeground(Color.WHITE);
        applyButton.setBackground(Ui.BLACK);
        applyButton.addActionListener(e -> {
            onPersist.run();
            onDevicesApplied.run();
            setVisible(false);
        });
        footer.add(refreshButton, BorderLayout.WEST);
        footer.add(applyButton, BorderLayout.EAST);
        footer.setBorder(Ui.padding(16, 0, 0, 0));
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        volumeSaveTimer = new Timer(300, e -> persist());
        volumeSaveTimer.setRepeats(false);
        bindListeners();
        bindTuningCodeActions();
        bindEscape();
        setPreferredSize(new Dimension(420, 800));
    }

    /** 调音编码区：只读当前编码 + 复制按钮，输入编码 + 应用按钮，编码本身与界面语言无关。 */
    private int addTuningCodeRows(JPanel form, int row) {
        currentCodeField.setEditable(false);
        currentCodeField.setFocusable(true);
        styleCodeField(currentCodeField);
        currentCodeField.setToolTipText(com.voicechanger.util.I18n.t("当前调音编码，由调音面板实时生成"));
        copyCodeButton = pillButton("一键复制调音编码");
        JPanel currentRow = new JPanel(new BorderLayout(6, 0));
        currentRow.setOpaque(false);
        currentRow.add(currentCodeField, BorderLayout.CENTER);
        currentRow.add(copyCodeButton, BorderLayout.EAST);
        row = addFull(form, currentRow, row);

        styleCodeField(inputCodeField);
        inputCodeField.setToolTipText(com.voicechanger.util.I18n.t("输入新的调音编码"));
        applyCodeButton = pillButton("应用新的调音编码");
        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        inputRow.add(inputCodeField, BorderLayout.CENTER);
        inputRow.add(applyCodeButton, BorderLayout.EAST);
        row = addFull(form, inputRow, row);

        form.add(codeHint, constraints(0, row, 2, new Insets(2, 0, 0, 0), 1));
        return row + 1;
    }

    private static void styleCodeField(JTextField field) {
        field.setFont(Ui.font(11.5f));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE_STRONG),
                Ui.padding(5, 8, 5, 8)));
        field.setBackground(Color.WHITE);
    }

    private void bindTuningCodeActions() {
        copyCodeButton.addActionListener(e -> {
            String code = currentCodeField.getText();
            if (code.isEmpty()) {
                return;
            }
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(code);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            codeHint.setForeground(Ui.MUTED);
            codeHint.setText(com.voicechanger.util.I18n.t("调音编码已复制到剪贴板"));
            LogService.info("调音编码已复制到剪贴板");
        });
        applyCodeButton.addActionListener(e -> {
            String code = inputCodeField.getText().trim();
            if (code.isEmpty()) {
                codeHint.setForeground(new Color(0xC0392B));
                codeHint.setText(com.voicechanger.util.I18n.t("请输入调音编码"));
                return;
            }
            String error = onApplyTuningCode == null ? null : onApplyTuningCode.apply(code);
            if (error == null) {
                codeHint.setForeground(Ui.MUTED);
                codeHint.setText(com.voicechanger.util.I18n.t("调音编码已应用"));
                refreshTuningCode();
            } else {
                codeHint.setForeground(new Color(0xC0392B));
                codeHint.setText(error);
            }
        });
    }

    /** 重新生成并显示当前调音编码；调音数据任何变化时调用。 */
    public void refreshTuningCode() {
        if (tuningCodeSupplier == null) {
            return;
        }
        currentCodeField.setText(tuningCodeSupplier.get());
        currentCodeField.setCaretPosition(0);
    }

    private int addSection(JPanel form, String title, int row) {
        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);
        section.add(translatable(title, 11.5f, Ui.MUTED, Font.BOLD), BorderLayout.WEST);
        Insets insets = new Insets(row == 0 ? 4 : 18, 0, 6, 0);
        form.add(section, constraints(0, row, 2, insets, 0));
        return row + 1;
    }

    private int addField(JPanel form, String label, JComboBox<DeviceInfo> combo, int row) {
        combo.setRenderer(deviceRenderer());
        combo.setBackground(Color.WHITE);
        combo.setFont(Ui.font(12f));
        form.add(translatable(label, 12f, Ui.TEXT, Font.PLAIN), constraints(0, row, 1, new Insets(3, 0, 3, 12), 0));
        form.add(combo, constraints(1, row, 1, new Insets(3, 0, 3, 0), 1));
        return row + 1;
    }

    private int addHint(JPanel form, String text, int row) {
        deviceHint.setText(text);
        form.add(deviceHint, constraints(1, row, 1, new Insets(0, 0, 0, 0), 1));
        return row + 1;
    }

    private int addFull(JPanel form, Component component, int row) {
        form.add(component, constraints(0, row, 2, new Insets(3, 0, 3, 0), 0));
        return row + 1;
    }

    private int addVolume(JPanel form, int row) {
        JPanel holder = new JPanel(new BorderLayout(10, 0));
        holder.setOpaque(false);
        holder.add(monitorVolume, BorderLayout.CENTER);
        JPanel valueHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        valueHolder.setOpaque(false);
        valueHolder.add(monitorVolumeValue);
        holder.add(valueHolder, BorderLayout.EAST);
        form.add(translatable("耳返音量", 12f, Ui.TEXT, Font.PLAIN), constraints(0, row, 1, new Insets(3, 0, 3, 12), 0));
        form.add(holder, constraints(1, row, 1, new Insets(3, 0, 3, 0), 1));
        return row + 1;
    }

    private int addLogPath(JPanel form, int row) {
        JPanel holder = new JPanel(new BorderLayout(6, 0));
        holder.setOpaque(false);
        logField.setFont(Ui.font(11.5f));
        logField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE_STRONG),
                Ui.padding(5, 8, 5, 8)));
        holder.add(logField, BorderLayout.CENTER);
        browseButton = pillButton("浏览");
        browseButton.addActionListener(e -> chooseLogDirectory());
        holder.add(browseButton, BorderLayout.EAST);
        form.add(translatable("日志存放地址", 12f, Ui.TEXT, Font.PLAIN), constraints(0, row, 1, new Insets(3, 0, 3, 12), 0));
        form.add(holder, constraints(1, row, 1, new Insets(3, 0, 3, 0), 1));
        row++;
        form.add(new JLabel(), constraints(0, row, 1, new Insets(0, 0, 0, 0), 0));
        form.add(translatable("留空则保存在用户数据目录", 10.5f, Ui.MUTED, Font.PLAIN),
                constraints(1, row, 1, new Insets(0, 0, 0, 0), 1));
        return row + 1;
    }

    /** 登记可翻译标签，语言切换时统一刷新。 */
    private JLabel translatable(String zhText, float size, Color color, int style) {
        JLabel label = Ui.label(com.voicechanger.util.I18n.t(zhText), size, color, style);
        i18nLabels.add(label);
        i18nKeys.add(zhText);
        return label;
    }

    /** 语言切换时更新菜单内全部文本。 */
    public void applyLanguage() {
        for (int i = 0; i < i18nLabels.size(); i++) {
            i18nLabels.get(i).setText(com.voicechanger.util.I18n.t(i18nKeys.get(i)));
        }
        refreshButton.setText(com.voicechanger.util.I18n.t("刷新设备"));
        applyButton.setText(com.voicechanger.util.I18n.t("应用并保存"));
        browseButton.setText(com.voicechanger.util.I18n.t("浏览"));
        copyCodeButton.setText(com.voicechanger.util.I18n.t("一键复制调音编码"));
        applyCodeButton.setText(com.voicechanger.util.I18n.t("应用新的调音编码"));
        currentCodeField.setToolTipText(com.voicechanger.util.I18n.t("当前调音编码，由调音面板实时生成"));
        inputCodeField.setToolTipText(com.voicechanger.util.I18n.t("输入新的调音编码"));
        // 状态提示属于瞬态消息，语言切换后清空；编码内容本身不随语言变化
        codeHint.setText(" ");
        boolean hasCable = hasVirtualCable;
        deviceHint.setText(com.voicechanger.util.I18n.t(hasCable
                ? "在会议/游戏软件中选择同一虚拟声卡的录音端作为麦克风。"
                : "未检测到虚拟声卡，请先安装 VB-CABLE 等虚拟音频驱动。"));
        updateVirtualDriverUi();
        pack();
    }

    private GridBagConstraints constraints(int x, int y, int width, Insets insets, double weightx) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.insets = insets;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = weightx;
        return c;
    }

    private void bindListeners() {
        sourceCombo.addActionListener(e -> {
            DeviceInfo info = (DeviceInfo) sourceCombo.getSelectedItem();
            settings.setSourceDeviceName(info == null ? "" : info.mixerName());
            persist();
        });
        outputCombo.addActionListener(e -> {
            DeviceInfo info = (DeviceInfo) outputCombo.getSelectedItem();
            settings.setOutputDeviceName(info == null ? "" : info.mixerName());
            persist();
        });
        monitorCombo.addActionListener(e -> {
            DeviceInfo info = (DeviceInfo) monitorCombo.getSelectedItem();
            settings.setMonitorDeviceName(info == null ? "" : info.mixerName());
            persist();
        });
        monitorToggle.addChangeListener(() -> {
            settings.setMonitorEnabled(monitorToggle.isSelected());
            monitorCombo.setEnabled(monitorToggle.isSelected());
            persist();
        });
        monitorVolume.addChangeListener(e -> {
            int value = monitorVolume.getValue();
            monitorVolumeValue.setText(value + "%");
            settings.setMonitorVolume(value / 100f);
            // 运行中立即生效，不必重启音频引擎
            if (onMonitorVolume != null) {
                onMonitorVolume.accept(settings.monitorVolume());
            }
            // 拖动过程中防抖，停止拖动 300ms 后统一写盘
            volumeSaveTimer.restart();
        });
        logField.addActionListener(e -> applyLogPath());
        logField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyLogPath();
            }
        });
    }

    private void applyLogPath() {
        String text = logField.getText().trim();
        if (text.equals(settings.logDirectory())) {
            return;
        }
        settings.setLogDirectory(text);
        LogService.initialize(appDir, text);
        LogService.info("日志存放地址已更改为: " + (text.isBlank() ? "软件所在文件夹" : text));
        onPersist.run();
    }

    private void chooseLogDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(com.voicechanger.util.I18n.t("选择日志存放文件夹"));
        if (!settings.logDirectory().isBlank()) {
            chooser.setCurrentDirectory(new File(settings.logDirectory()));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            logField.setText(chooser.getSelectedFile().getAbsolutePath());
            applyLogPath();
        }
    }

    public void refreshDeviceLists() {
        List<DeviceInfo> inputs = com.voicechanger.audio.DeviceManager.inputDevices();
        List<DeviceInfo> outputs = com.voicechanger.audio.DeviceManager.outputDevices();
        DeviceInfo input = fill(sourceCombo, inputs, settings.sourceDeviceName());
        if (input != null) {
            settings.setSourceDeviceName(input.mixerName());
        }
        DeviceInfo output = fill(outputCombo, outputs, settings.outputDeviceName(),
                "voicechanger", "cable");
        if (output != null) {
            settings.setOutputDeviceName(output.mixerName());
        }
        List<DeviceInfo> monitorCandidates = outputs.stream()
                .filter(info -> !com.voicechanger.audio.DeviceManager.isVoiceChangerVirtual(info))
                .filter(info -> !com.voicechanger.audio.DeviceManager.isVbCable(info))
                .toList();
        DeviceInfo monitorDevice = fill(monitorCombo, monitorCandidates, settings.monitorDeviceName(),
                "speaker");
        if (monitorDevice != null) {
            settings.setMonitorDeviceName(monitorDevice.mixerName());
        }
        boolean hasCable = com.voicechanger.audio.DeviceManager.hasVirtualCable(outputs);
        hasVirtualCable = hasCable;
        deviceHint.setForeground(hasCable ? Ui.MUTED : new Color(0xC0392B));
        deviceHint.setText(com.voicechanger.util.I18n.t(hasCable
                ? "在会议/游戏软件中选择同一虚拟声卡的录音端作为麦克风。"
                : "未检测到虚拟声卡，请先安装 VB-CABLE 等虚拟音频驱动。"));
        voiceChangerDriverInstalled =
                com.voicechanger.audio.DeviceManager.hasVoiceChangerVirtual(inputs)
                        && com.voicechanger.audio.DeviceManager.hasVoiceChangerVirtual(outputs);
        vbCableInstalled = com.voicechanger.audio.DeviceManager.hasVbCable(inputs)
                || com.voicechanger.audio.DeviceManager.hasVbCable(outputs);
        updateVirtualDriverUi();
    }

    private DeviceInfo fill(JComboBox<DeviceInfo> combo, List<DeviceInfo> devices, String stored,
                            String... preferredKeywords) {
        combo.removeAllItems();
        for (DeviceInfo info : devices) {
            combo.addItem(info);
        }
        DeviceInfo selected = AppSettings.resolve(devices, stored);
        if (selected == null && preferredKeywords != null) {
            for (String keyword : preferredKeywords) {
                selected = AppSettings.pickDefault(devices, keyword);
                if (selected != null) {
                    break;
                }
            }
        }
        if (selected != null) {
            combo.setSelectedItem(selected);
        }
        combo.setEnabled(combo.getItemCount() > 0);
        return selected;
    }

    /** 刷新虚拟声卡安装状态与按钮文案。 */
    private void updateVirtualDriverUi() {
        if (virtualDriverButton == null || virtualDriverStatus == null) {
            return;
        }
        boolean install = !voiceChangerDriverInstalled;
        Path script = appDir.resolve("driver").resolve("install")
                .resolve(install ? "install-driver.ps1" : "uninstall-driver.ps1");
        Path packageDir = appDir.resolve("driver").resolve("package");
        boolean ready = Files.isRegularFile(script)
                && (!install || Files.isDirectory(packageDir));
        String statusKey;
        if (voiceChangerDriverInstalled) {
            statusKey = "VoiceChanger 虚拟声卡已安装";
        } else if (ready) {
            statusKey = "VoiceChanger 虚拟声卡未安装";
        } else {
            statusKey = "未找到虚拟声卡驱动包";
        }
        virtualDriverStatus.setText(com.voicechanger.util.I18n.t(statusKey));
        virtualDriverStatus.setForeground(voiceChangerDriverInstalled ? Ui.MUTED
                : (ready ? new Color(0xC0392B) : Ui.MUTED));
        virtualDriverButton.setText(com.voicechanger.util.I18n.t(
                voiceChangerDriverInstalled ? "卸载虚拟声卡" : "安装虚拟声卡"));
        virtualDriverButton.setEnabled(ready);
        virtualDriverButton.setToolTipText(com.voicechanger.util.I18n.t(
                "安装或卸载虚拟声卡需要管理员权限，可能要求重启。"));
        updateVbCableUi();
    }

    /** 刷新 VB-CABLE 状态、按钮文案与署名/捐赠提示。 */
    private void updateVbCableUi() {
        if (vbCableButton == null || vbCableStatus == null) {
            return;
        }
        boolean install = !vbCableInstalled;
        Path script = appDir.resolve("driver").resolve("install")
                .resolve(install ? "install-vbcable.ps1" : "uninstall-vbcable.ps1");
        Path packageDir = appDir.resolve("driver").resolve("vbcable");
        boolean ready = Files.isRegularFile(script)
                && (!install || Files.isRegularFile(packageDir.resolve("VBCABLE_Setup_x64.exe")));
        String statusKey;
        if (vbCableInstalled) {
            statusKey = "VB-CABLE 已安装";
        } else if (ready) {
            statusKey = "VB-CABLE 未安装";
        } else {
            statusKey = "未找到 VB-CABLE 驱动包";
        }
        vbCableStatus.setText(com.voicechanger.util.I18n.t(statusKey));
        vbCableStatus.setForeground(vbCableInstalled ? Ui.MUTED
                : (ready ? new Color(0xC0392B) : Ui.MUTED));
        vbCableButton.setText(com.voicechanger.util.I18n.t(
                vbCableInstalled ? "卸载 VB-CABLE" : "安装 VB-CABLE"));
        vbCableButton.setEnabled(ready);
        vbCableButton.setToolTipText(com.voicechanger.util.I18n.t(
                "安装或卸载 VB-CABLE 需要管理员权限，可能要求重启。"));
        if (vbCableNotice != null) {
            vbCableNotice.setText("<html><a href=\"https://vb-cable.com\">"
                    + com.voicechanger.util.I18n.t(
                    "VB-CABLE 由 VB-Audio 提供（donationware），可访问 vb-cable.com 支持作者。")
                    + "</a></html>");
        }
    }

    /** 打开 VB-CABLE 官网（授权要求保留的捐赠入口）。 */
    private void openVbCableSite() {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://vb-cable.com"));
        } catch (Exception e) {
            LogService.info("无法打开 VB-CABLE 官网: " + e.getMessage());
        }
    }

    private void onVirtualDriverButton() {
        boolean install = !voiceChangerDriverInstalled;
        Path script = appDir.resolve("driver").resolve("install")
                .resolve(install ? "install-driver.ps1" : "uninstall-driver.ps1");
        if (!Files.isRegularFile(script)) {
            JOptionPane.showMessageDialog(this,
                    com.voicechanger.util.I18n.t("未找到虚拟声卡驱动包，请重新安装软件。"),
                    com.voicechanger.util.I18n.t("虚拟声卡"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String question = com.voicechanger.util.I18n.t(install
                ? "安装虚拟声卡需要管理员权限，安装后可能需要重启。是否继续？"
                : "卸载虚拟声卡需要管理员权限。是否继续？");
        int choice = JOptionPane.showConfirmDialog(this, question,
                com.voicechanger.util.I18n.t("虚拟声卡"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        Path packageDir = appDir.resolve("driver").resolve("package");
        String extra = install
                ? "-PackageDir " + psQuote(packageDir.toString()) + " -SkipVerify" : "";
        runVirtualDriverScript(script, extra, "voice-changer-driver.log");
    }

    private void onVbCableButton() {
        boolean install = !vbCableInstalled;
        Path script = appDir.resolve("driver").resolve("install")
                .resolve(install ? "install-vbcable.ps1" : "uninstall-vbcable.ps1");
        if (!Files.isRegularFile(script)) {
            JOptionPane.showMessageDialog(this,
                    com.voicechanger.util.I18n.t("未找到 VB-CABLE 驱动包，请重新安装软件。"),
                    com.voicechanger.util.I18n.t("虚拟声卡"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String question = com.voicechanger.util.I18n.t(install
                ? "安装 VB-CABLE 需要管理员权限，安装后建议重启。是否继续？"
                : "卸载 VB-CABLE 需要管理员权限，卸载后建议重启。是否继续？");
        int choice = JOptionPane.showConfirmDialog(this, question,
                com.voicechanger.util.I18n.t("虚拟声卡"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        String extra = install
                ? "-PackageDir " + psQuote(appDir.resolve("driver").resolve("vbcable").toString()) : "";
        runVirtualDriverScript(script, extra, "voice-changer-vbcable.log");
    }

    /**
     * 通过 UAC 提权运行驱动安装/卸载脚本，避免阻塞 UI。
     * 参数经 -EncodedCommand 传入，防止安装目录含空格时被截断。
     */
    private void runVirtualDriverScript(Path script, String extraArguments, String logName) {
        Path logFile = Path.of(System.getProperty("java.io.tmpdir"), logName);
        StringBuilder inner = new StringBuilder("$ErrorActionPreference='Continue'; & ")
                .append(psQuote(script.toString()));
        if (extraArguments != null && !extraArguments.isBlank()) {
            inner.append(' ').append(extraArguments);
        }
        inner.append(" 2>&1 | Out-File -FilePath ").append(psQuote(logFile.toString()))
                .append(" -Encoding utf8; exit $LASTEXITCODE");
        String encoded = java.util.Base64.getEncoder().encodeToString(
                inner.toString().getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
        String command = "$p = Start-Process -FilePath 'powershell.exe' -ArgumentList "
                + "'-NoProfile -ExecutionPolicy Bypass -EncodedCommand " + encoded + "'"
                + " -Verb RunAs -Wait -PassThru; exit $p.ExitCode";
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile",
                "-ExecutionPolicy", "Bypass", "-Command", command);
        builder.redirectErrorStream(true);
        virtualDriverButton.setEnabled(false);
        if (vbCableButton != null) {
            vbCableButton.setEnabled(false);
        }
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                Process process = builder.start();
                process.getInputStream().readAllBytes();
                return process.waitFor();
            }

            @Override
            protected void done() {
                int exitCode = -1;
                try {
                    exitCode = get();
                } catch (Exception e) {
                    LogService.error("虚拟声卡脚本执行失败", e);
                }
                String output = "";
                try {
                    output = Files.readString(logFile, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    // 脚本可能在提权窗口未产生输出
                }
                LogService.info("虚拟声卡操作结束: exit=" + exitCode
                        + (output.isBlank() ? "" : System.lineSeparator() + output));
                refreshDeviceLists();
                // 音频端点通常在设备安装后延迟出现，稍后再扫一次
                Timer rescan = new Timer(1500, ev -> refreshDeviceLists());
                rescan.setRepeats(false);
                rescan.start();
                boolean ok = exitCode == 0 || exitCode == 3;
                String message;
                if (exitCode == 3) {
                    message = com.voicechanger.util.I18n.t("虚拟声卡已写入系统，需要重启电脑后生效。");
                } else if (ok) {
                    message = com.voicechanger.util.I18n.t("虚拟声卡操作已结束。")
                            + System.lineSeparator()
                            + com.voicechanger.util.I18n.t("如果脚本提示需要重启，请重启电脑后再次打开软件。");
                } else {
                    message = com.voicechanger.util.I18n.t("虚拟声卡操作失败，请查看日志了解详情。");
                }
                JOptionPane.showMessageDialog(SettingsMenu.this, message,
                        com.voicechanger.util.I18n.t("虚拟声卡"),
                        ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            }
        }.execute();
    }

    private static String psQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    public void syncFromSettings() {
        monitorToggle.setSelected(settings.monitorEnabled(), false);
        monitorCombo.setEnabled(settings.monitorEnabled());
        monitorVolume.setValue(Math.round(settings.monitorVolume() * 100));
        monitorVolumeValue.setText(Math.round(settings.monitorVolume() * 100) + "%");
        logField.setText(settings.logDirectory());
        logField.setCaretPosition(0);
    }

    private void persist() {
        if (onPersist != null) {
            onPersist.run();
        }
    }

    public void showBelow(Component anchor) {
        refreshDeviceLists();
        syncFromSettings();
        refreshTuningCode();
        pack();
        int x = anchor.getLocationOnScreen().x + anchor.getWidth() - getWidth();
        int y = anchor.getLocationOnScreen().y + anchor.getHeight() + 8;
        setLocation(x, y);
        setVisible(true);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible && onHide != null) {
            onHide.run();
        }
    }

    /** ESC 关闭菜单。 */
    public void bindEscape() {
        getRootPane().registerKeyboardAction(e -> setVisible(false),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static ListCellRenderer<? super DeviceInfo> deviceRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index,
                        isSelected, cellHasFocus);
                if (value instanceof DeviceInfo info) {
                    label.setText(info.label());
                }
                label.setBorder(Ui.padding(2, 6, 2, 6));
                return label;
            }
        };
    }

    private static JButton pillButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(Ui.font(12f));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
        button.setBackground(new Color(0xF2F2F2));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

}
