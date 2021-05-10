package cc.appweb.gllearning.util

import android.app.Application
import android.content.Context

object AppUtil {

    private lateinit var mApplication: Application

    fun setApplication(application: Application) {
        mApplication = application
    }

    fun getApplication(): Application {
        return mApplication
    }

    fun getContext(): Context {
        return mApplication
    }
}