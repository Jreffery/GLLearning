package cc.appweb.gllearning.componet

import java.nio.ByteBuffer

class BgRender {

    companion object {
        init {
            System.loadLibrary("glrender")
        }

        const val ROTATE_0 = 0
        const val ROTATE_90 = 1
        const val ROTATE_180 = 2
        const val ROTATE_270 = 3

        const val MIRROR_NONE = 0
        const val MIRROR_HORIZONTAL = 1
        const val MIRROR_VERTICAL = 2
    }

    private var mNativePtr: Long = 0

    fun getNativePtr(): Long {
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
    external fun getDrawRawData(ptr: Long, buffer: ByteBuffer)

    /**
     * 销毁native对象
     *
     * @param ptr
     * */
    external fun destroy(ptr: Long)

    /**
     * 设置旋转角度
     * @param ptr native对象指针
     * @param type 旋转角度 {@see ROTATE_0}
     * */
    external fun setRotate(ptr: Long, type: Int)

    /**
     * 设置镜像类型
     * @param ptr native对象指针
     * @param type 镜像类型
     * */
    external fun setMirrorType(ptr: Long, type: Int);

}