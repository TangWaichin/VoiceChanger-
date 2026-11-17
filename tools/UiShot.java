import com.voicechanger.audio.AudioEngine;
import com.voicechanger.config.AppSettings;
import com.voicechanger.config.PresetStore;
import com.voicechanger.dsp.ProcessorCatalog;
import com.voicechanger.logging.LogService;
import com.voicechanger.ui.MainWindow;
import com.voicechanger.ui.SettingsMenu;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 界面截图工具：把窗口离屏渲染为 PNG，验证布局与齿轮按钮/设置菜单的绘制效果。
 */
public final class UiShot {

    public static void main(String[] args) throws Exception {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException ignored) {
            }
            System.exit(3);
        });
        watchdog.setDaemon(true);
        watchdog.start();
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("headless，跳过界面截图");
            return;
        }
        Path appDir = Files.createTempDirectory("voice-changer-ui");
        LogService.initialize(appDir, "");
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        MainWindow.applyGlobalStyle();

        ProcessorCatalog catalog = ProcessorCatalog.load(appDir);
        AppSettings settings = new AppSettings(catalog);
        settings.state("pitch").setEnabled(true);
        settings.state("pitch").setValue(find(catalog, "pitch", "semitones"), -4f);
        settings.state("formant").setEnabled(true);
        settings.state("reverb").setEnabled(true);
        PresetStore preset = new PresetStore(appDir);
        preset.save(settings);
        AudioEngine engine = new AudioEngine(catalog, settings.states());

        MainWindow[] holder = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new MainWindow(settings, preset, engine, appDir);
            holder[0].setVisible(true);
            holder[0].validate();
            holder[0].resetTuningScroll();
        });
        Thread.sleep(1200);
        capture(holder[0], "build/ui-main.png");

        SwingUtilities.invokeAndWait(() -> {
            holder[0].toggleSettings();
        });
        Thread.sleep(900);
        capture(holder[0], "build/ui-main-active.png");
        for (Window window : Window.getWindows()) {
            if (window instanceof SettingsMenu && window.isVisible()) {
                capture(window, "build/ui-settings.png");
            }
        }

        SwingUtilities.invokeAndWait(() -> {
            holder[0].dispose();
            System.exit(0);
        });
    }

    private static void capture(Window window, String path) throws Exception {
        window.validate();
        BufferedImage image = new BufferedImage(Math.max(1, window.getWidth()),
                Math.max(1, window.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        window.printAll(g);
        g.dispose();
        File out = new File(path);
        ImageIO.write(image, "png", out);
        System.out.println("截图已保存: " + out.getAbsolutePath()
                + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }

    private static com.voicechanger.dsp.ParamSpec find(ProcessorCatalog catalog, String processor,
                                                       String paramId) {
        for (com.voicechanger.dsp.ParamSpec spec : catalog.find(processor).params()) {
            if (spec.id().equals(paramId)) {
                return spec;
            }
        }
        throw new IllegalArgumentException(paramId);
    }
}
