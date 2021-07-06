//
// Created by 龚健飞 on 2021/5/20.
//

#include <android/log.h>
#include <jni.h>

#ifndef GLLEARNING_MYUTILS_H
#define GLLEARNING_MYUTILS_H

#ifndef LOG_ENABLE

#define LOG_ENABLE true

#endif

#if LOG_ENABLE

#define LOGD(TAG, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, ##args)
#define LOGI(TAG, args...) __android_log_print(ANDROID_LOG_INFO, TAG, ##args)
#define LOGW(TAG, args...) __android_log_print(ANDROID_LOG_WARN, TAG, ##args)
#define LOGE(TAG, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, ##args)

#else

#define LOGD(TAG, args...)
#define LOGI(TAG, args...)
#define LOGW(TAG, args...)
#define LOGE(TAG, args...)

#endif

#define MY_UTILS_TAG  "myUtils"

// 动态注册jni函数
static int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *methods,
                                 int numMethods) {
    LOGD(MY_UTILS_TAG, "registerNativeMethods, className=%s, numMethods=%d", className, numMethods);
    jclass clazz = env->FindClass(className);
    if (clazz == NULL) {
        LOGE(MY_UTILS_TAG, "Native registration unable to find class %s", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, methods, numMethods) < 0) {
        LOGE(MY_UTILS_TAG, "RegisterNatives failed for %s", className);
        return JNI_FALSE;
    }
    LOGD(MY_UTILS_TAG, "RegisterNatives suceess for %s", className);
    return JNI_TRUE;
}


#endif //GLLEARNING_MYUTILS_H
