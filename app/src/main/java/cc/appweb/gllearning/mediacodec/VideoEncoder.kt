package cc.appweb.gllearning.mediacodec

import android.media.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import cc.appweb.gllearning.util.StorageUtil
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * MediaCodec视频硬编器，video/avc
 *
 * @param width 视频宽度
 * @param height 视频高度
 * @param frameRate 帧率
 * @param bitRate 码率
 * @param iFrameInterval I帧时间间隔
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class VideoEncoder(width: Int, height: Int, frameRate: Int, bitRate: Int, iFrameInterval: Int) {

    private var mMediaCodec: MediaCodec

    private var mStartTime: Long = -1

    private var mOutputThread: OutputThread? = null

    companion object {
        private const val TAG = "VideoCoder"
    }

    init {
        // 输出可使用的编码器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mediaCodecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            try {
                mediaCodecList.codecInfos.forEach {
                    Log.d(TAG, "support codec name=${it.name} canonicalName=${it.canonicalName} " +
                            "isAlias=${it.isAlias} isEncoder=${it.isEncoder} isHardwareAccelerated=${it.isHardwareAccelerated}" +
                            " isSoftwareOnly=${it.isSoftwareOnly} isVendor=${it.isVendor} supportedTypes=${it.supportedTypes}")
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        // 创建codec--H.264/AVC video
        val mediacodec = MediaCodec.createEncoderByType("video/avc")
        // 创建视频格式
        MediaFormat.createVideoFormat("video/avc", width, height).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 设置码率控制模式为 固定码率
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                // 根据手机设置不同的颜色参数
                // 使用COLOR_FormatYUV420Flexible需要使用getInputImage的模式
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            } else {
                // 根据手机设置不同的颜色参数
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            }
            // 设置码率，参考网络传输和文件大小设置
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            // 设置帧率
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            // 设置I帧（关键帧）时间间隔，单位秒
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
            // 配置mediacodec，配置成编码器
            mediacodec.configure(this, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            // 使用Codec进入运行状态
            mediacodec.start()

            // 赋值给变量
            mMediaCodec = mediacodec
        }

    }

    /**
     * 进行帧编码，使用MediaCodec的同步模式
     *
     * @param inputArray 输入数据
     * @param outputArray 输出数据
     * */
    fun offerImage(inputArray: ByteArray) {
        val nowTime = System.currentTimeMillis()
        if (mStartTime < 0) {
            mStartTime = nowTime
            mOutputThread = OutputThread().apply {
                start()
            }
        }
        try {
            // 获取可用的输入缓存区索引
            val inputBufferIndex = mMediaCodec.dequeueInputBuffer(0)
            Log.i(TAG, "dequeueInputBuffer inputBufferIndex=$inputBufferIndex")
            if (inputBufferIndex >= 0) {
                val inputBuffer: ByteBuffer? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mMediaCodec.getInputBuffer(inputBufferIndex)
                } else {
                    mMediaCodec.inputBuffers[inputBufferIndex]
                }
                inputBuffer!!.apply {
                    // 清空bytebuffer数据，重置
                    clear()
                    // 传入待编码数据
                    put(inputArray)
                    // 传入Codec
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, inputArray.size, (nowTime - mStartTime) * 1000, 0)
                }
            } else {
                // 没有可用的输入缓存区，丢弃视频帧
                return
            }

            // 编码帧数据
            var bufferInfo = MediaCodec.BufferInfo()
            // 获取编码后数据
            var outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex >=0) {
                // 取出数据
                if (bufferInfo.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    val outputBuffer: ByteBuffer? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mMediaCodec.getOutputBuffer(outputBufferIndex)
                    } else {
                        mMediaCodec.outputBuffers[outputBufferIndex]
                    }
                    val byteArray = ByteBuffer.allocate(bufferInfo.size)
                    byteArray.put(outputBuffer!!)
                    byteArray.flip()
                    Log.i(TAG, "offer one frame flag=${bufferInfo.flags} size=${bufferInfo.size} offset=${bufferInfo.offset}" +
                            " presentationTime=${bufferInfo.presentationTimeUs}")
                    mOutputThread!!.mDataQueue.offer(FrameData(bufferInfo, byteArray))
                    // 释放输入缓存区给Codec
                    mMediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                    // 生成新的对象
                    bufferInfo = MediaCodec.BufferInfo()
                    outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

        } catch (t: Throwable) {
            t.printStackTrace()
            Log.e(TAG, "error: ${t.message}")
        }
    }

    /**
     * 停止编码，保存成文件
     * */
    fun stop() {
        mOutputThread!!.mRunning = false
        mMediaCodec.stop()
        mMediaCodec.release()
    }

    private data class FrameData(val info: MediaCodec.BufferInfo, val data: ByteBuffer)

    /**
     * 保存编码后文件的线程
     *
     * */
    private inner class OutputThread : Thread() {

        // mp4封装器
        lateinit var mMediaMuxer: MediaMuxer

        // 视频帧数据队列
        val mDataQueue = LinkedBlockingQueue<FrameData>()

        // 退出标志位
        @Volatile
        var mRunning = true

        // 最后视频帧的时间戳
        private var mLastPresentationTimeUs: Long = 0

        private var mContainEndOfStream = false

        override fun run() {
            name = "video_output"
            // 创建MediaMuxer以封装mp4数据
            mMediaMuxer = MediaMuxer(StorageUtil.getFile("${StorageUtil.PATH_LEARNING_MP4+File.separator}video${System.currentTimeMillis()}.mp4").absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // 创建一条轨道
            val trackID = mMediaMuxer.addTrack(mMediaCodec.outputFormat)
            // 使MediaMuxer处于开始状态
            mMediaMuxer.start()
            while (mRunning || mDataQueue.size > 0) {
                val frameData = mDataQueue.poll(500, TimeUnit.MILLISECONDS)
                Log.i(TAG, "poll one frame=${frameData != null}")
                frameData?.let {
                    // 写入数据
                    Log.i(TAG, "write capacity=${it.data.capacity()} size=${it.info.size} offset=${it.info.offset}")
                    mMediaMuxer.writeSampleData(trackID, it.data, it.info)
                    mLastPresentationTimeUs = it.info.presentationTimeUs
                    mContainEndOfStream = mContainEndOfStream || it.info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM
                }
            }
            if (!mContainEndOfStream && mLastPresentationTimeUs != 0L) {
                // 插入结束标志
                val bufferInfo = MediaCodec.BufferInfo()
                bufferInfo.size = 0
                bufferInfo.offset = 0
                bufferInfo.presentationTimeUs = mLastPresentationTimeUs + (200 * 1000)
                bufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                mMediaMuxer.writeSampleData(trackID, ByteBuffer.allocate(0), bufferInfo)
            }
            mMediaMuxer.stop()
            mMediaMuxer.release()
        }
    }

}