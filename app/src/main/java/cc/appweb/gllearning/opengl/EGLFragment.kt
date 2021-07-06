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
import cc.appweb.gllearning.databinding.EglFragmentBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * EGL学习示例
 * EGL是OpenGL ES和本地窗口系统之间的通信接口，它的主要作用是：
 * 1. 与设备的原生窗口系统通信
 * 2. 查询绘图表面的可用类型和配置
 * 3. 创建绘图表面
 * 4. 在OpenGL ES和其他图形渲染API之间同步渲染
 * 5. 管理纹理贴图等渲染资源
 *
 * */
class EGLFragment : Fragment(), View.OnClickListener {

    private lateinit var mFragmentBinding: EglFragmentBinding
    private lateinit var mBgRender: BgRender
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var mByteCount: Int = 0
    private var mOriginalBitmap: Bitmap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mFragmentBinding = EglFragmentBinding.inflate(inflater)
        return mFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mFragmentBinding.eglDraw.setOnClickListener(this)
        mFragmentBinding.eglGet.setOnClickListener(this)
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

    override fun onDestroy() {
        super.onDestroy()
        mBgRender.destroy(mBgRender.getNativePtr())
    }

    override fun onClick(v: View?) {
        when (v) {
            mFragmentBinding.eglDraw -> {
                // 调用draw将原图按opengl渲染
                val now = System.nanoTime()
                mBgRender.draw(mBgRender.getNativePtr())
                mFragmentBinding.drawTimeTv.text = "耗时${(System.nanoTime() - now) / 1000}微秒"
            }
            mFragmentBinding.eglGet -> {
                val now = System.nanoTime()
                val buffer = ByteBuffer.allocateDirect(mByteCount)
                // 读取渲染后的图像数据
                mBgRender.getDrawRawData(mBgRender.getNativePtr(), buffer)
                mFragmentBinding.getTimeTv.text = "耗时${(System.nanoTime() - now) / 1000}微秒"
                val bitmapBuffer = IntArray(mWidth * mHeight)
                // 小端
                buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(bitmapBuffer)
                // ARGB_8888 int color = (A & 0xff) << 24 | (B & 0xff) << 16 | (G & 0xff) << 8 | (R & 0xff);
                val grayBitmap = Bitmap.createBitmap(bitmapBuffer, mWidth, mHeight, Bitmap.Config.ARGB_8888)
                mFragmentBinding.grayIv.setImageBitmap(grayBitmap)

//                val output = FileOutputStream(StorageUtil.getFile(StorageUtil.PATH_LEARNING_RAW + File.separator + System.currentTimeMillis() + "b.raw"))
//                output.write(buffer.array())
//                output.flush()
//                output.close()
            }
        }
    }
}