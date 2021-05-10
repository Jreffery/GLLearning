package cc.appweb.gllearning.componet

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView

/**
 * 相机预览使用的TextureView
 *
 * */
class CameraTextureView(context: Context, attributesSet: AttributeSet):
        TextureView(context, attributesSet), TextureView.SurfaceTextureListener {

    companion object {
        const val TAG = "CameraTextureView"
    }

    init {
        // 添加表面纹理监听器，运行在主线程
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceTextureAvailable width=$width height=$height")
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceTextureSizeChanged width=$width height=$height")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.i(TAG, "onSurfaceTextureDestroyed")
        // 返回值：返回true，SurfaceTexture内部不会再渲染；返回false，需要后续自行调用SurfaceTexture.release()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // SurfaceTexture#updateTexImage()时回调
        Log.i(TAG, "onSurfaceTextureUpdated")
    }
}