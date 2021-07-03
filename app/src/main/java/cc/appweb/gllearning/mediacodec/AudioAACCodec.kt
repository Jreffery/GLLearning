package cc.appweb.gllearning.mediacodec

import android.media.*
import android.os.Build
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * 使用MediaCodec把音频pcm数据编码成AAC格式
 * AAC-LC模式每次输出都是以1024个采样点的编码容量输出
 * 如果以44100频率采样的音频，则每帧编码音频的帧时长为1024/44100 * 1000 = 23.22ms
 *
 * @param pcmFilePath pcm文件路径
 * @param aacFilePath 编码aac文件路径
 * @param channelNum 声道个数
 * @param sampleRate 采样率
 * @param channelType 声道类型
 * @param audioFormat 采样位数
 * */
class AudioAACCodec(val pcmFilePath: String, val aacFilePath: String, val channelNum: Int, val sampleRate: Int,
                    val channelType: Int, val audioFormat: Int) {

    // 编码器对象是否已使用，一个编码器对象只能使用一次
    private var mIsUsed = false

    companion object {
        private const val TAG = "AudioAACCodec"
    }

    @Synchronized
    fun start(callback: Boolean.() -> Unit) {
        if (mIsUsed) {
            callback.invoke(false)
        }
        mIsUsed = true
        // 开启编码线程
        EncodeThread(callback).start()
    }

    // 编码线程
    private inner class EncodeThread(val callback: Boolean.() -> Unit) : Thread() {

        override fun run() {
            // 文件读取size
            val bufferSize = 5 * AudioRecord.getMinBufferSize(sampleRate, if (channelNum == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO, audioFormat)
            // 获取aac的编码器
            val mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm")
            // 创建media format，设置相应参数
            val mediaFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelNum)
            // 设置aac编码的profile
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            // 设置编码码率
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
            // 设置编码器输入缓存区最大size
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            // 设置编码位数
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, audioFormat)
            }
            // configure配置生效
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            // 开启codec
            mediaCodec.start()

            // 打开文件
            val pcmFile = FileInputStream(pcmFilePath)
            val aacFile = FileOutputStream(aacFilePath)

            var mIsEnd = false
            var dataInput: ByteArray? = ByteArray(bufferSize)
            val outputCacheBuffer = ByteArray(bufferSize)
            while (!mIsEnd) {
                Log.i(TAG, "encoding")
                // 从文件中读取数据并输入到缓存区
                dataInput?.let {
                    // 读取pcm文件内容
                    val readSize = pcmFile.read(dataInput)
                    if (readSize > 0) {
                        // 等待可用的输入缓冲区，至多等待5毫秒每次
                        val inputIndex = mediaCodec.dequeueInputBuffer(5 * 1000)
                        if (inputIndex >= 0) {
                            // 取出buffer
                            val inputBuffer: ByteBuffer? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                mediaCodec.getInputBuffer(inputIndex)
                            } else {
                                mediaCodec.inputBuffers[inputIndex]
                            }
                            // 输入pcm数据
                            inputBuffer!!.apply {
                                clear()
                                put(it, 0, readSize)
                                mediaCodec.queueInputBuffer(inputIndex, 0, readSize, System.nanoTime() / 1000, 0)
                            }
                        } else {
                            Log.i(TAG, "no available input buffer")
                        }
                    } else {
                        // pcm文件已全部取出，需要向codec输入EOF
                        pcmFile.close()
                        dataInput = null
                        var inputIndex = -1
                        while (inputIndex < 0) {
                            inputIndex = mediaCodec.dequeueInputBuffer(-1)
                        }
                        // 取出buffer
                        val inputBuffer: ByteBuffer? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mediaCodec.getInputBuffer(inputIndex)
                        } else {
                            mediaCodec.inputBuffers[inputIndex]
                        }
                        // 输入EOF
                        inputBuffer!!.apply {
                            clear()
                            mediaCodec.queueInputBuffer(inputIndex, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }

                // 尝试读取输出缓存区
                val bufferInfo = MediaCodec.BufferInfo()
                // 获取编码后数据
                var outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                while (outputBufferIndex >= 0) {
                    // 取出数据
                    Log.i(TAG, "output flag=${bufferInfo.flags} size=${bufferInfo.size} offset=${bufferInfo.offset}" +
                            " presentationTime=${bufferInfo.presentationTimeUs}")

                    val outputBuffer: ByteBuffer? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mediaCodec.getOutputBuffer(outputBufferIndex)
                    } else {
                        mediaCodec.outputBuffers[outputBufferIndex]
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG > 0) {
                        // csd 编码器配置数据
                        Log.i(TAG, "aac csd ${outputBuffer!!.get()} ${outputBuffer.get()}")
                    } else if (bufferInfo.size > 0) {

                        // 填入ADTS header
                        aacFile.write(addADTStoPacket(7 + bufferInfo.size))
                        // 从输出缓存区中读取数据
                        outputBuffer!!.get(outputCacheBuffer, 0, outputBuffer.limit())

                        // 填入编码数据
                        aacFile.write(outputCacheBuffer, 0, outputBuffer.limit())
                        aacFile.flush()
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) > 0) {
                        // 输出已完成
                        mIsEnd = true
                        outputBufferIndex = -1
                        // 释放mediaCodec
                        mediaCodec.stop()
                        mediaCodec.release()
                        // 关闭文件
                        aacFile.close()
                        // 回调编码已完成
                        callback.invoke(true)
                        Log.i(TAG, "aac encode finish!")
                    } else {
                        // 释放输出缓存区给Codec
                        mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                        // 继续取出可用输出区，至多等待5毫秒每次
                        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 5 * 1000)
                    }
                }
            }
        }

        /**
         * 添加ADTS头，每个AAC编码块的前面都需要一个头部信息
         * ADTS内容 {@see https://blog.csdn.net/tantion/article/details/82743942}
         * @param packetLen 数据长度，包含头部的7字节
         */
        fun addADTStoPacket(packetLen: Int): ByteArray {
            Log.i(TAG, "addADTStoPacket packetLen=$packetLen")
            val adts = ByteArray(7) // 固定为7字节
            val profile = 2 // AAC LC
            val freqIdx = 4 // 44100 根据不同的采样率修改这个值
            val chanCfg = 2 // CPE
            adts[0] = 0xFF.toByte()
            adts[1] = 0xF9.toByte()
            adts[2] = ((profile - 1).shl(6) + freqIdx.shl(2) + chanCfg.shr(2)).toByte()
            adts[3] = (chanCfg.and(3).shl(6) + packetLen.shr(11)).toByte()
            adts[4] = (packetLen.and(0x7FF).shr(3)).toByte()
            adts[5] = ((packetLen.and(7).shl(5) + 0x1F)).toByte()
            adts[6] = 0xFC.toByte()
            return adts
        }

    }


}
