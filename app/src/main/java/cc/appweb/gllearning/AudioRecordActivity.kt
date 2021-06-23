package cc.appweb.gllearning

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cc.appweb.gllearning.audio.AudioRecordNativeMgr
import cc.appweb.gllearning.databinding.ActivityAudioRecordBinding
import cc.appweb.gllearning.util.AppConfig
import cc.appweb.gllearning.util.StorageUtil
import java.io.File
import java.io.FileOutputStream

/**
 * 录音Activity
 *
 * */
class AudioRecordActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var mActivityBinding: ActivityAudioRecordBinding

    /**
     * 录音对象实例
     * */
    @Volatile
    private var mAudioRecord: AudioRecord? = null

    /**
     * 获取录音数据线程
     * */
    private var mRecordThread: AudioRecordThread? = null

    /**
     * 当前录音状态
     * */
    private var mRecordStatus = RECORD_STATUS_STOPPED

    /**
     * 录音读取数据的大小
     * */
    private var mReadBufferSize = 1024

    /**
     * 是否使用native录音
     * */
    private var mUsingNativeRecorder = false

    private var mNativeRecorderListener = object: AudioRecordNativeMgr.IRecordListener {
        override fun onStart() {
            mActivityBinding.audioRecordContainer.post {
                mRecordStatus = RECORD_STATUS_OPENED
                mActivityBinding.recordView.isEnabled = true
                mActivityBinding.recordView.text = "停止录音"
                Toast.makeText(this@AudioRecordActivity, "录音开始", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onStop() {
            mActivityBinding.audioRecordContainer.post {
                mRecordStatus = RECORD_STATUS_STOPPED
                mActivityBinding.recordView.isEnabled = true
                mActivityBinding.recordView.text = "开始录音"
                Toast.makeText(this@AudioRecordActivity, "录音结束", Toast.LENGTH_SHORT).show()
            }
        }

    }

    companion object {
        const val TAG = "TAG_AudioRecord"
        const val AUDIO_RECORD_PERMISSION_RESULT = 1

        // 检查中状态，不可开始/停止
        const val RECORD_STATUS_CHECKING = 1

        // 停止状态
        const val RECORD_STATUS_STOPPED = 2

        // 开始状态
        const val RECORD_STATUS_OPENED = 3

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_record)

        mActivityBinding = ActivityAudioRecordBinding.bind(findViewById(R.id.audio_record_container))
        mActivityBinding.recordView.setOnClickListener(this)
        mActivityBinding.startPlayAudio.setOnClickListener(this)

        mUsingNativeRecorder = AppConfig.getBoolean(AppConfig.KEY_NATIVE_AUDIO_RECORDER_SWITCH, false)
        mActivityBinding.recorderSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.putBoolean(AppConfig.KEY_NATIVE_AUDIO_RECORDER_SWITCH, isChecked)
        }
        mActivityBinding.recorderSwitch.isChecked = mUsingNativeRecorder
        if (mUsingNativeRecorder) {
            AudioRecordNativeMgr.addRecordListener(mNativeRecorderListener)
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            mActivityBinding.recordView -> {
                if (mRecordStatus == RECORD_STATUS_CHECKING) {
                    return
                }
                if (mRecordStatus == RECORD_STATUS_STOPPED) {
                    // 开始录音
                    startRecord()
                } else if (mRecordStatus == RECORD_STATUS_OPENED) {
                    // 停止录音
                    closeRecord()
                }
            }
            mActivityBinding.startPlayAudio -> {
                // 跳转到播放音频
                startActivity(Intent(this, PlayAudioActivity::class.java))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            AUDIO_RECORD_PERMISSION_RESULT -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "onActivityResult permission granted")
                    // 继续开始录音
                    startRecord()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeRecord()
        if (mUsingNativeRecorder) {
            AudioRecordNativeMgr.removeRecordListener(mNativeRecorderListener)
        }
    }

    private fun startRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "permission granted")
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_RECORD_PERMISSION_RESULT)
            return
        }

        if (mUsingNativeRecorder) {
            if (mRecordStatus == RECORD_STATUS_STOPPED) {
                mRecordStatus = RECORD_STATUS_CHECKING
                mActivityBinding.recordView.isEnabled = false
                AudioRecordNativeMgr.startRecord(StorageUtil.getFile("${StorageUtil.PATH_LEARNING_VOICE + File.separator}voice_${System.currentTimeMillis()}n.pcm").absolutePath)
            }
        } else {
            // 开始前检查录音实例
            mAudioRecord?.let {
                it.stop()
                mAudioRecord = null
            }
            val minBufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            // 创建录音AudioRecord实例
            // @param audioSource 录音来源，MIC 麦克风
            // @param sampleRateInHz 采样率 通用 44100
            // @param channelConfig 声道个数  CHANNEL_IN_MONO 单声道，CHANNEL_IN_STEREO 双声道立体声
            // @param audioFormat 采样位数
            // @param bufferSizeInBytes 音频录制的缓冲区大小
            mAudioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBufferSize * 5)
            Log.i(TAG, "minBufferSize=${minBufferSize}")

            // 读取数据的buffer，不要太大太小
            mReadBufferSize = minBufferSize / 3
            if (mAudioRecord!!.state == AudioRecord.STATE_INITIALIZED) {
                mRecordThread = AudioRecordThread().apply {
                    mRecordStatus = RECORD_STATUS_CHECKING
                    mActivityBinding.recordView.isEnabled = false
                    start()
                    Toast.makeText(this@AudioRecordActivity, "录音开始", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "录音初始化失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun closeRecord() {
        if (mUsingNativeRecorder) {
            if (mRecordStatus == RECORD_STATUS_OPENED) {
                mRecordStatus = RECORD_STATUS_CHECKING
                mActivityBinding.recordView.isEnabled = false
                AudioRecordNativeMgr.stopRecord()
            }
        } else {
            mAudioRecord?.let {
                mRecordStatus = RECORD_STATUS_CHECKING
                mActivityBinding.recordView.isEnabled = false
                mAudioRecord = null

                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    // 停止采集
                    it.stop()
                }

                mRecordThread?.let { itt ->
                    // 停止采集线程
                    itt.mCanRunning = false
                    mRecordThread = null
                }
                Toast.makeText(this, "录音结束", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class AudioRecordThread : Thread() {

        /**
         * 继续读取标志位
         * */
        @Volatile
        var mCanRunning = false

        override fun run() {
            mAudioRecord?.let {
                // 生成保存用的文件
                val audioRecordFile = StorageUtil.getFile("${StorageUtil.PATH_LEARNING_VOICE + File.separator}voice_${System.currentTimeMillis()}.pcm")
                val fileOutputStream: FileOutputStream
                try {
                    fileOutputStream = FileOutputStream(audioRecordFile)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    return
                }
                // 启动录音
                it.startRecording()
                mActivityBinding.audioRecordContainer.post {
                    mRecordStatus = RECORD_STATUS_OPENED
                    mActivityBinding.recordView.isEnabled = true
                    mActivityBinding.recordView.text = "停止录音"
                }
                // 存放数据使用的buffer
                val dataBuffer = ByteArray(mReadBufferSize)
                var readRet = 0
                mCanRunning = true
                while (readRet > 0 || mCanRunning) {
                    try {
                        readRet = it.read(dataBuffer, 0, mReadBufferSize)
                        Log.i(TAG, "readRet=$readRet，mCanRunning=$mCanRunning")
                        if (readRet > 0) {
                            fileOutputStream.write(dataBuffer, 0, readRet)
                            fileOutputStream.flush()
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
                mActivityBinding.audioRecordContainer.post {
                    mRecordStatus = RECORD_STATUS_STOPPED
                    mActivityBinding.recordView.isEnabled = true
                    mActivityBinding.recordView.text = "开始录音"
                }
            } ?: apply {
                mActivityBinding.audioRecordContainer.post {
                    mRecordStatus = RECORD_STATUS_STOPPED
                    mActivityBinding.recordView.isEnabled = true
                    mActivityBinding.recordView.text = "开始录音"
                }
            }
        }
    }

}