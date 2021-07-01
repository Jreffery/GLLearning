//
// Created by 龚健飞 on 2021/5/20.
//

#include "VoicePlayer.h"
#include "myutils.h"
#include <cstdio>
#include "playcallback.h"
#include <iostream>

#define TAG "VoicePlayer"

// 对象与接口的关系
static SLObjectItf engineObject = nullptr; // 用SLObjectItf声明引擎接口对象
static SLEngineItf engineEngine = nullptr; // 声明具体的引擎对象实例

// 混音器对象
static SLObjectItf outputMixObject = nullptr;
// 混音器接口
static SLEnvironmentalReverbItf outputMixEnvironmentalReverb = nullptr;

// 播放器对象
static SLObjectItf playerObject = nullptr;
// 播放器接口
static SLPlayItf playerPlay = nullptr;
static SLAndroidSimpleBufferQueueItf bufferQueue = nullptr;

// 每次写入的大小
static SLuint32 enqueueSize;

// 待播放的pcm文件路径
static const char *playPcmFilePath = nullptr;
// 打开的文件
static FILE *playFile = nullptr;
static char *playBuffer = nullptr;

static std::mutex mtx;

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

static void createMix() {
    if (outputMixObject == nullptr) {
        // 混音器接口
        const SLInterfaceID mids[1] = {SL_IID_ENVIRONMENTALREVERB};
        // 是否需要接口
        const SLboolean mreq[1] = {SL_BOOLEAN_FALSE};

        // 调用引擎接口生成
        SLresult result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1, mids, mreq);
        LOGD(TAG, "CreateOutputMix result=%d", result);
        // 实现对象
        result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
        LOGD(TAG, "outputMixObject realize result=%d", result);
        // 获取接口
        result = (*outputMixObject)->GetInterface(outputMixObject, SL_IID_ENVIRONMENTALREVERB, &outputMixEnvironmentalReverb);
        LOGD(TAG, "outputMixObject realize result=%d", result);

        // 混音器配置参数
        SLEnvironmentalReverbSettings settings = SL_I3DL2_ENVIRONMENT_PRESET_GENERIC;
        result = (*outputMixEnvironmentalReverb)->SetEnvironmentalReverbProperties(
                outputMixEnvironmentalReverb, &settings);
        LOGD(TAG, "SetEnvironmentalReverbProperties result=%d", result);
    }
}

static void closeFile() {
    std::lock_guard<std::mutex> lock(mtx);
    if (playPcmFilePath != nullptr) {
        delete playPcmFilePath;
        playPcmFilePath = nullptr;
    }
    if (playBuffer != nullptr) {
        delete playBuffer;
        playBuffer = nullptr;
    }
    if (playFile != nullptr) {
        fclose(playFile);
        playFile = nullptr;
    }
}


static SLuint32 getPcmData(void **pcmBuffer) {
    LOGD(TAG, "getPcmData");
    // 打开文件
    SLuint32 ret = 0;
    {
        std::lock_guard<std::mutex> lock(mtx);
        if (playFile == nullptr && playPcmFilePath != nullptr) {
            LOGD(TAG, "getPcmData open file");
            playFile = fopen(playPcmFilePath, "r");
        }
        // 按顺序读取pcm文件
        if (playFile != nullptr && (feof(playFile) == 0)) {
            ret = fread(playBuffer, sizeof(char), enqueueSize, playFile);
            *pcmBuffer = playBuffer;
        }
    }

    LOGD(TAG, "getPcmData ret=%d", ret);
    return ret;
}

static void pcmBufferCallBack(SLAndroidSimpleBufferQueueItf bf, void * context) {
    LOGD(TAG, "pcmBufferCallBack");
    // 声明取数据的指针
    void *buffer;
    // 获取数据
    SLuint32 size = getPcmData(&buffer);
    if (size > 0) {
        // 写入数据
        SLresult result = (*bf)->Enqueue(bf, buffer, size);
        LOGD(TAG, "Enqueue size=%d, result=%d", size, result);
    } else {
        // 停止或播放完毕了
        LOGD(TAG, "needClose");
        closeFile();
        onStop();
    }

}

void playVoice(const char *pcmFilePath, SLuint32 numChannels, SLuint32 samplesPerSec, SLuint32 bitsPerSample,
        SLuint32 containerSize, SLuint32 channelMask, SLuint32 endianness) {

    LOGD(TAG, "playVoice pcmFilePath=%s", pcmFilePath);
    playPcmFilePath = pcmFilePath;
    enqueueSize = samplesPerSec * ((bitsPerSample + 7) / 8) * numChannels;
    playBuffer = new char[enqueueSize];

    // 创建引擎
    createEngine();

    // 创建混音器
    createMix();

    SLDataLocator_OutputMix outputMix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    // 接收端
    SLDataSink audioSnk = {&outputMix, nullptr};

    // 设置pcm格式的频率位数等信息并创建播放器
    SLDataLocator_AndroidSimpleBufferQueue android_queue={SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,1};
    // PCM 配置类型
    SLDataFormat_PCM pcm={
            SL_DATAFORMAT_PCM,//播放pcm格式的数据
            numChannels,    // 2个声道（立体声）
            samplesPerSec * 1000,  // 44100hz的频率
            bitsPerSample,  // 位数 16位
            containerSize,  // 和位数一致就行
            channelMask,    // 立体声（前左前右）
            endianness      // 大小端
    };
    // 发送端
    SLDataSource slDataSource = {&android_queue, &pcm};

    if (playerObject != nullptr) {
        (*playerObject)->Destroy(playerObject);
        playerObject = nullptr;
    }
    // 声明需要创建的接口
    const SLInterfaceID ids[3] = {SL_IID_BUFFERQUEUE, SL_IID_EFFECTSEND, SL_IID_VOLUME};
    const SLboolean req[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    SLresult result = (*engineEngine)->CreateAudioPlayer(engineEngine, &playerObject, &slDataSource, &audioSnk, 3, ids, req);
    LOGD(TAG, "CreateAudioPlayer result=%d", result);

    // 初始化播放器
    (*playerObject)->Realize(playerObject, SL_BOOLEAN_FALSE);
    // 得到接口后调用  获取Player接口
    (*playerObject)->GetInterface(playerObject, SL_IID_PLAY, &playerPlay);

    // 注册回调缓冲区 获取缓冲队列接口
    (*playerObject)->GetInterface(playerObject, SL_IID_BUFFERQUEUE, &bufferQueue);

    //缓冲接口回调
    (*bufferQueue)->RegisterCallback(bufferQueue, pcmBufferCallBack, nullptr);

    // 调用接口使得player进入播放状态
    (*playerPlay)->SetPlayState(playerPlay, SL_PLAYSTATE_PLAYING);
    onPlay();
    // 主动调用回调函数，向buffer中写入数据，以致开始工作
    pcmBufferCallBack(bufferQueue, nullptr);
}

void stopVoice() {
    LOGD(TAG, "stopVoice");
    if (playerPlay != nullptr) {
        // 设置播放状态为停止
        (*playerPlay)->SetPlayState(playerPlay, SL_PLAYSTATE_STOPPED);
        closeFile();
        onStop();
    }
}

void releaseVoice() {
    LOGD(TAG, "release");
    // 释放播放器
    if (playerObject != nullptr) {
        (*playerObject)->Destroy(playerObject);
        playerObject = nullptr;
        playerPlay = nullptr;
        bufferQueue = nullptr;
    }
    // 释放混音器对象和资源
    if (outputMixObject != nullptr) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = nullptr;
        outputMixEnvironmentalReverb = nullptr;
    }
    // 释放引擎对象和资源
    if (engineObject != nullptr) {
        (*engineObject)->Destroy(engineObject);
        engineObject = nullptr;
        engineEngine = nullptr;
    }
}


