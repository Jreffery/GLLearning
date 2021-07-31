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
    private var mEglDisplay: EGLDisplay? = null

    // EGL 版本信息
    private val mEglVersion = IntArray(2)

    // EGL配置属性
    private val mEGLConfig = arrayOf<EGLConfig?>(null)

    private val mNumConfig = IntArray(1)

    // EGL Surface
    private var mEglSurface: EGLSurface? = null

    // EGL context
    private var mEglContext: EGLContext? = null

    companion object {
        private const val TAG = "CommonGLRender"

        const val ROTATE_0 = 0
        const val ROTATE_90 = 1
        const val ROTATE_180 = 2
        const val ROTATE_270 = 3

        /**
         * 设置通用的2D纹理属性
         * */
        fun setTexture2DAttributes(textureId: Int) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            // 设置该纹理的填充属性
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE.toFloat())
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE.toFloat())
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
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
                            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
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

                    val surfaceAttr = intArrayOf(
                            EGL14.EGL_WIDTH, 1,
                            EGL14.EGL_HEIGHT, 1,
                            EGL14.EGL_NONE
                    )
                    mEglSurface = EGL14.eglCreatePbufferSurface(mEglDisplay, mEGLConfig[0], surfaceAttr, 0)
                    Log.d(TAG, "eglCreatePbufferSurface error=${EGL14.eglGetError()}")

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
     * 渲染初始化入口，GLES初始化工作可在此实现
     * */
    abstract fun onRenderInit()

    /**
     * 渲染销毁入口，GLES销毁工作可在此实现
     * */
    abstract fun onRenderDestroy()


}