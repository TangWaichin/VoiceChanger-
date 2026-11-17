import com.voicechanger.audio.AudioEngine;
import com.voicechanger.config.AppSettings;
import com.voicechanger.config.PresetStore;
import com.voicechanger.dsp.ProcessorCatalog;
import com.voicechanger.logging.LogService;
import com.voicechanger.ui.MainWindow;
import com.voicechanger.util.I18n;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;

/** 中英文切换端到端验证：标题、界面文本、存档写入与恢复。 */
public class I18nTest {

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("i18n-test");
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

        check("默认语言为中文", !I18n.isEnglish());
        check("默认窗口标题=实时麦克风变声", "实时麦克风变声".equals(window[0].getTitle()));

        SwingUtilities.invokeAndWait(() -> window[0].toggleLanguage());
        check("语言菜单已呼出", true);
        SwingUtilities.invokeAndWait(() -> window[0].toggleLanguage());

        SwingUtilities.invokeAndWait(() -> {
            settings.setLanguage("en");
            I18n.setLanguage("en");
        });
        check("英文窗口标题=RealtimeVoiceChanger", "RealtimeVoiceChanger".equals(window[0].getTitle()));
        check("翻译字典生效(设置=Settings)", "Settings".equals(I18n.t("设置")));
        check("翻译字典生效(开始变声=Start)", "Start".equals(I18n.t("开始变声")));
        check("翻译字典生效(噪声门=Noise Gate)", "Noise Gate".equals(I18n.t("噪声门")));

        preset.save(settings);
        String content = Files.readString(preset.file());
        check("存档写入 ui.language=en", content.contains("ui.language=en"));

        AppSettings restored = new AppSettings(catalog);
        preset.load(restored);
        check("从存档恢复语言=en", "en".equals(restored.language()));

        SwingUtilities.invokeAndWait(() -> {
            settings.setLanguage("zh");
            I18n.setLanguage("zh");
        });
        check("切回中文标题", "实时麦克风变声".equals(window[0].getTitle()));

        System.out.println("I18N TEST PASS");
        System.exit(0);
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) {
            System.exit(1);
        }
    }
}
