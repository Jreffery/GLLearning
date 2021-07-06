package cc.appweb.gllearning

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import cc.appweb.gllearning.databinding.ActivityOpenGlesActivityBinding
import cc.appweb.gllearning.opengl.GLMainFragment

/**
 * OpenGLES 学习示例
 * */
class OpenGLESActivity : AppCompatActivity() {

    private lateinit var mActivityBinding: ActivityOpenGlesActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_gles_activity)

        mActivityBinding = ActivityOpenGlesActivityBinding.bind(findViewById(R.id.open_gl_container))

        supportFragmentManager.beginTransaction().apply {
            add(mActivityBinding.fragmentContainer.id, GLMainFragment())
            commit()
        }
    }
}