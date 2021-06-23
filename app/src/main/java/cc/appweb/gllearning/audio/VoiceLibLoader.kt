package cc.appweb.gllearning.audio

/**
 * 加载Voice库
 * */
object VoiceLibLoader {

    private var mIsLoad = false

    @Synchronized
    fun tryLoad() {
        if (!mIsLoad) {
            System.loadLibrary("voice")
            mIsLoad = true
        }
    }

}
