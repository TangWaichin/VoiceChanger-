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
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 语言按钮左侧的官网按钮：灰色描边白色圆 + 黑色房子图标；
 * 点击后用系统默认浏览器打开 VoiceChanger 官网。
 */
public final class WebsiteButton extends JComponent {

    private boolean hover;
    private boolean pressed;
    private Runnable onClick;

    public WebsiteButton() {
        setPreferredSize(new Dimension(46, 46));
        setMinimumSize(new Dimension(46, 46));
        setMaximumSize(new Dimension(46, 46));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(false);
        setToolTipText("官网 / Website");
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
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressed = false;
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

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int size = 38;
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        boolean inverted = pressed;
        g2.setColor(inverted ? Ui.BLACK : (hover ? new Color(0xF1F1F1) : Color.WHITE));
        g2.fillOval(x, y, size, size);
        g2.setColor(new Color(0xBDBDBD));
        g2.setStroke(new BasicStroke(1.6f));
        g2.drawOval(x, y, size, size);

        double cx = x + size / 2d;
        double cy = y + size / 2d;
        g2.setColor(inverted ? Color.WHITE : Ui.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        // 屋顶
        g2.draw(new Line2D.Double(cx - 9, cy - 1.5, cx, cy - 9.5));
        g2.draw(new Line2D.Double(cx, cy - 9.5, cx + 9, cy - 1.5));
        // 墙体
        g2.draw(new RoundRectangle2D.Double(cx - 7, cy - 1.5, 14, 10, 2, 2));
        // 门
        g2.draw(new RoundRectangle2D.Double(cx - 2.2, cy + 3.8, 4.4, 4.7, 1, 1));
        g2.dispose();
    }
}
