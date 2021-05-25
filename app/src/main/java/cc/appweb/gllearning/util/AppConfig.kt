package cc.appweb.gllearning.util

import android.content.Context
import android.content.SharedPreferences

object AppConfig {

    const val KEY_NATIVE_AUDIO_PLAYER_SWITCH = "key_native_audio_player_switch"

    private lateinit var mPerf: SharedPreferences

    fun init(context: Context) {
        mPerf = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
    }

    fun putInt(key: String, value: Int) {
        mPerf.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return mPerf.getInt(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        mPerf.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return mPerf.getLong(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        mPerf.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return mPerf.getBoolean(key, defaultValue)
    }

    fun putString(key: String, value: String) {
        mPerf.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String?): String? {
        return mPerf.getString(key, defaultValue)
    }

}