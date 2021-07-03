package cc.appweb.gllearning.audio

import android.media.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * aac音频文件封装成m4a格式
 * @param aacFilePath aac文件的路径
 * @param m4aFilePath m4a保存文件的路径
 * @param sampleRate 采样率
 * @param channelNum 声道个数
 * @param audioFormat 编码位数
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class AACWrapM4A(val aacFilePath: String, val m4aFilePath: String,
                 val sampleRate: Int, val channelNum: Int, val audioFormat: Int) {

    // 封装器对象是否已使用，一个封装器对象只能使用一次
    private var mIsUsed = false

    private var mCodecSpecialData = ByteBuffer.allocate(2)

    init {
        // 配置csd数据 https://blog.csdn.net/chailongger/article/details/84378721
        // always aac lc
        val aacLc: Byte = 0x02
        val sampleBit: Byte
        when (sampleRate) {
            96000 -> {
                sampleBit = 0x00
            }
            88200 -> {
                sampleBit = 0x01
            }
            64000 -> {
                sampleBit = 0x02
            }
            48000 -> {
                sampleBit = 0x03
            }
            44100 -> {
                sampleBit = 0x04
            }
            32000 -> {
                sampleBit = 0x05
            }
            24000 -> {
                sampleBit = 0x06
            }
            22050 -> {
                sampleBit = 0x07
            }
            16000 -> {
                sampleBit = 0x08
            }
            12000 -> {
                sampleBit = 0x09
            }
            11025 -> {
                sampleBit = 0x0A
            }
            8000 -> {
                sampleBit = 0x0B
            }
            else -> {
                throw RuntimeException("unSupported Sample Rate")
            }
        }

        val channelNumBit: Byte
        when (channelNum) {
            1 -> {
                channelNumBit = 0x01
            }
            2 -> {
                channelNumBit = 0x02
            }
            3 -> {
                channelNumBit = 0x03
            }
            4 -> {
                channelNumBit = 0x04
            }
            5 -> {
                channelNumBit = 0x05
            }
            else -> {
                throw RuntimeException("unSupported channel number")
            }
        }
        val csdBytes = ByteArray(2)
        csdBytes[0] = (aacLc.toInt().shl(3) or sampleBit.toInt().shr(1)).toByte()
        csdBytes[1] = (sampleBit.toInt().shl(7) or channelNumBit.toInt().shl(3)).toByte()
        Log.i(TAG, "csd ${csdBytes[0]} ${csdBytes[1]}")
        mCodecSpecialData.put(csdBytes).flip()
    }

    companion object {
        private const val TAG = "AACWrapM4A"
    }

    @Synchronized
    fun start(callback: Boolean.() -> Unit) {
        if (mIsUsed) {
            callback.invoke(false)
        }
        mIsUsed = true
        WrapThread(callback).start()
    }

    /**
     * 封装线程
     * */
    private inner class WrapThread(val callback: Boolean.() -> Unit) : Thread() {
        override fun run() {
            // 读取aac文件
            val aacFile = FileInputStream(aacFilePath)
            // mp4格式
            val muxer = MediaMuxer(m4aFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // 设置音频文件的属性
            val mediaFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelNum)
            // 设置aac编码的profile
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            // 设置编码码率
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
            // 设置csd
            mediaFormat.setByteBuffer("csd-0", mCodecSpecialData)
            // 设置编码位数
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, audioFormat)
            }
            // 创建音频轨道
            val trackId = muxer.addTrack(mediaFormat)
            muxer.start()
            // 采样点个数
            var sampleCnt = 0L
            // ADTS header固定为7字节
            val adtsHeader = ByteArray(7)
            // 一帧的数据，每个aac音频帧是1024个采样，预留足够的空间
            val frameBuffer = ByteBuffer.allocate(2 * 1024 * channelNum * if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) 2 else 1)
            val frameData = ByteArray(frameBuffer.capacity())
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                // 读取ADTS头
                var size = aacFile.read(adtsHeader)
                if (size != 7) {
                    break
                }
                // 清空buffer
                frameBuffer.clear()
                // 获取帧长 https://blog.csdn.net/tantion/article/details/82743942
                var frameLength = (adtsHeader[3].toInt() and 0xff and 0x03).shl(11)
                frameLength = frameLength or adtsHeader[4].toInt().and(0xff).shl(3)
                frameLength = frameLength or adtsHeader[5].toInt().and(0xff).and(0xe0).shr(5)
                if (frameLength <= 7) {
                    continue
                }
                // 需要减去adts头部的7字节
                frameLength -= 7
                Log.i(TAG, "one frameLength=$frameLength")
                size = aacFile.read(frameData, 0, frameLength)
                if (size != frameLength) {
                    break
                }
                // 存入数据，需把adts头部信息去掉，muxer不需要adts
                frameBuffer.put(frameData, 0, frameLength)
                frameBuffer.flip()

                // 设置需要写入数据的信息
                bufferInfo.flags = 0
                bufferInfo.offset = 0
                bufferInfo.size = frameBuffer.limit()
                bufferInfo.presentationTimeUs = (1000 * 1000 * sampleCnt) / sampleRate
                muxer.writeSampleData(trackId, frameBuffer, bufferInfo)

                // 累加采样点，计算时间戳
                sampleCnt += 1024
            }
            // 插入结束标志
            bufferInfo.size = 0
            bufferInfo.offset = 0
            bufferInfo.presentationTimeUs = (1000 * 1000 * sampleCnt) / sampleRate
            bufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
            frameBuffer.clear()
            frameBuffer.flip()
            muxer.writeSampleData(trackId, frameBuffer, bufferInfo)

            // 释放资源
            muxer.stop()
            muxer.release()
            callback.invoke(true)
            Log.i(TAG, "muxer m4a finish!")
        }
    }
}