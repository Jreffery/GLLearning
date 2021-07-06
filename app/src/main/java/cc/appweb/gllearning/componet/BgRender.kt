package cc.appweb.gllearning.componet

import java.nio.ByteBuffer

class BgRender {

    companion object {
        init {
            System.loadLibrary("glrender")
        }
    }

    private var mNativePtr: Long = 0

    fun getNativePtr():Long {
        return mNativePtr
    }

    /**
     * 创建native render
     *
     * @param ptr native对象指针，默认为0
     * @param width 图像宽
     * @param height 图像高
     * @param buffer DirectByteBuffer，图像数据
     * */
    external fun create(ptr: Long, width: Int, height: Int, buffer: ByteBuffer)

    /**
     * 开始渲染
     *
     * @param ptr native对象指针
     * */
    external fun draw(ptr: Long)

    /**
     * 获取绘制好的图像数据
     *
     * @param ptr native对象指针
     * @param buffer DirectByteBuffer，图像数据
     * */
    external fun getDrawRawData(ptr:Long, buffer: ByteBuffer)

    /**
     * 销毁native对象
     *
     * @param ptr
     * */
    external fun destroy(ptr: Long)

}