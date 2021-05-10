package cc.appweb.gllearning.componet

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * 纹理绘画器
 * */
class TextureDrawer(private val mTextureId: Int) {

    companion object {

        // 顶点着色器代码
        @JvmStatic
        val vertexShaderCode =
                "attribute vec4 vPosition;" +
                        "attribute vec2 inputTextureCoordinate;\n" +
                        "varying vec2 textureCoordinate;\n" +
                        "void main()\n" +
                        "{\n" +
                        "gl_Position = vPosition;\n" +
                        "textureCoordinate = inputTextureCoordinate;\n" +
                        "}"

        // 片元着色器代码
        @JvmStatic
        val fragmentShaderCode =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 textureCoordinate;\n" +
                        "uniform samplerExternalOES s_texture;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = texture2D(s_texture, textureCoordinate);\n" +
                        "}"


        // 顶点坐标数组，描述窗口的坐标
        @JvmStatic
        val squareCoordinates = floatArrayOf(
                -1.0f,  1.0f,
                -1.0f, -1.0f,
                1.0f, -1.0f,
                1.0f,  1.0f)

        // 纹理坐标数组，描述纹理的坐标，旋转0度时的坐标
        @JvmStatic
        val textureVertices = floatArrayOf(
                1.0f, 1.0f,
                1.0f, 0.0f,
                0.0f, 0.0f,
                0.0f, 1.0f,)

        // 顶点绘制顺序
        @JvmStatic
        private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)

        // 每个顶点的坐标个数
        @JvmStatic
        private val COORDINATES_PER_VERTEX = 2

        // 每个顶点的步长
        @JvmStatic
        private val VERTEX_STRIDE = COORDINATES_PER_VERTEX * 4

    }

    // 顶点坐标buffer，用于与GPU交互
    private var mVertexBuffer: FloatBuffer

    // 顶点绘制坐标顺序buffer，用于与GPU交互
    private var mVertexDrawOrderBuffer: ShortBuffer

    // 纹理坐标buffer，用于与GPU交互
    @Volatile
    private var mTextureVerticesBuffer: FloatBuffer

    // 绘制程序
    private var mDrawProgram: Int = 0


    init {
        // 创建保存顶点坐标的ByteBuffer
        var tmpBuffer = ByteBuffer.allocateDirect(squareCoordinates.size * 4)
        // 大小端跟随系统
        tmpBuffer.order(ByteOrder.nativeOrder())
        // 将数据填充到mVertexBuffer
        mVertexBuffer = tmpBuffer.asFloatBuffer()
        mVertexBuffer.put(squareCoordinates)
        mVertexBuffer.position(0)

        //顶点绘制顺序
        tmpBuffer = ByteBuffer.allocateDirect(drawOrder.size * 2)
        tmpBuffer.order(ByteOrder.nativeOrder())
        mVertexDrawOrderBuffer = tmpBuffer.asShortBuffer()
        mVertexDrawOrderBuffer.put(drawOrder)
        mVertexDrawOrderBuffer.position(0)

        // 纹理坐标
        tmpBuffer = ByteBuffer.allocateDirect(textureVertices.size * 4)
        tmpBuffer.order(ByteOrder.nativeOrder())
        val tmpTextureBuffer = tmpBuffer.asFloatBuffer()
        tmpTextureBuffer.put(textureVertices)
        tmpTextureBuffer.position(0)
        mTextureVerticesBuffer = tmpTextureBuffer


        // 编译顶点着色器
        val vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(vertexShader, vertexShaderCode)
        GLES20.glCompileShader(vertexShader)
        // 编译片元着色器
        val fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(fragmentShader, fragmentShaderCode)
        GLES20.glCompileShader(fragmentShader)

        // 创建绘制程序，并绑定
        mDrawProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(mDrawProgram, vertexShader)
        GLES20.glAttachShader(mDrawProgram, fragmentShader)

        // 链接shader生成可执行文件
        GLES20.glLinkProgram(mDrawProgram)
    }

    /**
     * 执行画图逻辑
     * */
    fun draw() {
        // 使用绘制器程序
        GLES20.glUseProgram(mDrawProgram)
        // 使用纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        // 调用SurfaceTexture.updateTexImage会隐式将mTextureId绑定到GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId)
        // 获取着色器程序的顶点坐标变量
        val vPosition = GLES20.glGetAttribLocation(mDrawProgram, "vPosition")
        // 设置变量属性可用
        GLES20.glEnableVertexAttribArray(vPosition)
        // 指定属性数组的数据格式和位置
        // 参数1：顶点属性
        // 参数2：顶点属性的大小
        // 参数3：指定数据的类型
        // 参数4：数据是否被标准化，如果设置为GL_TRUE，所有数据都会被映射到0-1之间
        // 参数5：连续的顶点属性组之间的间隔
        // 参数6：数组指针
        GLES20.glVertexAttribPointer(vPosition, COORDINATES_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mVertexBuffer)

        //纹理坐标
        val textureCoordHandle = GLES20.glGetAttribLocation(mDrawProgram, "inputTextureCoordinate")
        GLES20.glEnableVertexAttribArray(textureCoordHandle)
        GLES20.glVertexAttribPointer(textureCoordHandle, COORDINATES_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mTextureVerticesBuffer)

        // 绘制
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, mVertexDrawOrderBuffer)

        // 结束
        // 关闭顶点属性
        GLES20.glDisableVertexAttribArray(vPosition)
        // 关闭纹理属性
        GLES20.glDisableVertexAttribArray(textureCoordHandle)
    }

    /**
     * @param orientation 摄像头旋转的角度
     *
     * */
    fun setPreviewOrientation(orientation: Int, mirror: Boolean) {
        val startIndex: Int
        val mirrorStartIndex: Int
        when (orientation) {
            0 -> {
                startIndex = 0 * 2
                mirrorStartIndex = 0
            }
            90 -> {
                startIndex = 1 * 2
                mirrorStartIndex = 1
            }
            180 -> {
                startIndex = 2 * 2
                mirrorStartIndex = 2
            }
            270 -> {
                startIndex = 3 * 2
                mirrorStartIndex = 3
            }
            else -> {
                return
            }
        }

        // 预览旋转，调整纹理坐标的绘制顺序
        val tmpTexture = FloatArray(8)
        System.arraycopy(textureVertices, startIndex, tmpTexture, 0, textureVertices.size - startIndex)
        System.arraycopy(textureVertices, 0, tmpTexture, textureVertices.size - startIndex, startIndex)

        // 预览镜像
        if (mirror) {
            val mirror1 = mirrorStartIndex % 4
            val mirror2 = (mirrorStartIndex + 1) % 4
            val mirror3 = (mirrorStartIndex + 2) % 4
            val mirror4 = (mirrorStartIndex + 3) % 4

            changeValue(tmpTexture, mirror1*2, mirror2*2)
            changeValue(tmpTexture, mirror1*2+1, mirror2*2+1)
            changeValue(tmpTexture, mirror3*2, mirror4*2)
            changeValue(tmpTexture, mirror3*2+1, mirror4*2+1)
        }

        val tmpBuffer = ByteBuffer.allocateDirect(tmpTexture.size * 4)
        tmpBuffer.order(ByteOrder.nativeOrder())
        val tmpTextureBuffer = tmpBuffer.asFloatBuffer()
        tmpTextureBuffer.put(tmpTexture)
        tmpTextureBuffer.position(0)
        mTextureVerticesBuffer = tmpTextureBuffer
    }

    private fun changeValue(array: FloatArray, index1: Int, index2: Int) {
        val tmp = array[index1]
        array[index1] = array[index2]
        array[index2] = tmp
    }

}