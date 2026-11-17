import com.voicechanger.audio.AudioEngine;
import com.voicechanger.audio.DeviceInfo;
import com.voicechanger.audio.DeviceManager;
import com.voicechanger.audio.NativeAudio;
import com.voicechanger.config.AppSettings;
import com.voicechanger.dsp.ProcessorCatalog;

import java.nio.file.Path;
import java.util.List;

/** 验证 AudioEngine 走原生后端时整条链路可用。 */
public final class EngineNativeTest {

    public static void main(String[] args) throws Exception {
        System.out.println("native available=" + NativeAudio.isAvailable());
        ProcessorCatalog catalog = ProcessorCatalog.load(Path.of(".").toAbsolutePath().normalize());
        AppSettings settings = new AppSettings(catalog);
        AudioEngine engine = new AudioEngine(catalog, settings.states());

        List<DeviceInfo> inputs = DeviceManager.inputDevices();
        List<DeviceInfo> outputs = DeviceManager.outputDevices();
        DeviceInfo input = inputs.stream()
                .filter(d -> d.name().contains("麦克风阵列")).findFirst().orElse(inputs.get(0));
        DeviceInfo output = outputs.stream()
                .filter(d -> d.name().toLowerCase().contains("jinzhou")).findFirst().orElse(outputs.get(0));
        System.out.println("input=" + input.label() + " output=" + output.label());

        engine.start(input, output, false, null, 0.8f);
        float inPeak = 0f;
        float outPeak = 0f;
        long deadline = System.currentTimeMillis() + 2500;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            inPeak = Math.max(inPeak, engine.inputLevel());
            outPeak = Math.max(outPeak, engine.outputLevel());
        }
        engine.stop();
        System.out.println("inputLevel=" + inPeak + " outputLevel=" + outPeak);
        boolean pass = inPeak > 0.0001f && outPeak > 0.0001f;
        System.out.println(pass ? "PASS" : "FAIL");
        System.exit(pass ? 0 : 1);
    }
}
