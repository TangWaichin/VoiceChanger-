package com.voicechanger.ui;

import com.voicechanger.util.Ui;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * 设置按钮左侧的语言按钮：灰色描边白色圆 + 黑色地球图标；
 * 风格与 GearButton 一致，按下后反色并呼出语言选择菜单。
 */
public final class GlobeButton extends JComponent {

    private boolean active;
    private boolean hover;
    private Runnable onClick;

    public GlobeButton() {
        setPreferredSize(new Dimension(46, 46));
        setMinimumSize(new Dimension(46, 46));
        setMaximumSize(new Dimension(46, 46));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(false);
        setToolTipText("语言 / Language");
        ToolTipManager.sharedInstance().setInitialDelay(300);
        addMouseListener(new MouseAdapter() {
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

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClick != null) {
                    onClick.run();
                }
            }
        });
    }

    public void onClick(Runnable onClick) {
        this.onClick = onClick;
    }

    public void setActive(boolean active) {
        this.active = active;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int size = 38;
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        g2.setColor(active ? Ui.BLACK : (hover ? new Color(0xF1F1F1) : Color.WHITE));
        g2.fillOval(x, y, size, size);
        g2.setColor(new Color(0xBDBDBD));
        g2.setStroke(new BasicStroke(1.6f));
        g2.drawOval(x, y, size, size);

        double cx = x + size / 2d;
        double cy = y + size / 2d;
        double r = 11.2;
        g2.setColor(active ? Color.WHITE : Ui.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        // 地球外圆
        g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        // 经线（中间竖直椭圆）
        g2.draw(new Ellipse2D.Double(cx - r * 0.45, cy - r, r * 0.9, r * 2));
        // 赤道与上下纬线
        g2.draw(new Line2D.Double(cx - r, cy, cx + r, cy));
        double lat = r * 0.45;
        double halfChord = Math.sqrt(r * r - lat * lat);
        g2.draw(new Line2D.Double(cx - halfChord, cy - lat, cx + halfChord, cy - lat));
        g2.draw(new Line2D.Double(cx - halfChord, cy + lat, cx + halfChord, cy + lat));
        g2.dispose();
    }
}
