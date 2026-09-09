#include <jni.h>
#include <chrono>
#include <cstdint>
#include <memory>
#include <ableton/Link.hpp>

// Helper macro for C-linkage export
#if defined(_WIN32)
#define JNI_EXPORT __declspec(dllexport)
#else
#define JNI_EXPORT __attribute__((visibility("default")))
#endif

extern "C" {

JNI_EXPORT jlong JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nInit(
    JNIEnv* env, jclass clazz, jdouble initialBpm) {
    try {
        auto* link = new ableton::Link(initialBpm);
        return reinterpret_cast<jlong>(link);
    } catch (...) {
        return 0;
    }
}

JNI_EXPORT void JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nFree(
    JNIEnv* env, jclass clazz, jlong handle) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        delete link;
    }
}

JNI_EXPORT void JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nSetEnabled(
    JNIEnv* env, jclass clazz, jlong handle, jboolean enabled) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        link->enable(enabled == JNI_TRUE);
    }
}

JNI_EXPORT jboolean JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nIsEnabled(
    JNIEnv* env, jclass clazz, jlong handle) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        return link->isEnabled() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNI_EXPORT jint JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nGetNumPeers(
    JNIEnv* env, jclass clazz, jlong handle) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        return static_cast<jint>(link->numPeers());
    }
    return 0;
}

JNI_EXPORT jdouble JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nGetTempo(
    JNIEnv* env, jclass clazz, jlong handle) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        auto sessionState = link->captureAppSessionState();
        return sessionState.tempo();
    }
    return 120.0;
}

JNI_EXPORT void JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nSetTempo(
    JNIEnv* env, jclass clazz, jlong handle, jdouble bpm, jlong timeUs) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        auto sessionState = link->captureAppSessionState();
        std::chrono::microseconds time(timeUs);
        sessionState.setTempo(bpm, time);
        link->commitAppSessionState(sessionState);
    }
}

JNI_EXPORT jdouble JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nGetBeatAtTime(
    JNIEnv* env, jclass clazz, jlong handle, jlong timeUs, jdouble quantum) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        auto sessionState = link->captureAppSessionState();
        std::chrono::microseconds time(timeUs);
        return sessionState.beatAtTime(time, quantum);
    }
    return 0.0;
}

JNI_EXPORT jdouble JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nGetPhaseAtTime(
    JNIEnv* env, jclass clazz, jlong handle, jlong timeUs, jdouble quantum) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        auto sessionState = link->captureAppSessionState();
        std::chrono::microseconds time(timeUs);
        return sessionState.phaseAtTime(time, quantum);
    }
    return 0.0;
}

JNI_EXPORT void JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nRequestBeatAtTime(
    JNIEnv* env, jclass clazz, jlong handle, jdouble beat, jlong timeUs, jdouble quantum) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        auto sessionState = link->captureAppSessionState();
        std::chrono::microseconds time(timeUs);
        sessionState.requestBeatAtTime(beat, time, quantum);
        link->commitAppSessionState(sessionState);
    }
}

JNI_EXPORT void JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nSetStartStopSyncEnabled(
    JNIEnv* env, jclass clazz, jlong handle, jboolean enabled) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        link->enableStartStopSync(enabled == JNI_TRUE);
    }
}

JNI_EXPORT jboolean JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nIsStartStopSyncEnabled(
    JNIEnv* env, jclass clazz, jlong handle) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        return link->isStartStopSyncEnabled() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNI_EXPORT jboolean JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nIsPlaying(
    JNIEnv* env, jclass clazz, jlong handle) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        auto sessionState = link->captureAppSessionState();
        return sessionState.isPlaying() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNI_EXPORT void JNICALL
Java_llm_slop_liquidlsd_link_NativeJniLinkBackend_00024NativeLinkBindings_nSetIsPlaying(
    JNIEnv* env, jclass clazz, jlong handle, jboolean isPlaying, jlong timeUs) {
    if (handle != 0) {
        auto* link = reinterpret_cast<ableton::Link*>(handle);
        auto sessionState = link->captureAppSessionState();
        std::chrono::microseconds time(timeUs);
        sessionState.setIsPlaying(isPlaying == JNI_TRUE, time);
        link->commitAppSessionState(sessionState);
    }
}

} // extern "C"
