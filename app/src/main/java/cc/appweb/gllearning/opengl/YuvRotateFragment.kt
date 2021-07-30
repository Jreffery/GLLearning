package cc.appweb.gllearning.opengl

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import cc.appweb.gllearning.componet.RotateRender
import cc.appweb.gllearning.componet.YuvRotateRender
import cc.appweb.gllearning.databinding.YuvRotateFragmentBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class YuvRotateFragment : Fragment(), View.OnClickListener {

    private lateinit var mFragmentBinding: YuvRotateFragmentBinding
    private lateinit var mRotateRender: YuvRotateRender
    private lateinit var mBitmapBuffer: ByteBuffer

    private var mWidth = 0
    private var mHeight = 0
    private var mRotateType = YuvRotateRender.ROTATE_0

    companion object {
        private const val TAG = "YuvRotateFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mFragmentBinding = YuvRotateFragmentBinding.inflate(inflater)
        return mFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mFragmentBinding.testBtn.setOnClickListener(this)
        mFragmentBinding.rotate0.setOnClickListener(this)
        mFragmentBinding.rotate90.setOnClickListener(this)
        mFragmentBinding.rotate180.setOnClickListener(this)
        mFragmentBinding.rotate270.setOnClickListener(this)
        mRotateRender = YuvRotateRender()
        mRotateRender.initRender()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val inputStream = context!!.resources.assets.open("nv12/1280x720nv12.yuv")
        mWidth = 1280
        mHeight = 720
        mBitmapBuffer = ByteBuffer.allocateDirect(mWidth * mHeight * 3 / 2)
        var size: Int
        val data = ByteArray(1024)
        while (inputStream.read(data).also { size = it } != -1) {
            mBitmapBuffer.put(data, 0, size)
        }
        mBitmapBuffer.flip()
        Log.d(TAG, "mBitmapBuffer capacity=${mBitmapBuffer.capacity()} limit=${mBitmapBuffer.limit()} pos=${mBitmapBuffer.position()}")
    }

    override fun onClick(v: View?) {
        when (v) {
            mFragmentBinding.testBtn -> {
                mRotateRender.setRotate(mRotateType)
                val bitmapBuffer = mRotateRender.getImage(mBitmapBuffer, mWidth, mHeight)
                mBitmapBuffer.position(0)
                val intBuffer = IntArray(mWidth * mHeight)
                // 小端
                bitmapBuffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(intBuffer)
                // ARGB_8888 int color = (A & 0xff) << 24 | (B & 0xff) << 16 | (G & 0xff) << 8 | (R & 0xff);
                val bitmapW: Int
                val bitmapH: Int
                if (mRotateType == RotateRender.ROTATE_90 || mRotateType == RotateRender.ROTATE_270) {
                    bitmapW = mHeight
                    bitmapH = mWidth
                } else {
                    bitmapW = mWidth
                    bitmapH = mHeight
                }

                val grayBitmap = Bitmap.createBitmap(intBuffer, bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
                mFragmentBinding.picIv.setImageBitmap(grayBitmap)
            }
            mFragmentBinding.rotate0 -> {
                mRotateType = RotateRender.ROTATE_0
            }
            mFragmentBinding.rotate90 -> {
                mRotateType = RotateRender.ROTATE_90
            }
            mFragmentBinding.rotate180 -> {
                mRotateType = RotateRender.ROTATE_180
            }
            mFragmentBinding.rotate270 -> {
                mRotateType = RotateRender.ROTATE_270
            }
        }
    }

}