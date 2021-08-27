package cc.appweb.gllearning.opengl

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import cc.appweb.gllearning.databinding.TextureViewRenderFragmentBinding
import java.nio.ByteBuffer

/**
 * 渲染YUV图片至TextureView示例
 * */
class TextureRenderFragment : Fragment(), View.OnClickListener {

    private lateinit var mFragmentBinding: TextureViewRenderFragmentBinding
    private lateinit var mN21Buffer: ByteBuffer

    private var mWidth = 0
    private var mHeight = 0

    companion object{
        private const val TAG = "TextureRenderFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mFragmentBinding = TextureViewRenderFragmentBinding.inflate(inflater)
        return mFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mFragmentBinding.renderBtn.setOnClickListener(this)
        // 添加表面纹理监听者
        mFragmentBinding.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                Log.d(TAG, "onSurfaceTextureAvailable width=$width height=$height")
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                Log.d(TAG, "onSurfaceTextureSizeChanged width=$width height=$height")
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Log.d(TAG, "onSurfaceTextureDestroyed")
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                Log.d(TAG, "onSurfaceTextureUpdated")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val inputStream = context!!.resources.assets.open("nv21/1280x720nv21.yuv")
        mWidth = 1280
        mHeight = 720
        mN21Buffer = ByteBuffer.allocateDirect(mWidth * mHeight * 3 / 2)
        var size: Int
        val data = ByteArray(1024)
        while (inputStream.read(data).also { size = it } != -1) {
            mN21Buffer.put(data, 0, size)
        }
        mN21Buffer.flip()
        Log.d(TAG, "mBitmapBuffer capacity=${mN21Buffer.capacity()} limit=${mN21Buffer.limit()} pos=${mN21Buffer.position()}")
    }

    override fun onClick(v: View?) {
        when(v) {
            mFragmentBinding.renderBtn -> {
                return
            }
        }
    }
}