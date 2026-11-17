package com.voicechanger.ui;

import com.voicechanger.util.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 描边胶囊按钮：外观与设置按钮同一风格，仅在鼠标按下时反色（黑底白字），松开立即恢复。
 */
public final class PressInvertButton extends JButton {

    public PressInvertButton(String text) {
        super(text);
        setFont(Ui.font(11f, Font.BOLD));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        setForeground(Ui.TEXT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                setForeground(Color.WHITE);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                setForeground(Ui.TEXT);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = getHeight();
        if (getModel().isPressed()) {
            g2.setColor(Ui.BLACK);
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
