//
// Created by 龚健飞 on 2021/6/22.
//

#include "VoiceRecorder.h"
#include "myutils.h"
#include <cstdio>
#include "recordcallback.h"
#include <iostream>

#define TAG "VoiceRecorder"

#define RECEIVE_DATA_SIZE 44100

// 对象与接口的关系
static SLObjectItf engineObject = nullptr; // 用SLObjectItf声明引擎接口对象
static SLEngineItf engineEngine = nullptr; // 声明具体的引擎对象实例

static SLObjectItf recordObject = nullptr; // 声明录音器对象
static SLRecordItf recordItf = nullptr; // 声明录音器接口
static SLAndroidSimpleBufferQueueItf dataBufferQueue = nullptr;  // 缓存队列接口

static char *dataReceived = nullptr;  // 存储录音数据的空间

static const char *recordFilePath = nullptr; // 待保存的文件路径
static FILE *recordFile = nullptr;  // 待保存的文件

static bool mRecording = false; // 正在录音状态
static std::mutex mtx;

static void ready2Stop() {
    // 释放堆内存
    if (recordFilePath != nullptr) {
        delete recordFilePath;
        recordFilePath = nullptr;
    }
    // 释放缓存空间内存
    if (dataReceived != nullptr) {
        delete dataReceived;
        dataReceived = nullptr;
    }
    // 关闭文件
    if (recordFile != nullptr) {
        fclose(recordFile);
        recordFile = nullptr;
    }

    // 释放录音对象
    if (recordObject != nullptr) {
        (*recordObject)->Destroy(recordObject);
        recordObject = nullptr;
        recordItf = nullptr;
        dataBufferQueue = nullptr;
    }
    // 释放sl引擎
    if (engineObject != nullptr) {
        (*engineObject)->Destroy(engineObject);
        engineObject = nullptr;
        engineEngine = nullptr;
    }

    onRecordStop();
}

static void recordCallback(SLAndroidSimpleBufferQueueItf bufferQueue, void *pContext) {
    std::lock_guard<std::mutex> lockGuard(mtx);
    if (mRecording) {
        SLuint32 result;
        SLAndroidSimpleBufferQueueState state;
        // 获取当前录音器的状态
        result = (*bufferQueue)->GetState(bufferQueue, &state);
        LOGD(TAG, "bufferQueue getState count=%d, index=%d, result=%d", state.count, state.index,
             result);
        if (recordFile == nullptr) {
            if (recordFilePath != nullptr) {
                // 打开或创建文件
                recordFile = fopen(recordFilePath, "w");
            }
        }
        if (recordFile != nullptr) {
            // 写入数据
            fwrite(dataReceived, sizeof(char), RECEIVE_DATA_SIZE, recordFile);
        }

        SLuint32 recordState;
        result = (*recordItf)->GetRecordState(recordItf, &recordState);
        LOGD(TAG, "recordItf GetRecordState state=%d, result=%d", recordState, result);
        if (recordState != SL_RECORDSTATE_STOPPED) {
            // 继续录音
            result = (*bufferQueue)->Enqueue(bufferQueue, dataReceived, RECEIVE_DATA_SIZE);
            LOGD(TAG, "continue Enqueue result=%d", result);
        }
    }
}

static void createEngine() {
    if (engineEngine == nullptr) {
        SLresult result;
        // 通过slCreateEngine创建引擎，engineObject指向引擎的指针
        result = slCreateEngine(&engineObject, 0, nullptr, 0, nullptr, nullptr);
        LOGD(TAG, "slCreateEngine result=%d", result);
        // 实现（Realize）engineObject对象，第二个参数：是否异步
        result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
        LOGD(TAG, "engineObject Realize result=%d", result);
        // 获取引擎，通过engineObject的GetInterface方法初始化engineEngine
        result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
        LOGD(TAG, "engineObject GetInterface result=%d", result);
    }
}

static void createRecord(SLuint32 endianness) {
    if (recordItf == nullptr) {
        // 录音设备
        SLDataLocator_IODevice device = {
                SL_DATALOCATOR_IODEVICE,
                SL_IODEVICE_AUDIOINPUT,
                SL_DEFAULTDEVICEID_AUDIOINPUT,
                nullptr
        };

        // 数据输入源
        SLDataSource dataSource = {&device, nullptr};

        // 采样的BufferQueue
        SLDataLocator_AndroidSimpleBufferQueue bufferQueue = {
                SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 1};

        // 采样格式等
        SLDataFormat_PCM pcmFormat = {
                SL_DATAFORMAT_PCM, // 数据格式
                1,  // 声道数
                SL_SAMPLINGRATE_44_1,  // 采样频率
                SL_PCMSAMPLEFORMAT_FIXED_16, // 每个采样点使用多少位存储
                SL_PCMSAMPLEFORMAT_FIXED_16,
                SL_SPEAKER_FRONT_CENTER, // 声音空间属性
                endianness // 大小端
        };

        // 声明数据读取方式
        SLDataSink dataSink = {&bufferQueue, &pcmFormat};

        // 需要创建录音器接口
        SLInterfaceID ids[] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
        SLboolean ids_required[] = {SL_BOOLEAN_TRUE};
        SLuint32 numInterfaces = 1;

        SLresult result;
        // 创建录音器对象
        result = (*engineEngine)->CreateAudioRecorder(engineEngine, &recordObject, &dataSource,
                                                      &dataSink, numInterfaces, ids, ids_required);
        LOGD(TAG, "engineObject CreateAudioRecorder result=%d", result);
        // 实现录音器对象
        result = (*recordObject)->Realize(recordObject, SL_BOOLEAN_FALSE);
        LOGD(TAG, "recordObject Realize result=%d", result);
        // 获取录音接口
        result = (*recordObject)->GetInterface(recordObject, SL_IID_RECORD, &recordItf);
        LOGD(TAG, "recordObject GetInterface recordItf result=%d", result);
        // 获取缓存队列接口
        result = (*recordObject)->GetInterface(recordObject, SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                                               &dataBufferQueue);
        LOGD(TAG, "recordObject GetInterface bufferQueue result=%d", result);
        // 注册数据回调
        result = (*dataBufferQueue)->RegisterCallback(dataBufferQueue, recordCallback, nullptr);
        LOGD(TAG, "dataBufferQueue RegisterCallback result=%d", result);
    }
}

static void ready2Record() {
    if (dataReceived == nullptr) {
        // 缓存空间
        dataReceived = new char[RECEIVE_DATA_SIZE];
    }
    SLuint32 result;
    // 开始录音器
    mRecording = true;
    result = (*recordItf)->SetRecordState(recordItf, SL_RECORDSTATE_RECORDING);
    LOGD(TAG, "recordItf SetRecordState result=%d", result);
    // 传入缓存空间，等待数据回调
    result = (*dataBufferQueue)->Enqueue(dataBufferQueue, dataReceived, RECEIVE_DATA_SIZE);
    LOGD(TAG, "dataBufferQueue Enqueue result=%d", result);
    onRecordStart();
}

void startRecord(const char *filepath, SLuint32 endianness) {
    LOGD(TAG, "startRecord");
    // 创建引擎
    createEngine();

    // 创建录音器
    createRecord(endianness);

    recordFilePath = filepath;
    // 准备就绪开始录音
    ready2Record();
}

void stopRecord() {
    LOGD(TAG, "stopRecord");
    std::lock_guard<std::mutex> lockGuard(mtx);
    if (recordItf != nullptr) {
        SLuint32 result;
        mRecording = false;
        // 使录音器进入Stopped状态
        result = (*recordItf)->SetRecordState(recordItf, SL_RECORDSTATE_STOPPED);
        LOGD(TAG, "recordItf SetRecordState result=%d", result);
        ready2Stop();
    }
}
