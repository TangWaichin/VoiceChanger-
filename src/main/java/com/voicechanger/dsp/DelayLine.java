package com.voicechanger.dsp;

/**
 * 浮点环形延迟线，支持分数延迟读取。
 */
public final class DelayLine {

    private final float[] buffer;
    private final int mask;
    private int writeIndex;

    public DelayLine(int sizePowerOfTwo) {
        int size = Integer.highestOneBit(Math.max(4, sizePowerOfTwo - 1)) << 1;
        this.buffer = new float[size];
        this.mask = size - 1;
    }

    public void write(float value) {
        buffer[writeIndex & mask] = value;
        writeIndex++;
    }

    public int capacity() {
        return buffer.length;
    }

    public float read(float delaySamples) {
        float d = Math.max(0f, Math.min(delaySamples, buffer.length - 2));
        double pos = writeIndex - 1 - d;
        int i0 = (int) Math.floor(pos);
        float frac = (float) (pos - i0);
        float a = buffer[i0 & mask];
        float b = buffer[(i0 + 1) & mask];
        return a + (b - a) * frac;
    }

    public void clear() {
        java.util.Arrays.fill(buffer, 0f);
        writeIndex = 0;
    }
}
