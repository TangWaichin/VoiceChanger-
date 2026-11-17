package com.voicechanger.ui;

import com.voicechanger.util.Ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 扁平开关：关闭为浅灰胶囊，开启为黑色胶囊。
 */
public final class ToggleSwitch extends JComponent {

    private final List<Runnable> listeners = new ArrayList<>();
    private boolean selected;
    private boolean hover;

    public ToggleSwitch() {
        setPreferredSize(new Dimension(46, 26));
        setMaximumSize(new Dimension(46, 26));
        setMinimumSize(new Dimension(46, 26));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setSelected(!selected, true);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean value, boolean notify) {
        if (selected == value) {
            return;
        }
        selected = value;
        repaint();
        if (notify) {
            for (Runnable listener : listeners) {
                listener.run();
            }
        }
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int trackHeight = height - 4;
        Color track = selected ? Ui.BLACK : (hover ? new Color(0xDEDEDE) : new Color(0xE7E7E7));
        g2.setColor(track);
        g2.fillRoundRect(0, 2, width, trackHeight, trackHeight, trackHeight);
        int knob = trackHeight - 6;
        int x = selected ? width - knob - 5 : 5;
        g2.setColor(Color.WHITE);
        g2.fillOval(x, 5, knob, knob);
        g2.setColor(new Color(0, 0, 0, 22));
        g2.drawOval(x, 5, knob, knob);
        g2.dispose();
    }
}
