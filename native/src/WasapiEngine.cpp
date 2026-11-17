#include "WasapiEngine.h"

#include <avrt.h>
#include <propsys.h>

#include <algorithm>
#include <cstddef>
#include <cstdio>
#include <cstring>
#include <cwctype>

#ifndef AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM
#define AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM 0x80000000
#endif
#ifndef AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY
#define AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY 0x08000000
#endif

namespace vc {

namespace {

const PROPERTYKEY kFriendlyName = {
    {0xa45c254e, 0xdf1c, 0x4efd, {0x80, 0x20, 0x67, 0xd1, 0x46, 0xa8, 0x50, 0xe0}}, 14};

constexpr REFERENCE_TIME kBufferDuration = 200000; // 20ms（无法读取设备周期时的兜底）

/** 把当前线程登记为 Pro Audio 任务，降低共享模式小缓冲下的爆音与丢帧概率。 */
class MmcssScope {
public:
    MmcssScope() {
        handle_ = AvSetMmThreadCharacteristicsW(L"Pro Audio", &taskIndex_);
    }

    ~MmcssScope() {
        if (handle_ != nullptr) {
            AvRevertMmThreadCharacteristics(handle_);
        }
    }

    MmcssScope(const MmcssScope&) = delete;
    MmcssScope& operator=(const MmcssScope&) = delete;

private:
    HANDLE handle_ = nullptr;
    DWORD taskIndex_ = 0;
};

/** 优先使用设备默认周期作为共享模式缓冲时长，降低端到端延迟。 */
REFERENCE_TIME bufferDurationFor(IAudioClient* client) {
    REFERENCE_TIME defaultPeriod = 0;
    REFERENCE_TIME minPeriod = 0;
    if (client != nullptr
            && SUCCEEDED(client->GetDevicePeriod(&defaultPeriod, &minPeriod))
            && defaultPeriod > 0) {
        return defaultPeriod;
    }
    return kBufferDuration;
}

} // namespace

std::string WasapiEngine::hrText(long hr) {
    char buffer[32];
    std::snprintf(buffer, sizeof(buffer), "0x%08lX", static_cast<unsigned long>(hr));
    return buffer;
}

bool WasapiEngine::containsIgnoreCase(const std::wstring& text, const std::wstring& part) {
    if (part.empty() || text.empty()) {
        return false;
    }
    auto it = std::search(text.begin(), text.end(), part.begin(), part.end(),
                          [](wchar_t a, wchar_t b) { return std::towlower(a) == std::towlower(b); });
    return it != text.end();
}

bool WasapiEngine::looksVirtual(const std::wstring& name) {
    return containsIgnoreCase(name, L"cable") || containsIgnoreCase(name, L"virtual")
            || containsIgnoreCase(name, L"voicemeeter") || containsIgnoreCase(name, L"vb-audio")
            || containsIgnoreCase(name, L"jinzhou") || containsIgnoreCase(name, L"虚拟");
}

std::wstring WasapiEngine::endpointName(IMMDevice* device) {
    std::wstring name;
    if (device == nullptr) {
        return name;
    }
    IPropertyStore* store = nullptr;
    if (SUCCEEDED(device->OpenPropertyStore(STGM_READ, &store)) && store != nullptr) {
        PROPVARIANT value{};
        if (SUCCEEDED(store->GetValue(kFriendlyName, &value))
                && value.vt == VT_LPWSTR && value.pwszVal != nullptr) {
            name = value.pwszVal;
        }
        PropVariantClear(&value);
        store->Release();
    }
    return name;
}

IMMDevice* WasapiEngine::findEndpoint(IMMDeviceEnumerator* enumerator, EDataFlow flow,
                                      const std::wstring& wanted, int preferVirtual) {
    if (enumerator == nullptr) {
        return nullptr;
    }
    IMMDevice* matched = nullptr;
    IMMDevice* virtualCandidate = nullptr;
    IMMDevice* realCandidate = nullptr;
    IMMDeviceCollection* collection = nullptr;
    if (SUCCEEDED(enumerator->EnumAudioEndpoints(flow, DEVICE_STATE_ACTIVE, &collection))
            && collection != nullptr) {
        UINT count = 0;
        collection->GetCount(&count);
        for (UINT i = 0; i < count; ++i) {
            IMMDevice* device = nullptr;
            if (FAILED(collection->Item(i, &device)) || device == nullptr) {
                continue;
            }
            std::wstring name = endpointName(device);
            if (!wanted.empty()) {
                if (name == wanted || containsIgnoreCase(name, wanted)
                        || containsIgnoreCase(wanted, name)) {
                    matched = device;
                    break;
                }
            }
            if (looksVirtual(name)) {
                if (virtualCandidate == nullptr) {
                    virtualCandidate = device;
                } else {
                    device->Release();
                }
            } else {
                if (realCandidate == nullptr) {
                    realCandidate = device;
                } else {
                    device->Release();
                }
            }
        }
        collection->Release();
    }
    if (matched == nullptr) {
        if (preferVirtual > 0 && virtualCandidate != nullptr) {
            matched = virtualCandidate;
        } else if (preferVirtual == 0 && realCandidate != nullptr) {
            matched = realCandidate;
        }
    }
    if (matched == nullptr) {
        enumerator->GetDefaultAudioEndpoint(flow, eConsole, &matched);
    }
    if (virtualCandidate != nullptr && virtualCandidate != matched) {
        virtualCandidate->Release();
    }
    if (realCandidate != nullptr && realCandidate != matched) {
        realCandidate->Release();
    }
    return matched;
}

WasapiEngine::SampleFormat WasapiEngine::describeFormat(const WAVEFORMATEX* format) {
    SampleFormat out;
    if (format == nullptr) {
        return out;
    }
    out.channels = format->nChannels > 0 ? format->nChannels : 1;
    out.bytesPerSample = format->nBlockAlign > 0 && format->nChannels > 0
            ? format->nBlockAlign / format->nChannels
            : static_cast<int>(format->wBitsPerSample / 8);
    if (out.bytesPerSample <= 0) {
        out.bytesPerSample = 2;
    }
    out.validBits = format->wBitsPerSample;
    bool isFloat = format->wFormatTag == WAVE_FORMAT_IEEE_FLOAT;
    if (format->wFormatTag == WAVE_FORMAT_EXTENSIBLE && format->cbSize >= 22) {
        const auto* extended = reinterpret_cast<const WAVEFORMATEXTENSIBLE*>(format);
        if (extended->SubFormat.Data1 == 3) {
            isFloat = true;
        }
        if (extended->Samples.wValidBitsPerSample > 0) {
            out.validBits = extended->Samples.wValidBitsPerSample;
        }
    }
    if (isFloat) {
        out.kind = SampleKind::F32;
        out.bytesPerSample = 4;
        out.validBits = 32;
    } else if (out.bytesPerSample == 1) {
        out.kind = SampleKind::U8;
        out.validBits = 8;
    } else if (out.bytesPerSample == 2) {
        out.kind = SampleKind::S16;
        out.validBits = 16;
    } else if (out.bytesPerSample == 3) {
        out.kind = SampleKind::S24;
    } else if (out.validBits > 0 && out.validBits <= 24) {
        out.kind = SampleKind::S24_32;
    } else {
        out.kind = SampleKind::S32;
        out.validBits = 32;
    }
    return out;
}

int WasapiEngine::rateOf(const WAVEFORMATEX* format) {
    return format != nullptr && format->nSamplesPerSec > 0
            ? static_cast<int>(format->nSamplesPerSec)
            : 48000;
}

std::string WasapiEngine::formatText(const WAVEFORMATEX* format) {
    if (format == nullptr) {
        return "unknown";
    }
    const SampleFormat spec = describeFormat(format);
    const char* kind = "PCM";
    switch (spec.kind) {
        case SampleKind::U8: kind = "8bit PCM"; break;
        case SampleKind::S16: kind = "16bit PCM"; break;
        case SampleKind::S24: kind = "24bit PCM"; break;
        case SampleKind::S24_32: kind = "24bit PCM"; break;
        case SampleKind::S32: kind = "32bit PCM"; break;
        case SampleKind::F32: kind = "32bit float"; break;
    }
    char buffer[128];
    std::snprintf(buffer, sizeof(buffer), "%d Hz / %d ch / %s",
                  rateOf(format), spec.channels, kind);
    return buffer;
}

WAVEFORMATEXTENSIBLE* WasapiEngine::makeFloatTarget(int rate, int channels) {
    auto* target = static_cast<WAVEFORMATEXTENSIBLE*>(
            CoTaskMemAlloc(sizeof(WAVEFORMATEXTENSIBLE)));
    if (target == nullptr) {
        return nullptr;
    }
    std::memset(target, 0, sizeof(WAVEFORMATEXTENSIBLE));
    target->Format.wFormatTag = WAVE_FORMAT_EXTENSIBLE;
    target->Format.nChannels = static_cast<WORD>(std::max(1, channels));
    target->Format.nSamplesPerSec = static_cast<DWORD>(std::max(1, rate));
    target->Format.wBitsPerSample = 32;
    target->Format.nBlockAlign = static_cast<WORD>(target->Format.nChannels * 4);
    target->Format.nAvgBytesPerSec = target->Format.nSamplesPerSec * target->Format.nBlockAlign;
    target->Format.cbSize = 22;
    target->Samples.wValidBitsPerSample = 32;
    target->dwChannelMask = target->Format.nChannels == 1 ? 0x4 : 0x3;
    static const GUID kSubtypeFloat =
            {0x00000003, 0x0000, 0x0010, {0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71}};
    target->SubFormat = kSubtypeFloat;
    return target;
}

void WasapiEngine::readMono(const BYTE* data, UINT32 frames, const SampleFormat& format,
                            ChannelDownmixer& downmix, float* mono) {
    const int channels = format.channels;
    const int frameBytes = format.bytesPerSample * channels;
    auto decode = [&format](const BYTE* p) -> float {
        // 有效位数决定归一化尺度：容器 32bit 但只有 20/24 个有效位时按有效位缩放
        const int bits = std::max(1, std::min(32, format.validBits > 0
                ? static_cast<int>(format.validBits) : 24));
        const float scale = static_cast<float>(1u << (bits - 1));
        switch (format.kind) {
            case SampleKind::F32: {
                float v = 0.f;
                std::memcpy(&v, p, sizeof(v));
                return v;
            }
            case SampleKind::U8:
                return (static_cast<int>(p[0]) - 128) / 128.f;
            case SampleKind::S16: {
                int16_t v = 0;
                std::memcpy(&v, p, sizeof(v));
                return v / 32768.f;
            }
            case SampleKind::S24: {
                int32_t v = static_cast<int32_t>(p[0])
                        | (static_cast<int32_t>(p[1]) << 8)
                        | (static_cast<int32_t>(p[2]) << 16);
                if ((v & 0x800000) != 0) {
                    v |= ~0xFFFFFF;
                }
                v >>= (24 - std::min(bits, 24));
                return v / scale;
            }
            case SampleKind::S24_32: {
                int32_t v = 0;
                std::memcpy(&v, p, sizeof(v));
                v >>= (32 - bits);
                return v / scale;
            }
            case SampleKind::S32: {
                int32_t v = 0;
                std::memcpy(&v, p, sizeof(v));
                return static_cast<float>(v) / 2147483648.f;
            }
        }
        return 0.f;
    };
    float values[32];
    for (UINT32 f = 0; f < frames; ++f) {
        const BYTE* frame = data + static_cast<size_t>(f) * frameBytes;
        if (channels <= 32) {
            for (int c = 0; c < channels; ++c) {
                values[c] = decode(frame + c * format.bytesPerSample);
            }
            mono[f] = downmix.process(values);
        } else {
            float sum = 0.f;
            for (int c = 0; c < channels; ++c) {
                sum += decode(frame + c * format.bytesPerSample);
            }
            mono[f] = sum / static_cast<float>(channels);
        }
    }
}

void WasapiEngine::writeFrames(BYTE* data, const float* mono, UINT32 frames,
                               const WAVEFORMATEX* format) {
    const SampleFormat spec = describeFormat(format);
    const int channels = spec.channels;
    for (UINT32 f = 0; f < frames; ++f) {
        const float value = clampSample(mono[f]);
        BYTE* frame = data + static_cast<size_t>(f) * spec.bytesPerSample * channels;
        for (int c = 0; c < channels; ++c) {
            BYTE* p = frame + c * spec.bytesPerSample;
            switch (spec.kind) {
                case SampleKind::F32: {
                    const float v = value;
                    std::memcpy(p, &v, sizeof(v));
                    break;
                }
                case SampleKind::U8: {
                    p[0] = static_cast<BYTE>(static_cast<int>(value * 127.f) + 128);
                    break;
                }
                case SampleKind::S16: {
                    const int16_t v = static_cast<int16_t>(value * 32767.f);
                    std::memcpy(p, &v, sizeof(v));
                    break;
                }
                case SampleKind::S24: {
                    const int bits = std::max(1, std::min(24,
                            spec.validBits > 0 ? static_cast<int>(spec.validBits) : 24));
                    const int64_t scaled = static_cast<int64_t>(
                            value * static_cast<float>((1u << (bits - 1)) - 1));
                    const uint32_t v = static_cast<uint32_t>(scaled) << (24 - bits);
                    p[0] = static_cast<BYTE>(v & 0xFF);
                    p[1] = static_cast<BYTE>((v >> 8) & 0xFF);
                    p[2] = static_cast<BYTE>((v >> 16) & 0xFF);
                    break;
                }
                case SampleKind::S24_32: {
                    const int bits = std::max(1, std::min(32,
                            spec.validBits > 0 ? static_cast<int>(spec.validBits) : 24));
                    const int64_t scaled = static_cast<int64_t>(
                            value * static_cast<float>((1u << (bits - 1)) - 1));
                    const uint32_t v = static_cast<uint32_t>(scaled) << (32 - bits);
                    std::memcpy(p, &v, sizeof(v));
                    break;
                }
                case SampleKind::S32: {
                    const int32_t v = static_cast<int32_t>(value * 2147483000.f);
                    std::memcpy(p, &v, sizeof(v));
                    break;
                }
            }
        }
    }
}

std::string WasapiEngine::captureInputFormat() const {
    std::lock_guard<std::mutex> lock(formatMutex_);
    return captureInputText_;
}

std::string WasapiEngine::captureDriverFormat() const {
    std::lock_guard<std::mutex> lock(formatMutex_);
    return captureDriverText_;
}

std::vector<std::wstring> WasapiEngine::listEndpoints(bool capture) {
    std::vector<std::wstring> names;
    const HRESULT comHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    IMMDeviceEnumerator* enumerator = nullptr;
    IMMDeviceCollection* collection = nullptr;
    if (SUCCEEDED(CoCreateInstance(CLSID_MMDeviceEnumerator, nullptr, CLSCTX_ALL,
                                   IID_IMMDeviceEnumerator,
                                   reinterpret_cast<void**>(&enumerator)))
            && enumerator != nullptr) {
        const EDataFlow flow = capture ? eCapture : eRender;
        if (SUCCEEDED(enumerator->EnumAudioEndpoints(flow, DEVICE_STATE_ACTIVE, &collection))
                && collection != nullptr) {
            UINT count = 0;
            collection->GetCount(&count);
            for (UINT i = 0; i < count; ++i) {
                IMMDevice* device = nullptr;
                if (SUCCEEDED(collection->Item(i, &device)) && device != nullptr) {
                    std::wstring name = endpointName(device);
                    if (!name.empty()) {
                        names.push_back(name);
                    }
                    device->Release();
                }
            }
            collection->Release();
        }
        enumerator->Release();
    }
    // 只有真正初始化成功才配对调用 CoUninitialize（RPC_E_CHANGED_MODE 时不能调用）
    if (SUCCEEDED(comHr)) {
        CoUninitialize();
    }
    return names;
}

void WasapiEngine::signalReady(bool ok, const std::string& error) {
    std::lock_guard<std::mutex> lock(readyMutex_);
    ++readyDone_;
    if (!ok && readyError_.empty()) {
        readyError_ = error.empty() ? "音频设备初始化失败" : error;
    }
    readyCv_.notify_all();
}

bool WasapiEngine::start(const Config& config, BlockCallback callback, std::string& error) {
    stop();
    config_ = config;
    if (config_.blockFrames <= 0) {
        config_.blockFrames = 480;
    }
    callback_ = std::move(callback);
    running_.store(true);
    stopEvent_ = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (stopEvent_ == nullptr) {
        running_.store(false);
        error = "无法创建停止事件";
        return false;
    }
    {
        std::lock_guard<std::mutex> lock(readyMutex_);
        readyDone_ = 0;
        readyTotal_ = config_.monitorEnabled ? 3 : 2;
        readyError_.clear();
    }

    outSink_ = std::make_unique<Sink>(config_.blockFrames);
    outSink_->name = config_.renderName;
    if (config_.monitorEnabled) {
        monSink_ = std::make_unique<Sink>(config_.blockFrames);
        monSink_->name = config_.monitorName;
    }

    captureThread_ = std::thread(&WasapiEngine::captureLoop, this);
    outSink_->thread = std::thread(&WasapiEngine::renderLoop, this, outSink_.get(), true, false);
    if (monSink_) {
        monSink_->thread = std::thread(&WasapiEngine::renderLoop, this, monSink_.get(), false, true);
    }

    {
        std::unique_lock<std::mutex> lock(readyMutex_);
        readyCv_.wait_for(lock, std::chrono::seconds(6),
                          [this] { return readyDone_ >= readyTotal_; });
        if (!readyError_.empty() || readyDone_ < readyTotal_) {
            error = readyError_.empty() ? "音频设备初始化超时" : readyError_;
            lock.unlock();
            stop();
            return false;
        }
    }
    return true;
}

void WasapiEngine::stop() {
    running_.store(false);
    {
        std::lock_guard<std::mutex> lock(formatMutex_);
        captureInputText_.clear();
        captureDriverText_.clear();
    }
    if (stopEvent_ != nullptr) {
        SetEvent(stopEvent_);
    }
    if (captureThread_.joinable()) {
        captureThread_.join();
    }
    {
        // 与 writeOutput/writeMonitor 互斥，避免 Java 线程写入已释放的队列
        std::lock_guard<std::mutex> lock(sinkMutex_);
        if (outSink_ && outSink_->thread.joinable()) {
            outSink_->thread.join();
        }
        if (monSink_ && monSink_->thread.joinable()) {
            monSink_->thread.join();
        }
        outSink_.reset();
        monSink_.reset();
    }
    if (stopEvent_ != nullptr) {
        CloseHandle(stopEvent_);
        stopEvent_ = nullptr;
    }
    callback_ = nullptr;
}

void WasapiEngine::writeOutput(const float* mono, int frames) {
    if (mono == nullptr || frames <= 0) {
        return;
    }
    std::lock_guard<std::mutex> lock(sinkMutex_);
    if (outSink_ && running_.load()) {
        outSink_->queue.push(mono, frames);
    }
}

void WasapiEngine::writeMonitor(const float* mono, int frames) {
    if (mono == nullptr || frames <= 0) {
        return;
    }
    std::lock_guard<std::mutex> lock(sinkMutex_);
    if (monSink_ && running_.load()) {
        monSink_->queue.push(mono, frames);
    }
}

void WasapiEngine::captureLoop() {
    MmcssScope mmcss;
    const HRESULT comHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    bool ready = false;
    std::string error;
    IMMDeviceEnumerator* enumerator = nullptr;
    IMMDevice* device = nullptr;
    IAudioClient* client = nullptr;
    IAudioCaptureClient* capture = nullptr;
    WAVEFORMATEX* mixFormat = nullptr;
    WAVEFORMATEXTENSIBLE* targetFormat = nullptr;
    WAVEFORMATEX* activeFormat = nullptr;
    std::string driverMode;
    HANDLE event = nullptr;

    do {
        if (FAILED(CoCreateInstance(CLSID_MMDeviceEnumerator, nullptr, CLSCTX_ALL,
                                    IID_IMMDeviceEnumerator,
                                    reinterpret_cast<void**>(&enumerator)))) {
            error = "无法创建音频设备枚举器";
            break;
        }
        device = findEndpoint(enumerator, eCapture, config_.captureName, -1);
        if (device == nullptr) {
            error = "未找到可用的录音设备";
            break;
        }
        if (FAILED(device->Activate(IID_IAudioClient, CLSCTX_ALL, nullptr,
                                    reinterpret_cast<void**>(&client)))) {
            error = "无法激活录音设备";
            break;
        }
        if (FAILED(client->GetMixFormat(&mixFormat)) || mixFormat == nullptr) {
            error = "无法读取录音设备格式";
            break;
        }
        {
            std::lock_guard<std::mutex> lock(formatMutex_);
            captureInputText_ = formatText(mixFormat);
            captureDriverText_.clear();
        }
        event = CreateEventW(nullptr, FALSE, FALSE, nullptr);
        if (event == nullptr) {
            error = "无法创建采集事件";
            break;
        }
        HRESULT hr = E_FAIL;
        const REFERENCE_TIME bufferDuration = bufferDurationFor(client);
        // ① 尝试直接以 48kHz / 单声道 / float32 打开（部分驱动支持）
        targetFormat = makeFloatTarget(config_.sampleRate, 1);
        if (targetFormat != nullptr
                && SUCCEEDED(client->IsFormatSupported(AUDCLNT_SHAREMODE_SHARED,
                        reinterpret_cast<const WAVEFORMATEX*>(targetFormat), nullptr))) {
            hr = client->Initialize(AUDCLNT_SHAREMODE_SHARED,
                                    AUDCLNT_STREAMFLAGS_EVENTCALLBACK,
                                    bufferDuration, 0,
                                    reinterpret_cast<const WAVEFORMATEX*>(targetFormat), nullptr);
            if (SUCCEEDED(hr)) {
                activeFormat = reinterpret_cast<WAVEFORMATEX*>(targetFormat);
                driverMode = "direct";
            }
        }
        // ② 让 Windows 音频引擎完成采样率/声道/位深转换
        if (activeFormat == nullptr && targetFormat != nullptr) {
            hr = client->Initialize(AUDCLNT_SHAREMODE_SHARED,
                                    AUDCLNT_STREAMFLAGS_EVENTCALLBACK
                                            | AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM
                                            | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY,
                                    bufferDuration, 0,
                                    reinterpret_cast<const WAVEFORMATEX*>(targetFormat), nullptr);
            if (SUCCEEDED(hr)) {
                activeFormat = reinterpret_cast<WAVEFORMATEX*>(targetFormat);
                driverMode = "AUTOCONVERT";
            }
        }
        // ③ 兜底：使用设备混音格式 + 自有转换链
        if (activeFormat == nullptr) {
            hr = client->Initialize(AUDCLNT_SHAREMODE_SHARED,
                                    AUDCLNT_STREAMFLAGS_EVENTCALLBACK,
                                    bufferDuration, 0, mixFormat, nullptr);
            if (FAILED(hr)) {
                error = "录音设备初始化失败 " + hrText(hr);
                break;
            }
            activeFormat = mixFormat;
            driverMode = "engine";
        }
        {
            std::lock_guard<std::mutex> lock(formatMutex_);
            captureDriverText_ = formatText(activeFormat) + " / " + driverMode;
        }
        if (FAILED(client->SetEventHandle(event))) {
            error = "无法绑定采集事件";
            break;
        }
        hr = client->GetService(IID_IAudioCaptureClient, reinterpret_cast<void**>(&capture));
        if (FAILED(hr) || capture == nullptr) {
            error = "无法获取采集接口 " + hrText(hr);
            break;
        }
        hr = client->Start();
        if (FAILED(hr)) {
            error = "无法启动录音设备 " + hrText(hr);
            break;
        }
        ready = true;
    } while (false);

    signalReady(ready, error);

    if (ready) {
        const SampleFormat spec = describeFormat(activeFormat);
        ChannelDownmixer downmix;
        downmix.prepare(spec.channels);
        SincResampler resampler(rateOf(activeFormat), config_.sampleRate);
        const int blockFrames = config_.blockFrames;
        std::vector<float> mono;
        std::vector<float> resampled;
        std::vector<float> pending;
        size_t pendingRead = 0;
        mono.reserve(4096);
        pending.reserve(8192);

        while (running_.load()) {
            HANDLE handles[2] = { event, stopEvent_ };
            DWORD waited = WaitForMultipleObjects(2, handles, FALSE, 100);
            if (!running_.load()) {
                break;
            }
            if (waited == (WAIT_OBJECT_0 + 1)) {
                break;
            }
            if (waited != WAIT_OBJECT_0) {
                continue;
            }
            UINT32 packet = 0;
            while (running_.load()
                    && SUCCEEDED(capture->GetNextPacketSize(&packet)) && packet > 0) {
                BYTE* data = nullptr;
                UINT32 frames = 0;
                DWORD flags = 0;
                if (FAILED(capture->GetBuffer(&data, &frames, &flags, nullptr, nullptr))) {
                    break;
                }
                if (frames > 0) {
                    mono.resize(frames);
                    if ((flags & AUDCLNT_BUFFERFLAGS_SILENT) != 0 || data == nullptr) {
                        std::fill(mono.begin(), mono.end(), 0.f);
                    } else {
                        readMono(data, frames, spec, downmix, mono.data());
                    }
                    resampler.process(mono.data(), static_cast<int>(frames), resampled);
                    if (!resampled.empty()) {
                        pending.insert(pending.end(), resampled.begin(), resampled.end());
                    }
                    while (pending.size() - pendingRead >= static_cast<size_t>(blockFrames)) {
                        if (callback_) {
                            callback_(pending.data() + pendingRead, blockFrames);
                        }
                        pendingRead += static_cast<size_t>(blockFrames);
                        if (!running_.load()) {
                            break;
                        }
                    }
                    if (pendingRead > 16384) {
                        pending.erase(pending.begin(),
                                      pending.begin() + static_cast<std::ptrdiff_t>(pendingRead));
                        pendingRead = 0;
                    }
                }
                capture->ReleaseBuffer(frames);
            }
        }
    }

    if (client != nullptr) {
        client->Stop();
    }
    if (capture != nullptr) {
        capture->Release();
    }
    if (client != nullptr) {
        client->Release();
    }
    if (device != nullptr) {
        device->Release();
    }
    if (enumerator != nullptr) {
        enumerator->Release();
    }
    if (event != nullptr) {
        CloseHandle(event);
    }
    if (targetFormat != nullptr) {
        CoTaskMemFree(targetFormat);
    }
    if (mixFormat != nullptr) {
        CoTaskMemFree(mixFormat);
    }
    // 如果通过 JNI AttachCurrentThread 绑定过，必须 DetachCurrentThread 释放 JVM 内部记录
    if (config_.vm != nullptr) {
        config_.vm->DetachCurrentThread();
    }
    if (SUCCEEDED(comHr)) {
        CoUninitialize();
    }
}

void WasapiEngine::renderLoop(Sink* sink, bool preferVirtual, bool monitor) {
    (void) monitor;
    MmcssScope mmcss;
    const HRESULT comHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    bool ready = false;
    std::string error;
    IMMDeviceEnumerator* enumerator = nullptr;
    IMMDevice* device = nullptr;
    IAudioClient* client = nullptr;
    IAudioRenderClient* render = nullptr;
    WAVEFORMATEX* mixFormat = nullptr;
    WAVEFORMATEXTENSIBLE* targetFormat = nullptr;
    WAVEFORMATEX* activeFormat = nullptr;
    HANDLE event = nullptr;
    UINT32 bufferFrames = 0;

    do {
        if (FAILED(CoCreateInstance(CLSID_MMDeviceEnumerator, nullptr, CLSCTX_ALL,
                                    IID_IMMDeviceEnumerator,
                                    reinterpret_cast<void**>(&enumerator)))) {
            error = "无法创建音频设备枚举器";
            break;
        }
        device = findEndpoint(enumerator, eRender, sink->name, preferVirtual ? 1 : 0);
        if (device == nullptr) {
            error = "未找到可用的播放设备";
            break;
        }
        if (FAILED(device->Activate(IID_IAudioClient, CLSCTX_ALL, nullptr,
                                    reinterpret_cast<void**>(&client)))) {
            error = "无法激活播放设备";
            break;
        }
        if (FAILED(client->GetMixFormat(&mixFormat)) || mixFormat == nullptr) {
            error = "无法读取播放设备格式";
            break;
        }
        event = CreateEventW(nullptr, FALSE, FALSE, nullptr);
        if (event == nullptr) {
            error = "无法创建播放事件";
            break;
        }
        HRESULT hr = E_FAIL;
        const REFERENCE_TIME bufferDuration = bufferDurationFor(client);
        targetFormat = makeFloatTarget(config_.sampleRate, 1);
        if (targetFormat != nullptr
                && SUCCEEDED(client->IsFormatSupported(AUDCLNT_SHAREMODE_SHARED,
                        reinterpret_cast<const WAVEFORMATEX*>(targetFormat), nullptr))) {
            hr = client->Initialize(AUDCLNT_SHAREMODE_SHARED,
                                    AUDCLNT_STREAMFLAGS_EVENTCALLBACK,
                                    bufferDuration, 0,
                                    reinterpret_cast<const WAVEFORMATEX*>(targetFormat), nullptr);
            if (SUCCEEDED(hr)) {
                activeFormat = reinterpret_cast<WAVEFORMATEX*>(targetFormat);
            }
        }
        if (activeFormat == nullptr && targetFormat != nullptr) {
            hr = client->Initialize(AUDCLNT_SHAREMODE_SHARED,
                                    AUDCLNT_STREAMFLAGS_EVENTCALLBACK
                                            | AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM
                                            | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY,
                                    bufferDuration, 0,
                                    reinterpret_cast<const WAVEFORMATEX*>(targetFormat), nullptr);
            if (SUCCEEDED(hr)) {
                activeFormat = reinterpret_cast<WAVEFORMATEX*>(targetFormat);
            }
        }
        if (activeFormat == nullptr) {
            hr = client->Initialize(AUDCLNT_SHAREMODE_SHARED,
                                    AUDCLNT_STREAMFLAGS_EVENTCALLBACK,
                                    bufferDuration, 0, mixFormat, nullptr);
            if (FAILED(hr)) {
                error = "播放设备初始化失败 " + hrText(hr);
                break;
            }
            activeFormat = mixFormat;
        }
        if (FAILED(client->SetEventHandle(event))) {
            error = "无法绑定播放事件";
            break;
        }
        hr = client->GetService(IID_IAudioRenderClient, reinterpret_cast<void**>(&render));
        if (FAILED(hr) || render == nullptr) {
            error = "无法获取播放接口 " + hrText(hr);
            break;
        }
        if (FAILED(client->GetBufferSize(&bufferFrames))) {
            error = "无法读取播放缓冲区";
            break;
        }
        hr = client->Start();
        if (FAILED(hr)) {
            error = "无法启动播放设备 " + hrText(hr);
            break;
        }
        ready = true;
    } while (false);

    signalReady(ready, error);

    if (ready) {
        SincResampler resampler(config_.sampleRate, rateOf(activeFormat));
        std::vector<float> produced;
        produced.reserve(4096);
        while (running_.load()) {
            HANDLE handles[2] = { event, stopEvent_ };
            DWORD waited = WaitForMultipleObjects(2, handles, FALSE, 100);
            if (!running_.load() || waited == (WAIT_OBJECT_0 + 1)) {
                break;
            }
            if (waited != WAIT_OBJECT_0) {
                continue;
            }
            UINT32 padding = 0;
            if (FAILED(client->GetCurrentPadding(&padding))) {
                break;
            }
            UINT32 available = bufferFrames > padding ? bufferFrames - padding : 0;
            if (available == 0) {
                continue;
            }
            while (sink->device.size() - sink->deviceRead < available) {
                if (!sink->queue.pop(sink->block.data(), config_.blockFrames, 40)) {
                    std::fill(sink->block.begin(), sink->block.end(), 0.f);
                }
                resampler.process(sink->block.data(), config_.blockFrames, produced);
                if (!produced.empty()) {
                    sink->device.insert(sink->device.end(), produced.begin(), produced.end());
                }
                if (!running_.load()) {
                    break;
                }
            }
            if (!running_.load()) {
                break;
            }
            BYTE* data = nullptr;
            if (SUCCEEDED(render->GetBuffer(available, &data)) && data != nullptr) {
                writeFrames(data, sink->device.data() + sink->deviceRead, available, activeFormat);
                render->ReleaseBuffer(available, 0);
                // 仅在真正写入设备后推进读位置，避免 GetBuffer 瞬时失败时丢音
                sink->deviceRead += available;
            }
            if (sink->deviceRead > 16384) {
                sink->device.erase(sink->device.begin(),
                                   sink->device.begin()
                                           + static_cast<std::ptrdiff_t>(sink->deviceRead));
                sink->deviceRead = 0;
            }
        }
    }

    if (client != nullptr) {
        client->Stop();
    }
    if (render != nullptr) {
        render->Release();
    }
    if (client != nullptr) {
        client->Release();
    }
    if (device != nullptr) {
        device->Release();
    }
    if (enumerator != nullptr) {
        enumerator->Release();
    }
    if (event != nullptr) {
        CloseHandle(event);
    }
    if (targetFormat != nullptr) {
        CoTaskMemFree(targetFormat);
    }
    if (mixFormat != nullptr) {
        CoTaskMemFree(mixFormat);
    }
    if (SUCCEEDED(comHr)) {
        CoUninitialize();
    }
}

} // namespace vc
