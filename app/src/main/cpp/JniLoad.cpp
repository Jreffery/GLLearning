//
// Created by 龚健飞 on 2021/5/19.
//
#include <jni.h>
#include "myutils.h"
#include "VoicePlayer.h"
#include "J2CMapping.h"
#include "playcallback.h"

#define LOG_TAG "voice_lib"

static JavaVM* javaVm = nullptr;
static jobject mPlayerMgrObj = nullptr;

static void jni_play(JNIEnv * env, jobject obj, jstring jpcmFilePath, jint channelNum,
        jint sampleRate, jint channelType, jint bitsPerSample, jint endianness) {
    if (mPlayerMgrObj == nullptr) {
        mPlayerMgrObj = env->NewGlobalRef(obj);
    }
    // 文件路径从jstring转换成char *
    jboolean copy = JNI_TRUE;
    const char *nativeString = env->GetStringUTFChars(jpcmFilePath, &copy);

    SLuint32 realBitsSample;
    if (bitsPerSample == ENCODING_8BIT) {
        realBitsSample = SL_PCMSAMPLEFORMAT_FIXED_8;
    } else if (bitsPerSample == ENCODING_16BIT) {
        realBitsSample = SL_PCMSAMPLEFORMAT_FIXED_16;
    } else {
        return;
    }

    SLuint32 realChannelType;
    if (channelType == CHANNEL_OUT_MONO) {
        realChannelType = SL_SPEAKER_FRONT_LEFT;
    } else if (channelType == CHANNEL_OUT_STEREO) {
        realChannelType = SL_SPEAKER_FRONT_CENTER;
    } else {
        return;
    }

    SLuint32 realEndianness;
    if (endianness == ENDIANNESS_BIG) {
        realEndianness = SL_BYTEORDER_BIGENDIAN;
    } else if (endianness == ENDIANNESS_LETTER) {
        realEndianness = SL_BYTEORDER_LITTLEENDIAN;
    } else {
        return;
    }

    playVoice(nativeString, channelNum, sampleRate, realBitsSample, realBitsSample, realChannelType, realEndianness);
}

static void jni_stop(JNIEnv * env, jobject obj) {
    stopVoice();
}

static void jni_release(JNIEnv * env, jobject obj) {
    releaseVoice();
    if (mPlayerMgrObj != nullptr) {
        env->DeleteGlobalRef(mPlayerMgrObj);
        mPlayerMgrObj = nullptr;
    }
}

// 类名称
static const char *audio_track_native_mgr_className = "cc/appweb/gllearning/audio/AudioTrackNativeMgr";
JNINativeMethod audio_track_methods[] = {
        {"play", "(Ljava/lang/String;IIIII)V", (void *)jni_play},
        {"stop","()V", (void *)jni_stop},
        {"release","()V", (void *)jni_release}
};
static jmethodID mOnPlayMethod = nullptr;
static jmethodID mOnStopMethod = nullptr;


// 动态注册jni函数
static int registerNativeMethods(JNIEnv* env, const char* className, JNINativeMethod* methods, int numMethods) {
    LOGD(LOG_TAG, "registerNativeMethods, className=%s, numMethods=%d", className, numMethods);
    jclass clazz = env->FindClass(className);
    if (clazz == NULL) {
        LOGE(LOG_TAG, "Native registration unable to find class %s", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, methods, numMethods) < 0) {
        LOGE(LOG_TAG, "RegisterNatives failed for %s", className);
        return JNI_FALSE;
    }
    LOGD(LOG_TAG, "RegisterNatives suceess for %s", className);
    return JNI_TRUE;
}

// 该方法定义在jni.h，以extern "C" 导出为C格式的函数符号
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGD(LOG_TAG, "JNI_OnLoad");
    javaVm = vm;
    JNIEnv *env;
    if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE(LOG_TAG, "Failed to get the environment using GetEnv()");
        return JNI_ERR;
    }

    // 获取回调方法id
    jclass playMgrClazz = env->FindClass(audio_track_native_mgr_className);
    mOnPlayMethod = env->GetMethodID(playMgrClazz, "onPlay", "()V");
    mOnStopMethod = env->GetMethodID(playMgrClazz, "onStop", "()V");
    env->DeleteLocalRef(playMgrClazz);

    LOGD(LOG_TAG, "JNI_OnLoad registerNativeMethods");
    // 注册native方法
    if (!registerNativeMethods(env, audio_track_native_mgr_className, audio_track_methods, sizeof(audio_track_methods)/sizeof(audio_track_methods[0])))
    {
        LOGD(LOG_TAG, "registerNativeMethods audio_track_native_mgr_className fail");
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

// 该方法定义在jni.h，以extern "C" 导出为C格式的函数符号
JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGD(LOG_TAG, "JNI_OnUnload");
}

void onPlay() {
    if (javaVm != nullptr && mPlayerMgrObj != nullptr && mOnPlayMethod != nullptr) {
        JNIEnv* env = nullptr;
        // 当前线程需要attach到JVM上
        javaVm->AttachCurrentThread(&env, nullptr);
        env->CallVoidMethod(mPlayerMgrObj, mOnPlayMethod);
    }
}

void onStop() {
    if (javaVm != nullptr && mPlayerMgrObj != nullptr && mOnStopMethod != nullptr) {
        JNIEnv* env = nullptr;
        // 当前线程需要attach到JVM上
        javaVm->AttachCurrentThread(&env, nullptr);
        env->CallVoidMethod(mPlayerMgrObj, mOnStopMethod);
    }
}


