package cc.appweb.gllearning.componet

import android.opengl.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import cc.appweb.gllearning.util.StorageUtil
import java.io.File
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.CountDownLatch

/**
 * 图像旋转渲染器
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class RotateRender : CommonGLRender() {

    private var mRotateType = ROTATE_0

    // FBO ID
    private var mFboId: Int = -1

    // FBO附着的纹理id
    private var mFboTextureId: Int = -1

    // 纹理 ID
    private var mTextureId: Int = -1

    // 顶点shader
    private var mVertexShader: Int = -1

    // 片元shader
    private var mFragmentShader: Int = -1

    // 程序 ID
    private var mProgramId: Int = -1

    // 3个VBO，OpenGLES坐标顶点、纹理坐标顶点、顶点绘制顺序
    private val mVboIds = IntArray(3)

    // VAO ID
    private var mVaoId: Int = -1

    // 原图宽度
    private var mOriginWidth = -1

    // 原图高度
    private var mOriginHeight = -1

    companion object {
        private const val TAG = "RotateRender"



        // GLSL语言基础 https://my.oschina.net/sweetdark/blog/208024
        // 顶点着色器 shader
        const val vertexShader =
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
        const val fragmentShader =
                "#version 300 es                            \n" +
                        "precision mediump float;                   \n" + // 设置默认的精度限定符
                        "in vec2 v_texCoord;                        \n" + // 导入纹理坐标，描述片段
                        "layout(location = 0) out vec4 outColor;    \n" + // 提供片段着色器输出变量的声明，这将是传递到下一阶段的颜色
                        "uniform sampler2D s_TextureMap;            \n" + // 声明GL_TEXTURE_2D绑定的空间变量，取出纹理数据
                        "void main()                                \n" +
                        "{                                          \n" +
                        "    vec4 tempColor = texture(s_TextureMap, v_texCoord);   \n" + // 通过纹理和纹理坐标采样颜色值
                        "    outColor = tempColor.bgra;" +  // android Bitmap.createBitmap color 里要求的排列是 ARGB （高位->低位），需要将b/r替换一下输出
                        "}"
    }

    fun getImage(imageByteByteBuffer: ByteBuffer, originWidth: Int, originHeight: Int): ByteBuffer {
        Log.d(TAG, "getImage originWidth=$originWidth originHeight=${originHeight}")
        lateinit var byteBuffer: ByteBuffer
        val countDownLatch = CountDownLatch(1)
        addTask {
//            StorageUtil.writeBufferIntoFile(StorageUtil.getFile(StorageUtil.PATH_LEARNING_RAW + File.separator + System.currentTimeMillis() + "b.raw").absolutePath, imageByteByteBuffer)

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
        addTask {
            val vertexArray = getVertexCoorsWithRotate(type) ?: return@addTask
            mRotateType = type
            // 重新载入
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVboIds[1])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, FloatBuffer.wrap(vertexArray), GLES30.GL_STATIC_DRAW)

        }
    }

    override fun onRenderInit() {
        ///////////////////////////GLES渲染环境搭建开始////////////////////////////////////////
        // 创建一个2D纹理用于连接FBO的颜色附着
        val texArray = IntArray(1)
        GLES30.glGenTextures(1, texArray, 0)
        mFboTextureId = texArray[0]
        // 设置该纹理的属性
        setTexture2DAttributes(mFboTextureId)

        // 创建FBO
        val fboArray = IntArray(1)
        GLES30.glGenFramebuffers(1, fboArray, 0)
        mFboId = fboArray[0]
        Log.d(TAG, "glGenFramebuffers error=${GLES30.glGetError()} fboId=${mFboId} fboTextureId=${mFboTextureId}")

        // 创建纹理
        GLES30.glGenTextures(1, texArray, 0)
        mTextureId = texArray[0]
        Log.d(TAG, "glGenTextures error=${GLES30.glGetError()} mTextureId=${mTextureId}")

        setTexture2DAttributes(mTextureId)

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
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, FloatBuffer.wrap(vVerticesCoors), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVboIds[1])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, FloatBuffer.wrap(vTextureCoors), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, mVboIds[2])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, 6 * 2, ShortBuffer.wrap(vTextureDrawIndices), GLES30.GL_STATIC_DRAW)
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

    override fun onRenderDestroy() {
        if (mTextureId != -1) {
            GLES30.glDeleteTextures(1, intArrayOf(mTextureId), 0)
            mTextureId = -1
        }
        if (mFboTextureId != -1) {
            GLES30.glDeleteTextures(1, intArrayOf(mFboTextureId), 0)
            mFboTextureId = -1
        }
        if (mFboId != -1) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(mFboId), 0)
            mFboId = -1
        }
        if (mProgramId != -1) {
            GLES30.glDeleteProgram(mProgramId)
            mProgramId = -1
        }
        GLES30.glDeleteBuffers(3, mVboIds, 0)

        if (mVaoId != -1) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(mVaoId), 0)
            mVaoId = -1
        }
        if (mVertexShader != -1) {
            GLES30.glDeleteShader(mVertexShader)
            mVertexShader = -1
        }
        if (mFragmentShader != -1) {
            GLES30.glDeleteShader(mFragmentShader)
            mFragmentShader = -1
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

}