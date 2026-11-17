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
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * 右上角设置按钮：灰色描边白色圆 + 黑色齿轮；
 * 按下后白色变黑、黑色齿轮变白，并呼出设置菜单。
 */
public final class GearButton extends JComponent {

    private static final int TEETH = 8;
    private boolean active;
    private boolean hover;
    private Runnable onClick;

    public GearButton() {
        setPreferredSize(new Dimension(46, 46));
        setMinimumSize(new Dimension(46, 46));
        setMaximumSize(new Dimension(46, 46));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(false);
        setToolTipText("设置");
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
        g2.setColor(active ? Color.WHITE : Ui.BLACK);
        g2.fill(gearShape(cx, cy, 11.2, 7.6, 3.4));
        g2.dispose();
    }

    private Shape gearShape(double cx, double cy, double outer, double inner, double hole) {
        Area area = new Area(new Ellipse2D.Double(cx - inner, cy - inner, inner * 2, inner * 2));
        double toothWidth = outer * 0.5;
        double toothHeight = outer - inner + 2.6;
        for (int i = 0; i < TEETH; i++) {
            double angle = i * Math.PI * 2 / TEETH;
            Path2D.Double tooth = new Path2D.Double();
            tooth.append(new java.awt.geom.RoundRectangle2D.Double(
                    -toothWidth / 2, -(inner - 0.6) - toothHeight,
                    toothWidth, toothHeight, 1.6, 1.6), false);
            AffineTransform transform = AffineTransform.getTranslateInstance(cx, cy);
            transform.rotate(angle);
            area.add(new Area(transform.createTransformedShape(tooth)));
        }
        area.subtract(new Area(new Ellipse2D.Double(cx - hole, cy - hole, hole * 2, hole * 2)));
        return area;
    }
}
