package cc.appweb.gllearning.opengl

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import cc.appweb.gllearning.R
import cc.appweb.gllearning.databinding.GlMainFragmentBinding

/**
 * OpenGLESActivity学习主界面，介绍各种示例
 * */
class GLMainFragment : Fragment(), View.OnClickListener {

    private lateinit var mFragmentBinding: GlMainFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mFragmentBinding = GlMainFragmentBinding.inflate(inflater)
        return mFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mFragmentBinding.eglRender.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v) {
            mFragmentBinding.eglRender -> {
                fragmentManager!!.beginTransaction().apply {
                    replace(R.id.fragment_container, EGLFragment())
                    addToBackStack(null)
                    commit()
                }
            }
        }
    }

}