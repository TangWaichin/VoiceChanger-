package com.voicechanger.ui;

import com.voicechanger.util.I18n;
import com.voicechanger.util.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.util.function.Consumer;

/**
 * 语言选择菜单：呼出逻辑与设置菜单一致（锚点下方弹出、ESC 关闭）。
 * 提供中文 / English 两个选项，选中后立即生效并回调保存。
 */
public final class LanguageMenu extends JDialog {

    private final Consumer<String> onSelect;
    private final Runnable onHide;
    private final JLabel titleLabel = Ui.label("语言 / Language", 14f, Ui.TEXT, Font.BOLD);
    private final JButton zhButton = optionButton("中文");
    private final JButton enButton = optionButton("English");

    public LanguageMenu(Window owner, Consumer<String> onSelect, Runnable onHide) {
        super(owner, "语言 / Language", ModalityType.MODELESS);
        this.onSelect = onSelect;
        this.onHide = onHide;
        setUndecorated(true);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE_STRONG),
                Ui.padding(18, 18, 16, 18)));
        root.add(titleLabel, BorderLayout.NORTH);

        JPanel options = new JPanel(new GridLayout(1, 2, 12, 0));
        options.setOpaque(false);
        options.add(zhButton);
        options.add(enButton);
        root.add(options, BorderLayout.CENTER);

        setContentPane(root);
        zhButton.addActionListener(e -> choose(I18n.ZH));
        enButton.addActionListener(e -> choose(I18n.EN));
        bindEscape();
    }

    private void choose(String language) {
        if (onSelect != null) {
            onSelect.accept(language);
        }
        setVisible(false);
    }

    public void showBelow(Component anchor) {
        refreshSelection();
        pack();
        int x = anchor.getLocationOnScreen().x + anchor.getWidth() - getWidth();
        int y = anchor.getLocationOnScreen().y + anchor.getHeight() + 8;
        setLocation(x, y);
        setVisible(true);
    }

    /** 当前语言对应的选项用黑底反色高亮。 */
    private void refreshSelection() {
        styleOption(zhButton, !I18n.isEnglish());
        styleOption(enButton, I18n.isEnglish());
    }

    private static void styleOption(JButton button, boolean selected) {
        button.setBackground(selected ? Ui.BLACK : new Color(0xF2F2F2));
        button.setForeground(selected ? Color.WHITE : Ui.TEXT);
        button.repaint();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible && onHide != null) {
            onHide.run();
        }
    }

    private void bindEscape() {
        getRootPane().registerKeyboardAction(e -> setVisible(false),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static JButton optionButton(String text) {
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
        button.setFont(Ui.font(12.5f));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
        button.setBackground(new Color(0xF2F2F2));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(110, 40));
        return button;
    }
}
