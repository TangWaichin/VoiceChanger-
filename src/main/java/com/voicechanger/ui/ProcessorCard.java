package com.voicechanger.ui;

import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;
import com.voicechanger.util.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个调音模块卡片：开关 + 说明 + 各参数滑杆。
 */
public final class ProcessorCard extends JPanel {

    private final ProcessorState state;
    private final List<ParamSpec> specs;
    private final List<JSlider> sliders = new ArrayList<>();
    private final List<JLabel> valueLabels = new ArrayList<>();
    private final List<JLabel> nameLabels = new ArrayList<>();
    private final String titleText;
    private final String descriptionText;
    private final JLabel title;
    private final JTextArea description;
    private final ToggleSwitch toggle;
    private final Runnable onChange;
    /** 滑杆停止拖动一小段时间后触发，用于按需重置效果器，避免拖动途中反复打断音频。 */
    private final javax.swing.Timer settleTimer;
    /** 批量刷新（应用调音编码）时屏蔽滑杆监听，避免触发连锁保存与重置。 */
    private boolean refreshing;

    public ProcessorCard(String titleText, String descriptionText, ProcessorState state,
                         List<ParamSpec> specs, Runnable onChange,
                         java.util.function.Consumer<String> onParamsSettled) {
        this.titleText = titleText;
        this.descriptionText = descriptionText;
        this.state = state;
        this.specs = specs;
        this.onChange = onChange;
        this.settleTimer = new javax.swing.Timer(220, e -> {
            if (onParamsSettled != null) {
                onParamsSettled.accept(state.processorId());
            }
        });
        this.settleTimer.setRepeats(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(Ui.padding(18, 18, 18, 18));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        title = Ui.label(titleText, 15f, Ui.TEXT, Font.BOLD);
        header.add(title, BorderLayout.WEST);
        toggle = new ToggleSwitch();
        toggle.setSelected(state.enabled(), false);
        JPanel switchHolder = new JPanel(new BorderLayout());
        switchHolder.setOpaque(false);
        switchHolder.add(toggle, BorderLayout.EAST);
        header.add(switchHolder, BorderLayout.CENTER);
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);

        add(Box.createVerticalStrut(6));
        description = new JTextArea(descriptionText);
        description.setEditable(false);
        description.setOpaque(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFont(Ui.font(11.5f));
        description.setForeground(Ui.MUTED);
        description.setBorder(null);
        description.setAlignmentX(LEFT_ALIGNMENT);
        description.setFocusable(false);
        description.setCaret(new javax.swing.text.DefaultCaret() {
            @Override
            protected void adjustVisibility(java.awt.Rectangle nloc) {
                // 只读说明文本无需让插入符可见，避免显示时把外层滚动视图带偏
            }
        });
        description.setRows(2);
        add(description);

        for (ParamSpec spec : specs) {
            add(Box.createVerticalStrut(10));
            add(buildParamRow(spec));
        }

        toggle.addChangeListener(() -> {
            state.setEnabled(toggle.isSelected());
            applyEnabledStyle();
            if (onChange != null) {
                onChange.run();
            }
        });
        applyEnabledStyle();
        com.voicechanger.util.I18n.addListener(this::applyLanguage);
        applyLanguage();
    }

    /** 语言切换时更新卡片标题、说明与参数名。 */
    public void applyLanguage() {
        title.setText(com.voicechanger.util.I18n.t(titleText));
        description.setText(com.voicechanger.util.I18n.t(descriptionText));
        for (int i = 0; i < nameLabels.size() && i < specs.size(); i++) {
            nameLabels.get(i).setText(com.voicechanger.util.I18n.t(specs.get(i).label()));
        }
        for (int i = 0; i < valueLabels.size() && i < specs.size(); i++) {
            ParamSpec spec = specs.get(i);
            valueLabels.get(i).setText(format(spec, state.value(spec.id(), spec.defaultValue())));
        }
        revalidate();
        repaint();
    }

    private JPanel buildParamRow(ParamSpec spec) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setOpaque(false);
        block.setAlignmentX(LEFT_ALIGNMENT);

        JPanel labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);
        JLabel name = Ui.label(spec.label(), 12f, Ui.TEXT);
        JLabel value = Ui.label(format(spec, state.value(spec.id(), spec.defaultValue())), 12f, Ui.MUTED);
        labelRow.add(name, BorderLayout.WEST);
        labelRow.add(value, BorderLayout.EAST);
        labelRow.setAlignmentX(LEFT_ALIGNMENT);
        block.add(labelRow);
        nameLabels.add(name);

        JSlider slider = FlatSliderUI.create(0, spec.steps(), Math.round(spec.normalize(
                state.value(spec.id(), spec.defaultValue())) * spec.steps()));
        slider.setAlignmentX(LEFT_ALIGNMENT);
        slider.addChangeListener(e -> {
            if (refreshing) {
                return;
            }
            float raw = spec.denormalize((float) slider.getValue() / spec.steps());
            state.setValue(spec, raw);
            value.setText(format(spec, raw));
            settleTimer.restart();
            if (onChange != null) {
                onChange.run();
            }
        });
        block.add(slider);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        sliders.add(slider);
        valueLabels.add(value);
        return block;
    }

    private String format(ParamSpec spec, float value) {
        String text = String.format(java.util.Locale.ROOT, "%." + spec.decimals() + "f", value);
        return spec.unit() == null || spec.unit().isEmpty()
                ? text : text + com.voicechanger.util.I18n.t(spec.unit());
    }

    private void applyEnabledStyle() {
        boolean on = state.enabled();
        title.setForeground(on ? Ui.TEXT : new Color(0xB3B3B3));
        description.setForeground(on ? Ui.MUTED : new Color(0xC7C7C7));
        for (JSlider slider : sliders) {
            slider.setEnabled(on);
        }
        for (JLabel label : valueLabels) {
            label.setForeground(on ? Ui.MUTED : new Color(0xC7C7C7));
        }
    }

    public ProcessorState state() {
        return state;
    }

    /** 外部批量修改状态（如应用调音编码）后，刷新开关、滑杆与数值显示。 */
    public void refreshFromState() {
        settleTimer.stop();
        refreshing = true;
        try {
            toggle.setSelected(state.enabled(), false);
            for (int i = 0; i < sliders.size() && i < specs.size(); i++) {
                ParamSpec spec = specs.get(i);
                float raw = state.value(spec.id(), spec.defaultValue());
                sliders.get(i).setValue(Math.round(spec.normalize(raw) * spec.steps()));
                valueLabels.get(i).setText(format(spec, raw));
            }
        } finally {
            refreshing = false;
        }
        applyEnabledStyle();
        revalidate();
        repaint();
    }
}
