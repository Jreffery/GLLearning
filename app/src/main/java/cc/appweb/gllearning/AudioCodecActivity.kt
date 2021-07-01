package cc.appweb.gllearning

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.appweb.gllearning.databinding.ActivityAudioCodecBinding
import cc.appweb.gllearning.util.DensityUtil
import cc.appweb.gllearning.util.StorageUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 使用MediaCodec编码pcm文件至aac文件，再封装成m4a文件
 *
 * */
class AudioCodecActivity : AppCompatActivity() {

    private lateinit var mActivityBinding: ActivityAudioCodecBinding

    // 是否正在loading
    private var mLoading = AtomicBoolean(false)

    // pcm文件列表
    private val mPcmList = mutableListOf<ListDataItem>()

    // aac文件列表
    private val mAacList = mutableListOf<ListDataItem>()

    // m4a文件列表
    private val mM4aList = mutableListOf<ListDataItem>()

    // 三种文件的标题
    private val mTitleList = listOf(
            ListDataItem(0, VIEW_TYPE_TITLE, "PCM文件", ""),
            ListDataItem(0, VIEW_TYPE_TITLE, "AAC文件", ""),
            ListDataItem(0, VIEW_TYPE_TITLE, "M4A文件", ""))

    private var mLoadingDialog: AlertDialog? = null

    companion object {
        const val TAG = "TAG_AudioCodec"

        const val VIEW_TYPE_TITLE = 1
        const val VIEW_TYPE_FILE = 2

        const val FILE_TYPE_PCM = 1
        const val FILE_TYPE_AAC = 2
        const val FILE_TYPE_M4A = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_codec)

        mActivityBinding = ActivityAudioCodecBinding.bind(findViewById(R.id.audio_codec_container))

        // 初始化recycle view
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        mActivityBinding.recycleView.layoutManager = layoutManager
        mActivityBinding.recycleView.addItemDecoration(DividerItemDecoration(this, RecyclerView.VERTICAL).apply {
            setDrawable(ResourcesCompat.getDrawable(resources, R.drawable.divider_drawable, null)!!)
        })
        mActivityBinding.recycleView.adapter = SectionAdapter()

        loadFilesData()
    }

    private fun loadFilesData() {
        val loading = mLoading.get()
        if (!loading) {
            Thread {
                mLoading.set(true)
                // load .pcm 文件
                val pcm = mutableListOf<ListDataItem>()
                innerLoad(0, StorageUtil.getFile(StorageUtil.PATH_LEARNING_VOICE), FILE_TYPE_PCM, ".pcm", pcm)
                // load .aac
                val aac = mutableListOf<ListDataItem>()
                innerLoad(1, StorageUtil.getFile(StorageUtil.PATH_LEARNING_AAC), FILE_TYPE_AAC, ".aac", aac)
                // load .m4a
                val m4a = mutableListOf<ListDataItem>()
                innerLoad(2, StorageUtil.getFile(StorageUtil.PATH_LEARNING_M4A), FILE_TYPE_M4A, ".m4a", m4a)
                // 更新操作
                mActivityBinding.root.post {
                    mPcmList.clear()
                    mPcmList.addAll(pcm)
                    mAacList.clear()
                    mAacList.addAll(aac)
                    mM4aList.clear()
                    mM4aList.addAll(m4a)
                    mActivityBinding.recycleView.adapter!!.notifyDataSetChanged()
                }

                mLoading.set(false)
            }.start()
        }
    }

    private fun innerLoad(titleIndex: Int, dir: File, fileTypeInt: Int, fileTypeStr: String, list: MutableList<ListDataItem>) {
        val files = dir.listFiles()
        list.clear()
        list.add(mTitleList[titleIndex])
        files?.let { allFiles ->
            for (file in allFiles) {
                if (file.isFile && file.name.endsWith(fileTypeStr)) {
                    list.add(ListDataItem(fileTypeInt, VIEW_TYPE_FILE, file.name, file.absolutePath))
                }
            }
        }
    }

    private inner class SectionAdapter : RecyclerView.Adapter<TextViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return when (position) {
                0 -> {
                    VIEW_TYPE_TITLE
                }
                mPcmList.size -> {
                    VIEW_TYPE_TITLE
                }
                mPcmList.size + mAacList.size -> {
                    VIEW_TYPE_TITLE
                }
                else -> {
                    VIEW_TYPE_FILE
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
            val bigTextView = TextView(this@AudioCodecActivity)
            val holder = TextViewHolder(bigTextView)
            bigTextView.ellipsize = TextUtils.TruncateAt.END
            if (viewType == VIEW_TYPE_TITLE) {
                bigTextView.setTextColor(Color.parseColor("#ee000000"))
                bigTextView.textSize = 18f
                bigTextView.setPadding(DensityUtil.dp2px(5f), DensityUtil.dp2px(5f), 0, DensityUtil.dp2px(5f))
            } else {
                bigTextView.setTextColor(Color.parseColor("#88000000"))
                bigTextView.textSize = 15f
                bigTextView.setPadding(DensityUtil.dp2px(10f), DensityUtil.dp2px(5f), 0, DensityUtil.dp2px(5f))
                bigTextView.setOnClickListener {
                    val position = holder.adapterPosition
                    val listData: ListDataItem
                    val type: Int
                    if (position < mPcmList.size) {
                        listData = mPcmList[position]
                        type = FILE_TYPE_PCM
                    } else if (position < mPcmList.size + mAacList.size) {
                        listData = mAacList[position - mPcmList.size]
                        type = FILE_TYPE_AAC
                    } else {
                        listData = mM4aList[position - mPcmList.size - mAacList.size]
                        type = FILE_TYPE_M4A
                    }
                    val name = listData.name.split(".")[0]
                    Log.i(TAG, "onclick type=$type name=$name")
                    if (type == FILE_TYPE_PCM) {
                        val aacFile = mAacList.find {
                            return@find it.name.startsWith(name)
                        }
                        aacFile?.let {
                            Toast.makeText(this@AudioCodecActivity, "已经编码", Toast.LENGTH_SHORT).show()
                        } ?: let {
                            MaterialAlertDialogBuilder(this@AudioCodecActivity)
                                    .setTitle("音频编码")
                                    .setMessage("是否将${listData.name}以aac编码")
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("确定") { dialog, which ->
                                        // 展示loading
                                        mLoadingDialog?.dismiss()
                                        mLoadingDialog = MaterialAlertDialogBuilder(this@AudioCodecActivity)
                                                .setView(R.layout.loading_view)
                                                .setCancelable(false)
                                                .show()
                                    }
                                    .setCancelable(false)
                                    .show()
                        }
                    }
                }
            }
            return holder
        }

        override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
            val listData: ListDataItem
            if (position < mPcmList.size) {
                listData = mPcmList[position]
            } else if (position < mPcmList.size + mAacList.size) {
                listData = mAacList[position - mPcmList.size]
            } else {
                listData = mM4aList[position - mPcmList.size - mAacList.size]
            }
            holder.view.text = listData.name
        }

        override fun getItemCount(): Int {
            return mPcmList.size + mAacList.size + mM4aList.size
        }

    }

}

data class ListDataItem(val fileType: Int, val viewType: Int, val name: String, val path: String)

private class TextViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)

