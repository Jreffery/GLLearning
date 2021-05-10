package cc.appweb.gllearning

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import cc.appweb.gllearning.databinding.ActivityAudioRecordBinding

class AudioRecordActivity : AppCompatActivity() {

    private lateinit var mActivityBinding: ActivityAudioRecordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_record)

        mActivityBinding = ActivityAudioRecordBinding.bind(findViewById(R.id.audio_record_container))

    }
}