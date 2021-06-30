package cc.appweb.gllearning

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.appweb.gllearning.databinding.ActivityMediaCodecBinding
import cc.appweb.gllearning.databinding.PlayVideoItemVewBinding
import cc.appweb.gllearning.mediacodec.IPlayListener
import cc.appweb.gllearning.mediacodec.VideoDecodePlayer
import cc.appweb.gllearning.util.StorageUtil

/**
 * 播放使用录制功能录制的视频
 * */
class MediaCodecActivity : AppCompatActivity() {

    private lateinit var mActivityBinding: ActivityMediaCodecBinding

    private val mDataList = mutableListOf<PlayVideoData>()

    private var mSurface: Surface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_codec)
        mActivityBinding = ActivityMediaCodecBinding.bind(findViewById(R.id.media_codec_container))

        // 初始化recycle view
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        mActivityBinding.playList.layoutManager = layoutManager
        mActivityBinding.playList.addItemDecoration(DividerItemDecoration(this, RecyclerView.VERTICAL).apply {
            setDrawable(ResourcesCompat.getDrawable(resources, R.drawable.divider_drawable, null)!!)
        })
        val adapter = MyAdapter()
        mActivityBinding.playList.adapter = adapter

        Thread {
            // load .mp4 文件
            val directory = StorageUtil.getFile(StorageUtil.PATH_LEARNING_MP4)
            val files = directory.listFiles()
            val dataList = mutableListOf<PlayVideoData>()
            files?.let { allFiles ->
                for (file in allFiles) {
                    if (file.isFile && file.name.endsWith(".mp4")) {
                        dataList.add(PlayVideoData(file.name, file.absolutePath))
                    }
                }
            }
            if (dataList.isNotEmpty()) {
                // 更新操作
                mActivityBinding.root.post {
                    mDataList.clear()
                    mDataList.addAll(dataList)
                    mActivityBinding.playList.adapter!!.notifyDataSetChanged()
                }
            }
        }.start()
    }

    private fun onClick(position: Int) {
        if (mActivityBinding.playerView.isAvailable) {
            mSurface ?: let {
                mSurface = Surface(mActivityBinding.playerView.surfaceTexture)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                VideoDecodePlayer(mDataList[position].path, mSurface!!, object: IPlayListener {
                    override fun onPlayDuration(durationUs: Long) {
                        val minute: String = (durationUs / 1000 / 1000 / 60).let {
                            if (it == 0L) {
                                return@let "00"
                            } else {
                                return@let it.toString()
                            }
                        }
                        val second: String = (durationUs / 1000 / 1000 % 60).let {
                            if (it < 10) {
                                return@let "0${it}"
                            } else {
                                return@let it.toString()
                            }
                        }
                        mActivityBinding.root.post {
                            mActivityBinding.playDuration.text = "${minute}:${second}"
                        }
                    }

                }).start()
            }
        }
    }

    private inner class MyAdapter : RecyclerView.Adapter<PlayItemViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayItemViewHolder {
            return PlayItemViewHolder(PlayVideoItemVewBinding.inflate(getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).apply {
                root.setOnClickListener {
                    onClick((it.tag as PlayItemViewHolder).adapterPosition)
                }
            })
        }

        override fun onBindViewHolder(holder: PlayItemViewHolder, position: Int) {
            mDataList[position].apply {
                holder.binding.root.tag = holder
                holder.binding.videoName.text = name
            }
        }

        override fun getItemCount(): Int {
            return mDataList.size
        }
    }

}

private data class PlayVideoData(val name: String, val path: String)

private class PlayItemViewHolder(val binding: PlayVideoItemVewBinding) : RecyclerView.ViewHolder(binding.root)