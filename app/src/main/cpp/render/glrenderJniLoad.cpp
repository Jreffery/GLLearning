//
// Created by 龚健飞 on 2021/7/8.
//

#include "myutils.h"
#include <jni.h>
#include "BgRender.h"

#define LOG_TAG "glrender"

static void
jni_create(JNIEnv *env, jobject obj, jlong ptr, jint width, jint height, jobject buffer);

static void jni_draw(JNIEnv *env, jobject obj, jlong ptr);

static void jni_getData(JNIEnv *env, jobject obj, jlong ptr, jobject buffer);

static void jni_destroy(JNIEnv *env, jobject obj, jlong ptr);

static const char *bg_render = "cc/appweb/gllearning/componet/BgRender";
static JNINativeMethod bg_render_methods[] = {
        {"create",         "(JIILjava/nio/ByteBuffer;)V", (void *) jni_create},
        {"draw",           "(J)V",                      (void *) jni_draw},
        {"getDrawRawData", "(JLjava/nio/ByteBuffer;)V", (void *) jni_getData},
        {"destroy",        "(J)V",                      (void *) jni_destroy}
};
static jfieldID renderPtrField;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGD(LOG_TAG, "JNI_OnLoad");
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE(LOG_TAG, "Failed to get the environment using GetEnv()");
        return JNI_ERR;
    }
    // 获取回调方法id
    jclass renderClazz = env->FindClass(bg_render);
    renderPtrField = env->GetFieldID(renderClazz, "mNativePtr", "J");
    env->DeleteLocalRef(renderClazz);
    // 注册native方法
    if (!registerNativeMethods(env, bg_render, bg_render_methods,
                               sizeof(bg_render_methods) / sizeof(bg_render_methods[0]))) {
        LOGD(LOG_TAG, "registerNativeMethods bg_render fail");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}


JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    LOGD(LOG_TAG, "JNI_OnLoad");
}

static void jni_create(JNIEnv *env, jobject obj, jlong ptr, jint width, jint height, jobject buffer) {
    LOGD(LOG_TAG, "jni_create");
    if (ptr != 0) {
        delete (BgRender *)ptr;
    }
    const char *data = (char *)env->GetDirectBufferAddress(buffer);
    BgRender* render = new BgRender(width, height, data);
    render->CreateGlesEnv();
    env->SetLongField(obj, renderPtrField, (jlong)render);
}

static void jni_draw(JNIEnv *env, jobject obj, jlong ptr) {
    LOGD(LOG_TAG, "jni_draw");
    BgRender* render = (BgRender *) ptr;
    render->Draw();
}

static void jni_getData(JNIEnv *env, jobject obj, jlong ptr, jobject buffer) {
    LOGD(LOG_TAG, "jni_getData");
    BgRender* render = (BgRender *) ptr;
    const signed char *data = (signed char *)env->GetDirectBufferAddress(buffer);
    render->GetData(data);
}

static void jni_destroy(JNIEnv *env, jobject obj, jlong ptr) {
    LOGD(LOG_TAG, "jni_destroy");
    BgRender* render = (BgRender *) ptr;
    render->DestroyGlesEnv();
    delete (BgRender *)ptr;
}

// 区别其他native方法，该方法使用静态注册
extern "C" JNIEXPORT void JNICALL
Java_cc_appweb_gllearning_componet_BgRender_setRotate(JNIEnv *env, jobject thiz, jlong ptr, jint type) {
    LOGD(LOG_TAG, "setRotate");
    BgRender* render = (BgRender *) ptr;
    render->SetRotate(type);
}