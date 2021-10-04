package cc.appweb.gllearning.opengl

import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import cc.appweb.gllearning.componet.WatermarkRender
import cc.appweb.gllearning.databinding.WatermarkFragmentBinding
import java.nio.ByteBuffer

/**
 * 添加水印至YUV图片的TextureView示例
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class WatermarkRenderFragment : Fragment(), View.OnClickListener {

    private lateinit var mFragmentBinding: WatermarkFragmentBinding
    private lateinit var mN21Buffer: ByteBuffer
    private lateinit var mWatermarkBuffer: ByteBuffer

    // YUV图片的宽
    private var mImageWidth = 0

    // YUV图片的高
    private var mImageHeight = 0

    // 水印图片的宽
    private var mWatermarkWidth = 0

    // 水印图片的高
    private var mWatermarkHeight = 0

    private var mRender: WatermarkRender? = null

    companion object {
        private const val TAG = "WatermarkRenderFragment"
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        mFragmentBinding = WatermarkFragmentBinding.inflate(inflater)
        return mFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mFragmentBinding.renderBtn.setOnClickListener(this)
        mFragmentBinding.addWatermark.setOnClickListener(this) // 添加表面纹理监听者
        mFragmentBinding.textureView.surfaceTextureListener = object :
            TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture,
                                                   width: Int,
                                                   height: Int) {
                Log.d(TAG, "onSurfaceTextureAvailable width=$width height=$height")
                ensureCreateRender(surface, width, height)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture,
                                                     width: Int,
                                                     height: Int) {
                Log.d(TAG, "onSurfaceTextureSizeChanged width=$width height=$height")
                ensureCreateRender(surface, width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Log.d(TAG, "onSurfaceTextureDestroyed")
                ensureDestroyRender()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { // Texture变化时回调
                Log.d(TAG, "onSurfaceTextureUpdated")
            }
        }

        mFragmentBinding.watermarkXTv.text = "x坐标（0-${mImageWidth - mWatermarkWidth}）："
        mFragmentBinding.watermarkYTv.text = "y坐标（0-${mImageHeight - mWatermarkHeight}）："
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val inputStream = context!!.resources.assets.open("nv21/1280x720nv21.yuv")
        mImageWidth = 1280
        mImageHeight = 720
        mN21Buffer = ByteBuffer.allocateDirect(mImageWidth * mImageHeight * 3 / 2)
        var size: Int
        val data = ByteArray(1024)
        while (inputStream.read(data).also { size = it } != -1) {
            mN21Buffer.put(data, 0, size)
        }
        mN21Buffer.flip()

        val watermarkBitmap = BitmapFactory.decodeStream(
            context!!.resources.assets.open("png/huawei_watermark.png"))
        mWatermarkWidth = watermarkBitmap.width
        mWatermarkHeight = watermarkBitmap.height
        mWatermarkBuffer = ByteBuffer.allocateDirect(mWatermarkWidth * mWatermarkHeight * 4)
        watermarkBitmap.copyPixelsToBuffer(mWatermarkBuffer)
        mWatermarkBuffer.flip()

        Log.d(TAG,
            "mBitmapBuffer capacity=${mN21Buffer.capacity()} limit=${mN21Buffer.limit()} " + "pos=${mN21Buffer.position()} watermark={${mWatermarkWidth}, ${mWatermarkHeight}}")
    }

    /**
     * 创建Render，如果render已存在则先销毁
     * */
    private fun ensureCreateRender(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        mRender?.let {
            ensureDestroyRender()
        }
        mRender = WatermarkRender(surfaceTexture, width, height).apply {
            initRender()
        }
    }

    /**
     * 销毁Render
     * */
    private fun ensureDestroyRender() {
        mRender?.destroy()
        mRender = null
    }

    override fun onClick(v: View?) {
        when (v) {
            mFragmentBinding.renderBtn -> {
                mRender?.render(mN21Buffer, mImageWidth, mImageHeight)
            }
            mFragmentBinding.addWatermark -> {
                var x = 0
                var y = 0
                try {
                    x = mFragmentBinding.watermarkX.text.toString().toInt()
                    if (x < 0) {
                        x = 0
                    } else if (x > mImageWidth - mWatermarkWidth) {
                        x = mImageWidth - mWatermarkWidth
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }

                try {
                    y = mFragmentBinding.watermarkY.text.toString().toInt()
                    if (y < 0) {
                        y = 0
                    } else if (y > mImageHeight - mWatermarkHeight) {
                        y = mImageHeight - mWatermarkHeight
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
                mRender?.setWatermark(mWatermarkBuffer, mWatermarkWidth, mWatermarkHeight, x, y)
            }
        }
    }
}