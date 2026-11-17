package com.voicechanger.dsp;

/**
 * 极简双二阶滤波器（RBJ cookbook），供均衡/共振峰等模块复用。
 */
public final class Biquad {

    private float b0 = 1f;
    private float b1;
    private float b2;
    private float a1;
    private float a2;
    private float x1;
    private float x2;
    private float y1;
    private float y2;

    public void peaking(float sampleRate, float freq, float q, float gainDb) {
        double a = Math.pow(10d, gainDb / 40d);
        double w0 = 2d * Math.PI * Math.max(10d, Math.min(freq, sampleRate * 0.45d)) / sampleRate;
        double alpha = Math.sin(w0) / (2d * Math.max(0.1d, q));
        double a0 = 1d + alpha / a;
        b0 = (float) ((1d + alpha * a) / a0);
        b1 = (float) ((-2d * Math.cos(w0)) / a0);
        b2 = (float) ((1d - alpha * a) / a0);
        a1 = (float) ((-2d * Math.cos(w0)) / a0);
        a2 = (float) ((1d - alpha / a) / a0);
    }

    public void lowShelf(float sampleRate, float freq, float gainDb) {
        double a = Math.pow(10d, gainDb / 40d);
        double w0 = 2d * Math.PI * Math.max(20d, Math.min(freq, sampleRate * 0.45d)) / sampleRate;
        double cos = Math.cos(w0);
        double sin = Math.sin(w0);
        double beta = Math.sqrt(a) / 0.9d;
        double a0 = (a + 1d) + (a - 1d) * cos + beta * sin;
        b0 = (float) (a * ((a + 1d) - (a - 1d) * cos + beta * sin) / a0);
        b1 = (float) (2d * a * ((a - 1d) - (a + 1d) * cos) / a0);
        b2 = (float) (a * ((a + 1d) - (a - 1d) * cos - beta * sin) / a0);
        a1 = (float) (-2d * ((a - 1d) + (a + 1d) * cos) / a0);
        a2 = (float) (((a + 1d) + (a - 1d) * cos - beta * sin) / a0);
    }

    public void highShelf(float sampleRate, float freq, float gainDb) {
        double a = Math.pow(10d, gainDb / 40d);
        double w0 = 2d * Math.PI * Math.max(20d, Math.min(freq, sampleRate * 0.45d)) / sampleRate;
        double cos = Math.cos(w0);
        double sin = Math.sin(w0);
        double beta = Math.sqrt(a) / 0.9d;
        double a0 = (a + 1d) - (a - 1d) * cos + beta * sin;
        b0 = (float) (a * ((a + 1d) + (a - 1d) * cos + beta * sin) / a0);
        b1 = (float) (-2d * a * ((a - 1d) + (a + 1d) * cos) / a0);
        b2 = (float) (a * ((a + 1d) + (a - 1d) * cos - beta * sin) / a0);
        a1 = (float) (2d * ((a - 1d) - (a + 1d) * cos) / a0);
        a2 = (float) (((a + 1d) - (a - 1d) * cos - beta * sin) / a0);
    }

    public void highPass(float sampleRate, float freq, float q) {
        double w0 = 2d * Math.PI * Math.max(10d, Math.min(freq, sampleRate * 0.45d)) / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2d * Math.max(0.1d, q));
        double a0 = 1d + alpha;
        b0 = (float) ((1d + cos) / 2d / a0);
        b1 = (float) (-(1d + cos) / a0);
        b2 = b0;
        a1 = (float) (-2d * cos / a0);
        a2 = (float) ((1d - alpha) / a0);
    }

    public void lowPass(float sampleRate, float freq, float q) {
        double w0 = 2d * Math.PI * Math.max(10d, Math.min(freq, sampleRate * 0.45d)) / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2d * Math.max(0.1d, q));
        double a0 = 1d + alpha;
        b0 = (float) ((1d - cos) / 2d / a0);
        b1 = (float) ((1d - cos) / a0);
        b2 = b0;
        a1 = (float) (-2d * cos / a0);
        a2 = (float) ((1d - alpha) / a0);
    }

    public float process(float x) {
        float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1;
        x1 = x;
        y2 = y1;
        y1 = y;
        return y;
    }

    public void reset() {
        x1 = x2 = y1 = y2 = 0f;
    }
}
