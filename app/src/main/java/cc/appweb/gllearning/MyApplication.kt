package cc.appweb.gllearning

import android.app.Application
import cc.appweb.gllearning.util.AppConfig
import cc.appweb.gllearning.util.AppUtil

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppUtil.setApplication(this)
        AppConfig.init(this)
    }
}