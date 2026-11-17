package com.voicechanger.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 界面中英文切换：以中文原文为 key 的字典翻译。
 * 语言代码：zh（默认）/ en；切换后通知监听器刷新界面文本。
 */
public final class I18n {

    public static final String ZH = "zh";
    public static final String EN = "en";

    private static final Map<String, String> EN_TEXT = new LinkedHashMap<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static String language = ZH;

    static {
        // 主窗口
        EN_TEXT.put("实时变声器", "Voice Changer");
        EN_TEXT.put("麦克风拦截 · 实时调音 · 虚拟麦克风输出", "Mic capture · Live tuning · Virtual mic output");
        EN_TEXT.put("设置", "Settings");
        EN_TEXT.put("语言", "Language");
        EN_TEXT.put("官网", "Website");
        EN_TEXT.put("调音台", "Mixer");
        EN_TEXT.put("开启的选项按顺序处理，关闭的选项自动跳过",
                "Enabled modules run in order; disabled ones are skipped");
        EN_TEXT.put("运行状态", "Status");
        EN_TEXT.put("未启动", "Stopped");
        EN_TEXT.put("运行中", "Running");
        EN_TEXT.put("启动失败", "Start failed");
        EN_TEXT.put("选择虚拟麦克风后点击右侧按钮开始", "Pick a virtual mic, then press Start");
        EN_TEXT.put("已输出到 ", "Output: ");
        EN_TEXT.put("开始变声", "Start");
        EN_TEXT.put("停止变声", "Stop");
        EN_TEXT.put("输入电平", "Input");
        EN_TEXT.put("输出电平", "Output");
        EN_TEXT.put("耳返电平", "Monitor");
        EN_TEXT.put("当前输出设备", "Current output device");
        EN_TEXT.put("使用步骤\n"
                + "1. 在设置里选择真实的麦克风作为信号源。\n"
                + "2. 变声输出选择虚拟声卡的播放端（如 CABLE Input）。\n"
                + "3. 在会议/游戏软件中把麦克风选为虚拟声卡的录音端（如 CABLE Output）。\n"
                + "4. 耳返可选真实耳机，边调边听；耳返音量过大易啸叫。",
                "Quick start\n"
                + "1. Select your real microphone as the source in Settings.\n"
                + "2. Set the output to a virtual cable playback end (e.g. CABLE Input).\n"
                + "3. In your meeting/game app, pick the cable recording end as the mic.\n"
                + "4. Optional monitoring via real headphones; avoid loud feedback.");
        EN_TEXT.put("日志：", "Log: ");
        EN_TEXT.put("存档：", "Preset: ");
        EN_TEXT.put("提示", "Notice");
        EN_TEXT.put("没有可用的录音设备，请检查麦克风连接。", "No recording device available. Check your microphone.");
        EN_TEXT.put("没有可用的播放设备，无法把变声结果送回电脑。", "No playback device available for the processed audio.");
        EN_TEXT.put("启动失败：", "Start failed: ");
        EN_TEXT.put("\n请检查设备是否被其他程序独占。", "\nCheck whether another app is using the device exclusively.");
        EN_TEXT.put("输入设备已断开", "Input disconnected");
        EN_TEXT.put("请检查麦克风连接后重新开始", "Reconnect the microphone and start again");
        EN_TEXT.put("输入设备已断开，变声已停止。请检查麦克风连接后重新开始。",
                "The input device was disconnected, so voice changing stopped. Reconnect it and start again.");

        // 设置菜单
        EN_TEXT.put("自动保存", "Auto-save");
        EN_TEXT.put("音频链路", "Audio routing");
        EN_TEXT.put("音频信号源", "Audio source");
        EN_TEXT.put("变声输出（虚拟麦克风）", "Output (virtual mic)");
        EN_TEXT.put("在会议/游戏软件中选择同一虚拟声卡的录音端作为麦克风。",
                "In your meeting/game app, select the recording end of the same virtual cable.");
        EN_TEXT.put("未检测到虚拟声卡，请先安装 VB-CABLE 等虚拟音频驱动。",
                "No virtual cable detected. Install VB-CABLE or a similar driver first.");
        EN_TEXT.put("虚拟声卡", "Virtual audio device");
        EN_TEXT.put("VoiceChanger 虚拟声卡已安装", "VoiceChanger virtual audio device is installed");
        EN_TEXT.put("VoiceChanger 虚拟声卡未安装", "VoiceChanger virtual audio device is not installed");
        EN_TEXT.put("未找到虚拟声卡驱动包", "Virtual audio device package not found");
        EN_TEXT.put("安装虚拟声卡", "Install virtual audio device");
        EN_TEXT.put("卸载虚拟声卡", "Uninstall virtual audio device");
        EN_TEXT.put("安装虚拟声卡需要管理员权限，安装后可能需要重启。是否继续？",
                "Installing the virtual audio device requires administrator rights and may require a reboot. Continue?");
        EN_TEXT.put("卸载虚拟声卡需要管理员权限。是否继续？",
                "Uninstalling the virtual audio device requires administrator rights. Continue?");
        EN_TEXT.put("未找到虚拟声卡驱动包，请重新安装软件。",
                "Virtual audio device package not found. Please reinstall the application.");
        EN_TEXT.put("虚拟声卡操作已结束。", "Virtual audio device operation finished.");
        EN_TEXT.put("虚拟声卡操作失败，请查看日志了解详情。",
                "Virtual audio device operation failed. See the log for details.");
        EN_TEXT.put("如果脚本提示需要重启，请重启电脑后再次打开软件。",
                "If the script asks for a reboot, restart Windows and open the app again.");
        EN_TEXT.put("安装或卸载虚拟声卡需要管理员权限，可能要求重启。",
                "Installing or removing the virtual audio device requires administrator rights and may require a reboot.");
        EN_TEXT.put("VB-CABLE 已安装", "VB-CABLE is installed");
        EN_TEXT.put("VB-CABLE 未安装", "VB-CABLE is not installed");
        EN_TEXT.put("未找到 VB-CABLE 驱动包", "VB-CABLE package not found");
        EN_TEXT.put("安装 VB-CABLE", "Install VB-CABLE");
        EN_TEXT.put("卸载 VB-CABLE", "Uninstall VB-CABLE");
        EN_TEXT.put("未找到 VB-CABLE 驱动包，请重新安装软件。",
                "VB-CABLE package not found. Please reinstall the application.");
        EN_TEXT.put("安装 VB-CABLE 需要管理员权限，安装后建议重启。是否继续？",
                "Installing VB-CABLE requires administrator rights and a reboot is recommended. Continue?");
        EN_TEXT.put("卸载 VB-CABLE 需要管理员权限，卸载后建议重启。是否继续？",
                "Uninstalling VB-CABLE requires administrator rights and a reboot is recommended. Continue?");
        EN_TEXT.put("安装或卸载 VB-CABLE 需要管理员权限，可能要求重启。",
                "Installing or removing VB-CABLE requires administrator rights and may require a reboot.");
        EN_TEXT.put("VB-CABLE 由 VB-Audio 提供（donationware），可访问 vb-cable.com 支持作者。",
                "VB-CABLE is provided by VB-Audio (donationware). Visit vb-cable.com to support the author.");
        EN_TEXT.put("虚拟声卡已写入系统，需要重启电脑后生效。",
                "The virtual audio device was written to the system. Restart Windows to finish.");
        EN_TEXT.put("耳返", "Monitoring");
        EN_TEXT.put("开启耳返监听", "Enable monitoring");
        EN_TEXT.put("耳返设备", "Monitor device");
        EN_TEXT.put("耳返音量", "Monitor volume");
        EN_TEXT.put("日志", "Logging");
        EN_TEXT.put("日志存放地址", "Log directory");
        EN_TEXT.put("浏览", "Browse");
        EN_TEXT.put("留空则保存在用户数据目录", "Leave empty to use the user data directory");
        EN_TEXT.put("刷新设备", "Refresh devices");
        EN_TEXT.put("应用并保存", "Apply & Save");
        EN_TEXT.put("选择日志存放文件夹", "Choose log directory");
        EN_TEXT.put("调音编码", "Tuning Code");
        EN_TEXT.put("一键复制调音编码", "Copy Tuning Code");
        EN_TEXT.put("一键恢复默认数值", "Restore Defaults");
        EN_TEXT.put("应用新的调音编码", "Apply Tuning Code");
        EN_TEXT.put("当前调音编码，由调音面板实时生成", "Current tuning code, generated live from the tuning panel");
        EN_TEXT.put("输入新的调音编码", "Enter a new tuning code");
        EN_TEXT.put("调音编码已复制到剪贴板", "Tuning code copied to clipboard");
        EN_TEXT.put("调音编码已应用", "Tuning code applied");
        EN_TEXT.put("调音编码无效，请检查后再试", "Invalid tuning code, please check and try again");
        EN_TEXT.put("请输入调音编码", "Please enter a tuning code");

        // 自定义调音接口
        EN_TEXT.put("切换自定义接口", "Switch to Custom API");
        EN_TEXT.put("切换默认面板", "Switch to Default Panel");
        EN_TEXT.put("自定义调音接口", "Custom Audio API");
        EN_TEXT.put("实体麦克风api接口", "Physical Mic API");
        EN_TEXT.put("虚拟麦克风api接口", "Virtual Mic API");
        EN_TEXT.put("一键复制api", "Copy API");
        EN_TEXT.put("api已复制到剪贴板", "API copied to clipboard");
        EN_TEXT.put("导入自定义调音文件", "Import Custom File");
        EN_TEXT.put("前往文件存放地址", "Open File Folder");
        EN_TEXT.put("自定义调音文件", "Custom Tuning Files");
        EN_TEXT.put("选择", "Select");
        EN_TEXT.put("运行中：", "Running: ");
        EN_TEXT.put("尚未选择自定义调音文件", "No custom file selected");
        EN_TEXT.put("自定义调音文件已导入: ", "Custom file imported: ");
        EN_TEXT.put("自定义调音文件导入失败: ", "Failed to import custom file: ");
        EN_TEXT.put("自定义调音文件启动失败: ", "Failed to start custom file: ");
        EN_TEXT.put("自定义调音文件已停止: ", "Custom file stopped: ");
        EN_TEXT.put("没有可用的自定义调音文件", "No custom files in folder");
        EN_TEXT.put("自定义调音文件已被移除，已停止运行: ", "Custom file removed, stopped: ");
        EN_TEXT.put("每 5 秒自动刷新", "Auto refresh every 5s");
        EN_TEXT.put("文件资源管理器打开失败: ", "Failed to open folder: ");
        EN_TEXT.put("音频规格", "Audio format");
        EN_TEXT.put("输入：", "In: ");
        EN_TEXT.put("驱动：", "Driver: ");
        EN_TEXT.put("输出：", "Out: ");
        EN_TEXT.put("（未启动）", "(stopped)");
        EN_TEXT.put("音频格式为 48k 单声道 16bit", "Audio format is 48k mono 16bit");
        EN_TEXT.put("自定义调音接口启动失败：端口 47801/47802 可能被占用，请关闭占用程序后重试。",
                "Failed to start the custom audio API: ports 47801/47802 may be in use. Close the occupying program and try again.");

        // 调音模块名称与描述
        EN_TEXT.put("噪声门", "Noise Gate");
        EN_TEXT.put("低于阈值的底噪被压下去，说话时才放行，可减少房间噪音。",
                "Suppresses noise below the threshold and opens when you speak, reducing room noise.");
        EN_TEXT.put("变调", "Pitch Shift");
        EN_TEXT.put("整体升降音高，颗粒越长越平滑，越短越灵敏。",
                "Shifts overall pitch. Longer grains sound smoother; shorter grains respond faster.");
        EN_TEXT.put("共振峰", "Formant");
        EN_TEXT.put("移动人声共振峰：向上更纤细，向下更浑厚，音高保持不变。",
                "Shifts vocal formants: up for a thinner tone, down for a deeper tone, pitch unchanged.");
        EN_TEXT.put("机器人", "Robot");
        EN_TEXT.put("环形调制制造机械感，方波比例越高越硬朗。",
                "Ring modulation for a mechanical feel; more square wave sounds harsher.");
        EN_TEXT.put("失真", "Distortion");
        EN_TEXT.put("过载削波带来沙哑、嘶吼的质感，驱动越大越脏。",
                "Overdrive clipping for a gritty, raspy tone; higher drive sounds dirtier.");
        EN_TEXT.put("均衡", "Equalizer");
        EN_TEXT.put("调整低、中、高频比例：低频厚度、中频清晰度、高频空气感。",
                "Balances low, mid and high bands: body, clarity and air.");
        EN_TEXT.put("压缩", "Compressor");
        EN_TEXT.put("压低过大的音量峰值并可用补偿增益抬回整体响度，声音更贴耳。",
                "Tames loud peaks; makeup gain restores overall loudness for a tighter sound.");
        EN_TEXT.put("合唱", "Chorus");
        EN_TEXT.put("叠加轻微失谐的副本，声音更宽、更有厚度。",
                "Layers slightly detuned copies for a wider, thicker sound.");
        EN_TEXT.put("颤音", "Vibrato");
        EN_TEXT.put("让音高周期性轻微摆动，唱歌与播音都更生动。",
                "Gently oscillates pitch for livelier singing and speech.");
        EN_TEXT.put("回声", "Echo");
        EN_TEXT.put("可调延迟与反馈的回声，适合营造空间感或特效。",
                "Echo with adjustable delay and feedback for space or special effects.");
        EN_TEXT.put("混响", "Reverb");
        EN_TEXT.put("模拟房间反射，房间越大尾音越长，阻尼越大高频衰减越快。",
                "Simulates room reflections. Bigger rooms mean longer tails; more damping darkens them.");
        EN_TEXT.put("输出", "Output");
        EN_TEXT.put("整体电平放大，前视限幅器提前压住超过阈值的峰值，高增益下也不破音。",
                "Boosts overall level; a look-ahead limiter catches peaks above the threshold before they reach the output, preventing clipping even at high gain.");

        // 参数标签
        EN_TEXT.put("输出增益", "Output gain");
        EN_TEXT.put("限幅阈值", "Limiter threshold");
        EN_TEXT.put("阈值", "Threshold");
        EN_TEXT.put("启动", "Attack");
        EN_TEXT.put("释放", "Release");
        EN_TEXT.put("载波频率", "Carrier freq");
        EN_TEXT.put("方波比例", "Square wave");
        EN_TEXT.put("干湿比", "Mix");
        EN_TEXT.put("音高", "Pitch");
        EN_TEXT.put("颗粒长度", "Grain size");
        EN_TEXT.put("延迟时间", "Delay time");
        EN_TEXT.put("反馈", "Feedback");
        EN_TEXT.put("速率", "Rate");
        EN_TEXT.put("深度", "Depth");
        EN_TEXT.put("低频", "Low");
        EN_TEXT.put("中频中心", "Mid center");
        EN_TEXT.put("中频", "Mid");
        EN_TEXT.put("高频", "High");
        EN_TEXT.put("基础延迟", "Base delay");
        EN_TEXT.put("驱动", "Drive");
        EN_TEXT.put("亮度", "Tone");
        EN_TEXT.put("共振峰", "Formant");
        EN_TEXT.put("压缩比", "Ratio");
        EN_TEXT.put("补偿增益", "Makeup gain");
        EN_TEXT.put("房间大小", "Room size");
        EN_TEXT.put("阻尼", "Damping");

        // 单位
        EN_TEXT.put(" 半音", " st");
    }

    private I18n() {
    }

    public static String language() {
        return language;
    }

    public static boolean isEnglish() {
        return EN.equals(language);
    }

    /** 设置语言并通知所有界面监听器；非法值按中文处理。 */
    public static void setLanguage(String value) {
        String next = EN.equalsIgnoreCase(value == null ? "" : value.trim()) ? EN : ZH;
        boolean changed = !next.equals(language);
        language = next;
        if (changed) {
            for (Runnable listener : new ArrayList<>(LISTENERS)) {
                listener.run();
            }
        }
    }

    /** 翻译中文原文；中文模式或未收录的文本原样返回。 */
    public static String t(String zhText) {
        if (zhText == null || !isEnglish()) {
            return zhText;
        }
        return EN_TEXT.getOrDefault(zhText, zhText);
    }

    public static void addListener(Runnable listener) {
        if (!LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }
}
