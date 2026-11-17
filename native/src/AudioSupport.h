#pragma once

#include <windows.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

namespace vc {

/** 宽字符转 UTF-8，用于把设备名/错误信息交回 Java。 */
inline std::string wideToUtf8(const std::wstring& text) {
    if (text.empty()) {
        return {};
    }
    int size = WideCharToMultiByte(CP_UTF8, 0, text.c_str(), static_cast<int>(text.size()),
                                   nullptr, 0, nullptr, nullptr);
    if (size <= 0) {
        return {};
    }
    std::string out(static_cast<size_t>(size), '\0');
    WideCharToMultiByte(CP_UTF8, 0, text.c_str(), static_cast<int>(text.size()),
                        out.data(), size, nullptr, nullptr);
    return out;
}

inline std::wstring utf8ToWide(const std::string& text) {
    if (text.empty()) {
        return {};
    }
    int size = MultiByteToWideChar(CP_UTF8, 0, text.c_str(), static_cast<int>(text.size()), nullptr, 0);
    if (size <= 0) {
        return {};
    }
    std::wstring out(static_cast<size_t>(size), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, text.c_str(), static_cast<int>(text.size()), out.data(), size);
    return out;
}

/**
 * 流式窗函数 sinc 重采样：下采样时带抗混叠低通，跨块保持相位连续。
 * 抽头数按转换比例自适应：升采样 32，普通降采样 64，极端降采样 96。
 */
class SincResampler {
public:
    SincResampler(int inputRate, int outputRate)
        : ratio_(inputRate > 0 && outputRate > 0
                         ? static_cast<double>(inputRate) / static_cast<double>(outputRate)
                         : 1.0) {
        identity_ = std::abs(ratio_ - 1.0) < 1e-9;
        if (!identity_) {
            taps_ = chooseTaps(ratio_);
            buildTable();
            prev_.assign(taps_ - 1, 0.f);
        }
    }

    void reset() {
        pos_ = 0.0;
        std::fill(prev_.begin(), prev_.end(), 0.f);
    }

    void process(const float* input, int frames, std::vector<float>& output) {
        output.clear();
        if (input == nullptr || frames <= 0) {
            return;
        }
        if (identity_) {
            output.assign(input, input + frames);
            return;
        }
        const int prevLen = static_cast<int>(prev_.size());
        work_.resize(static_cast<size_t>(prevLen + frames));
        std::copy(prev_.begin(), prev_.end(), work_.begin());
        std::copy(input, input + frames, work_.begin() + prevLen);
        const int workLen = prevLen + frames;
        const double limit = static_cast<double>(workLen) - taps_ / 2.0;
        while (pos_ < limit) {
            const int center = static_cast<int>(std::floor(pos_));
            const double frac = pos_ - center;
            int phase = static_cast<int>(frac * kPhases + 0.5);
            if (phase >= kPhases) {
                phase = kPhases - 1;
            }
            const float* row = table_.data() + static_cast<size_t>(phase) * taps_;
            const int base = center - taps_ / 2;
            float sum = 0.f;
            for (int k = 0; k < taps_; ++k) {
                int index = base + k;
                if (index < 0) {
                    index = 0;
                } else if (index >= workLen) {
                    index = workLen - 1;
                }
                sum += work_[static_cast<size_t>(index)] * row[k];
            }
            output.push_back(sum);
            pos_ += ratio_;
        }
        const int keep = std::min(taps_ - 1, workLen);
        std::copy(work_.end() - keep, work_.end(), prev_.begin());
        pos_ -= static_cast<double>(workLen - keep);
        if (pos_ < 0.0) {
            pos_ = 0.0;
        }
    }

private:
    static constexpr int kPhases = 128;
    static constexpr double kPi = 3.14159265358979323846;

    /** 按转换比例选择抽头数，兼顾短延迟与抗混叠。 */
    static int chooseTaps(double ratio) {
        if (ratio < 1.0) {
            return 32;
        }
        return ratio < 2.0 ? 64 : 96;
    }

    double ratio_;
    bool identity_ = false;
    int taps_ = 64;
    double pos_ = 0.0;
    std::vector<float> prev_;
    std::vector<float> work_;
    std::vector<float> table_;

    void buildTable() {
        const double cutoff = std::min(1.0, 1.0 / ratio_) * 0.95;
        table_.assign(static_cast<size_t>(kPhases) * taps_, 0.f);
        for (int p = 0; p < kPhases; ++p) {
            const double frac = static_cast<double>(p) / kPhases;
            double sum = 0.0;
            for (int k = 0; k < taps_; ++k) {
                const double distance = static_cast<double>(k - taps_ / 2) - frac;
                const double x = distance * cutoff;
                const double sinc = std::abs(x) < 1e-9
                        ? 1.0 : std::sin(kPi * x) / (kPi * x);
                const double n = static_cast<double>(k) / (taps_ - 1);
                const double window = 0.42 - 0.5 * std::cos(2.0 * kPi * n)
                        + 0.08 * std::cos(4.0 * kPi * n);
                const double h = sinc * window * cutoff;
                table_[static_cast<size_t>(p) * taps_ + k] = static_cast<float>(h);
                sum += h;
            }
            if (sum > 1e-9) {
                for (int k = 0; k < taps_; ++k) {
                    table_[static_cast<size_t>(p) * taps_ + k] =
                            static_cast<float>(table_[static_cast<size_t>(p) * taps_ + k] / sum);
                }
            }
        }
    }
};

/**
 * 多声道下混：默认等权平均；当某声道能量占主导（或反相抵消）时
 * 平滑切换到主导声道，避免相位抵消导致音量塌陷。
 */
class ChannelDownmixer {
public:
    void prepare(int channels) {
        channels_ = std::max(1, channels);
        energy_.assign(static_cast<size_t>(channels_), 0.f);
        dominant_ = -1;
        blend_ = 0.f;
    }

    float process(const float* frame) {
        if (channels_ <= 1) {
            return frame[0];
        }
        float sumEnergy = 0.f;
        float maxEnergy = 0.f;
        int maxEnergyIndex = 0;
        float sum = 0.f;
        float maxAbs = 0.f;
        int maxAbsIndex = 0;
        for (int c = 0; c < channels_; ++c) {
            const float sample = frame[c];
            const float absolute = std::fabs(sample);
            sum += sample;
            if (absolute > maxAbs) {
                maxAbs = absolute;
                maxAbsIndex = c;
            }
            energy_[static_cast<size_t>(c)] =
                    energy_[static_cast<size_t>(c)] * 0.999f + absolute * 0.001f;
            sumEnergy += energy_[static_cast<size_t>(c)];
            if (energy_[static_cast<size_t>(c)] > maxEnergy) {
                maxEnergy = energy_[static_cast<size_t>(c)];
                maxEnergyIndex = c;
            }
        }
        const float meanEnergy = sumEnergy / static_cast<float>(channels_);
        const float average = sum / static_cast<float>(channels_);
        const bool energyDominant = meanEnergy > 1e-7f && maxEnergy > meanEnergy * 3.f;
        // 各声道都有明显信号但平均后接近 0，说明存在相位抵消
        const bool cancel = maxAbs > 0.01f && std::fabs(average) < maxAbs * 0.25f;
        if (energyDominant) {
            dominant_ = maxEnergyIndex;
        }
        const bool useSelected = energyDominant || cancel;
        const int selectedIndex = energyDominant ? dominant_ : maxAbsIndex;
        const float target = useSelected ? 1.f : 0.f;
        blend_ += (target - blend_) * 0.002f; // 48kHz 下约 10ms 交叉淡化
        const float selected = selectedIndex >= 0 ? frame[selectedIndex] : average;
        return average * (1.f - blend_) + selected * blend_;
    }

private:
    int channels_ = 1;
    std::vector<float> energy_;
    int dominant_ = -1;
    float blend_ = 0.f;
};

/** 固定块长的单声道音频队列，满时丢最旧的数据以保证实时性。 */
class BlockQueue {
public:
    BlockQueue(int frames, int capacity)
        : frames_(frames), capacity_(capacity),
          slots_(static_cast<size_t>(capacity), std::vector<float>(static_cast<size_t>(frames), 0.f)) {
    }

    void push(const float* data, int frames) {
        if (data == nullptr || frames <= 0) {
            return;
        }
        std::lock_guard<std::mutex> lock(mutex_);
        int count = std::min(frames, frames_);
        if (count_ == capacity_) {
            read_ = (read_ + 1) % capacity_;
            --count_;
        }
        std::copy(data, data + count, slots_[static_cast<size_t>(write_)].begin());
        if (count < frames_) {
            std::fill(slots_[static_cast<size_t>(write_)].begin() + count,
                      slots_[static_cast<size_t>(write_)].end(), 0.f);
        }
        write_ = (write_ + 1) % capacity_;
        ++count_;
        ready_.notify_one();
    }

    /** 等待并取出一块；超时返回 false。 */
    bool pop(float* dest, int frames, int timeoutMs) {
        std::unique_lock<std::mutex> lock(mutex_);
        if (count_ == 0) {
            ready_.wait_for(lock, std::chrono::milliseconds(timeoutMs),
                            [this] { return count_ > 0; });
        }
        if (count_ == 0) {
            return false;
        }
        std::copy(slots_[static_cast<size_t>(read_)].begin(),
                  slots_[static_cast<size_t>(read_)].end(), dest);
        read_ = (read_ + 1) % capacity_;
        --count_;
        return true;
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mutex_);
        read_ = 0;
        write_ = 0;
        count_ = 0;
    }

private:
    int frames_;
    int capacity_;
    int read_ = 0;
    int write_ = 0;
    int count_ = 0;
    std::vector<std::vector<float>> slots_;
    std::mutex mutex_;
    std::condition_variable ready_;
};

inline float clampSample(float value) {
    if (value > 1.f) {
        return 1.f;
    }
    if (value < -1.f) {
        return -1.f;
    }
    return value;
}

} // namespace vc
