package com.voicechanger.ui;

import com.voicechanger.util.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 胶囊按钮：黑色实心或描边两种样式。
 */
public final class PillButton extends JButton {

    private final boolean filled;

    public PillButton(String text, boolean filled) {
        super(text);
        this.filled = filled;
        setFont(Ui.font(13f, java.awt.Font.BOLD));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(11, 20, 11, 20));
        setForeground(filled ? Color.WHITE : Ui.TEXT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = getHeight();
        if (filled) {
            g2.setColor(getModel().isPressed() ? new Color(0x333333)
                    : getModel().isRollover() ? new Color(0x2A2A2A) : Ui.BLACK);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        } else {
            g2.setColor(getModel().isRollover() ? new Color(0xF2F2F2) : Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(Ui.LINE_STRONG);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
