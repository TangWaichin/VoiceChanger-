package com.voicechanger.audio;

import javax.sound.sampled.AudioFormat;

/**
 * 采样格式转换工具：支持 8 / 16 / 24 / 32bit 整型与 32bit 浮点 PCM，
 * 以及 64 抽头窗函数 sinc 流式重采样（下采样带抗混叠低通）。
 * 全部写入调用方提供的缓冲，实时链路上不做每块分配。
 */
public final class AudioConverter {

    private AudioConverter() {
    }

    /**
     * 单声道 float 帧 -> 目标声道数的 float 帧，写入 out（容量需 >= length * channels）。
     */
    public static void monoToChannels(float[] mono, int length, int channels, float[] out) {
        if (channels <= 1) {
            System.arraycopy(mono, 0, out, 0, length);
            return;
        }
        for (int i = 0; i < length; i++) {
            for (int c = 0; c < channels; c++) {
                out[i * channels + c] = mono[i];
            }
        }
    }

    /**
     * float 帧 -> 16bit 小端 PCM，写入 out（容量需 >= length * 2）。
     */
    public static void monoToPcm16(float[] mono, int length, byte[] out) {
        for (int i = 0; i < length; i++) {
            int v = Math.round(mono[i] * 32767f);
            if (v > 32767) {
                v = 32767;
            } else if (v < -32768) {
                v = -32768;
            }
            out[i * 2] = (byte) (v & 0xFF);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
    }

    /**
     * 多声道 16bit 小端 PCM -> 单声道 float 帧（取平均），返回帧数。
     */
    public static int pcmToMono(byte[] pcm, int offset, int length, int channels, float[] out) {
        int frames = length / (2 * channels);
        int idx = offset;
        for (int f = 0; f < frames; f++) {
            float sum = 0f;
            for (int c = 0; c < channels; c++) {
                int lo = pcm[idx++] & 0xFF;
                int hi = pcm[idx++];
                sum += ((hi << 8) | lo) / 32768f;
            }
            out[f] = sum / channels;
        }
        return frames;
    }

    /**
     * 按实际 AudioFormat 把交错 PCM 解为多声道 float 帧，返回帧数。
     * out 容量需 >= frames * channels。
     */
    public static int pcmToChannels(AudioFormat format, byte[] pcm, int offset, int length,
                                    float[] out) {
        int channels = Math.max(1, format.getChannels());
        int bytes = bytesPerSample(format);
        int frameSize = channels * bytes;
        if (frameSize <= 0) {
            return 0;
        }
        int frames = length / frameSize;
        for (int f = 0; f < frames; f++) {
            int base = offset + f * frameSize;
            for (int c = 0; c < channels; c++) {
                out[f * channels + c] = decodeSample(format, pcm, base + c * bytes);
            }
        }
        return frames;
    }

    /**
     * 按实际 AudioFormat 把交错 PCM 解为单声道 float 帧（等权平均），返回帧数。
     */
    public static int pcmToMono(AudioFormat format, byte[] pcm, int offset, int length, float[] out) {
        int channels = Math.max(1, format.getChannels());
        int bytes = bytesPerSample(format);
        int frameSize = channels * bytes;
        if (frameSize <= 0) {
            return 0;
        }
        int frames = length / frameSize;
        for (int f = 0; f < frames; f++) {
            int base = offset + f * frameSize;
            float sum = 0f;
            for (int c = 0; c < channels; c++) {
                sum += decodeSample(format, pcm, base + c * bytes);
            }
            out[f] = sum / channels;
        }
        return frames;
    }

    /**
     * float 样本 -> 指定 AudioFormat 的交错 PCM，sampleCount 为样本总数。
     * out 容量需 >= sampleCount * bytesPerSample。
     */
    public static void monoToPcm(AudioFormat format, float[] samples, int sampleCount, byte[] out) {
        int bits = format.getSampleSizeInBits() > 0 ? format.getSampleSizeInBits() : 16;
        // 容器字节数由 frameSize 推导，兼容 24bit 有效位放在 32bit 容器里的设备格式
        int bytes = bytesPerSample(format);
        boolean big = format.isBigEndian();
        boolean isFloat = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding());
        boolean unsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding());
        int index = 0;
        for (int i = 0; i < sampleCount; i++) {
            float v = samples[i];
            if (v > 1f) {
                v = 1f;
            } else if (v < -1f) {
                v = -1f;
            }
            if (isFloat && bits == 32 && bytes >= 4) {
                writeInt(out, index, Float.floatToIntBits(v), big);
            } else if (bits == 8 && unsigned) {
                out[index] = (byte) (Math.round(v * 127f) + 128);
            } else if (bits == 8) {
                out[index] = (byte) Math.round(v * 127f);
            } else if (bytes == 2) {
                writeShort(out, index, (short) Math.round(v * 32767f), big);
            } else if (bytes == 3) {
                int s = Math.round(v * ((1 << Math.min(bits, 24) - 1) - 1));
                s <<= 24 - Math.min(bits, 24);
                if (big) {
                    out[index] = (byte) ((s >> 16) & 0xFF);
                    out[index + 1] = (byte) ((s >> 8) & 0xFF);
                    out[index + 2] = (byte) (s & 0xFF);
                } else {
                    out[index] = (byte) (s & 0xFF);
                    out[index + 1] = (byte) ((s >> 8) & 0xFF);
                    out[index + 2] = (byte) ((s >> 16) & 0xFF);
                }
            } else if (bits < 32) {
                // 32bit 容器 + 较少有效位：按最高位对齐
                int s = Math.round(v * ((1 << (bits - 1)) - 1));
                writeInt(out, index, s << (32 - bits), big);
            } else {
                writeInt(out, index, Math.round(v * 2147483000f), big);
            }
            index += bytes;
        }
    }

    /** 每个样本占用的字节数（由 AudioFormat 推导）。 */
    public static int bytesPerSample(AudioFormat format) {
        int frameSize = format.getFrameSize();
        int channels = format.getChannels();
        if (frameSize > 0 && channels > 0) {
            return Math.max(1, frameSize / channels);
        }
        return Math.max(1, format.getSampleSizeInBits() / 8);
    }

    private static float decodeSample(AudioFormat format, byte[] data, int index) {
        int bits = format.getSampleSizeInBits() > 0 ? format.getSampleSizeInBits() : 16;
        int bytes = bytesPerSample(format);
        boolean big = format.isBigEndian();
        if (AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding()) && bits == 32 && bytes >= 4) {
            return Float.intBitsToFloat(readInt(data, index, big));
        }
        if (AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding()) && bits == 8) {
            return ((data[index] & 0xFF) - 128) / 128f;
        }
        if (bits == 8) {
            return data[index] / 128f;
        }
        if (bytes == 2) {
            return readShort(data, index, big) / 32768f;
        }
        if (bytes == 3) {
            int b0 = data[index] & 0xFF;
            int b1 = data[index + 1] & 0xFF;
            int b2 = data[index + 2] & 0xFF;
            int v = big ? (b0 << 16) | (b1 << 8) | b2 : b0 | (b1 << 8) | (b2 << 16);
            if ((v & 0x800000) != 0) {
                v |= ~0xFFFFFF;
            }
            int valid = Math.min(bits, 24);
            v >>= 24 - valid;
            return v / (float) (1 << (valid - 1));
        }
        if (bits < 32) {
            // 32bit 容器 + 较少有效位：最高位对齐，先右移再按有效位归一化
            int v = readInt(data, index, big) >> (32 - bits);
            return v / (float) (1 << (bits - 1));
        }
        return readInt(data, index, big) / 2147483648f;
    }

    private static short readShort(byte[] data, int index, boolean big) {
        int b0 = data[index] & 0xFF;
        int b1 = data[index + 1] & 0xFF;
        return (short) (big ? (b0 << 8) | b1 : (b1 << 8) | b0);
    }

    private static int readInt(byte[] data, int index, boolean big) {
        int b0 = data[index] & 0xFF;
        int b1 = data[index + 1] & 0xFF;
        int b2 = data[index + 2] & 0xFF;
        int b3 = data[index + 3] & 0xFF;
        return big ? (b0 << 24) | (b1 << 16) | (b2 << 8) | b3
                : (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    private static void writeShort(byte[] data, int index, short value, boolean big) {
        int v = value & 0xFFFF;
        if (big) {
            data[index] = (byte) ((v >> 8) & 0xFF);
            data[index + 1] = (byte) (v & 0xFF);
        } else {
            data[index] = (byte) (v & 0xFF);
            data[index + 1] = (byte) ((v >> 8) & 0xFF);
        }
    }

    private static void writeInt(byte[] data, int index, int value, boolean big) {
        if (big) {
            data[index] = (byte) ((value >> 24) & 0xFF);
            data[index + 1] = (byte) ((value >> 16) & 0xFF);
            data[index + 2] = (byte) ((value >> 8) & 0xFF);
            data[index + 3] = (byte) (value & 0xFF);
        } else {
            data[index] = (byte) (value & 0xFF);
            data[index + 1] = (byte) ((value >> 8) & 0xFF);
            data[index + 2] = (byte) ((value >> 16) & 0xFF);
            data[index + 3] = (byte) ((value >> 24) & 0xFF);
        }
    }

    /**
     * 流式窗函数 sinc 重采样：下采样时带抗混叠低通，跨块保持相位连续。
     * 输出写入调用方缓冲，容量用 maxOutputFrames 预估。
     */
    public static final class StreamResampler {

        private static final int PHASES = 128;

        private final double ratio;
        private final boolean identity;
        private final int taps;
        private final float[][] table;
        private final float[] prev;
        private float[] work = new float[0];
        private double position;

        public StreamResampler(int fromRate, int toRate) {
            this.ratio = (double) fromRate / (double) toRate;
            this.identity = Math.abs(ratio - 1d) < 1e-9;
            this.taps = identity ? 0 : chooseTaps(ratio);
            this.table = identity ? null : buildTable(ratio, taps);
            this.prev = new float[identity ? 0 : taps - 1];
        }

        /** 按转换比例选择抽头数：升采样 32，普通降采样 64，极端降采样 96。 */
        private static int chooseTaps(double ratio) {
            if (ratio < 1.0) {
                return 32;
            }
            return ratio < 2.0 ? 64 : 96;
        }

        /** 采样率一致时调用方可跳过重采样，零拷贝直通。 */
        public boolean isIdentity() {
            return identity;
        }

        /** 预估 inputFrames 个输入帧的最大输出帧数（含跨块相位与滤波余量）。 */
        public int maxOutputFrames(int inputFrames) {
            if (identity) {
                return inputFrames;
            }
            return (int) Math.ceil((inputFrames + taps) / ratio) + 2;
        }

        /**
         * 处理 inLen 个输入帧，结果写入 out，返回输出帧数（可能为 0）。
         * out 容量不得小于 maxOutputFrames(inLen)。
         */
        public int process(float[] in, int inLen, float[] out) {
            if (inLen <= 0) {
                return 0;
            }
            if (identity) {
                System.arraycopy(in, 0, out, 0, inLen);
                return inLen;
            }
            int tapCount = taps;
            int prevLen = prev.length;
            int need = prevLen + inLen;
            if (work.length < need) {
                work = new float[need];
            }
            System.arraycopy(prev, 0, work, 0, prevLen);
            System.arraycopy(in, 0, work, prevLen, inLen);
            int workLen = need;
            double limit = workLen - tapCount / 2.0;
            int produced = 0;
            while (position < limit) {
                if (produced >= out.length) {
                    break;
                }
                int center = (int) Math.floor(position);
                double frac = position - center;
                int phase = (int) Math.round(frac * PHASES);
                if (phase >= PHASES) {
                    phase = PHASES - 1;
                }
                float[] row = table[phase];
                int base = center - tapCount / 2;
                float sum = 0f;
                for (int k = 0; k < tapCount; k++) {
                    int index = base + k;
                    if (index < 0) {
                        index = 0;
                    } else if (index >= workLen) {
                        index = workLen - 1;
                    }
                    sum += work[index] * row[k];
                }
                out[produced++] = sum;
                position += ratio;
            }
            int keep = Math.min(tapCount - 1, workLen);
            System.arraycopy(work, workLen - keep, prev, 0, keep);
            position -= (workLen - keep);
            if (position < 0) {
                position = 0;
            }
            return produced;
        }

        private static float[][] buildTable(double ratio, int tapCount) {
            double cutoff = Math.min(1.0, 1.0 / ratio) * 0.95;
            float[][] table = new float[PHASES][tapCount];
            for (int p = 0; p < PHASES; p++) {
                double frac = (double) p / PHASES;
                double sum = 0d;
                for (int k = 0; k < tapCount; k++) {
                    double distance = (k - tapCount / 2) - frac;
                    double x = distance * cutoff;
                    double sinc = Math.abs(x) < 1e-9 ? 1.0 : Math.sin(Math.PI * x) / (Math.PI * x);
                    double n = (double) k / (tapCount - 1);
                    double window = 0.42 - 0.5 * Math.cos(2d * Math.PI * n)
                            + 0.08 * Math.cos(4d * Math.PI * n);
                    table[p][k] = (float) (sinc * window * cutoff);
                    sum += table[p][k];
                }
                if (sum > 1e-9) {
                    for (int k = 0; k < tapCount; k++) {
                        table[p][k] /= (float) sum;
                    }
                }
            }
            return table;
        }
    }
}
