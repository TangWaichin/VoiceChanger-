package com.voicechanger.audio;

/**
 * 多声道下混：默认等权平均；当某声道能量明显占主导（常见于麦克风阵列）
 * 时平滑切换到主导声道，避免相位抵消导致音量塌陷。
 */
public final class ChannelDownmixer {

    private float[] energy = new float[0];
    private int channels = 1;
    private int dominant = -1;
    private float blend;

    public void prepare(int channelCount) {
        channels = Math.max(1, channelCount);
        energy = new float[channels];
        dominant = -1;
        blend = 0f;
    }

    /** values 为交错帧数据，offset 指向当前帧首样本。 */
    public float process(float[] values, int offset, int channelCount) {
        if (channelCount <= 1) {
            return values[offset];
        }
        float sumEnergy = 0f;
        float maxEnergy = 0f;
        int maxEnergyIndex = 0;
        float sum = 0f;
        float maxAbs = 0f;
        int maxAbsIndex = 0;
        for (int c = 0; c < channelCount; c++) {
            float sample = values[offset + c];
            float absolute = Math.abs(sample);
            sum += sample;
            if (absolute > maxAbs) {
                maxAbs = absolute;
                maxAbsIndex = c;
            }
            energy[c] = energy[c] * 0.999f + absolute * 0.001f;
            sumEnergy += energy[c];
            if (energy[c] > maxEnergy) {
                maxEnergy = energy[c];
                maxEnergyIndex = c;
            }
        }
        float meanEnergy = sumEnergy / channelCount;
        float average = sum / channelCount;
        boolean energyDominant = meanEnergy > 1e-7f && maxEnergy > meanEnergy * 3f;
        // 各声道都有明显信号但平均后接近 0，说明存在相位抵消
        boolean cancel = maxAbs > 0.01f && Math.abs(average) < maxAbs * 0.25f;
        if (energyDominant) {
            dominant = maxEnergyIndex;
        }
        boolean useSelected = energyDominant || cancel;
        int selectedIndex = energyDominant ? dominant : maxAbsIndex;
        float target = useSelected ? 1f : 0f;
        blend += (target - blend) * 0.002f; // 48kHz 下约 10ms 交叉淡化
        float selected = selectedIndex >= 0 ? values[offset + selectedIndex] : average;
        return average * (1f - blend) + selected * blend;
    }
}
