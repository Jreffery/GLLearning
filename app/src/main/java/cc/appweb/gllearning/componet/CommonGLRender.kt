package cc.appweb.gllearning.componet

import android.opengl.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import cc.appweb.gllearning.opengl.GLLoopThread

/**
 * 通用OpenGLES渲染器，抽象公共的逻辑
 * 1. 创建EGL、销毁EGL
 * 2. 启动Loop线程
 * 3. 提供设置纹理公共属性方法
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
abstract class CommonGLRender {

    // 线程引用
    private var mThread: GLLoopThread? = null

    // EGL 抽象显示设备
    protected var mEglDisplay: EGLDisplay? = null

    // EGL 版本信息
    private val mEglVersion = IntArray(2)

    // EGL配置属性
    private val mEGLConfig = arrayOf<EGLConfig?>(null)

    private val mNumConfig = IntArray(1)

    // EGL Surface
    protected var mEglSurface: EGLSurface? = null

    // EGL context
    private var mEglContext: EGLContext? = null

    companion object {
        private const val TAG = "CommonGLRender"

        const val ROTATE_0 = 0
        const val ROTATE_90 = 1
        const val ROTATE_180 = 2
        const val ROTATE_270 = 3

        // 顶点坐标
        val vVerticesCoors = floatArrayOf(
                -1.0f, -1.0f,   // 左下 （屏幕左上角）
                1.0f, -1.0f,   // 右下 （屏幕右上角）
                -1.0f, 1.0f,   // 左上 （屏幕左下角）
                1.0f, 1.0f,
        )

        //纹理坐标，与正常纹理方向不同，原点位于左下角
        val vTextureCoors = floatArrayOf(
                0.0f, 0.0f,  // 左下
                1.0f, 0.0f,  // 右下
                0.0f, 1.0f,  // 左上
                1.0f, 1.0f)

        // 绘制顺序
        val vTextureDrawIndices = shortArrayOf(0, 1, 2, 1, 2, 3)

        // 纹理旋转的顶点顺序
        fun getVertexCoorsWithRotate(rotateType: Int): FloatArray? {
            when (rotateType) {
                ROTATE_0 -> {
                    return floatArrayOf(
                            0.0f, 0.0f,  // 左下
                            1.0f, 0.0f, // 右下
                            0.0f, 1.0f,  // 左上
                            1.0f, 1.0f, // 右上
                    )
                }
                ROTATE_90 -> {
                    return floatArrayOf(
                            0.0f, 1.0f,
                            0.0f, 0.0f,
                            1.0f, 1.0f,
                            1.0f, 0.0f,
                    )
                }
                ROTATE_180 -> {
                    return floatArrayOf(
                            1.0f, 1.0f,
                            0.0f, 1.0f,
                            1.0f, 0.0f,
                            0.0f, 0.0f,
                    )
                }
                ROTATE_270 -> {
                    return floatArrayOf(
                            1.0f, 0.0f,
                            1.0f, 1.0f,
                            0.0f, 0.0f,
                            0.0f, 1.0f,
                    )
                }
                else -> {
                    return null
                }
            }
        }


        /**
         * 设置通用的2D纹理属性
         * */
        fun setTexture2DAttributes(textureId: Int, closure: (()->Unit)? = null) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            // 设置该纹理的填充属性
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE.toFloat())
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE.toFloat())
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            closure?.invoke()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_NONE)
        }
    }

    /**
     * 初始化渲染器
     * */
    fun initRender() {
        mThread ?: let {
            mThread = GLLoopThread().apply {
                start()

                // 创建EGL
                addTask {
                    //////////////////////////////EGL环境搭建开始/////////////////////////////////////////
                    // 初始化EGL
                    mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                    Log.d(TAG, "mEglDisplay == EGL_NO_DISPLAY ? ${(mEglDisplay == EGL14.EGL_NO_DISPLAY)}")

                    EGL14.eglInitialize(mEglDisplay, mEglVersion, 0, mEglVersion, 1)
                    Log.d(TAG, "eglInitialize error=${EGL14.eglGetError()} majorVersion=${mEglVersion[0]} minorVersion=${mEglVersion[1]}")

                    // EGL config 属性
                    val confAttr: IntArray = intArrayOf(
                            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                            // EGL_WINDOW_BIT EGL_PBUFFER_BIT we will create a pixelbuffer surface
                            EGL14.EGL_SURFACE_TYPE, getSurfaceType(),
                            EGL14.EGL_RED_SIZE, 8,
                            EGL14.EGL_GREEN_SIZE, 8,
                            EGL14.EGL_BLUE_SIZE, 8,
                            EGL14.EGL_ALPHA_SIZE, 8,// if you need the alpha channel
                            EGL14.EGL_DEPTH_SIZE, 8,// if you need the depth buffer
                            EGL14.EGL_STENCIL_SIZE, 8,
                            EGL14.EGL_NONE
                    )
                    EGL14.eglChooseConfig(mEglDisplay, confAttr, 0, mEGLConfig, 0, 1, mNumConfig, 0)
                    Log.d(TAG, "eglInitialize error=${EGL14.eglGetError()}")

                    // 创建Window
                    mEglSurface = createWindowSurfaceSelf(mEglDisplay!!, mEGLConfig[0]!!, getSurfaceAttr())
                            ?: EGL14.eglCreatePbufferSurface(mEglDisplay, mEGLConfig[0], getSurfaceAttr(), 0)
                    Log.d(TAG, "egl create surface error=${EGL14.eglGetError()}")

                    // EGL context 属性
                    val ctxAttr = intArrayOf(
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, // 使用EGL 2.0
                            EGL14.EGL_NONE
                    )
                    mEglContext = EGL14.eglCreateContext(mEglDisplay, mEGLConfig[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0)
                    Log.d(TAG, "eglCreateContext error=${EGL14.eglGetError()}")

                    EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)
                    Log.d(TAG, "eglMakeCurrent error=${EGL14.eglGetError()}")
                    //////////////////////////////EGL环境搭建结束/////////////////////////////////////////

                    onRenderInit()
                }
            }
        }
    }

    fun destroy() {
        mThread?.addTask {
            // 销毁资源
            Log.d(TAG, "destroy")
            // 释放EGL环境
            if (mEglDisplay != EGL14.EGL_NO_DISPLAY) {
                // 解绑上下文
                EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroyContext(mEglDisplay, mEglContext)
                EGL14.eglDestroySurface(mEglDisplay, mEglSurface)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(mEglDisplay)
            }

            mEglDisplay = null
            mEglSurface = null
            mEglContext = null

            onRenderDestroy()
        }

        mThread?.stopLoop()
        mThread = null
    }

    /**
     * 向任务队列添加任务
     * */
    protected fun addTask(task: Runnable) {
        mThread?.addTask(task)
    }

    /**
     * 创建窗口，子类可以实现创建自己类型的窗口
     *
     * @param display 创建好的抽象显示设备
     * @param config 创建好的显示配置
     * @param surfaceAttr 创建好的显示属性
     * @return 子类创建的窗口，null则表示创建默认的窗口
     * */
    protected open fun createWindowSurfaceSelf(display: EGLDisplay, config: EGLConfig, surfaceAttr: IntArray): EGLSurface? {
        return null
    }

    /**
     * 返回创建EGL的Surface Type，默认为PBuffer，用于离屏渲染
     * */
    protected open fun getSurfaceType(): Int {
        return EGL14.EGL_PBUFFER_BIT
    }

    /**
     * 返回创建EGL的Surface的属性
     * */
    protected open fun getSurfaceAttr(): IntArray {
        return intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
        )
    }

    /**
     * 渲染初始化入口，GLES初始化工作可在此实现
     * */
    abstract fun onRenderInit()

    /**
     * 渲染销毁入口，GLES销毁工作可在此实现
     * */
    abstract fun onRenderDestroy()

}