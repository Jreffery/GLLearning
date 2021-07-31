package cc.appweb.gllearning.componet

import android.opengl.GLES30
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.CountDownLatch

/**
 * YUV420SP（NV12）图像旋转渲染器，输出为NV12
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class Yuv2YuvRotateRender : CommonGLRender() {

    private var mRotateType = ROTATE_180

    // FBO ID
    private var mFboId: Int = 0

    // FBO附着的纹理id
    private var mFboTextureId: Int = 0;

    // y变量 纹理ID
    private var mYTextureId: Int = 0

    // uv变量 纹理ID
    private var mUVTextureId: Int = 0

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
        private const val TAG = "Yuv2YuvRotateRender"

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
                        "precision highp float;                   \n" + // 设置默认的精度限定符
                        "in vec2 v_texCoord;                        \n" + // 导入纹理坐标，描述片段
                        "layout(location = 0) out vec4 outColor;    \n" + // 提供片段着色器输出变量的声明，这将是传递到下一阶段的颜色
                        "uniform sampler2D yTextureMap;             \n" + // 保存y变量的纹理
                        "uniform sampler2D uvTextureMap;            \n" + // 保存uv变量的纹理
                        "uniform float width;                       \n" +
                        "uniform mat3 rotateM;                      \n" +
                        "void main()                                \n" +
                        "{                                          \n" +
//                        "    mat3 rotateM = mat3(-1.0,0.0,0.0,0.0,-1.0,0.0,0.0,0.0,1.0);  \n" +
                        "    float offset = 1.0/width;              \n" +
                        "    vec2 move = vec2(0.5, 0.5);            \n" +
                        "    if (v_texCoord.t > (2.0/3.0) ) {       \n" +
                        "        float tt = (v_texCoord.t - (2.0/3.0)) * 3.0;  \n" +
                        "        vec2 b1 = vec2(v_texCoord.s, tt) - move;       \n" +
                        "        vec2 a1 = (rotateM * vec3(b1, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        vec2 b2 = vec2(v_texCoord.s + 2.0 * offset, tt) - move; \n" +
                        "        vec2 a2 = (rotateM * vec3(b2, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        outColor.rg = texture(uvTextureMap, a1).ra;  \n" +
                        "        outColor.ba = texture(uvTextureMap, a2).ra;  \n" +
                        "    } else {                               \n" +
                        "        float tt = v_texCoord.t * 3.0 / 2.0;  \n" +
                        "        vec2 b1 = vec2(v_texCoord.s, tt) - move;       \n" +
                        "        vec2 a1 = (rotateM * vec3(b1, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        vec2 b2 = vec2(v_texCoord.s + offset, tt) - move; \n" +
                        "        vec2 a2 = (rotateM * vec3(b2, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        vec2 b3 = vec2(v_texCoord.s + offset * 2.0, tt) - move;       \n" +
                        "        vec2 a3 = (rotateM * vec3(b3, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        vec2 b4 = vec2(v_texCoord.s + offset * 3.0, tt) - move; \n" +
                        "        vec2 a4 = (rotateM * vec3(b4, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        outColor.r = texture(yTextureMap, a1).r;  \n" +
                        "        outColor.g = texture(yTextureMap, a2).r;  \n" +
                        "        outColor.b = texture(yTextureMap, a3).r;  \n" +
                        "        outColor.a = texture(yTextureMap, a4).r;  \n" +
                        "    }                                      \n" +
                        "}"
    }

    fun getImage(imageByteByteBuffer: ByteBuffer, originWidth: Int, originHeight: Int): ByteBuffer {
        Log.d(TAG, "getImage originWidth=$originWidth originHeight=${originHeight}")
        lateinit var byteBuffer: ByteBuffer
        val countDownLatch = CountDownLatch(1)
        addTask {
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
            // RGBA -> YUV（NV12），width和height需要相应改变
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, fboWidth / 4,
                    fboHeight * 3 / 2, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, mFboTextureId, 0)
            Log.d(TAG, "getImage glFramebufferTexture2D error=${GLES30.glGetError()} " +
                    "glCheckFramebufferStatus=${GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)}")
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_NONE)

            // 上传纹理
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mYTextureId)
            // GL_LUMINANCE -> (y, y, y, 1)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, mOriginWidth,
                    mOriginHeight, 0, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, imageByteByteBuffer)
            Log.d(TAG, "getImage glTexImage2D yTexture error=${GLES30.glGetError()}")
            imageByteByteBuffer.position(mOriginWidth * mOriginHeight)

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mUVTextureId)
            // GL_LUMINANCE_ALPHA -> (u, u, u, v)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE_ALPHA, mOriginWidth / 2,
                    mOriginHeight / 2, 0, GLES30.GL_LUMINANCE_ALPHA, GLES30.GL_UNSIGNED_BYTE, imageByteByteBuffer)
            Log.d(TAG, "getImage glTexImage2D uvTexture error=${GLES30.glGetError()}")

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
            mRotateType = type
        }
    }

    override fun onRenderInit() {
        ///////////////////////////GLES渲染环境搭建开始////////////////////////////////////////
        // 创建一个2D纹理用于连接FBO的颜色附着
        val texArray = IntArray(2)
        GLES30.glGenTextures(1, texArray, 0)
        mFboTextureId = texArray[0]
        // 设置该纹理ID为2D纹理
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mFboTextureId)
        setTexture2DAttributes(mFboTextureId)

        // 创建FBO
        val fboArray = IntArray(1)
        GLES30.glGenFramebuffers(1, fboArray, 0)
        mFboId = fboArray[0]
        Log.d(TAG, "glGenFramebuffers error=${GLES30.glGetError()} fboId=${mFboId} fboTextureId=${mFboTextureId}")

        // 创建Y变量纹理
        GLES30.glGenTextures(2, texArray, 0)
        mYTextureId = texArray[0]
        Log.d(TAG, "glGenTextures error=${GLES30.glGetError()} mYTextureId=${mYTextureId} mUVTextureId=${texArray[1]}")
        setTexture2DAttributes(mYTextureId)
        setTexture2DAttributes(mUVTextureId)

        // 创建shader
        mVertexShader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
        GLES30.glShaderSource(mVertexShader, vertexShader)
        Log.d(TAG, "glShaderSource vertex error=${GLES30.glGetError()}")
        GLES30.glCompileShader(mVertexShader)
        Log.d(TAG, "glCompileShader vertex error=${GLES30.glGetError()}")

        mFragmentShader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
        GLES30.glShaderSource(mFragmentShader, fragmentShader)
        Log.d(TAG, "glShaderSource fragment error=${GLES30.glGetError()}")
        GLES30.glCompileShader(mFragmentShader)
        Log.d(TAG, "glCompileShader fragment error=${GLES30.glGetError()}")

        mProgramId = GLES30.glCreateProgram()
        GLES30.glAttachShader(mProgramId, mVertexShader)
        Log.d(TAG, "glAttachShader vertex error=${GLES30.glGetError()}")
        GLES30.glAttachShader(mProgramId, mFragmentShader)
        Log.d(TAG, "glAttachShader fragment error=${GLES30.glGetError()}")
        GLES30.glLinkProgram(mProgramId)
        Log.d(TAG, "glLinkProgram mProgramId=$mProgramId error=${GLES30.glGetError()}")

        // 生成VBO，减少传输顶点的次数
        GLES30.glGenBuffers(3, mVboIds, 0)
        // 上传VBO数据
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVboIds[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, FloatBuffer.wrap(vVertices), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVboIds[1])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, FloatBuffer.wrap(vFboTexCoors), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, mVboIds[2])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, 6 * 2, ShortBuffer.wrap(indices), GLES30.GL_STATIC_DRAW)
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
        if (mYTextureId != 0) {
            GLES30.glDeleteTextures(1, IntArray(mYTextureId), 0)
            mYTextureId = 0
        }
        if (mUVTextureId != 0) {
            GLES30.glDeleteTextures(1, IntArray(mUVTextureId), 0)
            mUVTextureId = 0
        }
        if (mFboTextureId != 0) {
            GLES30.glDeleteTextures(1, IntArray(mFboTextureId), 0)
            mFboTextureId = 0
        }
        if (mFboId != 0) {
            GLES30.glDeleteFramebuffers(1, IntArray(mFboId), 0)
            mFboId = 0
        }
        if (mProgramId != 0) {
            GLES30.glDeleteProgram(mProgramId)
            mProgramId = 0
        }
        GLES30.glDeleteBuffers(3, mVboIds, 0)

        if (mVaoId != 0) {
            GLES30.glDeleteVertexArrays(1, IntArray(mVaoId), 0)
            mVaoId = 0
        }
        if (mVertexShader != 0) {
            GLES30.glDeleteShader(mVertexShader)
            mVertexShader = 0
        }
        if (mFragmentShader != 0) {
            GLES30.glDeleteShader(mFragmentShader)
            mFragmentShader = 0
        }
    }

    /**
     * 渲染
     * */
    private fun draw(): ByteBuffer {
        val rotateMat3: FloatArray
        val drawWidth: Int
        val drawHeight: Int
        when (mRotateType) {
            ROTATE_90 -> {
                rotateMat3 = floatArrayOf(0.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f)
                drawWidth = mOriginHeight
                drawHeight = mOriginWidth
            }
            ROTATE_180 -> {
                rotateMat3 = floatArrayOf(-1.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f)
                drawWidth = mOriginWidth
                drawHeight = mOriginHeight
            }
            ROTATE_270 -> {
                rotateMat3 = floatArrayOf(0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f)
                drawWidth = mOriginHeight
                drawHeight = mOriginWidth
            }
            else -> {
                rotateMat3 = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f)
                drawWidth = mOriginWidth
                drawHeight = mOriginHeight
            }
        }

        Log.d(TAG, "draw rotate=${mRotateType} drawWidth=${drawWidth} drawHeight=${drawHeight}")
        // 框定渲染区域
        GLES30.glViewport(0, 0, drawWidth / 4, drawHeight * 3 / 2)
        Log.d(TAG, "draw glViewport error=${GLES30.glGetError()}")

        GLES30.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        Log.d(TAG, "draw glClear error=${GLES30.glGetError()}")

        GLES30.glUseProgram(mProgramId)
        Log.d(TAG, "draw glUseProgram error=${GLES30.glGetError()}")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFboId)
        Log.d(TAG, "draw glBindFramebuffer error=${GLES30.glGetError()}")

        // 将纹理ID与程序中的uniform变量绑定
        val yLoc = GLES30.glGetUniformLocation(mProgramId, "yTextureMap")
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + yLoc)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mYTextureId)
        // Set the Y plane sampler to texture unit
        GLES30.glUniform1i(yLoc, yLoc)
        val uvLoc = GLES30.glGetUniformLocation(mProgramId, "uvTextureMap")
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + uvLoc)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mUVTextureId)
        // Set the UV plane sampler to texture unit
        GLES30.glUniform1i(uvLoc, uvLoc)
        Log.d(TAG, "draw active&bind yLoc=$yLoc uvLoc=$uvLoc")

        // 上传width的值
        val widthLoc = GLES30.glGetUniformLocation(mProgramId, "width")
        Log.d(TAG, "glGetUniformLocation widthLoc=${widthLoc} error=${GLES30.glGetError()}")
        GLES30.glUniform1f(widthLoc, drawWidth.toFloat())
        Log.d(TAG, "glUniform1f error=${GLES30.glGetError()}")

        // 上传旋转变换矩阵mat3
        val mat3Loc = GLES30.glGetUniformLocation(mProgramId, "rotateM")
        Log.d(TAG, "glGetUniformLocation rotateM=${mat3Loc} error=${GLES30.glGetError()}")
        GLES30.glUniformMatrix3fv(mat3Loc, 1, false, rotateMat3, 0)
        Log.d(TAG, "glUniformMatrix3fv error=${GLES30.glGetError()}")

        GLES30.glBindVertexArray(mVaoId)
        Log.d(TAG, "draw glBindVertexArray error=${GLES30.glGetError()}")
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, 0)
        Log.d(TAG, "draw glDrawElements error=${GLES30.glGetError()}")

        val buffer = ByteBuffer.allocateDirect(drawHeight * drawWidth * 3 / 2)
        GLES30.glReadPixels(0, 0, drawWidth / 4, drawHeight * 3 / 2, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        Log.d(TAG, "draw glReadPixels error=${GLES30.glGetError()} position=${buffer.position()}" +
                " capacity=${buffer.capacity()} limit=${buffer.limit()}")
        return buffer
    }

}