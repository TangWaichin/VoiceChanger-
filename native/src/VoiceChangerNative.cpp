#include "WasapiEngine.h"

#include <jni.h>

#include <string>
#include <vector>

namespace {

JavaVM* g_vm = nullptr;
jobject g_listener = nullptr;
jmethodID g_onBlock = nullptr;
jfloatArray g_blockArray = nullptr;
int g_blockFrames = 480;
vc::WasapiEngine* g_engine = nullptr;

thread_local JNIEnv* t_env = nullptr;
thread_local bool t_attached = false;

JNIEnv* attachThread() {
    if (t_env != nullptr) {
        return t_env;
    }
    if (g_vm == nullptr) {
        return nullptr;
    }
    JNIEnv* env = nullptr;
    const jint state = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8);
    if (state == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) {
            return nullptr;
        }
        t_attached = true;
    } else if (state != JNI_OK) {
        return nullptr;
    }
    t_env = env;
    return env;
}

std::wstring readWide(JNIEnv* env, jstring text) {
    if (text == nullptr) {
        return {};
    }
    const jchar* chars = env->GetStringChars(text, nullptr);
    if (chars == nullptr) {
        return {};
    }
    const jsize length = env->GetStringLength(text);
    std::wstring out(reinterpret_cast<const wchar_t*>(chars),
                     reinterpret_cast<const wchar_t*>(chars) + length);
    env->ReleaseStringChars(text, chars);
    return out;
}

/** 采集线程回调：把单声道数据送到 Java 侧做 DSP。 */
void deliverBlock(const float* data, int frames) {
    JNIEnv* env = attachThread();
    if (env == nullptr || g_listener == nullptr || g_onBlock == nullptr
            || g_blockArray == nullptr || data == nullptr || frames <= 0) {
        return;
    }
    if (frames > g_blockFrames) {
        frames = g_blockFrames;
    }
    env->SetFloatArrayRegion(g_blockArray, 0, frames, data);
    env->CallVoidMethod(g_listener, g_onBlock, g_blockArray, static_cast<jint>(frames));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

void releaseListener(JNIEnv* env) {
    if (env != nullptr) {
        if (g_listener != nullptr) {
            env->DeleteGlobalRef(g_listener);
        }
        if (g_blockArray != nullptr) {
            env->DeleteGlobalRef(g_blockArray);
        }
    }
    g_listener = nullptr;
    g_blockArray = nullptr;
    g_onBlock = nullptr;
}

jobjectArray namesToArray(JNIEnv* env, const std::vector<std::wstring>& names) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        return nullptr;
    }
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(names.size()), stringClass, nullptr);
    if (array == nullptr) {
        return nullptr;
    }
    for (size_t i = 0; i < names.size(); ++i) {
        const std::string utf8 = vc::wideToUtf8(names[i]);
        jstring item = env->NewStringUTF(utf8.c_str());
        if (item != nullptr) {
            env->SetObjectArrayElement(array, static_cast<jsize>(i), item);
            env->DeleteLocalRef(item);
        }
    }
    return array;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_8;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_voicechanger_audio_NativeAudio_nativeProbe(JNIEnv*, jclass) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_voicechanger_audio_NativeAudio_nativeListDevices(JNIEnv* env, jclass, jboolean capture) {
    return namesToArray(env, vc::WasapiEngine::listEndpoints(capture == JNI_TRUE));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_voicechanger_audio_NativeAudio_nativeStart(JNIEnv* env, jclass, jobject listener,
                                                   jstring capture, jstring render,
                                                   jstring monitor, jboolean monitorEnabled,
                                                   jint blockFrames) {
    if (g_engine != nullptr) {
        g_engine->stop();
        delete g_engine;
        g_engine = nullptr;
    }
    releaseListener(env);

    if (listener == nullptr) {
        return env->NewStringUTF("监听器为空");
    }
    g_blockFrames = blockFrames > 0 ? blockFrames : 480;
    jclass listenerClass = env->GetObjectClass(listener);
    if (listenerClass == nullptr) {
        return env->NewStringUTF("无法获取监听器类型");
    }
    g_onBlock = env->GetMethodID(listenerClass, "onCaptureBlock", "([FI)V");
    env->DeleteLocalRef(listenerClass);
    if (g_onBlock == nullptr) {
        env->ExceptionClear();
        return env->NewStringUTF("监听器缺少 onCaptureBlock(float[], int) 方法");
    }
    g_listener = env->NewGlobalRef(listener);
    jfloatArray array = env->NewFloatArray(g_blockFrames);
    if (array == nullptr) {
        releaseListener(env);
        return env->NewStringUTF("无法分配音频缓冲");
    }
    g_blockArray = static_cast<jfloatArray>(env->NewGlobalRef(array));
    env->DeleteLocalRef(array);

    vc::WasapiEngine::Config config;
    config.captureName = readWide(env, capture);
    config.renderName = readWide(env, render);
    config.monitorName = readWide(env, monitor);
    config.monitorEnabled = monitorEnabled == JNI_TRUE;
    config.blockFrames = g_blockFrames;
    config.sampleRate = 48000;
    config.vm = g_vm; // 用于采集线程退出时 DetachCurrentThread

    g_engine = new vc::WasapiEngine();
    std::string error;
    if (!g_engine->start(config, &deliverBlock, error)) {
        delete g_engine;
        g_engine = nullptr;
        releaseListener(env);
        return env->NewStringUTF(error.c_str());
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_voicechanger_audio_NativeAudio_nativeStop(JNIEnv* env, jclass) {
    if (g_engine != nullptr) {
        g_engine->stop();
        delete g_engine;
        g_engine = nullptr;
    }
    releaseListener(env);
}

extern "C" JNIEXPORT void JNICALL
Java_com_voicechanger_audio_NativeAudio_nativeWriteOutput(JNIEnv* env, jclass, jfloatArray data,
                                                         jint length) {
    if (g_engine == nullptr || data == nullptr || length <= 0) {
        return;
    }
    jfloat* buffer = env->GetFloatArrayElements(data, nullptr);
    if (buffer != nullptr) {
        g_engine->writeOutput(buffer, length);
        env->ReleaseFloatArrayElements(data, buffer, JNI_ABORT);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_voicechanger_audio_NativeAudio_nativeWriteMonitor(JNIEnv* env, jclass, jfloatArray data,
                                                           jint length) {
    if (g_engine == nullptr || data == nullptr || length <= 0) {
        return;
    }
    jfloat* buffer = env->GetFloatArrayElements(data, nullptr);
    if (buffer != nullptr) {
        g_engine->writeMonitor(buffer, length);
        env->ReleaseFloatArrayElements(data, buffer, JNI_ABORT);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_voicechanger_audio_NativeAudio_nativeCaptureInputFormat(JNIEnv* env, jclass) {
    if (g_engine == nullptr) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(g_engine->captureInputFormat().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_voicechanger_audio_NativeAudio_nativeCaptureDriverFormat(JNIEnv* env, jclass) {
    if (g_engine == nullptr) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(g_engine->captureDriverFormat().c_str());
}
