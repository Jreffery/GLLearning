package cc.appweb.gllearning

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cc.appweb.gllearning.databinding.ActivityMainBinding

/**
 * 功能列表，通过这个Activity可以进入各个API演示使用
 *
 * */
class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var mActivityBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mActivityBinding = ActivityMainBinding.bind(findViewById(R.id.activity_main_container))
        mActivityBinding.camera1Surfaceview.setOnClickListener(this)
        mActivityBinding.camera1Glsurfaceview.setOnClickListener(this)
        mActivityBinding.camerax.setOnClickListener(this)
        mActivityBinding.camera1Textureview.setOnClickListener(this)
        mActivityBinding.camera2Textureview.setOnClickListener(this)
        mActivityBinding.audioRecord.setOnClickListener(this)
        mActivityBinding.playAudio.setOnClickListener(this)
        mActivityBinding.videoCodec.setOnClickListener(this)
        mActivityBinding.audioCodec.setOnClickListener(this)
        var permission = arrayOfNulls<String>(2)
        var index = 0
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permission[index++] = Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permission[index++] = Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (index != 0) {
            if (index == 1) {
                permission = permission.copyOf(index)
            }
            ActivityCompat.requestPermissions(this, permission, 0)
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            mActivityBinding.camera1Surfaceview -> {
                startActivity(Intent(this, API1SurfaceViewActivity::class.java))
            }
            mActivityBinding.camera1Glsurfaceview -> {
                startActivity(Intent(this, API1GLSurfaceViewActivity::class.java))
            }
            mActivityBinding.camera1Textureview -> {
                startActivity(Intent(this, API1TextureViewActivity::class.java))
            }
            mActivityBinding.camera2Textureview -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivity(Intent(this, API2TextureViewActivity::class.java))
                }
            }
            mActivityBinding.camerax -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivity(Intent(this, CameraXActivity::class.java))
                }
            }
            mActivityBinding.audioRecord -> {
                startActivity(Intent(this, AudioRecordActivity::class.java))
            }
            mActivityBinding.playAudio -> {
                startActivity(Intent(this, PlayAudioActivity::class.java))
            }
            mActivityBinding.videoCodec -> {
                startActivity(Intent(this, MediaCodecActivity::class.java))
            }
            mActivityBinding.audioCodec -> {
                startActivity(Intent(this, AudioCodecActivity::class.java))
            }
        }
    }
}