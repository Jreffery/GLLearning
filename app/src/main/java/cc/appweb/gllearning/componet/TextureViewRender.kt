package cc.appweb.gllearning.componet

import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch

/**
 * 表面纹理渲染器，采用NV21数据源
 * */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class TextureViewRender(private val surfaceTexture: SurfaceTexture, private val viewWidth: Int,
                        private val viewHeight: Int) : CommonGLRender() {

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

    // 顶点坐标数组
    private val mVertexCoorsBuffer = ByteBuffer.allocateDirect(8 * 4).apply {
        order(ByteOrder.nativeOrder()).asFloatBuffer().put(vVerticesCoors).flip()
    }

    // 纹理坐标数组
    private val mTextureCoorsBuffer = ByteBuffer.allocateDirect(8 * 4).apply {
        order(ByteOrder.nativeOrder()).asFloatBuffer().put(vTextureCoors).flip()
    }

    // 绘制顶点顺序数组
    private val mIndicesBuffer = ByteBuffer.allocateDirect(6 * 2).apply {
        order(ByteOrder.nativeOrder()).asShortBuffer().put(vTextureDrawIndices).flip()
    }

    companion object {
        private const val TAG = "TextureViewRender"

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


        // 片段着色器shader，通过YUV合成RGB
        const val fragmentShader =
                "#version 300 es                            \n" +
                        "precision mediump float;                   \n" + // 设置默认的精度限定符
                        "in vec2 v_texCoord;                        \n" + // 导入纹理坐标，描述片段
                        "layout(location = 0) out vec4 outColor;    \n" + // 提供片段着色器输出变量的声明，这将是传递到下一阶段的颜色
                        "uniform sampler2D yTextureMap;             \n" + // 保存y变量的纹理
                        "uniform sampler2D uvTextureMap;            \n" + // 保存uv变量的纹理
                        "void main()                                \n" +
                        "{                                          \n" +
                        "    vec3 yvu;                              \n" +
                        "    yvu.x = texture(yTextureMap, v_texCoord).r;   \n" +
                        "    yvu.y = texture(uvTextureMap, v_texCoord).r-0.5;   \n" + // v
                        "    yvu.z = texture(uvTextureMap, v_texCoord).a-0.5;   \n" + // u
                        "    highp vec3 rgb;                        \n" +
                        "    rgb.r = yvu.x + 1.402 * yvu.y;               \n" +
                        "    rgb.g = yvu.x - 0.344 * yvu.z - 0.714 * yvu.y; \n" +
                        "    rgb.b = yvu.x + 1.772 * yvu.z;         \n" +
                        "    outColor = vec4(rgb.bgr, 1.0);               \n" +  // rb色彩反转
                        "}"
    }

    override fun createWindowSurfaceSelf(display: EGLDisplay, config: EGLConfig, surfaceAttr: IntArray): EGLSurface? {
        // 创建SurfaceHolder类型的窗口
        return EGL14.eglCreateWindowSurface(display, config, surfaceTexture, surfaceAttr, 0)
    }

    override fun getSurfaceType(): Int {
        return EGL14.EGL_WINDOW_BIT
    }

    override fun getSurfaceAttr(): IntArray {
        return intArrayOf(
                EGL14.EGL_NONE
        )
    }

    override fun onRenderInit() {
        Log.d(TAG, "onRenderInit")
        // 创建纹理
        val texArray = IntArray(2)
        GLES30.glGenTextures(2, texArray, 0)
        mYTextureId = texArray[0]
        mUVTextureId = texArray[1]
        Log.d(TAG, "glGenTextures error=${GLES30.glGetError()} mYTextureId=$mYTextureId mUVTextureId=$mUVTextureId")
        // 设置纹理属性
        setTexture2DAttributes(mYTextureId)
        setTexture2DAttributes(mUVTextureId)

        // 创建顶点shader
        mVertexShader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
        GLES30.glShaderSource(mVertexShader, vertexShader)
        GLES30.glCompileShader(mVertexShader)
        Log.d(TAG, "compile vertex shader error=${GLES30.glGetError()}")

        // 创建片元shader
        mFragmentShader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
        GLES30.glShaderSource(mFragmentShader, fragmentShader)
        GLES30.glCompileShader(mFragmentShader)
        Log.d(TAG, "compile fragment shader error=${GLES30.glGetError()}")

        // 创建程序
        mProgramId = GLES30.glCreateProgram()
        GLES30.glAttachShader(mProgramId, mVertexShader)
        GLES30.glAttachShader(mProgramId, mFragmentShader)
        GLES30.glLinkProgram(mProgramId)
        Log.d(TAG, "link program error=${GLES30.glGetError()}")

    }

    override fun onRenderDestroy() {
        Log.d(TAG, "onRenderDestroy")
        if (mYTextureId != -1) {
            GLES30.glDeleteTextures(1, IntArray(mYTextureId), 0)
            mYTextureId = -1
        }
        if (mUVTextureId != -1) {
            GLES30.glDeleteTextures(1, IntArray(mUVTextureId), 0)
            mUVTextureId = -1
        }
        if (mProgramId != -1) {
            GLES30.glDeleteProgram(mProgramId)
            mProgramId = -1
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
     * 开始渲染
     *
     * @param nv21ByteBuffer 存储nv21数据的ByteBuffer
     * @param width 图片宽度
     * @param height 图片高度
     * */
    fun render(nv21ByteBuffer: ByteBuffer, width: Int, height: Int) {
        val lock = CountDownLatch(1)
        addTask {
            Log.d(TAG, "render start width=$width height=$height")
            val start = System.currentTimeMillis()
            // 把数据游标放置到最前
            nv21ByteBuffer.position(0)

            // 框定渲染范围
            setViewport(viewWidth, viewHeight, width, height)
            Log.d(TAG, "draw glViewport error=${GLES30.glGetError()}")

            // 清除缓冲区
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            Log.d(TAG, "draw glClear error=${GLES30.glGetError()}")

            // 使用程序
            GLES30.glUseProgram(mProgramId)
            val yLoc = GLES30.glGetUniformLocation(mProgramId, "yTextureMap")
            val uvLoc = GLES30.glGetUniformLocation(mProgramId, "uvTextureMap")
            Log.d(TAG, "render yLoc=$yLoc uv=$uvLoc error=${GLES30.glGetError()}")

            // 激活纹理---Y
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + yLoc)
            // 绑定纹理
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mYTextureId)
            // 赋值到程序中
            GLES30.glUniform1i(yLoc, yLoc)
            Log.d(TAG, "glUniform1i yLoc error=${GLES30.glGetError()}")
            // 上传纹理
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, width,
                    height, 0, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, nv21ByteBuffer)
            Log.d(TAG, "render glTexImage2D yTexture error=${GLES30.glGetError()}")
            nv21ByteBuffer.position(width * height)

            // ---UV
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + uvLoc)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mUVTextureId)
            GLES30.glUniform1i(uvLoc, uvLoc)
            Log.d(TAG, "glUniform1i uvLoc error=${GLES30.glGetError()}")
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE_ALPHA, width / 2,
                    height / 2, 0, GLES30.GL_LUMINANCE_ALPHA, GLES30.GL_UNSIGNED_BYTE, nv21ByteBuffer)
            Log.d(TAG, "render glTexImage2D uvTexture error=${GLES30.glGetError()}")

            // 激活 a_position
            GLES30.glEnableVertexAttribArray(0)
            // 传值 a_position
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, mVertexCoorsBuffer)
            Log.d(TAG, "glVertexAttribPointer 0 error=${GLES30.glGetError()}")

            // 激活 a_texCoord
            GLES30.glEnableVertexAttribArray(1)
            // 传值 a_texCoord
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 2 * 4, mTextureCoorsBuffer)
            Log.d(TAG, "glVertexAttribPointer 1 error=${GLES30.glGetError()}")

            // draw
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, mIndicesBuffer)
            Log.d(TAG, "glDrawElements GL_TRIANGLES error=${GLES30.glGetError()}")
            GLES30.glFlush()
            Log.d(TAG, "glFlush() error=${GLES30.glGetError()}")

            // 交换缓冲区
            EGL14.eglSwapBuffers(mEglDisplay, mEglSurface)
            Log.d(TAG, "eglSwapBuffers error=${EGL14.eglGetError()}")

            lock.countDown()
            Log.d(TAG, "render finish use ${System.currentTimeMillis() - start}ms")
        }
        lock.await()
    }

    /**
     * 框定渲染范围，达到fitCenter的效果
     *
     * @param viewWidth 渲染view的宽度
     * @param viewHeight 渲染view的高度
     * @param imageWidth 图片宽度
     * @param imageHeight 图片高度
     * */
    private fun setViewport(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int) {
        val drawWidth: Int
        val drawHeight: Int
        val x: Int
        val y: Int
        val viewRatio: Float = viewWidth.toFloat() / viewHeight.toFloat()
        val imageRatio: Float = imageWidth.toFloat() / imageHeight.toFloat()
        if (viewRatio > imageRatio) {
            drawHeight = viewHeight
            drawWidth = (imageRatio * drawHeight.toFloat()).toInt()
            y = 0
            x = (viewWidth - drawWidth) / 2
        } else {
            drawWidth = viewWidth
            drawHeight = (drawWidth.toFloat() * imageHeight.toFloat() / imageWidth.toFloat()).toInt()
            x = 0
            y = (viewHeight - drawHeight) / 2
        }
        Log.d(TAG, "setViewport drawWidth=$drawWidth drawHeight=$drawHeight x=$x y=$y")
        GLES30.glViewport(x, y, drawWidth, drawHeight)
    }

}