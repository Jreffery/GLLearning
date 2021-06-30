package cc.appweb.gllearning.componet

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import androidx.annotation.MainThread
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 相机预览使用的GLSurfaceView
 * GLSurfaceView内部管理了EGLContext
 * */
class CameraGLSurfaceView(context: Context, attributesSet: AttributeSet)
    : GLSurfaceView(context, attributesSet), GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        const val TAG = "CameraGLSurfaceView"
    }

    // 纹理id
    private var mTextureId: Int = 0
    // 表面纹理
    @Volatile private var mSurfaceTexture: SurfaceTexture? = null

    private var mSurfaceTextureCreatedCallback: (SurfaceTexture.() -> Unit)? = null

    // 绘制渲染器
    private var mDrawer: TextureDrawer? = null

    // Preview orientation， 0，90，180，270
    private var mPreviewOrientation: Int

    // Preview use mirror
    private var mPreviewMirror: Boolean

    init {
        // 使用适配OpenGL ES 2.0的EGL版本
        setEGLContextClientVersion(2)
        // 设置渲染器，同时开启内部GLThread线程，回调在GLThread线程
        setRenderer(this)
        // 设置渲染模式，根据纹理层的监听，有数据就绘制
        renderMode = RENDERMODE_WHEN_DIRTY
        // 预览角度，初始化为0
        mPreviewOrientation = 0
        // 预览镜像
        mPreviewMirror = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated")
        // 创建适用需求场景的纹理
        mTextureId = createTextureId()
        // 创建SurfaceTexture用于接收相机预览数据
        mSurfaceTexture = SurfaceTexture(mTextureId).apply {
            // 添加帧数据回调监听
            setOnFrameAvailableListener(this@CameraGLSurfaceView)
            mSurfaceTextureCreatedCallback?.let {
                mSurfaceTextureCreatedCallback = null
                post {
                    it.invoke(this)
                }
            }
        }
        // 实例化绘制器
        mDrawer = TextureDrawer(mTextureId).apply {
            setPreviewOrientation(mPreviewOrientation, mPreviewMirror)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged width=$width height=$height")
        // x, y以像素为单位，指定了窗口的左下角位置。
        // width, height表示视口矩形的宽度和高度，根据窗口的实时变化重绘窗口
        // 在默认情况下，视口被设置为占据窗口的整个像素矩形，窗口大小和视口大小相同。如果选择一个更小的绘图区域，可以用glviewport函数实现这一变换，在窗口中定义一个像素矩形，将图像映射到这个矩形中。
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        Log.i(TAG, "onDrawFrame")
        // 以rgba来清空缓冲区当前的所有颜色
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        // 以glClearColor设置的值来清除颜色缓冲以及深度缓冲
        // 类似的其他缓冲区 GL_ACCUM_BUFFER_BIT、GL_STENCIL_BUFFER_BIT
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        // 从图像流中将纹理图像更新为最新的帧
        // 当updateTexImage()被调用时，SurfaceTexture对象所关联的OpenGLES中纹理对象的内容将被更新为Image Stream中最新的图片
        mSurfaceTexture!!.updateTexImage()
        val transformMatrix = FloatArray(16)
        mSurfaceTexture!!.getTransformMatrix(transformMatrix)
        // 调用绘制
        mDrawer!!.draw()
    }

    /**
     * 创建纹理id
     * */
    private fun createTextureId(): Int {
        // 用于接收OpenGL的数据返回
        val texture = intArrayOf(1)
        // 向OpenGL请求创建一个纹理
        GLES20.glGenTextures(1, texture, 0)
        // 把创建的纹理绑定到纹理目标上，当把一张纹理绑定到一个目标上时，之前对这个目标的绑定就会失效
        // 当一张纹理被绑定后，GL对于这个目标的操作都会影响到这个被绑定的纹理
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0])
        // 设置该目标的属性：当纹理映射到较大区域时，采用linear（使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色）方式进行填充
        // 其他填充方式有：GL_NEAREST，使用纹理中坐标最接近的一个像素的颜色作为需要绘制的像素颜色
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR.toFloat())
        // 设置该目标的属性：当纹理映射到较小区域时，采用linear方式进行填充
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat())
        // 设置该目标的属性：纹理坐标范围是0~1 ，如果设置的值超过1，纹理X方向采用GL_CLAMP_TO_EDGE边缘截取方式处理
        // 其他处理方式有：GL_REPEAT、GL_CLAMP、GL_MIRRORED_REPEAT_ARB、CLAMP_TO_BORDER_ARB
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE)

        return texture[0]
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        Log.i(TAG, "onFrameAvailable")
        // 纹理层有新数据，就通知view绘制
        requestRender()
    }

    @MainThread
    fun getSurfaceTexture(callback: SurfaceTexture.() -> Unit) {
        mSurfaceTexture?.let {
            callback.invoke(it)
        } ?: let {
            mSurfaceTextureCreatedCallback = callback
        }
    }

    /**
     * @param orientation 摄像头旋转的角度
     *
     * */
    fun setPreviewOrientation(orientation: Int, mirror: Boolean) {
        if (orientation == 0 || orientation == 90 || orientation == 180 || orientation == 270) {
            mPreviewOrientation = orientation
            mDrawer?.let {
                it.setPreviewOrientation(orientation, mirror)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mSurfaceTexture = null
    }

}