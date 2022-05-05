package cc.appweb.gllearning.componet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES30
import android.opengl.GLES31
import android.os.Build.VERSION_CODES
import android.util.Log
import android.view.View
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import cc.appweb.gllearning.entity.BlankDetectResult
import java.nio.ByteBuffer

/**
 * @description: 计算着色器也需要EGL
 * @date: 2022/5/5.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP_MR1)
class ComputeRender : CommonGLRender() {

    private lateinit var workGroupCount: WorkGroupLimit
    private lateinit var workGroupSize: WorkGroupLimit
    private var workGroupInvocations: Int = 0
    private val computeGrid by lazy {
        if (Gird_20x20.size <= workGroupSize.x && Gird_20x20.size <= workGroupSize.y && Gird_20x20.cnt <= workGroupInvocations) {
            Gird_20x20
        } else if (Gird_18x18.size <= workGroupSize.x && Gird_18x18.size <= workGroupSize.y && Gird_18x18.cnt <= workGroupInvocations) {
            Gird_18x18
        } else if (Gird_16x16.size <= workGroupSize.x && Gird_16x16.size <= workGroupSize.y && Gird_16x16.cnt <= workGroupInvocations) {
            Gird_16x16
        } else if (Gird_12x12.size <= workGroupSize.x && Gird_12x12.size <= workGroupSize.y && Gird_12x12.cnt <= workGroupInvocations) {
            Gird_12x12
        } else {
            Gird_10x10
        }
    }

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
                "layout(local_size_x = %d, local_size_y = %d) in;    \n" + // 定义本地工作组大小
                "layout(rgba8ui, binding = 0) uniform readonly uimage2D input_image;    \n" + // 输出用Image
                "layout(rgba8ui, binding = 1) uniform writeonly uimage2D output_image;    \n" + // 输出用Image
                "uniform int computeType;                            \n" + // 计算类型
                "uniform int width;                                  \n" + // 原图宽度
                "uniform int startHeight;                            \n" + // 原图检测起始高度
                "uniform int endHeight;                              \n" + // 原图检测结束高度
                "shared int increase;                                \n" + // 色值统计
                "void main()                                         \n" + // 主函数
                "{                                                   \n" + //
                "  increase = 0;                                   \n" + // 初始化
                "  barrier();                                      \n" + // 内存屏障
                "  ivec2 pos = ivec2(gl_GlobalInvocationID.xy);  \n" + // 输入的坐标
                "  if (pos.x < width && pos.y < (endHeight-startHeight)) { \n" + // 框定计算范围
                "    uvec4 pixel = imageLoad(input_image, ivec2(pos.x, pos.y+startHeight));     \n" + // 提取像素
                "    if (computeType == 1) {                       \n" + // 颜色识别
                "      if (pixel.r == uint(255) && pixel.g == uint(255) && pixel.b == uint(255)) { \n" + // 判断白色
                "        atomicAdd(increase, 1);                     \n" + // 统计自增
                "      }                                             \n" + //
                "    } else {                                        \n" + //
                "      int cnt = 0;                                  \n" + // 计数统计
                "      cnt |= (int(pixel.r) & 0xff) << 24;           \n" + // 红色
                "      cnt |= (int(pixel.g) & 0xff) << 16;           \n" + // 绿色
                "      cnt |= (int(pixel.b) & 0xff) << 8;            \n" + // 蓝色
                "      cnt |= (int(pixel.a) & 0xff) << 0;            \n" + // 透明度
                "      atomicAdd(increase, cnt);                     \n" + // 
                "    }                                               \n" + //
                "  }                                               \n" + //
                "  barrier();                                      \n" + // 内存屏障
                "  if (gl_LocalInvocationID.x == uint(0) && gl_LocalInvocationID.y == uint(0)) { \n" + // 只赋值一次
                "    int r = (increase >> 24) & 0xff;              \n" + // 提取r计数
                "    int g = (increase >> 16) & 0xff;              \n" + // 提取g计数
                "    int b = (increase >> 8) & 0xff;               \n" + // 提取b计数
                "    int a = (increase >> 0) & 0xff;               \n" + // 提取a计数
                "    uvec4 data = uvec4(r,g,b,a);                    \n" + // 定义颜色
                "    ivec2 pos = ivec2(gl_WorkGroupID.xy);         \n" + // 获取全局工作组的运行id
                "    imageStore(output_image, pos.xy, data);       \n" + // 赋值
                "  }                                               \n" + //
                "}", computeGrid.size, computeGrid.size
        )
    }

    companion object {
        private const val TAG = "ComputeRender"

        private val Gird_10x10 = GirdData(10, 10 * 10)
        private val Gird_12x12 = GirdData(12, 12 * 12)
        private val Gird_16x16 = GirdData(16, 16 * 16)
        private val Gird_18x18 = GirdData(18, 18 * 18)
        private val Gird_20x20 = GirdData(20, 20 * 20)

    }

    override fun onRenderInit() { // 获取全局工作组最大大小
        val workGroup = IntArray(3) // 获取x
        GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0, workGroup, 0) // 获取y
        GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1, workGroup, 1) // 获取z
        GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2, workGroup, 2)
        workGroupCount = WorkGroupLimit(workGroup[0], workGroup[1], workGroup[2])
        Log.i(
            TAG,
            "WORK_GROUP_COUNT x=${workGroupCount.x} y=${workGroupCount.y} z=${workGroupCount.z}"
        )

        // 获取局部工作组的最大大小
        // 获取x
        GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0, workGroup, 0) // 获取y
        GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 1, workGroup, 1) // 获取z
        GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 2, workGroup, 2)
        workGroupSize = WorkGroupLimit(workGroup[0], workGroup[1], workGroup[2])
        Log.i(
            TAG, "WORK_GROUP_SIZE x=${workGroupCount.x} y=${workGroupCount.y} z=${workGroupCount.z}"
        )

        // 局部工作工作组的最大单位数
        val workGroupInvocation = IntArray(1)
        GLES31.glGetIntegerv(GLES31.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, workGroupInvocation, 0)
        workGroupInvocations = workGroupInvocation[0]
        Log.i(TAG, "WORK_GROUP_INVOCATIONS $workGroupInvocations")

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

    /**
     * 检测view渲染是否纯色
     * @param view 待检测的View
     * @param startY 开始检测的y坐标，[0, 100)
     * @param endY 结束检测的y坐标，(0, 100]
     * */
    @MainThread
    fun detectBlank(
        view: View,
        startY: Int,
        endY: Int,
        resultClosure: ((BlankDetectResult) -> Unit)? = null
    ) {
        val start = System.currentTimeMillis()
        val w = view.width
        val h = view.height
        val bitmap = Bitmap.createBitmap(
            view.context.resources.displayMetrics, w, h, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        addTask {
            val byte = ByteBuffer.allocateDirect(w * h * 4)
            bitmap.copyPixelsToBuffer(byte)
            byte.flip()

            val textureArray = IntArray(2)
            GLES30.glGenTextures(2, textureArray, 0)
            var inputTextureId = textureArray[0]
            var outputTextureId1 = textureArray[1] // 设置纹理属性
            setTexture2DAttributes(inputTextureId) {
                GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 8, GLES30.GL_RGBA8UI, w, h)
                Log.d(TAG, "glTexStorage2D input error=${GLES30.glGetError()}") // 上传纹理
                GLES30.glTexSubImage2D(
                    GLES30.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    w,
                    h,
                    GLES30.GL_RGBA_INTEGER,
                    GLES30.GL_UNSIGNED_BYTE,
                    byte
                )
                Log.d(TAG, "glTexSubImage2D input error=${GLES30.glGetError()}")
            }

            // 绑定帧缓冲
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                inputTextureId,
                0
            )

            val startYInt = h * startY / 100
            val endYInt = h * endY / 100
            val diffHeight = endYInt - startYInt

            val outputW =
                if (w % computeGrid.size == 0) w / computeGrid.size else w / computeGrid.size + 1
            val outputH =
                if (diffHeight % computeGrid.size == 0) diffHeight / computeGrid.size else diffHeight / computeGrid.size + 1

            setTexture2DAttributes(outputTextureId1) {
                GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8UI, outputW, outputH)
                Log.d(TAG, "glTexStorage2D output1 error=${GLES30.glGetError()}")
            }
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT1,
                GLES30.GL_TEXTURE_2D,
                outputTextureId1,
                0
            )
            Log.d(TAG, "glFramebufferTexture2D output1 error=${GLES30.glGetError()}")

            compute(inputTextureId, outputTextureId1, 1, w, startYInt, endYInt, outputW, outputH)

            val allCnt = diffHeight * w
            if (outputW * outputH < computeGrid.cnt) { // 绑定需读取的buffer空间
                GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT1)
                Log.d(TAG, "glReadBuffer error=${GLES30.glGetError()}")

                // 读取buffer
                val buffer = ByteBuffer.allocate(outputW * outputH * 4)
                GLES30.glReadPixels(
                    0, 0, outputW, outputH, GLES30.GL_RGBA_INTEGER, GLES30.GL_UNSIGNED_BYTE, buffer
                )
                Log.d(TAG, "glReadPixels error=${GLES30.glGetError()}")

                val colorCnt = countColor(buffer)
                val blankDetectResult = BlankDetectResult(1, colorCnt * 100 / allCnt, "#FFFFFF")
                Log.d(TAG, "DetectResult=$blankDetectResult")
                resultClosure?.invoke(blankDetectResult)
            } else {
                GLES30.glGenTextures(1, textureArray, 0)
                val outputTextureId2 = textureArray[0]

                val w2 =
                    if (outputW % computeGrid.size == 0) outputW / computeGrid.size else outputW / computeGrid.size + 1
                val h2 =
                    if (outputH % computeGrid.size == 0) outputH / computeGrid.size else outputH / computeGrid.size + 1

                setTexture2DAttributes(outputTextureId2) {
                    GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8UI, w2, h2)
                    Log.d(TAG, "glTexStorage2D output2 error=${GLES30.glGetError()}")
                }
                GLES30.glFramebufferTexture2D(
                    GLES30.GL_FRAMEBUFFER,
                    GLES30.GL_COLOR_ATTACHMENT2,
                    GLES30.GL_TEXTURE_2D,
                    outputTextureId2,
                    0
                )
                Log.d(TAG, "glFramebufferTexture2D output2 error=${GLES30.glGetError()}")
                compute(outputTextureId1, outputTextureId2, 2, outputW, 0, outputH, w2, h2)

                // 绑定需读取的buffer空间
                GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT2)
                Log.d(TAG, "glReadBuffer error=${GLES30.glGetError()}")

                // 读取buffer
                val buffer = ByteBuffer.allocate(w2 * h2 * 4)
                GLES30.glReadPixels(
                    0, 0, w2, h2, GLES30.GL_RGBA_INTEGER, GLES30.GL_UNSIGNED_BYTE, buffer
                )
                Log.d(TAG, "glReadPixels error=${GLES30.glGetError()}")

                val colorCnt = countColor(buffer)
                val blankDetectResult = BlankDetectResult(1, colorCnt * 100 / allCnt, "#FFFFFF")
                Log.d(TAG, "DetectResult=$blankDetectResult")
                resultClosure?.invoke(blankDetectResult)

                if (outputTextureId2 != -1) {
                    GLES30.glDeleteTextures(1, intArrayOf(outputTextureId2), 0)
                    Log.d(TAG, "glDeleteTextures output2 error=${GLES30.glGetError()}")
                }
            }

            if (inputTextureId != -1) {
                GLES30.glDeleteTextures(1, intArrayOf(inputTextureId), 0)
                Log.d(TAG, "glDeleteTextures input error=${GLES30.glGetError()}")
            }
            if (outputTextureId1 != -1) {
                GLES30.glDeleteTextures(1, intArrayOf(outputTextureId1), 0)
                Log.d(TAG, "glDeleteTextures output1 error=${GLES30.glGetError()}")
            }

            val cost = System.currentTimeMillis() - start
            Log.d(TAG, "compute cost $cost ms")
        }
    }

    private fun countColor(byteBuffer: ByteBuffer): Int {
        val intBuffer = byteBuffer.asIntBuffer()
        var cnt = 0
        for (i in 0 until intBuffer.capacity()) {
            cnt += intBuffer.get(i)
        }
        return cnt
    }

    /**
     * @param inputTex 输入纹理
     * @param outputTex 输出纹理
     * @param computeType 计算类型
     * @param inputW 输入纹理宽度
     * @param startYInt 输入纹理开始的高度
     * @param endYInt 输入纹理结束的高度
     * @param outputW 输出纹理的宽度
     * @param outputH 输出纹理的高度
     * */
    private fun compute(
        inputTex: Int,
        outputTex: Int,
        computeType: Int,
        inputW: Int,
        startYInt: Int,
        endYInt: Int,
        outputW: Int,
        outputH: Int
    ) { 
        
        // 使用program
        Log.d(
            TAG,
            "compute inputTex=$inputTex outputTex=$outputTex computeType=$computeType inputW=$inputW startYInt=$startYInt endYInt=$endYInt outputW=$outputW outputH=$outputH"
        )

        GLES30.glUseProgram(programId)
        Log.d(TAG, "glUseProgram error=${GLES30.glGetError()}")

        // 绑定缓冲
        GLES31.glBindImageTexture(
            0, inputTex, 0, false, 0, GLES31.GL_READ_ONLY, GLES30.GL_RGBA8UI
        )
        Log.d(TAG, "glBindImageTexture input error=${GLES30.glGetError()}")

        // 绑定缓冲
        GLES31.glBindImageTexture(
            1, outputTex, 0, false, 0, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA8UI
        )
        Log.d(TAG, "glBindImageTexture error=${GLES30.glGetError()}")

        // 赋值type
        val typeLoc = GLES30.glGetUniformLocation(programId, "computeType")
        Log.d(TAG, "glGetUniformLocation error=${GLES30.glGetError()} typeLoc=$typeLoc")
        GLES30.glUniform1i(typeLoc, computeType)
        Log.d(TAG, "glUniform1i error=${GLES30.glGetError()}")

        // 赋值width
        val widthLoc = GLES30.glGetUniformLocation(programId, "width")
        Log.d(TAG, "glGetUniformLocation error=${GLES30.glGetError()} widthLoc=$widthLoc")
        GLES30.glUniform1i(widthLoc, inputW)
        Log.d(TAG, "glUniform1i error=${GLES30.glGetError()}")

        // 赋值startHeight
        val startHeightLoc = GLES30.glGetUniformLocation(programId, "startHeight")
        Log.d(TAG, "glGetUniformLocation error=${GLES30.glGetError()} startHeightLoc=$startYInt")
        GLES30.glUniform1i(startHeightLoc, startYInt)
        Log.d(TAG, "glUniform1i error=${GLES30.glGetError()}")

        // 赋值endHeight
        val endHeightLoc = GLES30.glGetUniformLocation(programId, "endHeight")
        Log.d(TAG, "glGetUniformLocation error=${GLES30.glGetError()} endHeightLoc=$endHeightLoc")
        GLES30.glUniform1i(endHeightLoc, endYInt)
        Log.d(TAG, "glUniform1i error=${GLES30.glGetError()}")

        // 开始计算
        GLES31.glDispatchCompute(outputW, outputH, 1)
        Log.d(TAG, "glDispatchCompute error=${GLES31.glGetError()}")

        GLES30.glFinish()
        Log.d(
            TAG,
            "glFinish error=${GLES30.glGetError()}"
        )
    }
}

/**
 * 计算网格大小
 * */
data class GirdData(val size: Int, val cnt: Int)

/**
 * 工作组大小限制
 * */
data class WorkGroupLimit(val x: Int, val y: Int, val z: Int)