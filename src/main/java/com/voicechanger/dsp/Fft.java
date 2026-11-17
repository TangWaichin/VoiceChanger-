package com.voicechanger.dsp;

/**
 * 迭代式基 2 复数 FFT，供频谱类模块使用。
 */
public final class Fft {

    private final int size;
    private final int[] reverse;
    private final float[] cosTable;
    private final float[] sinTable;

    public Fft(int size) {
        this.size = size;
        this.reverse = new int[size];
        int bits = Integer.numberOfTrailingZeros(size);
        for (int i = 0; i < size; i++) {
            reverse[i] = Integer.reverse(i) >>> (32 - bits);
        }
        cosTable = new float[size / 2];
        sinTable = new float[size / 2];
        for (int i = 0; i < size / 2; i++) {
            double angle = -2d * Math.PI * i / size;
            cosTable[i] = (float) Math.cos(angle);
            sinTable[i] = (float) Math.sin(angle);
        }
    }

    public int size() {
        return size;
    }

    public void forward(float[] re, float[] im) {
        transform(re, im);
    }

    public void inverse(float[] re, float[] im) {
        transform(im, re);
        float scale = 1f / size;
        for (int i = 0; i < size; i++) {
            re[i] *= scale;
            im[i] *= scale;
        }
    }

    private void transform(float[] re, float[] im) {
        for (int i = 0; i < size; i++) {
            int j = reverse[i];
            if (j > i) {
                float tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                float ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }
        for (int len = 2; len <= size; len <<= 1) {
            int half = len >> 1;
            int step = size / len;
            for (int start = 0; start < size; start += len) {
                for (int k = 0; k < half; k++) {
                    float wr = cosTable[k * step];
                    float wi = sinTable[k * step];
                    int i0 = start + k;
                    int i1 = i0 + half;
                    float xr = re[i1] * wr - im[i1] * wi;
                    float xi = re[i1] * wi + im[i1] * wr;
                    re[i1] = re[i0] - xr;
                    im[i1] = im[i0] - xi;
                    re[i0] += xr;
                    im[i0] += xi;
                }
            }
        }
    }
}
