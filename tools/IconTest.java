import com.voicechanger.audio.AudioEngine;
import com.voicechanger.config.AppSettings;
import com.voicechanger.config.PresetStore;
import com.voicechanger.dsp.ProcessorCatalog;
import com.voicechanger.logging.LogService;
import com.voicechanger.ui.MainWindow;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;

/** 窗口/任务栏图标验证：加载数量、尺寸与截图。 */
public class IconTest {

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("icon-test");
        LogService.initialize(dir, "");
        ProcessorCatalog catalog = ProcessorCatalog.load(dir);
        AppSettings settings = new AppSettings(catalog);
        PresetStore preset = new PresetStore(dir);
        AudioEngine engine = new AudioEngine(catalog, settings.states());
        MainWindow[] window = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            window[0] = new MainWindow(settings, preset, engine, dir);
            window[0].setVisible(true);
        });
        int count = window[0].getIconImages().size();
        System.out.println("loaded icons = " + count);
        for (java.awt.Image img : window[0].getIconImages()) {
            System.out.println("  size " + img.getWidth(null) + "x" + img.getHeight(null));
        }
        System.out.println(count == 4 ? "ICON TEST PASS" : "ICON TEST FAIL");
        System.exit(count == 4 ? 0 : 1);
    }
}
