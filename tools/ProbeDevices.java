import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;

public final class ProbeDevices {
    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Mixer.Info[] infos = AudioSystem.getMixerInfo();
        out.println("mixer count = " + infos.length);
        for (int i = 0; i < infos.length; i++) {
            Mixer mixer = AudioSystem.getMixer(infos[i]);
            out.println("[" + i + "] name=" + infos[i].getName()
                    + " | desc=" + infos[i].getDescription()
                    + " | vendor=" + infos[i].getVendor()
                    + " | ver=" + infos[i].getVersion()
                    + " | src=" + mixer.getSourceLineInfo().length
                    + " | tgt=" + mixer.getTargetLineInfo().length);
        }
    }
}
