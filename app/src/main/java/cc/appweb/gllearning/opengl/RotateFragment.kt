package cc.appweb.gllearning.opengl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import cc.appweb.gllearning.R
import cc.appweb.gllearning.componet.BgRender
import cc.appweb.gllearning.databinding.GlRotateFragmentBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 通过OpenGL渲染实现旋转、镜像
 *
 * */
class RotateFragment : Fragment(), View.OnClickListener {

    private lateinit var mFragmentBinding: GlRotateFragmentBinding
    private lateinit var mBgRender: BgRender
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var mByteCount: Int = 0
    private var mOriginalBitmap: Bitmap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mFragmentBinding = GlRotateFragmentBinding.inflate(layoutInflater)
        return mFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mFragmentBinding.rotate0.setOnClickListener(this)
        mFragmentBinding.rotate90.setOnClickListener(this)
        mFragmentBinding.rotate180.setOnClickListener(this)
        mFragmentBinding.rotate270.setOnClickListener(this)
        mFragmentBinding.mirrorHorizontal.setOnClickListener(this)
        mFragmentBinding.mirrorVertical.setOnClickListener(this)
        mFragmentBinding.draw.setOnClickListener(this)
        mFragmentBinding.get.setOnClickListener(this)
        mFragmentBinding.originalIv.setImageBitmap(mOriginalBitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bitmap = BitmapFactory.decodeStream(resources.openRawResource(R.raw.gl_clock))
        mBgRender = BgRender()
        // 解码图片
        mByteCount = bitmap.byteCount
        mWidth = bitmap.width
        mHeight = bitmap.height
        val buffer = ByteBuffer.allocateDirect(mByteCount)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.flip()
        // 创建render
        mBgRender.create(mBgRender.getNativePtr(), bitmap.width, bitmap.height, buffer)
        mOriginalBitmap = bitmap
    }

    override fun onClick(v: View?) {
        when (v) {
            mFragmentBinding.draw -> {
                // 调用draw将原图按opengl渲染
                mBgRender.draw(mBgRender.getNativePtr())
            }
            mFragmentBinding.get -> {
                val buffer = ByteBuffer.allocateDirect(mByteCount)
                // 读取渲染后的图像数据
                mBgRender.getDrawRawData(mBgRender.getNativePtr(), buffer)
                val bitmapBuffer = IntArray(mWidth * mHeight)
                // 小端
                buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(bitmapBuffer)
                // ARGB_8888 int color = (A & 0xff) << 24 | (B & 0xff) << 16 | (G & 0xff) << 8 | (R & 0xff);
                val grayBitmap = Bitmap.createBitmap(bitmapBuffer, mWidth, mHeight, Bitmap.Config.ARGB_8888)
                mFragmentBinding.rotateIv.setImageBitmap(grayBitmap)
            }
            mFragmentBinding.rotate0 ->{
                mBgRender.setRotate(mBgRender.getNativePtr(), BgRender.ROTATE_0)
            }
            mFragmentBinding.rotate90 ->{
                mBgRender.setRotate(mBgRender.getNativePtr(), BgRender.ROTATE_90)
            }
            mFragmentBinding.rotate180 ->{
                mBgRender.setRotate(mBgRender.getNativePtr(), BgRender.ROTATE_180)
            }
            mFragmentBinding.rotate270 ->{
                mBgRender.setRotate(mBgRender.getNativePtr(), BgRender.ROTATE_270)
            }
        }
    }

}