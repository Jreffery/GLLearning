package cc.appweb.gllearning.componet

import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

/**
 * 添加水印渲染器
 * */
@RequiresApi(VERSION_CODES.JELLY_BEAN_MR2)
class WatermarkRender(surfaceTexture: SurfaceTexture,
                      private val viewWidth: Int,
                      private val viewHeight: Int) :
    TextureViewRender(surfaceTexture, viewWidth, viewHeight) {

    // 水印纹理
    private var mWatermarkTexture: Int = -1

    // 顶点shader
    private var mWatermarkVertexShader: Int = -1

    // 片元shader
    private var mWatermarkFragmentShader: Int = -1

    // 水印program
    private var mWatermarkProgram: Int = -1

    // 水印的尺寸属性
    private val mWatermarkOption = WatermarkOption()

    companion object {
        private const val TAG = "WatermarkRender"

        private const val vertexShader = "#version 300 es                            \n" + // 声明使用OpenGLES 3.0
            "layout(location = 0) in vec4 a_position;   \n" + // 声明输入四维向量
            "layout(location = 1) in vec2 a_texCoord;   \n" + // 声明输入二维向量
            "out vec2 v_texCoord;                       \n" + // 声明输出二维向量，纹理坐标
            "void main()                                \n" +
            "{                                          \n" +
            "   gl_Position = a_position;               \n" + // 内建变量赋值，不需要变换，gl_Position描述三维空间里变换后的位置
            "   v_texCoord = a_texCoord;                \n" + // 输出向量赋值，纹理的坐标
            "}                                          \n"


        // 用于FBO渲染的片段着色器shader，取每个像素的灰度值
        private const val fragmentShader = "#version 300 es                            \n" +
            "precision mediump float;                   \n" + // 设置默认的精度限定符
            "in vec2 v_texCoord;                        \n" + // 导入纹理坐标，描述片段
            "layout(location = 0) out vec4 outColor;    \n" + // 提供片段着色器输出变量的声明，这将是传递到下一阶段的颜色
            "uniform sampler2D s_TextureMap;            \n" + // 声明GL_TEXTURE_2D绑定的空间变量，取出纹理数据
            "void main()                                \n" + "{                                          \n" +
            "    vec4 tempColor = texture(s_TextureMap, v_texCoord);   \n" + // 通过纹理和纹理坐标采样颜色值
            "    outColor = tempColor;" +  // android Bitmap.createBitmap color 里要求的排列是 ARGB （高位->低位），需要将b/r替换一下输出
            "}"
    }

    /**
     * 设置水印
     * */
    fun setWatermark(byteBuffer: ByteBuffer, width: Int, height: Int, x: Int, y: Int) {
        Log.i(TAG, "setWatermark bufferSize=${byteBuffer.limit()} position=${byteBuffer.position()} width=${width} height=${height} x=${x} y=${y}")
        mWatermarkOption.apply {
            watermarkBuffer = byteBuffer
            waterMarkWidth = width
            watermarkHeight = height
            watermarkX = x
            watermarkY = y
        }
    }

    override fun onRenderInit() {
        Log.i(TAG, "onRenderInit")
        super.onRenderInit() // 初始化水印的shader program
        // 创建水印纹理
        val texArray = IntArray(1)
        GLES30.glGenTextures(1, texArray, 0)
        mWatermarkTexture = texArray[0] // 设置纹理属性
        setTexture2DAttributes(mWatermarkTexture)
        Log.i(TAG, "watermarkTexture=${mWatermarkTexture} error=${GLES30.glGetError()}")

        // 创建水印顶点shader
        mWatermarkVertexShader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
        Log.i(TAG, "createShader vertex error=${GLES30.glGetError()}")
        GLES30.glShaderSource(mWatermarkVertexShader, vertexShader)
        Log.i(TAG, "glShaderSource vertex error=${GLES30.glGetError()}")
        GLES30.glCompileShader(mWatermarkVertexShader)
        Log.i(TAG, "glCompileShader vertex error=${GLES30.glGetError()}")

        // 创建水印片元shader
        mWatermarkFragmentShader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
        Log.i(TAG, "createShader fragment error=${GLES30.glGetError()}")
        GLES30.glShaderSource(mWatermarkFragmentShader, fragmentShader)
        Log.i(TAG, "glShaderSource fragment error=${GLES30.glGetError()}")
        GLES30.glCompileShader(mWatermarkFragmentShader)
        Log.i(TAG, "glCompileShader fragment error=${GLES30.glGetError()}")

        mWatermarkProgram = GLES30.glCreateProgram()
        Log.i(TAG, "create program error=${GLES30.glGetError()}")
        GLES30.glAttachShader(mWatermarkProgram, mWatermarkVertexShader)
        Log.i(TAG, "glAttach vertex error=${GLES30.glGetError()}")
        GLES30.glAttachShader(mWatermarkProgram, mWatermarkFragmentShader)
        Log.i(TAG, "glAttach fragment error=${GLES30.glGetError()}")
        GLES30.glLinkProgram(mWatermarkProgram)
        Log.i(TAG, "glLinkProgram error=${GLES30.glGetError()}")
    }

    override fun onRenderDestroy() {
        Log.i(TAG, "onRenderDestroy")
        super.onRenderDestroy()
        if (mWatermarkTexture != -1) {
            GLES30.glDeleteTextures(1, IntArray(mWatermarkTexture), 0)
            mWatermarkTexture = -1
        }
        if (mWatermarkProgram != -1) {
            GLES30.glDeleteProgram(mWatermarkProgram)
            mWatermarkProgram = -1
        }
        if (mWatermarkVertexShader != -1) {
            GLES30.glDeleteShader(mWatermarkVertexShader)
            mWatermarkVertexShader = -1
        }
        if (mWatermarkFragmentShader != -1) {
            GLES30.glDeleteShader(mWatermarkFragmentShader)
            mWatermarkFragmentShader = -1
        }
    }

    override fun finishViewport(x: Int, y: Int, drawWidth: Int, drawHeight: Int) {
        Log.i(TAG, "finishViewport")
        super.finishViewport(x, y, drawWidth, drawHeight) // 记录绘制内容的区域，计算水印的实际位置
        mWatermarkOption.x = x + mWatermarkOption.watermarkX
        mWatermarkOption.y = y + mWatermarkOption.watermarkY
        mWatermarkOption.drawWidth = mWatermarkOption.waterMarkWidth
        mWatermarkOption.drawHeight = mWatermarkOption.watermarkHeight
    }

    override fun finishRender() {
        Log.i(TAG, "finishRender")
        mWatermarkOption.watermarkBuffer?.let {
            // 框定渲染区域
            GLES30.glViewport(mWatermarkOption.x, mWatermarkOption.y, mWatermarkOption.drawWidth, mWatermarkOption.drawHeight)
            Log.i(TAG, "glViewport error=${GLES30.glGetError()} x=${mWatermarkOption.x} y=${mWatermarkOption.y} " +
                "w=${mWatermarkOption.drawWidth} h=${mWatermarkOption.watermarkHeight}")

            // 使用程序
            GLES30.glUseProgram(mWatermarkProgram)
            Log.i(TAG, "useProgram error=${GLES30.glGetError()}")

            // 获取纹理取样器
            val texLoc = GLES30.glGetUniformLocation(mWatermarkProgram, "s_TextureMap")
            Log.i(TAG, "glGetUniformLocation loc=${texLoc} error=${GLES30.glGetError()}")
            // 激活纹理
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + texLoc)
            Log.i(TAG, "glActiveTexture error=${GLES30.glGetError()}")
            // 绑定纹理
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mWatermarkTexture)
            Log.i(TAG, "glBindTexture error=${GLES30.glGetError()}")
            // 赋值到程序中
            GLES30.glUniform1i(texLoc, texLoc)
            Log.i(TAG, "glUniform error=${GLES30.glGetError()}")
            // 上传纹理
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, mWatermarkOption.waterMarkWidth,
                mWatermarkOption.watermarkHeight, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, it)
            Log.i(TAG, "glTexImage2D error=${GLES30.glGetError()}")

            // 激活 a_position
            GLES30.glEnableVertexAttribArray(0)
            Log.i(TAG, "glEnableVertexAttribArray a_position error=${GLES30.glGetError()}")
            // 传值 a_position
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, mVertexCoorsBuffer)
            Log.i(TAG, "glVertexAttribPointer a_position error=${GLES30.glGetError()}")

            // 激活 a_texCoord
            GLES30.glEnableVertexAttribArray(1)
            Log.i(TAG, "glEnableVertexAttribArray a_texCoord error=${GLES30.glGetError()}")
            // 传值 a_texCoord
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 2 * 4, mTextureCoorsBuffer)
            Log.i(TAG, "glVertexAttribPointer a_texCoord error=${GLES30.glGetError()}")

            // draw
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, mIndicesBuffer)
            Log.i(TAG, "glDrawElements error=${GLES30.glGetError()}")

            GLES30.glFlush()
        }
        super.finishRender()
    }

}

/**
 * 渲染水印的位置属性
 * @param x 经计算真实在绘制区域的x属性
 * @param y 经计算真实在绘制区域的y属性
 * @param drawWidth 经计算真实在绘制区域的width属性
 * @param drawHeight 经计算真实在绘制区域的height属性
 * @param watermarkX 相对画面内容的水印的x属性
 * @param watermarkY 相对画面内容的水印的y属性
 * @param waterMarkWidth 相对画面内容的水印的width属性
 * @param watermarkHeight 相对画面内容的水印的height属性
 * @param watermarkBuffer 水印
 * */
private data class WatermarkOption(var x: Int = 0,
                                   var y: Int = 0,
                                   var drawWidth: Int = 0,
                                   var drawHeight: Int = 0,
                                   var watermarkX: Int = 0,
                                   var watermarkY: Int = 0,
                                   var waterMarkWidth: Int = 0,
                                   var watermarkHeight: Int = 0,
                                   var watermarkBuffer: ByteBuffer? = null)