//
// Created by 龚健飞 on 2021/5/20.
//
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#ifndef GLLEARNING_SOUNDPLAYER_H
#define GLLEARNING_SOUNDPLAYER_H

/**
 * 播放声音
 * @param pcmFilePath pcm文件路径
 * @param numChannels 声道数
 * @param samplesPerSec 采样率
 * @param bitsPerSample 采样位数
 * @param containerSize ?? 暂和bitsPerSample一样
 * @param channelMask 声道类型
 * @param endianness 大小端
 * */
void playVoice(const char *pcmFilePath, SLuint32 numChannels, SLuint32 samplesPerSec, SLuint32 bitsPerSample,
               SLuint32 containerSize, SLuint32 channelMask, SLuint32 endianness);

/**
 * 停止播放
 * */
void stopVoice();

/**
 * 释放资源
 * */
void releaseVoice();

#endif //GLLEARNING_SOUNDPLAYER_H
