import com.voicechanger.audio.NativeAudio;

import java.util.Arrays;

/** 原生引擎冒烟测试：采集 3 秒，回写虚拟声卡，统计块数与峰值。 */
public final class NativeAudioTest {

    public static void main(String[] args) throws Exception {
        System.out.println("available=" + NativeAudio.isAvailable() + " error=" + NativeAudio.loadError());
        if (!NativeAudio.isAvailable()) {
            System.exit(2);
        }
        String[] captures = NativeAudio.captureDevices();
        String[] renders = NativeAudio.renderDevices();
        System.out.println("capture devices=" + Arrays.toString(captures));
        System.out.println("render devices=" + Arrays.toString(renders));

        String capture = pick(captures, "麦克风阵列", "microphone", "麦克风");
        String render = pick(renders, "cable", "jinzhou", "virtual", "vb-audio");
        System.out.println("use capture=" + capture + " render=" + render);

        final int[] blocks = {0};
        final float[] peak = {0f};
        String error = NativeAudio.start((block, frames) -> {
            blocks[0]++;
            for (int i = 0; i < frames; i++) {
                float abs = Math.abs(block[i]);
                if (abs > peak[0]) {
                    peak[0] = abs;
                }
            }
            NativeAudio.writeOutput(block, frames);
        }, capture, render, "", false, 480);
        if (error != null) {
            System.out.println("start failed: " + error);
            System.exit(3);
        }

        Thread.sleep(3000);
        NativeAudio.stop();
        System.out.println("blocks=" + blocks[0] + " peak=" + peak[0]);
        boolean pass = blocks[0] > 50;
        System.out.println(pass ? "PASS" : "FAIL");
        System.exit(pass ? 0 : 1);
    }

    private static String pick(String[] names, String... keywords) {
        for (String keyword : keywords) {
            for (String name : names) {
                if (name.toLowerCase().contains(keyword.toLowerCase())) {
                    return name;
                }
            }
        }
        return names.length > 0 ? names[0] : "";
    }
}
