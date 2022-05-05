package cc.appweb.gllearning.componet

import android.opengl.GLES30
import android.opengl.GLES31
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

/**
 * @description: 计算着色器也需要EGL
 * @date: 2022/5/5.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP_MR1)
class ComputeTestRender : CommonGLRender() {

    // FBO ID
    private var fboId: Int = -1

    // 程序 ID
    private var programId = -1

    // compute shader
    private var computeShaderId = -1

    private val computeShader by lazy {
        String.format(
            "#version 310 es                                         \n" +  // opengles3.1才支持compute shader
                "precision highp uimage2D;                            \n" + // 精度
                "layout(local_size_x = 10, local_size_y = 10) in;    \n" + // 定义本地工作组大小
                "layout(rgba8ui, binding = 0) uniform writeonly uimage2D output_image;    \n" + // 输出用Image
                "shared int increase;                                \n" + // 色值统计
                "void main()                                         \n" + // 主函数
                "{                                                   \n" + //
                "  increase = 0;                                   \n" + // 初始化
                "  barrier();                                      \n" + // 内存屏障
                "  atomicAdd(increase, 1);                        \n" + // 统计自增
                "  barrier();                                      \n" + // 内存屏障
//                "  int r = (increase >> 24) & 0xff;              \n" + // 提取r计数
//                "  int g = (increase >> 16) & 0xff;              \n" + // 提取g计数
//                "  int b = (increase >> 8) & 0xff;               \n" + // 提取b计数
//                "  int a = (increase >> 0) & 0xff;               \n" + // 提取a计数
                "  uvec4 data = uvec4(0,1,2,1);                    \n" + // 定义颜色
//                "  data.r = float(r);                            \n" + // 赋值r
//                "  data.g = float(g);                            \n" + // 赋值g
//                "  data.b = float(b);                            \n" + // 赋值b
//                "  data.a = float(a);                            \n" + // 赋值a
                "  ivec2 pos = ivec2(gl_GlobalInvocationID.xy);         \n" + // 获取全局工作组的运行id
                "  imageStore(output_image, pos.xy, data);       \n" + // 赋值
                "}"
        )
    }

    companion object {
        private const val TAG = "ComputeTestRender"
    }

    override fun onRenderInit() {

        // 创建程序
        programId = GLES30.glCreateProgram()
        Log.d(TAG, "glCreateProgram error=${GLES30.glGetError()} programId=${programId}")

        // 创建shader
        computeShaderId = GLES30.glCreateShader(GLES31.GL_COMPUTE_SHADER)
        Log.d(
            TAG, "glCreateShader compute error=${GLES30.glGetError()} shaderId=${computeShaderId}"
        )

        // 设置shader源码
        GLES30.glShaderSource(computeShaderId, computeShader)
        Log.d(TAG, "glShaderSource error=${GLES30.glGetError()}")

        // 编译shader
        GLES30.glCompileShader(computeShaderId)
        val status = IntArray(1)
        Log.d(TAG, "glCompileShader error=${GLES30.glGetError()}")
        GLES30.glGetShaderiv(computeShaderId, GLES30.GL_COMPILE_STATUS, status, 0)
        Log.d(TAG, "compile status=${status[0]} info=${GLES30.glGetShaderInfoLog(computeShaderId)}")

        // 绑定program和shader
        GLES30.glAttachShader(programId, computeShaderId)
        Log.d(TAG, "glAttachShader error=${GLES30.glGetError()}")

        // 链接
        GLES30.glLinkProgram(programId)
        Log.d(TAG, "glLinkProgram error=${GLES30.glGetError()}")

        // 创建FBO
        val fboArray = IntArray(1)
        GLES30.glGenFramebuffers(1, fboArray, 0)
        fboId = fboArray[0]
        Log.d(TAG, "glGenFramebuffers error=${GLES30.glGetError()} fboId=${fboId}")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        Log.d(TAG, "glBindFramebuffer error=${GLES30.glGetError()}")
    }

    override fun onRenderDestroy() {
        if (fboId != -1) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = -1
        }
        if (programId != -1) {
            GLES30.glDeleteProgram(programId)
            programId = -1
        }
        if (computeShaderId != -1) {
            GLES30.glDeleteShader(computeShaderId)
            computeShaderId = -1
        }
    }

    @MainThread
    fun test() {
        addTask {
            val textureArray = IntArray(1)
            GLES30.glGenTextures(1, textureArray, 0)
            var outputTextureId = textureArray[0] // 设置纹理属性

            setTexture2DAttributes(outputTextureId) {
                GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8UI, 10, 10)
                Log.d(TAG, "glTexStorage2D output1 error=${GLES30.glGetError()}")
            }
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                outputTextureId,
                0
            )
            Log.d(TAG, "glFramebufferTexture2D output1 error=${GLES30.glGetError()}")

            GLES30.glUseProgram(programId)
            Log.d(TAG, "glUseProgram error=${GLES30.glGetError()}")

            // 绑定缓冲
            GLES31.glBindImageTexture(
                0, outputTextureId, 0, false, 0, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA8UI
            )
            Log.d(TAG, "glBindImageTexture error=${GLES30.glGetError()}")

            // 开始计算
            GLES31.glDispatchCompute(1, 1, 1)
            Log.d(TAG, "glDispatchCompute error=${GLES31.glGetError()}")

            GLES30.glFinish()
            Log.d(
                TAG, "glFinish error=${GLES30.glGetError()}"
            )

            GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0)
            Log.d(TAG, "glReadBuffer error=${GLES30.glGetError()}")

            // 读取buffer
            val buffer = ByteBuffer.allocate(10 * 10 * 4)
            val ret = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_IMPLEMENTATION_COLOR_READ_FORMAT, ret, 0)
            Log.i(TAG, "GL_IMPLEMENTATION_COLOR_READ_FORMAT format=${ret[0]}")
            GLES30.glGetIntegerv(GLES30.GL_IMPLEMENTATION_COLOR_READ_TYPE, ret, 0)
            Log.i(TAG, "GL_IMPLEMENTATION_COLOR_READ_TYPE format=${ret[0]}")
            GLES30.glReadPixels(
                0, 0, 10, 10, GLES30.GL_RGBA_INTEGER, GLES30.GL_UNSIGNED_BYTE, buffer
            )
            
            Log.i(TAG, "0=${buffer[0]}")
            Log.i(TAG, "1=${buffer[1]}")
            Log.i(TAG, "2=${buffer[2]}")
            Log.i(TAG, "3=${buffer[3]}")
            Log.i(TAG, "4=${buffer[4]}")
            Log.i(TAG, "5=${buffer[5]}")
            Log.i(TAG, "6=${buffer[6]}")
            Log.i(TAG, "10*9+2=${buffer[10*9*4+2]}")
            
            Log.d(TAG, "glReadPixels error=${GLES30.glGetError()}")
        }
    }

}