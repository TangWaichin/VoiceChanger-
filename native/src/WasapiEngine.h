#pragma once

#include "AudioSupport.h"

#include <audioclient.h>
#include <jni.h>
#include <mmdeviceapi.h>

#include <atomic>
#include <functional>
#include <memory>
#include <string>
#include <thread>
#include <vector>

namespace vc {

/**
 * 基于 WASAPI（共享模式 + 事件回调）的实时音频引擎。
 *
 * 采集线程把设备混音格式统一转换为 48kHz 单声道 float，按固定块长回调；
 * 输出/耳返各有一条渲染线程，从无锁感队列取数据后写回设备。
 * 设备按"友好名称"匹配，找不到时使用系统默认设备。
 */
class WasapiEngine {
public:
    using BlockCallback = std::function<void(const float*, int)>;

    struct Config {
        std::wstring captureName;
        std::wstring renderName;
        std::wstring monitorName;
        bool monitorEnabled = false;
        int blockFrames = 480;
        int sampleRate = 48000;
        JavaVM* vm = nullptr; // 可选，用于 DetachCurrentThread 清理 JNI native attach 线程
    };

    WasapiEngine() = default;
    ~WasapiEngine() { stop(); }

    WasapiEngine(const WasapiEngine&) = delete;
    WasapiEngine& operator=(const WasapiEngine&) = delete;

    bool start(const Config& config, BlockCallback callback, std::string& error);
    void stop();

    /** 枚举当前活动端点（采集或播放）的友好名称。 */
    static std::vector<std::wstring> listEndpoints(bool capture);

    void writeOutput(const float* mono, int frames);
    void writeMonitor(const float* mono, int frames);

    bool running() const { return running_.load(); }

    /** 采集设备混音格式与驱动实际使用格式（供界面显示），未启动时为空。 */
    std::string captureInputFormat() const;
    std::string captureDriverFormat() const;

private:
    struct Sink {
        explicit Sink(int blockFrames)
            : queue(blockFrames, 8), block(static_cast<size_t>(blockFrames), 0.f) {
        }

        BlockQueue queue;
        std::vector<float> block;
        std::vector<float> device;
        size_t deviceRead = 0;
        std::wstring name;
        std::thread thread;
    };

    enum class SampleKind { U8, S16, S24, S24_32, S32, F32 };

    struct SampleFormat {
        SampleKind kind = SampleKind::S16;
        int channels = 2;
        int bytesPerSample = 2;
        int validBits = 16;
    };

    void captureLoop();
    void renderLoop(Sink* sink, bool preferVirtual, bool monitor);
    void signalReady(bool ok, const std::string& error);

    static IMMDevice* findEndpoint(IMMDeviceEnumerator* enumerator, EDataFlow flow,
                                   const std::wstring& wanted, int preferVirtual);
    static std::wstring endpointName(IMMDevice* device);
    static bool looksVirtual(const std::wstring& name);
    static bool containsIgnoreCase(const std::wstring& text, const std::wstring& part);
    static SampleFormat describeFormat(const WAVEFORMATEX* format);
    static int rateOf(const WAVEFORMATEX* format);
    static std::string formatText(const WAVEFORMATEX* format);
    static WAVEFORMATEXTENSIBLE* makeFloatTarget(int rate, int channels);
    static std::string hrText(long hr);
    static void writeFrames(BYTE* data, const float* mono, UINT32 frames,
                            const WAVEFORMATEX* format);
    static void readMono(const BYTE* data, UINT32 frames, const SampleFormat& format,
                         ChannelDownmixer& downmix, float* mono);

    Config config_;
    BlockCallback callback_;
    std::atomic<bool> running_{false};
    HANDLE stopEvent_ = nullptr;

    std::thread captureThread_;
    std::unique_ptr<Sink> outSink_;
    std::unique_ptr<Sink> monSink_;
    // 保护 outSink_/monSink_ 指针本身：Java 线程写输出与停止线程释放队列可能并发
    mutable std::mutex sinkMutex_;

    mutable std::mutex formatMutex_;
    std::string captureInputText_;
    std::string captureDriverText_;

    std::mutex readyMutex_;
    std::condition_variable readyCv_;
    int readyDone_ = 0;
    int readyTotal_ = 0;
    std::string readyError_;
};

} // namespace vc
