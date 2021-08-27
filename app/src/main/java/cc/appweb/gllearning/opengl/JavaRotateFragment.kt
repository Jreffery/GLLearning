package cc.appweb.gllearning.opengl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import cc.appweb.gllearning.R
import cc.appweb.gllearning.componet.CommonGLRender
import cc.appweb.gllearning.componet.RotateRender
import cc.appweb.gllearning.databinding.JavaRotateFragmentBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 通过Java层 OpenGLES实现图像旋转，使用RotateRender.java
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class JavaRotateFragment : Fragment(), View.OnClickListener {

    private lateinit var mFragmentBinding: JavaRotateFragmentBinding
    private lateinit var mRotateRender: RotateRender

    private var mByteCount = 0
    private var mWidth = 0
    private var mHeight = 0
    private var mBitmapBuffer: ByteBuffer? = null
    private var mRotateType = CommonGLRender.ROTATE_0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mFragmentBinding = JavaRotateFragmentBinding.inflate(layoutInflater)
        return mFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mFragmentBinding.testBtn.setOnClickListener(this)
        mFragmentBinding.rotate0.setOnClickListener(this)
        mFragmentBinding.rotate90.setOnClickListener(this)
        mFragmentBinding.rotate180.setOnClickListener(this)
        mFragmentBinding.rotate270.setOnClickListener(this)
        mRotateRender = RotateRender()
        mRotateRender.initRender()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bitmap = BitmapFactory.decodeStream(resources.openRawResource(R.raw.c_luo))
        // 解码图片
        mByteCount = bitmap.byteCount
        mWidth = bitmap.width
        mHeight = bitmap.height
        mBitmapBuffer = ByteBuffer.allocateDirect(mByteCount)
        bitmap.copyPixelsToBuffer(mBitmapBuffer)
        mBitmapBuffer!!.flip()
    }

    override fun onClick(v: View?) {
        when (v) {
            mFragmentBinding.testBtn -> {
                mRotateRender.setRotate(mRotateType)
                val bitmapBuffer = mRotateRender.getImage(mBitmapBuffer!!, mWidth, mHeight)
                val intBuffer = IntArray(mWidth * mHeight)
                // 小端
                bitmapBuffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(intBuffer)
                // ARGB_8888 int color = (A & 0xff) << 24 | (B & 0xff) << 16 | (G & 0xff) << 8 | (R & 0xff);
                val bitmapW: Int
                val bitmapH: Int
                if (mRotateType == CommonGLRender.ROTATE_90 || mRotateType == CommonGLRender.ROTATE_270) {
                    bitmapW = mHeight
                    bitmapH = mWidth
                } else {
                    bitmapW = mWidth
                    bitmapH = mHeight
                }

                val grayBitmap = Bitmap.createBitmap(intBuffer, bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
                mFragmentBinding.picIv.setImageBitmap(grayBitmap)
//                StorageUtil.writeBufferIntoFile(StorageUtil.getFile(StorageUtil.PATH_LEARNING_RAW + File.separator + System.currentTimeMillis() + "b.raw").absolutePath, bitmapBuffer)
            }
            mFragmentBinding.rotate0 -> {
                mRotateType = CommonGLRender.ROTATE_0
            }
            mFragmentBinding.rotate90 -> {
                mRotateType = CommonGLRender.ROTATE_90
            }
            mFragmentBinding.rotate180 -> {
                mRotateType = CommonGLRender.ROTATE_180
            }
            mFragmentBinding.rotate270 -> {
                mRotateType = CommonGLRender.ROTATE_270
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mRotateRender.destroy()
    }

}