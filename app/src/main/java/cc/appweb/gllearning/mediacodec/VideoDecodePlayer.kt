package cc.appweb.gllearning.mediacodec

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * MediaCodec硬解、播放器
 *
 * @param filePath mp4本地文件路径
 * @param surface 承载播放的surface
 * */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class VideoDecodePlayer(val filePath: String, val surface: Surface, val playListener: IPlayListener?) {

    private var mThread: ExtractorThread? = null

    // 最多缓存30帧
    private val mPlayFrameList = LinkedBlockingDeque<PlayFrame>(30)

    // 可用缓存区
    private val mMediaCodecInputBufferIndex = LinkedBlockingDeque<Int>(100)

    // 播放的时长
    @Volatile
    private var mPlayPresentationTime: Long = 0

    // 最后解码的时间
    private var mLastDecodePresentationTime: Long = 0

    // 解码是否继续
    @Volatile
    private var mDecodeRun = true

    // 播放是否继续
    @Volatile
    private var mPlayRun = true

    companion object {
        private const val TAG = "VideoDecodePlayer"
    }

    /**
     * 开始解码并播放
     * */
    fun start() {
        mThread ?: let {
            mThread = ExtractorThread().apply {
                start()
            }
        }
    }

    /**
     * 提取线程
     * */
    private inner class ExtractorThread : Thread() {

        override fun run() {
            // 媒体提取器，提取容器里面的媒体轨道数据
            val mediaExtractor = MediaExtractor()
            // 设置媒体文件的路径
            mediaExtractor.setDataSource(filePath)
            // 获取容器文件中所有轨道数
            val trackCnt = mediaExtractor.trackCount
            // 遍历所有轨道，找到所需轨道的索引
            var selectedTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until trackCnt) {
                val mediaFormat = mediaExtractor.getTrackFormat(i)
                // H.264视频轨道
                if (mediaFormat.getString(MediaFormat.KEY_MIME).equals("video/avc")) {
                    Log.i(TAG, "mediaFormat width=${mediaFormat.getInteger(MediaFormat.KEY_WIDTH)}, height=${mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)}")
                    selectedTrackIndex = i
                    videoFormat = mediaFormat
                    // 选择该轨道
                    mediaExtractor.selectTrack(selectedTrackIndex)
                    break
                }
            }
            if (selectedTrackIndex == -1) {
                // 没有找到视频轨道
                Log.i(TAG, "no such track!")
                return
            }

            // 开启解码线程
            DecodeThread(videoFormat!!).start()

            var lastPresentationTime: Long
            // 从容器中读取每帧数据
            var byteBuffer = ByteBuffer.allocate(100000)
            var size: Int
            while ((mediaExtractor.readSampleData(byteBuffer, 0).also { size = it }) != -1) {
                // 播放时间戳
                lastPresentationTime = mediaExtractor.sampleTime
                mPlayFrameList.offer(PlayFrame(size, lastPresentationTime, mediaExtractor.sampleFlags, byteBuffer))
                byteBuffer = ByteBuffer.allocate(100000)
                if (mPlayFrameList.remainingCapacity() < 3) {
                    // 容量少于3帧，减缓提取帧数据的速度
                    (lastPresentationTime - mLastDecodePresentationTime).also {
                        if (it > 0) {
                            // 等播放完剩余的一半后再开始提取
                            Log.i(TAG, "extractor sleep ${it / 2} microseconds")
                            sleep((it / 2) / 1000)
                        }
                    }
                }
                // 转到下一帧
                mediaExtractor.advance()
            }

            // 释放提取器资源
            mediaExtractor.release()
            mDecodeRun = false
        }
    }

    /**
     * 解码线程
     * */
    private inner class DecodeThread(val videoFormat: MediaFormat) : Thread() {

        override fun run() {
            // 创建解码器
            val mediaCodec = MediaCodec.createDecoderByType("video/avc")
            // 设置缓冲区回调
            mediaCodec.setCallback(DecodeCallback())
            // 配置解码器，配置了surface会导致获取不到数据
            mediaCodec.configure(videoFormat, surface, null, 0)
            // 开启解码器
            mediaCodec.start()

            var lastQueueTime = 0L
            while (mDecodeRun || !mPlayFrameList.isEmpty()) {
                // 至多等待500毫秒
                val inputBufferIndex = mMediaCodecInputBufferIndex.poll(500, TimeUnit.MILLISECONDS)
                inputBufferIndex?.let { inputIndex ->
                    // 同样至多等待500毫秒
                    val playFrame = mPlayFrameList.poll(500, TimeUnit.MILLISECONDS)
                    playFrame?.let { frame ->
                        if (lastQueueTime != 0L) {
                            // 与上次送解码的时间差
                            val diff1 = (System.nanoTime() - lastQueueTime) / 1000
                            // 当前帧与上一帧的时间差
                            val diff2 = frame.presentationTimeUs - mLastDecodePresentationTime
                            if (diff2 > diff1 && diff2 - diff1 > 5000) {
                                // 时间差大于5毫秒，则稍做停顿送解码
                                Log.d(TAG, "decode wait ${diff2 - diff1} microseconds")
                                sleep((diff2 - diff1) / 1000)
                            }
                        }

                        // 提交给Codec解码
                        mediaCodec.getInputBuffer(inputIndex)?.let { buffer ->
                            Log.i(TAG, "queueInputBuffer index=${inputIndex} size=${frame.size} " +
                                    "presentation=${frame.presentationTimeUs} flags=${frame.flags} frame.byBuffer=${frame.byteBuffer.remaining()}")
                            buffer.clear()
                            buffer.put(frame.byteBuffer)
                            mediaCodec.queueInputBuffer(inputIndex, 0, frame.size, frame.presentationTimeUs, frame.flags)
                            mLastDecodePresentationTime = frame.presentationTimeUs
                            lastQueueTime = System.nanoTime()
                        }

                    } ?: let {
                        // 重新入队
                        mMediaCodecInputBufferIndex.add(inputIndex)
                    }
                }
            }
            // 插入结束位
            var endIndex: Int = -1
            while (endIndex < 0) {
                endIndex = mMediaCodecInputBufferIndex.poll(500, TimeUnit.MILLISECONDS)
            }
            mediaCodec.getInputBuffer(endIndex)?.let { buffer ->
                buffer.clear()
                mediaCodec.queueInputBuffer(endIndex, 0, 0, mLastDecodePresentationTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            mPlayRun = false
            Log.i(TAG, "decode finish!")
        }
    }


    // 回调在主线程
    private inner class DecodeCallback : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            // 输入缓冲区可用回调
            Log.i(TAG, "onInputBufferAvailable index=$index")
            mMediaCodecInputBufferIndex.add(index)
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            // 输出缓冲区可用回调
            Log.i(TAG, "onOutputBufferAvailable to render index=$index presentation=${info.presentationTimeUs}")
            // render=true，送渲染
            mPlayPresentationTime = info.presentationTimeUs
            codec.releaseOutputBuffer(index, true)
            playListener?.onPlayDuration(mPlayPresentationTime)
            if (!mPlayRun && mLastDecodePresentationTime == info.presentationTimeUs) {
                Log.i(TAG, "stop and release codec")
                codec.stop()
                codec.release()
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            // 内部失败回调
            Log.e(TAG, "onError e=${e.message}")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            // 输出格式改变回调
            Log.i(TAG, "onOutputFormatChanged")
        }

    }
}

private data class PlayFrame(val size: Int, val presentationTimeUs: Long, val flags: Int, val byteBuffer: ByteBuffer)

interface IPlayListener {
    fun onPlayDuration(durationUs: Long)
}