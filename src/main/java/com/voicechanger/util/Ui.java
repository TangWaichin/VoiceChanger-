package com.voicechanger.util;

import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

/**
 * 统一的视觉样式：白色为主、灰黑点缀。
 */
public final class Ui {

    public static final Color BG = new Color(0xF7F7F7);
    public static final Color CARD = Color.WHITE;
    public static final Color LINE = new Color(0xE6E6E6);
    public static final Color LINE_STRONG = new Color(0xC7C7C7);
    public static final Color TEXT = new Color(0x141414);
    public static final Color MUTED = new Color(0x8C8C8C);
    public static final Color TRACK = new Color(0xECECEC);
    public static final Color BLACK = new Color(0x111111);

    private static final String FAMILY = pickFamily();

    private Ui() {
    }

    private static String pickFamily() {
        List<String> prefs = List.of("Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
                "Noto Sans CJK SC", "Source Han Sans SC", "Segoe UI");
        List<String> available = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String name : prefs) {
            if (available.contains(name)) {
                return name;
            }
        }
        return Font.DIALOG;
    }

    public static Font font(float size) {
        return font(size, Font.PLAIN);
    }

    public static Font font(float size, int style) {
        return new Font(FAMILY, style, 12).deriveFont(style, size);
    }

    public static JLabel label(String text, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(size));
        label.setForeground(color);
        return label;
    }

    public static JLabel label(String text, float size, Color color, int style) {
        JLabel label = new JLabel(text);
        label.setFont(font(size, style));
        label.setForeground(color);
        return label;
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }

    public static Border lineBorder() {
        return new LineBorder(LINE, 1, true);
    }
}
