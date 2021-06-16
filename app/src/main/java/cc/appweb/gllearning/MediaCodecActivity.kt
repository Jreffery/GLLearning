package cc.appweb.gllearning

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import cc.appweb.gllearning.databinding.ActivityMediaCodecBinding

class MediaCodecActivity : AppCompatActivity() {

    private lateinit var mActivityBinding: ActivityMediaCodecBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_codec)
        mActivityBinding = ActivityMediaCodecBinding.bind(findViewById(R.id.media_codec_container))
    }
}