package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.Fft;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.Arrays;
import java.util.List;

/**
 * 共振峰移动：短时频谱包络搬移，音高不变而音色（性别感、大小感）改变。
 */
public final class FormantShiftProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("shift", "共振峰", -12f, 12f, 0f, 1f, " 半音", 0),
            ParamSpec.percent("mix", "干湿比", 100f));

    private static final int FFT_SIZE = 512;
    private static final int HOP = FFT_SIZE / 4;

    private final float[] window = new float[FFT_SIZE];
    private final float[] inBuf = new float[FFT_SIZE];
    private final float[] outBuf = new float[FFT_SIZE * 4];
    private final com.voicechanger.dsp.DelayLine dryLine = new com.voicechanger.dsp.DelayLine(4096);
    private final float[] re = new float[FFT_SIZE];
    private final float[] im = new float[FFT_SIZE];
    private final float[] envelope = new float[FFT_SIZE / 2 + 1];
    private final float[] overlap = new float[FFT_SIZE];
    private Fft fft;
    private int inLen;
    private int outLen;
    private int outRead;

    public FormantShiftProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "formant";
    }

    @Override
    public String displayName() {
        return "共振峰";
    }

    @Override
    public String description() {
        return "移动人声共振峰：向上更纤细，向下更浑厚，音高保持不变。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected void onPrepare(int sampleRate, int maxFrames) {
        fft = new Fft(FFT_SIZE);
        for (int i = 0; i < FFT_SIZE; i++) {
            window[i] = (float) (0.5d - 0.5d * Math.cos(2d * Math.PI * i / FFT_SIZE));
        }
        inLen = 0;
        outLen = 0;
        outRead = 0;
        Arrays.fill(overlap, 0f);
        dryLine.clear();
    }

    @Override
    public void reset() {
        onPrepare(sampleRate(), 0);
    }

    @Override
    public void process(float[] buffer, int length) {
        if (fft == null) {
            onPrepare(sampleRate(), length);
        }
        float shift = param("shift");
        float mix = param("mix") / 100f;
        if (Math.abs(shift) < 0.01f || mix <= 0.001f) {
            return;
        }
        for (int i = 0; i < length; i++) {
            float x = buffer[i];
            dryLine.write(x);
            if (inLen == FFT_SIZE) {
                processFrame(shift, mix);
            }
            inBuf[inLen++] = x;
            if (outRead < outLen) {
                buffer[i] = outBuf[outRead++];
                if (outRead == outLen) {
                    outRead = 0;
                    outLen = 0;
                }
            } else {
                buffer[i] = 0f;
            }
        }
    }

    private void processFrame(float shift, float mix) {
        for (int i = 0; i < FFT_SIZE; i++) {
            re[i] = inBuf[i] * window[i];
            im[i] = 0f;
        }
        fft.forward(re, im);
        float ratio = (float) Math.pow(2d, shift / 12d);
        buildEnvelope();
        int bins = FFT_SIZE / 2 + 1;
        for (int k = 1; k < bins - 1; k++) {
            float target = sampleEnvelope(k / ratio);
            float gain = (float) Math.exp(Math.max(-1.5, Math.min(1.5, target - envelope[k])));
            re[k] *= gain;
            im[k] *= gain;
            re[FFT_SIZE - k] *= gain;
            im[FFT_SIZE - k] *= gain;
        }
        fft.inverse(re, im);
        for (int i = 0; i < FFT_SIZE; i++) {
            overlap[i] += re[i] * window[i] * 0.6667f;
        }
        for (int i = 0; i < HOP; i++) {
            float wet = overlap[i];
            float dry = dryLine.read(FFT_SIZE - 1);
            append(wet * mix + dry * (1f - mix));
        }
        System.arraycopy(overlap, HOP, overlap, 0, FFT_SIZE - HOP);
        Arrays.fill(overlap, FFT_SIZE - HOP, FFT_SIZE, 0f);
        System.arraycopy(inBuf, HOP, inBuf, 0, FFT_SIZE - HOP);
        inLen = FFT_SIZE - HOP;
    }

    private void append(float value) {
        if (outLen < outBuf.length) {
            outBuf[outLen++] = value;
        }
    }

    private void buildEnvelope() {
        int bins = FFT_SIZE / 2 + 1;
        int radius = 4;
        for (int k = 0; k < bins; k++) {
            float sum = 0f;
            int count = 0;
            for (int j = -radius; j <= radius; j++) {
                int idx = k + j;
                if (idx >= 0 && idx < bins) {
                    float mag = (float) Math.sqrt(re[idx] * re[idx] + im[idx] * im[idx]) + 1e-6f;
                    sum += (float) Math.log(mag);
                    count++;
                }
            }
            envelope[k] = sum / count;
        }
    }

    private float sampleEnvelope(float binPos) {
        int bins = FFT_SIZE / 2 + 1;
        float pos = Math.max(0f, Math.min(bins - 1.001f, binPos));
        int i0 = (int) pos;
        float frac = pos - i0;
        int i1 = Math.min(bins - 1, i0 + 1);
        return envelope[i0] + (envelope[i1] - envelope[i0]) * frac;
    }
}
