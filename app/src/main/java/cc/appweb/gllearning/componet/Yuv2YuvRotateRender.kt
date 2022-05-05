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
 * YUV420SP（NV21）图像旋转渲染器，输出为NV21
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class Yuv2YuvRotateRender : CommonGLRender() {

    private var mRotateType = ROTATE_180

    // FBO ID
    private var mFboId: Int = -1

    // FBO附着的纹理id
    private var mFboTextureId: Int = -1

    // y变量 纹理ID
    private var mYTextureId: Int = -1

    // uv变量 纹理ID
    private var mUVTextureId: Int = -1

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
        private const val TAG = "Yuv2YuvRotateRender"

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


        // 用于FBO渲染的片段着色器shader，从YUV旋转成YUV
        const val fragmentShader =
                "#version 300 es                            \n" +
                        "precision highp float;                   \n" + // 设置默认的精度限定符
                        "in vec2 v_texCoord;                        \n" + // 导入纹理坐标，描述片段
                        "layout(location = 0) out vec4 outColor;    \n" + // 提供片段着色器输出变量的声明，这将是传递到下一阶段的颜色
                        "uniform sampler2D yTextureMap;             \n" + // 保存y变量的纹理
                        "uniform sampler2D uvTextureMap;            \n" + // 保存uv变量的纹理
                        "uniform float width;                       \n" + // 原图的宽度
                        "uniform mat3 rotateM;                      \n" + // 旋转变换矩阵
                        "void main()                                \n" +
                        "{                                          \n" +
//                        "    mat3 rotateM = mat3(-1.0,0.0,0.0,0.0,-1.0,0.0,0.0,0.0,1.0);  \n" +
                        "    float offset = 1.0/width;              \n" + // YYYY 变量合并时的步长
                        "    vec2 move = vec2(0.5, 0.5);            \n" + // 纹理坐标平移矩阵（把原点作为中心），用作旋转前后的平移
                        "    if (v_texCoord.t > (2.0/3.0) ) {       \n" + // NV21在长度的2/3后是UV的分量
                        "        float tt = (v_texCoord.t - (2.0/3.0)) * 3.0;  \n" + // 还原真实的t分量
                        "        vec2 b1 = vec2(v_texCoord.s, tt) - move;       \n" + // 平移，把原点作为中心
                        "        vec2 a1 = (rotateM * vec3(b1, 1.0) + vec3(move, 1.0)).st;  \n" +  // 旋转，并把坐标还原平移前
                        "        vec2 b2 = vec2(v_texCoord.s + 2.0 * offset, tt) - move; \n" +
                        "        vec2 a2 = (rotateM * vec3(b2, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        outColor.rg = texture(uvTextureMap, a1).ra;  \n" +  // 把两个UV合并成一个RGBA
                        "        outColor.ba = texture(uvTextureMap, a2).ra;  \n" +
                        "    } else {                               \n" +
                        "        float tt = v_texCoord.t * 3.0 / 2.0;  \n" + // 还原真实的t分量
                        "        vec2 b1 = vec2(v_texCoord.s, tt) - move;       \n" +
                        "        vec2 a1 = (rotateM * vec3(b1, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        vec2 b2 = vec2(v_texCoord.s + offset, tt) - move; \n" +
                        "        vec2 a2 = (rotateM * vec3(b2, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        vec2 b3 = vec2(v_texCoord.s + offset * 2.0, tt) - move;       \n" +
                        "        vec2 a3 = (rotateM * vec3(b3, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        vec2 b4 = vec2(v_texCoord.s + offset * 3.0, tt) - move; \n" +
                        "        vec2 a4 = (rotateM * vec3(b4, 1.0) + vec3(move, 1.0)).st;  \n" +
                        "        outColor.r = texture(yTextureMap, a1).r;  \n" +  // 把4个Y分量合并成一个RGBA
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
            // RGBA -> YUV（NV21），width和height需要相应改变
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
        mUVTextureId = texArray[1]
        Log.d(TAG, "glGenTextures error=${GLES30.glGetError()} mYTextureId=${mYTextureId} mUVTextureId=${mUVTextureId}")
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
        if (mYTextureId != -1) {
            GLES30.glDeleteTextures(1, intArrayOf(mYTextureId), 0)
            mYTextureId = -1
        }
        if (mUVTextureId != -1) {
            GLES30.glDeleteTextures(1, intArrayOf(mUVTextureId), 0)
            mUVTextureId = -1
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