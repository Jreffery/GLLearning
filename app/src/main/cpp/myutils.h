//
// Created by 龚健飞 on 2021/5/20.
//

#include <android/log.h>

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

#elif

#define LOGD(TAG, args...)
#define LOGI(TAG, args...)
#define LOGW(TAG, args...)
#define LOGE(TAG, args...)

#endif


#endif //GLLEARNING_MYUTILS_H
