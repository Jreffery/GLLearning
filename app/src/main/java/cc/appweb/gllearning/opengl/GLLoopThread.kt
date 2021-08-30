package cc.appweb.gllearning.opengl

import android.util.Log
import java.util.concurrent.ArrayBlockingQueue

/**
 * OpenGLES使用的Loop线程
 * 由于EGL和GLES状态是与线程相关的，所以需要单独起一个线程
 *
 * */
class GLLoopThread : Thread() {

    // 退出标志位
    private var mStop = false

    // 任务队列
    private val mTaskList = ArrayBlockingQueue<Runnable>(16)

    companion object {
        private const val TAG = "GLLoopThread"
        private var threadNum = 0
    }

    /**
     * 向任务队列添加任务
     * */
    fun addTask(task: Runnable) {
        mTaskList.put(task)
    }

    /**
     * 退出任务循环
     * */
    fun stopLoop() {
        mTaskList.put {
            mStop = true
        }
    }

    override fun run() {
        name = "GLRender-${threadNum++}"
        Log.i(TAG, "LoopThread start")
        while (!mStop || !mTaskList.isEmpty()) {
            // 获取任务
            val runnable = mTaskList.take()
            Log.d(TAG, "LoopThread run one task")
            runnable?.run()
        }
        Log.i(TAG, "LoopThread end")
    }
}