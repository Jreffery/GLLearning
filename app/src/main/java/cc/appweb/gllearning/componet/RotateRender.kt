package cc.appweb.gllearning.componet

import android.opengl.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import cc.appweb.gllearning.util.StorageUtil
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch

/**
 * 图像旋转渲染器
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class RotateRender {

    // 任务队列
    private val mTaskList = ArrayBlockingQueue<Runnable>(16)

    private var mRotateType = ROTATE_0

    // LoopThread 停止标记
    @Volatile
    private var mStop = false

    // 线程引用
    private var mThread: LoopThread? = null

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

    // FBO ID
    private var mFboId: Int = 0

    // FBO附着的纹理id
    private var mFboTextureId: Int = 0;

    // 纹理 ID
    private var mTextureId: Int = 0

    // 顶点shader
    private var mVertexShader: Int = 0

    // 片元shader
    private var mFragmentShader: Int = 0

    // 程序 ID
    private var mProgramId: Int = 0

    // 3个VBO，OpenGLES坐标顶点、纹理坐标顶点、顶点绘制顺序
    private val mVboIds = IntArray(3)

    // VAO ID
    private var mVaoId: Int = 0

    // 原图宽度
    private var mOriginWidth = 0

    // 原图高度
    private var mOriginHeight = 0

    companion object {
        private const val TAG = "RotateRender"
        private var mThreadNum = 0

        const val ROTATE_0 = 0
        const val ROTATE_90 = 1
        const val ROTATE_180 = 2
        const val ROTATE_270 = 3

        val vVertices = floatArrayOf(
                -1.0f, -1.0f,   // 左下 （屏幕左上角）
                1.0f, -1.0f,   // 右下 （屏幕右上角）
                -1.0f, 1.0f,   // 左上 （屏幕左下角）
                1.0f, 1.0f,
        )

        //fbo 纹理坐标与正常纹理方向不同，原点位于左下角
        val vFboTexCoors = floatArrayOf(
                0.0f, 0.0f,  // 左下
                1.0f, 0.0f,  // 右下
                0.0f, 1.0f,  // 左上
                1.0f, 1.0f)

        // 绘制顺序
        val indices = shortArrayOf(0, 1, 2, 1, 2, 3)

        // GLSL语言基础 https://my.oschina.net/sweetdark/blog/208024
        // 顶点着色器 shader
        val vertexShader =
                "#version 300 es                            \n" + // 声明使用OpenGLES 3.0
                        "layout(location = 0) in vec4 a_position;   \n" + // 声明输入四维向量
                        "layout(location = 1) in vec2 a_texCoord;   \n" + // 声明输入二维向量
                        "out vec2 v_texCoord;                       \n" + // 声明输出二维向量，纹理坐标
                        "void main()                                \n" +
                        "{                                          \n" +
                        "   gl_Position = a_position;               \n" + // 内建变量赋值，不需要变换，gl_Position描述三维空间里变换后的位置
                        "   v_texCoord = a_texCoord;                \n" + // 输出向量赋值，纹理的坐标
                        "}                                          \n"


        // 用于FBO渲染的片段着色器shader，取每个像素的灰度值
        val fragmentShader =
                "#version 300 es                            \n" +
                        "precision mediump float;                   \n" + // 设置默认的精度限定符
                        "in vec2 v_texCoord;                        \n" + // 导入纹理坐标，描述片段
                        "layout(location = 0) out vec4 outColor;    \n" + // 提供片段着色器输出变量的声明，这将是传递到下一阶段的颜色
                        "uniform sampler2D s_TextureMap;            \n" + // 声明GL_TEXTURE_2D绑定的空间变量，取出纹理数据
                        "void main()                                \n" +
                        "{                                          \n" +
                        "    vec4 tempColor = texture(s_TextureMap, v_texCoord);   \n" + // 通过纹理和纹理坐标采样颜色值
                        "    outColor = vec4(tempColor.b, tempColor.g, tempColor.r, tempColor.a);" +  // (原图 RGBA 通道被转成了 BGRA ？待解决)
                        "}"
    }

    /**
     * 初始化渲染器
     * */
    fun initRender() {
        mThread ?: let {
            // 创建线程
            mThread = LoopThread().apply {
                start()
            }
            mTaskList.put {

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

                ///////////////////////////GLES渲染环境搭建开始////////////////////////////////////////
                // 创建一个2D纹理用于连接FBO的颜色附着
                val texArray = IntArray(1)
                GLES30.glGenTextures(1, texArray, 0)
                mFboTextureId = texArray[0]
                // 设置该纹理ID为2D纹理
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mFboTextureId)
                // 设置该纹理的填充属性
                GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE.toFloat())
                GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE.toFloat())
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_NONE)
                // 创建FBO
                val fboArray = IntArray(1)
                GLES30.glGenFramebuffers(1, fboArray, 0)
                mFboId = fboArray[0]
                Log.d(TAG, "glGenFramebuffers error=${GLES30.glGetError()} fboId=${mFboId} fboTextureId=${mFboTextureId}")

                // 创建纹理
                GLES30.glGenTextures(1, texArray, 0)
                mTextureId = texArray[0]
                Log.d(TAG, "glGenTextures error=${GLES30.glGetError()} mTextureId=${mTextureId}")

                // 绑定纹理单元
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureId)
                // 设置该纹理的填充属性
                GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE.toFloat())
                GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE.toFloat())
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_NONE)

                // 创建shader
                mVertexShader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
                GLES30.glShaderSource(mVertexShader, vertexShader)
                GLES30.glCompileShader(mVertexShader)
                Log.d(TAG, "glCompileShader vertex error=${GLES30.glGetError()}")

                mFragmentShader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
                GLES30.glShaderSource(mFragmentShader, fragmentShader)
                GLES30.glCompileShader(mFragmentShader)
                Log.d(TAG, "glCompileShader fragment error=${GLES30.glGetError()}")

                mProgramId = GLES30.glCreateProgram()
                GLES30.glAttachShader(mProgramId, mVertexShader)
                GLES30.glAttachShader(mProgramId, mFragmentShader)
                GLES30.glLinkProgram(mProgramId)
                Log.d(TAG, "glLinkProgram error=${GLES30.glGetError()}")

                // 生成VBO，减少传输顶点的次数
                GLES30.glGenBuffers(3, mVboIds, 0)
                // 上传VBO数据
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVboIds[0])
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, FloatBuffer.wrap(vVertices), GLES30.GL_STATIC_DRAW)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVboIds[1])
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, FloatBuffer.wrap(vFboTexCoors), GLES30.GL_STATIC_DRAW)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, mVboIds[2])
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, 6*2, ShortBuffer.wrap(indices), GLES30.GL_STATIC_DRAW)
                Log.d(TAG, "glBufferData error=${GLES30.glGetError()}")

                // 生成VAO，减少VBO的操作
                val vaoIdArray = IntArray(1)
                GLES30.glGenVertexArrays(1, vaoIdArray, 0)
                mVaoId = vaoIdArray[0]
                Log.d(TAG, "glGenVertexArrays mVaoId=${mVaoId} error=${GLES30.glGetError()}")
                GLES30.glBindVertexArray(mVaoId)

                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVboIds[0])
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)

                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVboIds[1])
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)

                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, mVboIds[2])
                GLES30.glBindVertexArray(GLES30.GL_NONE)

                ///////////////////////////GLES渲染环境搭建结束////////////////////////////////////////
            }
        }
    }

    fun getImage(imageByteByteBuffer: ByteBuffer, originWidth: Int, originHeight: Int): ByteBuffer {
        Log.d(TAG, "getImage originWidth=$originWidth originHeight=${originHeight}")
        lateinit var byteBuffer: ByteBuffer
        val countDownLatch = CountDownLatch(1)
        mTaskList.put {

//            val output = FileOutputStream(StorageUtil.getFile(StorageUtil.PATH_LEARNING_RAW + File.separator + System.currentTimeMillis() + "b.raw"))
//            output.write(imageByteByteBuffer.array())
//            output.flush()
//            output.close()

            mOriginWidth = originWidth
            mOriginHeight = originHeight

            // FBO width/height
            val fboWidth: Int
            val fboHeight: Int
            if (mRotateType == ROTATE_90 || mRotateType == ROTATE_270) {
                fboWidth = mOriginHeight
                fboHeight = mOriginWidth
            } else {
                fboWidth = mOriginWidth
                fboHeight = mOriginHeight
            }
            // FBO需要有纹理附着
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFboId)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mFboTextureId)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, fboWidth,
                    fboHeight, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, mFboTextureId, 0)
            Log.d(TAG, "getImage glFramebufferTexture2D error=${GLES30.glGetError()} " +
                    "glCheckFramebufferStatus=${GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)}")
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_NONE)

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureId)
            Log.d(TAG, "getImage glBindTexture error=${GLES30.glGetError()}")
            // 上传纹理
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, mOriginWidth,
                    mOriginHeight, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, imageByteByteBuffer)
            Log.d(TAG, "getImage glTexImage2D error=${GLES30.glGetError()}")
            byteBuffer = draw()
            countDownLatch.countDown()
        }
        countDownLatch.await()
        Log.d(TAG, "countDownLatch.await() finish!!")
        return byteBuffer
    }

    fun setRotate(type: Int) {
        Log.d(TAG, "setRotate type=${type}")
        mThread?.let {
            mTaskList.put {
                val vertexArray: FloatArray
                when (type) {
                    ROTATE_0 -> {
                        vertexArray = floatArrayOf(
                                0.0f, 0.0f,  // 左下
                                1.0f, 0.0f, // 右下
                                0.0f, 1.0f,  // 左上
                                1.0f, 1.0f, // 右上
                        )
                    }
                    ROTATE_90 -> {
                        vertexArray = floatArrayOf(
                                0.0f, 1.0f,
                                0.0f, 0.0f,
                                1.0f, 1.0f,
                                1.0f, 0.0f,
                        )
                    }
                    ROTATE_180 -> {
                        vertexArray = floatArrayOf(
                                1.0f, 1.0f,
                                0.0f, 1.0f,
                                1.0f, 0.0f,
                                0.0f, 0.0f,
                        )
                    }
                    ROTATE_270 -> {
                        vertexArray = floatArrayOf(
                                1.0f, 0.0f,
                                1.0f, 1.0f,
                                0.0f, 0.0f,
                                0.0f, 1.0f,
                        )
                    }
                    else -> {
                        return@put
                    }
                }
                mRotateType = type
                // 重新载入
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVboIds[1])
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, FloatBuffer.wrap(vertexArray), GLES30.GL_STATIC_DRAW)
            }
        }
    }

    /**
     * 销毁资源
     * */
    fun destroy() {
        mThread?.let {
            mTaskList.put {
                mStop = true

                // 销毁资源
                Log.d(TAG, "destroy")
                // 8. 释放EGL环境
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

                if (mTextureId != 0) {
                    GLES30.glDeleteTextures(1, IntArray(mTextureId), 0)
                }
                mTextureId = 0
                if (mFboTextureId != 0) {
                    GLES30.glDeleteTextures(1, IntArray(mFboTextureId), 0)
                }
                mFboTextureId = 0
                if (mFboId != 0) {
                    GLES30.glDeleteFramebuffers(1, IntArray(mFboId), 0)
                }
                mFboId = 0
                if (mProgramId != 0) {
                    GLES30.glDeleteProgram(mProgramId)
                }
                mProgramId = 0
                GLES30.glDeleteBuffers(3, mVboIds, 0)

                if (mVaoId != 0) {
                    GLES30.glDeleteVertexArrays(1, IntArray(mVaoId), 0)
                }
                mVaoId = 0
                if (mVertexShader != 0) {
                    GLES30.glDeleteShader(mVertexShader)
                }
                mVertexShader = 0
                if (mFragmentShader != 0) {
                    GLES30.glDeleteShader(mFragmentShader)
                }
                mFragmentShader = 0
            }
            mThread = null
        }
    }

    /**
     * 渲染
     * */
    private fun draw(): ByteBuffer {
        val drawWidth: Int
        val drawHeight: Int
        if (mRotateType == ROTATE_90 || mRotateType == ROTATE_270) {
            drawWidth = mOriginHeight
            drawHeight = mOriginWidth
        } else {
            drawWidth = mOriginWidth
            drawHeight = mOriginHeight
        }
        Log.d(TAG, "draw rotate=${mRotateType} drawWidth=${drawWidth} drawHeight=${drawHeight}")
        // 框定渲染区域
        GLES30.glViewport(0, 0, drawWidth, drawHeight)
        Log.d(TAG, "draw glViewport error=${GLES30.glGetError()}")

        GLES30.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        Log.d(TAG, "draw glClear error=${GLES30.glGetError()}")

        GLES30.glUseProgram(mProgramId)
        Log.d(TAG, "draw glUseProgram error=${GLES30.glGetError()}")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFboId)
        Log.d(TAG, "draw glBindFramebuffer error=${GLES30.glGetError()}")
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        Log.d(TAG, "draw glActiveTexture error=${GLES30.glGetError()}")

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureId)
        Log.d(TAG, "draw glBindTexture error=${GLES30.glGetError()}")
        GLES30.glBindVertexArray(mVaoId)
        Log.d(TAG, "draw glBindVertexArray error=${GLES30.glGetError()}")
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, 0)
        Log.d(TAG, "draw glDrawElements error=${GLES30.glGetError()}")

        val buffer = ByteBuffer.allocateDirect(drawHeight * drawWidth * 4)
        GLES30.glReadPixels(0, 0, drawWidth, drawHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        Log.d(TAG, "draw glReadPixels error=${GLES30.glGetError()} position=${buffer.position()}" +
                " capacity=${buffer.capacity()} limit=${buffer.limit()}")
        return buffer
    }

    /**
     * loop线程，承载创建EGL和GLES渲染的任务
     * */
    private inner class LoopThread : Thread() {
        override fun run() {
            name = "RotateRender-${mThreadNum++}"
            Log.i(TAG, "LoopThread start")
            while (!mStop) {
                // 获取任务
                val runnable = mTaskList.take()
                runnable?.run()
            }
            Log.i(TAG, "LoopThread end")
        }
    }

}