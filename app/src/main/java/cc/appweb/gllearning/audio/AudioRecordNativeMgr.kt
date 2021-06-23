package cc.appweb.gllearning.audio

import cc.appweb.gllearning.util.AppUtil
import java.nio.ByteOrder

/**
 * 封装OpenSL es进行录音的管理器
 * */
object AudioRecordNativeMgr {

    init {
        VoiceLibLoader.tryLoad()
    }

    /**
     * 是否正在进行录音
     * */
    @Volatile
    private var mRecording = false

    private val mRecordListeners = mutableListOf<IRecordListener>()

    /**
     * 开始录音
     * @param filePath 保存录音pcm文件的路径
     * */
    fun startRecord(filePath: String) {
        if (!mRecording) {
            AppUtil.runOnWorkThread {
                native_start(filePath, if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) ENDIANNESS_LETTER else ENDIANNESS_BIG)
            }
        }
    }

    /**
     * 停止录音
     * */
    fun stopRecord() {
        if (mRecording) {
            AppUtil.runOnWorkThread {
                native_stop()
            }
        }
    }

    fun addRecordListener(listener: IRecordListener) {
        mRecordListeners.add(listener)
    }

    fun removeRecordListener(listener: IRecordListener) {
        mRecordListeners.remove(listener)
    }

    /**
     * JNI开始录音的回调
     * */
    private fun onStart() {
        mRecording = true
        mRecordListeners.forEach {
            it.onStart()
        }
    }

    /**
     * JNI停止录音的回调
     * */
    private fun onStop() {
        mRecording = false
        mRecordListeners.forEach {
            it.onStop()
        }
    }

    /**
     * 录音回调监听者
     * */
    interface IRecordListener {
        fun onStart()
        fun onStop()
    }

    /**
     * native方法，开始录音
     * */
    private external fun native_start(filePath: String, endianness: Int)

    /**
     * native方法，停止录音
     * */
    private external fun native_stop()

}