//
// Created by 龚健飞 on 2021/6/22.
//

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#ifndef GLLEARNING_VOICERECORDER_H
#define GLLEARNING_VOICERECORDER_H

/**
 * 开始录音
 * @param filepath 保存录音文件的路径
 * @param endianness 大小端
 * */
void startRecord(const char *filepath, SLuint32 endianness);

/**
 * 停止录音
 * */
void stopRecord();


#endif //GLLEARNING_VOICERECORDER_H
