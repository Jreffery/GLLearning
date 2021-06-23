package cc.appweb.gllearning.util

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

object AppUtil {

    private lateinit var mApplication: Application
    // 主线程回调
    private val mHandler = Handler(Looper.getMainLooper())
    // 工作线程池
    private lateinit var mThreadPoolExecutor: ExecutorService

    fun setApplication(application: Application) {
        mApplication = application
        mThreadPoolExecutor = Executors.newScheduledThreadPool(4)
    }

    fun getApplication(): Application {
        return mApplication
    }

    fun getContext(): Context {
        return mApplication
    }

    fun runOnUIThread(action: ()->Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.invoke()
        } else {
            mHandler.post {
                action.invoke()
            }
        }
    }

    fun runOnWorkThread(action: () -> Unit) {
        mThreadPoolExecutor.submit(action)
    }

}