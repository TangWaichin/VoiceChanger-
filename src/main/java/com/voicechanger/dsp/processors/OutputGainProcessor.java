package com.voicechanger.dsp.processors;

import com.voicechanger.dsp.AbstractProcessor;
import com.voicechanger.dsp.ParamSpec;
import com.voicechanger.dsp.ProcessorState;

import java.util.List;

/**
 * 输出增益 + 前视硬限幅（look-ahead brickwall limiter），放在链路末端。
 * 信号延迟一个前视窗口输出，限幅增益在峰值到达前已就位，
 * 因此高增益下也不会削波，且不改变上游任何模块的工作电平。
 */
public final class OutputGainProcessor extends AbstractProcessor {

    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.of("gain", "输出增益", -24f, 48f, 0f, 0.5f, " dB", 1),
            ParamSpec.of("limit", "限幅阈值", -20f, 0f, -1f, 0.5f, " dB", 1));

    /** 前视窗口时长，也是限幅器引入的固定延迟。 */
    private static final float LOOKAHEAD_MS = 5f;
    /** 限幅释放时长，避免增益快速起伏产生 pumping。 */
    private static final float RELEASE_MS = 120f;

    private float[] delay = new float[0];
    private int write;
    private int lookahead = 1;
    private float envelope;
    private float limiterGain = 1f;
    private float smoothGain = 1f;
    // 采样率相关的时间常数，prepare 时计算一次，避免每块重复 Math.exp
    private float attack;
    private float release;
    private float peakDecay;
    private float gainCoeff;

    public OutputGainProcessor(ProcessorState state) {
        super(state);
    }

    @Override
    public String id() {
        return "output";
    }

    @Override
    public String displayName() {
        return "输出";
    }

    @Override
    public String description() {
        return "整体电平放大，前视限幅器提前压住超过阈值的峰值，高增益下也不破音。";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected void onPrepare(int sampleRate, int maxFrames) {
        lookahead = Math.max(1, Math.round(sampleRate * LOOKAHEAD_MS / 1000f));
        delay = new float[lookahead + 1];
        write = 0;
        envelope = 0f;
        limiterGain = 1f;
        smoothGain = dbToGain(param("gain"));
        // 前视窗口的 1/4 作为 attack 时间常数，峰值输出时增益已基本就位
        attack = (float) Math.exp(-1d / (lookahead * 0.25d));
        release = (float) Math.exp(-1d / (0.001d * RELEASE_MS * sampleRate));
        peakDecay = (float) Math.exp(-1d / (double) lookahead);
        // 增益推子一阶平滑时间常数（5ms），避免调节时产生咔哒声
        gainCoeff = (float) Math.exp(-1d / (0.005d * sampleRate));
    }

    @Override
    public void reset() {
        onPrepare(sampleRate(), 0);
    }

    @Override
    public void process(float[] buffer, int length) {
        if (delay.length == 0) {
            onPrepare(sampleRate(), length);
        }
        float targetGain = dbToGain(param("gain"));
        float limit = dbToGain(param("limit"));
        for (int i = 0; i < length; i++) {
            smoothGain = targetGain + (smoothGain - targetGain) * gainCoeff;
            float x = buffer[i] * smoothGain;

            // 峰值检测：peak-hold + 前视窗口长度的指数衰减
            float abs = Math.abs(x);
            envelope = Math.max(abs, envelope * peakDecay);
            float needed = envelope > limit ? limit / envelope : 1f;
            float coeff = needed < limiterGain ? attack : release;
            limiterGain = needed + (limiterGain - needed) * coeff;

            // 读出前视窗口之前的采样，当前采样写入延迟线
            float delayed = delay[write];
            delay[write] = x;
            if (++write >= delay.length) {
                write = 0;
            }
            float y = delayed * limiterGain;
            // 兜底硬削波：attack 收敛残余过冲在此削平，输出永远不越界
            buffer[i] = Math.max(-1f, Math.min(1f, y));
        }
    }
}
