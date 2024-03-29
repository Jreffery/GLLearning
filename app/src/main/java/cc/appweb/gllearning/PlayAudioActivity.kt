package cc.appweb.gllearning

import android.media.AudioFormat
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.appweb.gllearning.audio.AudioTrackManager
import cc.appweb.gllearning.audio.AudioTrackNativeMgr
import cc.appweb.gllearning.audio.CHANNEL_OUT_MONO
import cc.appweb.gllearning.audio.ENCODING_16BIT
import cc.appweb.gllearning.databinding.ActivityPlayAudioBinding
import cc.appweb.gllearning.databinding.PlayAudioItemViewBinding
import cc.appweb.gllearning.util.AppConfig
import cc.appweb.gllearning.util.StorageUtil

/**
 * 播放音频
 * 1. Java层使用AudioTrack
 * 2. Native使用OpenSL
 * */
class PlayAudioActivity : AppCompatActivity() {

    private lateinit var mActivityBinding: ActivityPlayAudioBinding
    private var mUsingNativePlayer = false

    private lateinit var mHandler: Handler
    private val mAudioDataList = mutableListOf<PlayAudioData>()

    companion object {
        private const val TAG = "TAG_PlayAudio"

        private const val TYPE_STATUS_STOPPED = 0
        private const val TYPE_STATUS_PLAYING = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play_audio)

        mActivityBinding = ActivityPlayAudioBinding.bind(findViewById(R.id.play_audio_container))
        mHandler = Handler(Looper.getMainLooper())

        // 初始化recycle view
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        mActivityBinding.recycleView.layoutManager = layoutManager
        mActivityBinding.recycleView.addItemDecoration(DividerItemDecoration(this, RecyclerView.VERTICAL).apply {
            setDrawable(ResourcesCompat.getDrawable(resources, R.drawable.divider_drawable, null)!!)
        })
        val adapter = MyAdapter()
        mActivityBinding.recycleView.adapter = adapter
        mUsingNativePlayer = AppConfig.getBoolean(AppConfig.KEY_NATIVE_AUDIO_PLAYER_SWITCH, false)
        mActivityBinding.playerSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.putBoolean(AppConfig.KEY_NATIVE_AUDIO_PLAYER_SWITCH, isChecked)
        }
        mActivityBinding.playerSwitch.isChecked = mUsingNativePlayer
    }


    override fun onResume() {
        super.onResume()
        Thread {
            // load .pcm 文件
            val directory = StorageUtil.getFile(StorageUtil.PATH_LEARNING_VOICE)
            val files = directory.listFiles()
            val dataList = mutableListOf<PlayAudioData>()
            files?.let { allFiles ->
                for (file in allFiles) {
                    if (file.isFile && file.name.endsWith(".pcm")) {
                        dataList.add(PlayAudioData(file.name, file.absolutePath, TYPE_STATUS_STOPPED))
                    }
                }
            }
            if (dataList.isNotEmpty()) {
                // 更新操作
                mHandler.post {
                    mAudioDataList.clear()
                    mAudioDataList.addAll(dataList)
                    mActivityBinding.recycleView.adapter!!.notifyDataSetChanged()
                }
            }
        }.start()
    }

    private fun onClick(position: Int) {
        // 点击播放
        mAudioDataList[position].apply {
            if (mUsingNativePlayer) {
                AudioTrackNativeMgr.playAudio(path, 1, 44100, CHANNEL_OUT_MONO, ENCODING_16BIT, object : AudioTrackNativeMgr.OnPlayListener {
                    override fun onStart() {
                        Log.i(TAG, "native play audio onStart path=$path")
                        type = TYPE_STATUS_PLAYING
                        mActivityBinding.recycleView.adapter!!.notifyDataSetChanged()
                        mActivityBinding.audioStatus.text = "播放中"
                    }

                    override fun onEnd() {
                        Log.i(TAG, "native play audio onEnd path=$path")
                        type = TYPE_STATUS_STOPPED
                        mActivityBinding.recycleView.adapter!!.notifyDataSetChanged()
                        mActivityBinding.audioStatus.text = "未播放"
                    }
                })
            } else {
                // 统一使用AudioRecordActivity的录音配置
                // 使用媒体音量
                // 44100的采样率
                // 单声道
                // 16位编码
                AudioTrackManager.playAudio(path, AudioManager.STREAM_MUSIC, 44100,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, object : AudioTrackManager.OnPlayListener {

                    override fun onStart() {
                        Log.i(TAG, "play audio onStart path=$path")
                        type = TYPE_STATUS_PLAYING
                        mActivityBinding.recycleView.adapter!!.notifyDataSetChanged()
                        mActivityBinding.audioStatus.text = "播放中"
                    }

                    override fun onEnd() {
                        Log.i(TAG, "play audio onEnd path=$path")
                        type = TYPE_STATUS_STOPPED
                        mActivityBinding.recycleView.adapter!!.notifyDataSetChanged()
                        mActivityBinding.audioStatus.text = "未播放"
                    }

                })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mUsingNativePlayer) {
            AudioTrackNativeMgr.stopAudio()
        } else {
            AudioTrackManager.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mUsingNativePlayer) {
            AudioTrackNativeMgr.releasePlayer()
        }
    }

    private inner class MyAdapter : RecyclerView.Adapter<MyViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            return MyViewHolder(PlayAudioItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply {
                root.setOnClickListener {
                    onClick((it.tag as MyViewHolder).adapterPosition)
                }
            })
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            mAudioDataList[position].apply {
                holder.itemBinding.audioName.text = name
                holder.itemBinding.root.tag = holder
                if (type == TYPE_STATUS_STOPPED) {
                    holder.itemBinding.audioName.setTextColor(ResourcesCompat.getColor(resources, R.color.black, null))
                } else if (type == TYPE_STATUS_PLAYING) {
                    holder.itemBinding.audioName.setTextColor(ResourcesCompat.getColor(resources, R.color.teal_200, null))
                }
            }
        }

        override fun getItemCount(): Int {
            return mAudioDataList.size
        }

    }
}

private data class PlayAudioData(val name: String, val path: String, var type: Int)

private class MyViewHolder(val itemBinding: PlayAudioItemViewBinding) : RecyclerView.ViewHolder(itemBinding.root)


