package com.voicechanger.ui;

import com.voicechanger.util.Ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 简短的电平条：浅灰轨道 + 黑色实时电平。
 */
public final class LevelMeter extends JComponent {

    private float value;

    public LevelMeter() {
        setPreferredSize(new Dimension(120, 6));
        setOpaque(false);
    }

    public void setValue(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - this.value) > 0.005f) {
            this.value = clamped;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int height = getHeight();
        g2.setColor(new Color(0xEDEDED));
        g2.fillRoundRect(0, 0, getWidth(), height, height, height);
        int filled = Math.round(getWidth() * value);
        if (filled > 2) {
            g2.setColor(Ui.BLACK);
            g2.fillRoundRect(0, 0, filled, height, height, height);
        }
        g2.dispose();
    }
}
