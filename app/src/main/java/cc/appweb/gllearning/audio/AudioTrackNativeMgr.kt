package cc.appweb.gllearning.audio

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import cc.appweb.gllearning.util.AppUtil
import java.nio.ByteOrder

/**
 * 封装OpenSL es底层播放的管理器
 * */
object AudioTrackNativeMgr {

    const val TAG = "AudioTrackNativeMgr"

    private var mHandlerThread: HandlerThread? = null
    private var mHandler: Handler? = null
    private var mPlayingItem: PlayItem? = null
    private var mPendingPlayItem: PlayItem? = null

    init {
        VoiceLibLoader.tryLoad()
    }

    /**
     * 播放音频
     *
     * @param pcmFilePath 待播放pcm文件的路径
     * @param sampleRate 采样率 44100
     * @param channelType 声道类型 cc.appweb.gllearning.audio.J2CMappingKt.getCHANNEL_OUT_MONO
     * @param audioFormat 编码精度 cc.appweb.gllearning.audio.J2CMappingKt.getENCODING_16BIT
     * */
    fun playAudio(pcmFilePath: String, channelNum: Int, sampleRate: Int,
                  channelType: Int, audioFormat: Int, listener: OnPlayListener?) {
        mHandlerThread ?: apply {
            mHandlerThread = HandlerThread("voice_player").also {
                it.start()
                mHandler = Handler(it.looper)
            }
        }
        mHandler?.post {
            val playItem = PlayItem(pcmFilePath, channelNum, sampleRate, channelType, audioFormat, listener);
            mPlayingItem?.let {
                mPendingPlayItem = playItem
                stop()
            } ?:let {
                mPlayingItem = playItem
                play(playItem.pcmFilePath, playItem.channelNum, playItem.sampleRate, playItem.channelType, playItem.audioFormat,
                        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) ENDIANNESS_LETTER else ENDIANNESS_BIG)
            }

        }
    }

    fun stopAudio() {
        mHandler?.post {
            mPlayingItem?.let {
                stop()
            }
        }
    }

    fun releasePlayer() {
        mHandler?.post {
            release()
            mHandlerThread!!.quit()
            mHandlerThread = null
            mHandler = null
        }
    }

    private external fun play(pcmFilePath: String, channelNum: Int, sampleRate: Int, channelType: Int, audioFormat: Int, endianness: Int)

    /**
     * 停止播放
     * */
    private external fun stop()

    /**
     * 释放资源
     * */
    private external fun release()

    /**
     * JNI回调方法，开始播放时回调
     * */
    private fun onPlay() {
        Log.i(TAG, "onPlay")
        mHandler?.post {
            mPlayingItem?.listener?.apply {
                AppUtil.runOnUIThread {
                    onStart()
                }
            }
        }
    }

    /**
     * JNI回调方法，停止播放时回调
     * */
    private fun onStop() {
        Log.i(TAG, "onStop")
        mHandler?.post {
            mPlayingItem?.listener?.apply {
                AppUtil.runOnUIThread {
                    onEnd()
                }
            }
            mPlayingItem = null
            mPendingPlayItem?.let {
                mPendingPlayItem = null
                playAudio(it.pcmFilePath, it.channelNum, it.sampleRate, it.channelType, it.audioFormat, it.listener)
            }
        }
    }

    interface OnPlayListener {
        fun onStart() {}
        fun onEnd() {}
    }

    private data class PlayItem(val pcmFilePath: String, val channelNum: Int, val sampleRate: Int,
                        val channelType: Int, val audioFormat: Int, val listener: OnPlayListener?)
}