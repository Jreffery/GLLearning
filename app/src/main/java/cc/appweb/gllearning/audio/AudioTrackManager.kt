package cc.appweb.gllearning.audio

import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger


/**
 * 封装AudioTrack播放音频的管理器
 * */
object AudioTrackManager {

    private const val TAG = "AudioTrackManager"

    // 播放PCM流的对象
    private var mAudioTrack: AudioTrack? = null

    // 主线程回调
    private val mHandler = Handler(Looper.getMainLooper())

    // 线程数计数
    private val mThreadNum = AtomicInteger(0)

    /**
     * 播放音频
     *
     * @param pcmFilePath 待播放pcm文件的路径
     * @param streamType 文件流类型 android.media.AudioManager#STREAM_MUSIC
     * @param sampleRate 采样率 44100
     * @param channelType 声道类型 android.media.AudioFormat.CHANNEL_OUT_MONO
     * @param audioFormat 编码精度 android.media.AudioFormat.ENCODING_PCM_8BIT
     * */
    fun playAudio(pcmFilePath: String, streamType: Int, sampleRate: Int,
                  channelType: Int, audioFormat: Int, playListener: OnPlayListener?) {
        runOnUIThread {
            // 根据采样率，采样精度，单双声道来得到frame的大小。
            // 用于播放类型为MODE_STREAM时定义buffer大小
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelType, audioFormat)
            // 创建AudioTrack
            // MODE_STREAM以流的方式持续从Java层向native写入数据，MODE_STATIC一次性写入数据
            try {
                mAudioTrack?.stop()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            mAudioTrack = AudioTrack(streamType, sampleRate, channelType, audioFormat, minBufferSize, AudioTrack.MODE_STREAM).apply {
                // 开始播放线程
                PlayThread(pcmFilePath, minBufferSize, this, playListener).start()
            }
        }
    }

    fun stop() {
        try {
            mAudioTrack?.stop()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun runOnUIThread(action: AudioTrackManager.()->Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.invoke(this)
        } else {
            mHandler.post {
                action.invoke(this)
            }
        }
    }

    private class PlayThread(val pcmFilePath: String,val minBufferSize: Int, val audioTrack: AudioTrack, val playListener: OnPlayListener?) : Thread() {
        override fun run() {
            var fileInput: FileInputStream? = null
            try {
                // 设置线程名称
                name = "AudioPlay${mThreadNum.incrementAndGet()}"
                // 设置线程优先级
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                fileInput = FileInputStream(pcmFilePath)
                // min buffer size
                val byteBuffer = ByteArray(minBufferSize)
                // 调用播放
                playListener?.let {
                    runOnUIThread {
                        it.onStart()
                    }
                }
                var readCnt = fileInput.read(byteBuffer, 0, minBufferSize)
                audioTrack.play()
                while (readCnt > 0) {
                    // 写入从文件读出的pcm流，阻塞到播放完数据
                    val transferCnt = audioTrack.write(byteBuffer, 0, readCnt)
                    if (transferCnt != readCnt && audioTrack.playState == AudioTrack.PLAYSTATE_STOPPED) {
                        // 外部调用stop
                        break
                    }
                    audioTrack.play()
                    readCnt = fileInput.read(byteBuffer, 0, minBufferSize)
                    Log.i(TAG, "play path=${pcmFilePath} size=${readCnt}")
                }
                audioTrack.stop()
                audioTrack.release()
                fileInput.close()
            } catch (t : Throwable) {
                t.printStackTrace()
                try {
                    // 关闭播放
                    if (audioTrack.playState != AudioTrack.PLAYSTATE_STOPPED) {
                        audioTrack.stop()
                    }
                    audioTrack.release()
                } catch (tt: Throwable) {
                    tt.printStackTrace()
                }
                try {
                    fileInput?.close()
                } catch (tt : Throwable) {
                    tt.printStackTrace()
                }
            }
            // 回收对象
            runOnUIThread {
                if (audioTrack == mAudioTrack) {
                    mAudioTrack = null
                }
                playListener?.onEnd()
            }
        }
    }


    interface OnPlayListener {
        fun onStart() {}
        fun onEnd() {}
    }
}