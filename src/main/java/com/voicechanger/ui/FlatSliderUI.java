package com.voicechanger.ui;

import com.voicechanger.util.Ui;

import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 细白轨道 + 黑色圆点滑块。
 */
public final class FlatSliderUI extends BasicSliderUI {

    public FlatSliderUI(JSlider slider) {
        super(slider);
    }

    public static JSlider create(int min, int max, int value) {
        JSlider slider = new JSlider(min, max, value);
        slider.setUI(new FlatSliderUI(slider));
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setPreferredSize(new Dimension(160, 22));
        return slider;
    }

    @Override
    protected Dimension getThumbSize() {
        return new Dimension(14, 14);
    }

    @Override
    public void paintTrack(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int trackHeight = 4;
        int y = trackRect.y + trackRect.height / 2 - trackHeight / 2;
        g2.setColor(slider.isEnabled() ? Ui.TRACK : new Color(0xF2F2F2));
        g2.fillRoundRect(trackRect.x, y, trackRect.width, trackHeight, trackHeight, trackHeight);
        int thumbCenter = thumbRect.x + thumbRect.width / 2;
        int filled = thumbCenter - trackRect.x;
        if (filled > 0) {
            g2.setColor(slider.isEnabled() ? new Color(0x1A1A1A) : new Color(0xDDDDDD));
            g2.fillRoundRect(trackRect.x, y, filled, trackHeight, trackHeight, trackHeight);
        }
        g2.dispose();
    }

    @Override
    public void paintThumb(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(slider.isEnabled() ? Ui.BLACK : new Color(0xC9C9C9));
        g2.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
        g2.dispose();
    }

    @Override
    public void paintFocus(Graphics g) {
        // 不需要焦点虚线框
    }

    @Override
    protected void installDefaults(JSlider slider) {
        super.installDefaults(slider);
        slider.setBorder(null);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
    }
}
