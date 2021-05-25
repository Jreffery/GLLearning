package cc.appweb.gllearning.util

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper

object AppUtil {

    private lateinit var mApplication: Application
    // 主线程回调
    private val mHandler = Handler(Looper.getMainLooper())

    fun setApplication(application: Application) {
        mApplication = application
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

}