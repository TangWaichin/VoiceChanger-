import com.voicechanger.audio.AudioConverter;
import com.voicechanger.audio.AudioEngine;
import com.voicechanger.audio.AudioFormatSpec;
import com.voicechanger.audio.ChannelDownmixer;
import com.voicechanger.audio.CustomAudioBridge;
import com.voicechanger.audio.DeviceInfo;
import com.voicechanger.audio.DeviceManager;
import com.voicechanger.config.AppSettings;
import com.voicechanger.config.PresetStore;
import com.voicechanger.dsp.AudioProcessor;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorCatalog;
import com.voicechanger.dsp.ProcessorState;
import com.voicechanger.dsp.processors.EchoProcessor;
import com.voicechanger.logging.LogService;
import com.voicechanger.ui.CustomInterfacePanel;
import com.voicechanger.ui.MainWindow;

import javax.swing.SwingUtilities;
import javax.sound.sampled.AudioFormat;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 离线自测：验证模块注册、DSP 稳定性、变调精度、存档与日志机制。
 */
public final class SelfTest {

    private static int failures;
    private static final int SR = 48000;

    public static void main(String[] args) throws Exception {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(60000);
            } catch (InterruptedException ignored) {
            }
            System.out.println("自测超时");
            System.exit(3);
        });
        watchdog.setDaemon(true);
        watchdog.start();
        Path base = Files.createTempDirectory("voice-changer-test");
        LogService.initialize(base, "");
        check("日志文件已创建", Files.exists(base.resolve(LogService.LOG_FILE_NAME)));

        ProcessorCatalog catalog = ProcessorCatalog.load(base);
        check("内置模块数量为 12", catalog.entries().size() == 12);
        for (ProcessorCatalog.Entry entry : catalog.entries()) {
            check("模块参数非空: " + entry.id(), !entry.params().isEmpty());
        }

        for (ProcessorCatalog.Entry entry : catalog.entries()) {
            ProcessorState state = catalog.newState(entry.id());
            state.setEnabled(true);
            AudioProcessor processor = catalog.create(state);
            processor.prepare(SR, 480);
            float[] buffer = sine(220f, SR);
            int offset = 0;
            while (offset < buffer.length) {
                int block = Math.min(480, buffer.length - offset);
                float[] chunk = new float[block];
                System.arraycopy(buffer, offset, chunk, 0, block);
                processor.process(chunk, block);
                check("输出有限值: " + entry.id(), allFinite(chunk));
                check("输出未失控: " + entry.id(), maxAbs(chunk) < 8f);
                offset += block;
            }
        }

        // 变调精度：+12 半音应把 220Hz 变为约 440Hz
        ProcessorCatalog.Entry pitchEntry = catalog.find("pitch");
        ProcessorState pitchState = new ProcessorState("pitch", pitchEntry.params());
        pitchState.setValue(find(pitchEntry.params(), "semitones"), 12f);
        AudioProcessor shifter = catalog.create(pitchState);
        shifter.prepare(SR, 480);
        float[] input = sine(220f, SR);
        float[] output = new float[SR];
        for (int i = 0; i < input.length; i += 480) {
            float[] chunk = new float[480];
            System.arraycopy(input, i, chunk, 0, 480);
            shifter.process(chunk, 480);
            System.arraycopy(chunk, 0, output, i, Math.min(480, output.length - i));
        }
        double freq = frequency(output, 12000, SR);
        System.out.printf("变调 +12 半音测得频率: %.1f Hz (期望约 440 Hz)%n", freq);
        check("变调频率误差 < 8%", Math.abs(freq - 440d) / 440d < 0.08);

        // 共振峰模块不改变基频
        ProcessorCatalog.Entry formantEntry = catalog.find("formant");
        ProcessorState formantState = new ProcessorState("formant", formantEntry.params());
        formantState.setValue(find(formantEntry.params(), "shift"), 6f);
        AudioProcessor formant = catalog.create(formantState);
        formant.prepare(SR, 480);
        float[] fOut = new float[SR];
        for (int i = 0; i < input.length; i += 480) {
            float[] chunk = new float[480];
            System.arraycopy(input, i, chunk, 0, 480);
            formant.process(chunk, 480);
            System.arraycopy(chunk, 0, fOut, i, Math.min(480, fOut.length - i));
        }
        double formantFreq = frequency(fOut, 16000, SR);
        System.out.printf("共振峰 +6 半音测得基频: %.1f Hz (期望约 220 Hz)%n", formantFreq);
        check("共振峰不改变基频", Math.abs(formantFreq - 220d) / 220d < 0.05);

        // 关闭的模块必须被跳过
        ProcessorState disabled = catalog.newState("pitch");
        disabled.setEnabled(false);
        check("默认关闭状态", !catalog.newState("pitch").enabled());

        // 存档往返
        AppSettings settings = new AppSettings(catalog);
        settings.setLogDirectory("D:/logs/demo");
        settings.setSourceDeviceName("Mic A");
        settings.setOutputDeviceName("CABLE Input");
        settings.setMonitorEnabled(true);
        settings.setMonitorDeviceName("Headphones");
        settings.setMonitorVolume(0.42f);
        settings.state("pitch").setEnabled(true);
        settings.state("pitch").setValue(find(pitchEntry.params(), "semitones"), -7f);
        settings.state("reverb").setValue(find(catalog.find("reverb").params(), "mix"), 66f);
        PresetStore store = new PresetStore(base);
        store.save(settings);
        check("存档文件已生成", store.exists());

        AppSettings restored = new AppSettings(catalog);
        check("存档读取成功", store.load(restored));
        check("信号源已恢复", "Mic A".equals(restored.sourceDeviceName()));
        check("输出设备已恢复", "CABLE Input".equals(restored.outputDeviceName()));
        check("耳返开关已恢复", restored.monitorEnabled());
        check("耳返设备已恢复", "Headphones".equals(restored.monitorDeviceName()));
        check("耳返音量已恢复", Math.abs(restored.monitorVolume() - 0.42f) < 0.001f);
        check("日志地址已恢复", "D:/logs/demo".equals(restored.logDirectory()));
        check("变调开关已恢复", restored.state("pitch").enabled());
        check("变调数值已恢复", Math.abs(restored.state("pitch").value("semitones", 0f) + 7f) < 0.01f);
        check("混响数值已恢复", Math.abs(restored.state("reverb").value("mix", 0f) - 66f) < 0.01f);

        // 调音编码：往返一致、幂等、存档同步、非法输入拒绝
        verifyTuningCodec(catalog, settings, restored, store);

        // 本轮回归项：回声最大延迟、自定义接口数据通路、脚本退出、模式切换、日志落盘
        verifyEchoMaxDelay();
        verifyAudioFormatSupport();
        verifyCustomBridge();
        verifyCustomPanelScriptExit(base);
        verifyCustomFileAutoRefresh(base);
        verifyCustomScriptCascadeStop(base);
        verifyCustomModeUi(base, catalog);
        verifyLogFlush(base);

        // 自定义日志目录自动创建
        Path customLogs = base.resolve("custom-logs");
        Path logFile = LogService.initialize(base, customLogs.toString());
        check("自定义日志目录已创建并写入", logFile != null && Files.exists(logFile));

        List<DeviceInfo> inputs = DeviceManager.inputDevices();
        List<DeviceInfo> outputs = DeviceManager.outputDevices();
        System.out.println("录音设备 " + inputs.size() + " 个: " + inputs);
        System.out.println("播放设备 " + outputs.size() + " 个: " + outputs);
        System.out.println("检测到虚拟声卡: " + DeviceManager.hasVirtualCable(outputs));
        check("音频格式为 48k 单声道 16bit", AudioFormatSpec.std().getSampleRate() == 48000f
                && AudioFormatSpec.std().getChannels() == 1);

        System.out.println(failures == 0 ? "全部自测通过" : ("失败项: " + failures));
        System.exit(failures > 0 ? 1 : 0);
    }

    /** 回声 800ms 上限必须完整生效（回归：延迟线容量不足会被截断到约 682ms）。 */
    private static void verifyEchoMaxDelay() {
        List<ParamSpec> specs = EchoProcessor.PARAMS;
        ProcessorState state = new ProcessorState("echo", specs);
        state.setEnabled(true);
        for (ParamSpec spec : specs) {
            switch (spec.id()) {
                case "delay" -> state.setValue(spec, 800f);
                case "feedback" -> state.setValue(spec, 0f);
                case "mix" -> state.setValue(spec, 100f);
                default -> { }
            }
        }
        EchoProcessor echo = new EchoProcessor(state);
        echo.prepare(SR, 2048);
        float[] buffer = new float[SR];
        buffer[0] = 1f;
        echo.process(buffer, buffer.length);
        int peak = -1;
        float max = 0f;
        for (int i = 1000; i < buffer.length; i++) {
            float abs = Math.abs(buffer[i]);
            if (abs > max) {
                max = abs;
                peak = i;
            }
        }
        int expected = Math.round(0.8f * SR);
        check("回声 800ms 延迟完整生效", Math.abs(peak - expected) <= 2);
    }

    /** 多种采样规格编解码、sinc 抗混叠与多声道下混。 */
    private static void verifyAudioFormatSupport() {
        verifyPcmRoundTrip(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 1, 2, 48000, false), 1e-4, "16bit PCM");
        verifyPcmRoundTrip(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 24, 1, 3, 48000, false), 1e-5, "24bit PCM");
        verifyPcmRoundTrip(new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 48000, 32, 1, 4, 48000, false), 1e-6, "32bit float");
        verifyPcmRoundTrip(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 32, 1, 4, 48000, false), 1e-5, "32bit int");
        verifyPcmRoundTrip(new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 48000, 8, 1, 1, 48000, false), 0.01, "8bit PCM");
        check("格式描述包含位深", AudioFormatSpec.describe(
                new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 48000, 32, 2, 8, 48000, false))
                .contains("32bit float"));

        // 96k -> 48k，30kHz 输入会混叠到 18kHz，抗混叠后应低于 -60dB
        int inRate = 96000;
        int outRate = 48000;
        float[] input = new float[inRate];
        for (int i = 0; i < input.length; i++) {
            input[i] = 0.4f * (float) Math.sin(2 * Math.PI * 30000.0 * i / inRate);
        }
        AudioConverter.StreamResampler resampler = new AudioConverter.StreamResampler(inRate, outRate);
        float[] output = new float[outRate + 1024];
        int written = 0;
        float[] block = new float[480];
        float[] converted = new float[resampler.maxOutputFrames(480)];
        for (int offset = 0; offset < input.length; offset += 480) {
            int count = Math.min(480, input.length - offset);
            System.arraycopy(input, offset, block, 0, count);
            int n = resampler.process(block, count, converted);
            if (written + n <= output.length) {
                System.arraycopy(converted, 0, output, written, n);
            }
            written += n;
        }
        double alias = toneAmplitude(output, 4800, 4096, 18000.0, outRate);
        check("sinc 重采样混叠抑制 > 60dB", alias / 0.4 < 0.001);

        ChannelDownmixer downmix = new ChannelDownmixer();
        downmix.prepare(2);
        float[] stereo = new float[4000];
        for (int i = 0; i < 2000; i++) {
            stereo[i * 2] = 0.5f;
            stereo[i * 2 + 1] = 0.5f;
        }
        float inPhase = 0f;
        for (int i = 0; i < 2000; i++) {
            inPhase = downmix.process(stereo, i * 2, 2);
        }
        check("同相立体声下混幅度保持", Math.abs(inPhase - 0.5f) < 0.02f);

        ChannelDownmixer anti = new ChannelDownmixer();
        anti.prepare(2);
        for (int i = 0; i < 2000; i++) {
            stereo[i * 2] = 0.5f;
            stereo[i * 2 + 1] = -0.5f;
        }
        float antiPhase = 0f;
        for (int i = 0; i < 2000; i++) {
            antiPhase = anti.process(stereo, i * 2, 2);
        }
        check("反相立体声下混不塌陷", Math.abs(antiPhase) > 0.3f);
    }

    private static void verifyPcmRoundTrip(AudioFormat format, double tolerance, String name) {
        float[] input = {0.5f, -0.5f, 0.25f, -0.25f, 0.125f, -0.125f};
        int bytes = AudioConverter.bytesPerSample(format);
        byte[] pcm = new byte[input.length * bytes];
        AudioConverter.monoToPcm(format, input, input.length, pcm);
        float[] output = new float[input.length];
        int frames = AudioConverter.pcmToMono(format, pcm, 0, pcm.length, output);
        boolean ok = frames == input.length;
        for (int i = 0; i < input.length && ok; i++) {
            ok = Math.abs(output[i] - input[i]) <= tolerance;
        }
        check(name + " 编解码往返", ok);
    }

    private static double toneAmplitude(float[] data, int offset, int count, double freq, double rate) {
        double re = 0;
        double im = 0;
        for (int i = 0; i < count; i++) {
            double w = 2 * Math.PI * freq * i / rate;
            re += data[offset + i] * Math.cos(w);
            im -= data[offset + i] * Math.sin(w);
        }
        return 2 * Math.sqrt(re * re + im * im) / count;
    }

    /** 自定义接口数据通路：480 帧常规块、525 帧超长块、虚拟麦克风回传。 */
    private static void verifyCustomBridge() throws Exception {
        CustomAudioBridge bridge = new CustomAudioBridge();
        CountDownLatch received = new CountDownLatch(1);
        float[] captured = new float[CustomAudioBridge.BLOCK_FRAMES];
        boolean[] sinkOk = {false};
        if (!bridge.start((mono, length) -> {
            if (length != CustomAudioBridge.BLOCK_FRAMES) {
                return;
            }
            boolean ok = true;
            for (int i = 0; i < length && ok; i++) {
                ok = Math.abs(mono[i] - 16000f / 32768f) < 1e-4f;
            }
            if (ok) {
                System.arraycopy(mono, 0, captured, 0, length);
                sinkOk[0] = true;
                received.countDown();
            }
        })) {
            System.out.println("[SKIP] 自定义接口端口被占用，跳过桥接回归");
            return;
        }
        try (Socket mic = new Socket(InetAddress.getLoopbackAddress(), CustomAudioBridge.MIC_API_PORT)) {
            mic.setSoTimeout(3000);
            Thread.sleep(200);
            check("实体麦克风 API 常规块转发", pushAndRead(bridge, mic, CustomAudioBridge.BLOCK_FRAMES));
            check("实体麦克风 API 超长块按 480 帧重分块", pushAndRead(bridge, mic, 525));
            check("实体麦克风 API 跨块余量拼接", pushCrossBlock(bridge, mic));
        }

        try (Socket out = new Socket(InetAddress.getLoopbackAddress(), CustomAudioBridge.VIRTUAL_MIC_API_PORT)) {
            byte[] pcm = new byte[CustomAudioBridge.BLOCK_BYTES];
            for (int i = 0; i < CustomAudioBridge.BLOCK_FRAMES; i++) {
                pcm[i * 2] = (byte) (16000 & 0xFF);
                pcm[i * 2 + 1] = (byte) ((16000 >> 8) & 0xFF);
            }
            out.getOutputStream().write(pcm);
            out.getOutputStream().flush();
            check("虚拟麦克风 API 数据到达下游", received.await(3, TimeUnit.SECONDS));
            check("虚拟麦克风 API 数据完整", sinkOk[0] && captured.length == CustomAudioBridge.BLOCK_FRAMES);
        }
        bridge.stop();
        check("桥接停止后接口释放", !bridge.isRunning());
    }

    private static boolean pushAndRead(CustomAudioBridge bridge, Socket mic, int frames) throws Exception {
        float[] block = new float[frames];
        java.util.Arrays.fill(block, 0.5f);
        bridge.pushMicrophone(block, frames);
        // API 约定每次固定 480 帧（960 字节），不足一帧的部分由桥内部缓存
        byte[] pcm = new byte[CustomAudioBridge.BLOCK_BYTES];
        InputStream in = mic.getInputStream();
        int filled = 0;
        while (filled < pcm.length) {
            int n = in.read(pcm, filled, pcm.length - filled);
            if (n <= 0) {
                return false;
            }
            filled += n;
        }
        int first = (pcm[1] << 8) | (pcm[0] & 0xFF);
        return filled == CustomAudioBridge.BLOCK_BYTES && Math.abs(first - 16384) <= 1;
    }

    /**
     * 上一用例推送 525 帧后桥内还余 45 帧；再推 480 帧应发满 960 字节，
     * 前 45 帧是旧数据、其余是新数据，验证跨块拼接没有错位。
     */
    private static boolean pushCrossBlock(CustomAudioBridge bridge, Socket mic) throws Exception {
        float[] block = new float[CustomAudioBridge.BLOCK_FRAMES];
        java.util.Arrays.fill(block, -0.25f);
        bridge.pushMicrophone(block, block.length);
        byte[] pcm = new byte[CustomAudioBridge.BLOCK_BYTES];
        InputStream in = mic.getInputStream();
        int filled = 0;
        while (filled < pcm.length) {
            int n = in.read(pcm, filled, pcm.length - filled);
            if (n <= 0) {
                return false;
            }
            filled += n;
        }
        int head = (pcm[1] << 8) | (pcm[0] & 0xFF);
        int boundary = (pcm[91] << 8) | (pcm[90] & 0xFF);
        int tail = (pcm[959] << 8) | (pcm[958] & 0xFF);
        return Math.abs(head - 16384) <= 1
                && Math.abs(boundary + 8192) <= 1
                && Math.abs(tail + 8192) <= 1;
    }

    /** 自定义脚本自行退出后，列表状态必须从“运行中”恢复。 */
    private static void verifyCustomPanelScriptExit(Path base) throws Exception {
        if (GraphicsEnvironment.isHeadless()
                || !System.getProperty("os.name", "").toLowerCase().contains("win")) {
            System.out.println("[SKIP] 无图形或非 Windows 环境，跳过脚本退出回归");
            return;
        }
        Path dataDir = base.resolve("custom-panel-test");
        Path script = dataDir.resolve("custom").resolve("exit-test.bat");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "@echo off\r\nexit /b 0\r\n");

        CustomInterfacePanel[] panel = new CustomInterfacePanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new CustomInterfacePanel(dataDir));
        Method select = CustomInterfacePanel.class.getDeclaredMethod("selectFile", Path.class);
        select.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                select.invoke(panel[0], script);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(1500);
        Field activeField = CustomInterfacePanel.class.getDeclaredField("activeFile");
        activeField.setAccessible(true);
        Field statusField = CustomInterfacePanel.class.getDeclaredField("statusLabel");
        statusField.setAccessible(true);
        boolean cleared = activeField.get(panel[0]) == null;
        String status = ((javax.swing.JLabel) statusField.get(panel[0])).getText();
        check("脚本退出后激活状态已清除", cleared);
        check("脚本退出后状态提示已更新", status != null && status.startsWith("自定义调音文件已停止:"));
    }

    /** 列表自动刷新：目录新增文件后，下一轮刷新应显示出来，且列表区标注刷新间隔。 */
    private static void verifyCustomFileAutoRefresh(Path base) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[SKIP] 无图形环境，跳过列表自动刷新回归");
            return;
        }
        Path dataDir = base.resolve("custom-refresh-test");
        Path customDir = dataDir.resolve("custom");
        Files.createDirectories(customDir);
        CustomInterfacePanel[] panel = new CustomInterfacePanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new CustomInterfacePanel(dataDir));
        check("初始列表无新增文件", !containsText(panel[0], "auto-refresh.py"));
        check("列表区显示刷新间隔标注", containsText(panel[0], "每 5 秒自动刷新"));

        Files.writeString(customDir.resolve("auto-refresh.py"), "print('hi')\n");
        Method refresh = CustomInterfacePanel.class.getDeclaredMethod("refreshIfChanged");
        refresh.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> invoke(refresh, panel[0]));
        check("新增文件后列表自动刷新", containsText(panel[0], "auto-refresh.py"));
    }

    /** 切回默认面板时必须连同 bat/cmd 包裹脚本的子进程一起关闭。 */
    private static void verifyCustomScriptCascadeStop(Path base) throws Exception {
        if (GraphicsEnvironment.isHeadless()
                || !System.getProperty("os.name", "").toLowerCase().contains("win")) {
            System.out.println("[SKIP] 无图形或非 Windows 环境，跳过脚本级联停止回归");
            return;
        }
        Path dataDir = base.resolve("custom-cascade-test");
        Path script = dataDir.resolve("custom").resolve("cascade-test.bat");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "@echo off\r\nping -n 60 127.0.0.1 > nul\r\n");

        CustomInterfacePanel[] panel = new CustomInterfacePanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new CustomInterfacePanel(dataDir));
        Method select = CustomInterfacePanel.class.getDeclaredMethod("selectFile", Path.class);
        select.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> invoke(select, panel[0], script));
        Thread.sleep(1000);
        Process process = (Process) readField(panel[0], "activeProcess");
        check("bat 脚本已启动", process != null && process.isAlive());
        List<ProcessHandle> children = process == null ? List.of() : process.descendants().toList();
        check("bat 脚本存在子进程", !children.isEmpty());

        Method stop = CustomInterfacePanel.class.getDeclaredMethod("stopActiveFile");
        stop.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> invoke(stop, panel[0]));
        Thread.sleep(1000);
        boolean alive = (process != null && process.isAlive())
                || children.stream().anyMatch(ProcessHandle::isAlive);
        check("切回默认面板后脚本及子进程全部关闭", !alive);
    }

    private static void invoke(Method method, Object target, Object arg) {
        try {
            method.invoke(target, arg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean containsText(java.awt.Container container, String text) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof javax.swing.JLabel label && text.equals(label.getText())) {
                return true;
            }
            if (component instanceof java.awt.Container child && containsText(child, text)) {
                return true;
            }
        }
        return false;
    }

    /** 模式切换：自定义接口开启/关闭时面板状态与引擎状态一致。 */
    private static void verifyCustomModeUi(Path base, ProcessorCatalog catalog) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[SKIP] 无图形环境，跳过模式切换回归");
            return;
        }
        if (portInUse(CustomAudioBridge.MIC_API_PORT) || portInUse(CustomAudioBridge.VIRTUAL_MIC_API_PORT)) {
            System.out.println("[SKIP] 自定义接口端口被占用，跳过模式切换回归");
            return;
        }
        Path dataDir = base.resolve("custom-mode-test");
        Files.createDirectories(dataDir);
        AppSettings settings = new AppSettings(catalog);
        PresetStore preset = new PresetStore(dataDir);
        AudioEngine engine = new AudioEngine(catalog, settings.states());
        MainWindow[] window = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            window[0] = new MainWindow(settings, preset, engine, dataDir);
            window[0].setVisible(true);
            window[0].validate();
        });
        try {
            javax.swing.JLabel title = (javax.swing.JLabel) readField(window[0], "tuningTitle");
            javax.swing.JButton toggle = (javax.swing.JButton) readField(window[0], "modeToggleButton");
            javax.swing.JButton reset = (javax.swing.JButton) readField(window[0], "resetDefaultsButton");
            check("初始为默认调音面板", "调音台".equals(title.getText()) && !engine.isCustomMode());
            Method toggleMode = MainWindow.class.getDeclaredMethod("toggleCustomMode");
            toggleMode.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> invoke(toggleMode, window[0]));
            check("切换到自定义接口", engine.isCustomMode()
                    && "自定义调音接口".equals(title.getText())
                    && "切换默认面板".equals(toggle.getText())
                    && !reset.isVisible());
            SwingUtilities.invokeAndWait(() -> invoke(toggleMode, window[0]));
            check("恢复默认调音面板", !engine.isCustomMode()
                    && "调音台".equals(title.getText())
                    && reset.isVisible());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                window[0].dispose();
                engine.close();
            });
        }
    }

    /** 日志写入后应在 1.5 秒内自动落盘（回归：尾部日志长期滞留缓冲区）。 */
    private static void verifyLogFlush(Path base) throws Exception {
        Path dir = base.resolve("log-flush-test");
        Path file = LogService.initialize(dir, "");
        LogService.info("flush-check-line");
        Thread.sleep(1500);
        check("日志 1.5 秒内自动落盘", file != null && Files.readString(file).contains("flush-check-line"));
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(Method method, Object target) {
        try {
            method.invoke(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean portInUse(int port) {
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** 调音编码：纯十进制、往返幂等、存档同步、非法输入拒绝且不污染状态。 */
    private static void verifyTuningCodec(ProcessorCatalog catalog, AppSettings settings,
                                          AppSettings restored, PresetStore store) throws Exception {
        String code = com.voicechanger.config.TuningCodec.encode(settings);
        check("调音编码为纯十进制", code.chars().allMatch(Character::isDigit));
        check("存档恢复后编码一致", code.equals(com.voicechanger.config.TuningCodec.encode(restored)));

        AppSettings decoded = new AppSettings(catalog);
        com.voicechanger.config.TuningCodec.decode(decoded, code);
        check("编码解码后幂等", code.equals(com.voicechanger.config.TuningCodec.encode(decoded)));
        check("编码开关已恢复", decoded.state("pitch").enabled());
        check("编码参数已恢复", Math.abs(decoded.state("pitch").value("semitones", 0f) + 7f) < 0.01f);
        check("存档包含调音编码", PresetStore.readPreview(store.file()).contains("tuning.code=" + code));

        char[] tampered = code.toCharArray();
        tampered[10] = (char) ((tampered[10] - '0' + 1) % 10 + '0');
        check("篡改编码被拒绝", rejectsTuningCode(decoded, new String(tampered)));
        check("非数字编码被拒绝", rejectsTuningCode(decoded, code.substring(0, 20) + "A" + code.substring(21)));
        check("长度错误编码被拒绝", rejectsTuningCode(decoded, code + "0"));
        check("失败解码不污染状态", code.equals(com.voicechanger.config.TuningCodec.encode(decoded)));
    }

    private static boolean rejectsTuningCode(AppSettings settings, String code) {
        try {
            com.voicechanger.config.TuningCodec.decode(settings, code);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static ParamSpec find(List<ParamSpec> specs, String id) {
        for (ParamSpec spec : specs) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static float[] sine(float freq, int length) {
        float[] data = new float[length];
        for (int i = 0; i < length; i++) {
            data[i] = 0.4f * (float) Math.sin(2d * Math.PI * freq * i / SR);
        }
        return data;
    }

    private static double frequency(float[] data, int from, int sampleRate) {
        int crossings = 0;
        int first = -1;
        int last = -1;
        for (int i = Math.max(1, from); i < data.length; i++) {
            if (data[i - 1] <= 0f && data[i] > 0f) {
                if (first < 0) {
                    first = i;
                }
                last = i;
                crossings++;
            }
        }
        if (crossings < 3) {
            return 0d;
        }
        return (crossings - 1) * (double) sampleRate / (last - first);
    }

    private static boolean allFinite(float[] data) {
        for (float v : data) {
            if (!Float.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    private static float maxAbs(float[] data) {
        float max = 0f;
        for (float v : data) {
            max = Math.max(max, Math.abs(v));
        }
        return max;
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            failures++;
            System.out.println("[FAIL] " + name);
        } else {
            System.out.println("[ OK ] " + name);
        }
    }
}
