package cc.appweb.gllearning.opengl

import android.os.Build
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mFragmentBinding = GlMainFragmentBinding.inflate(inflater)
        return mFragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mFragmentBinding.eglRender.setOnClickListener(this)
        mFragmentBinding.glRotate.setOnClickListener(this)
        mFragmentBinding.javaRotate.setOnClickListener(this)
        mFragmentBinding.yuvRotate.setOnClickListener(this)
        mFragmentBinding.textureRender.setOnClickListener(this)
        mFragmentBinding.watermarkRender.setOnClickListener(this)
        mFragmentBinding.scaleRender.setOnClickListener(this)
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
            mFragmentBinding.glRotate -> {
                fragmentManager!!.beginTransaction().apply {
                    replace(R.id.fragment_container, RotateFragment())
                    addToBackStack(null)
                    commit()
                }
            }
            mFragmentBinding.javaRotate -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    fragmentManager!!.beginTransaction().apply {
                        replace(R.id.fragment_container, JavaRotateFragment())
                        addToBackStack(null)
                        commit()
                    }
                }
            }
            mFragmentBinding.yuvRotate -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    fragmentManager!!.beginTransaction().apply {
                        replace(R.id.fragment_container, YuvRotateFragment())
                        addToBackStack(null)
                        commit()
                    }
                }
            }
            mFragmentBinding.textureRender -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    fragmentManager!!.beginTransaction().apply {
                        replace(R.id.fragment_container, TextureRenderFragment())
                        addToBackStack(null)
                        commit()
                    }
                }
            }
            mFragmentBinding.watermarkRender -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    fragmentManager!!.beginTransaction().apply {
                        replace(R.id.fragment_container, WatermarkRenderFragment())
                        addToBackStack(null)
                        commit()
                    }
                }
            }
            mFragmentBinding.scaleRender -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    fragmentManager!!.beginTransaction().apply {
                        replace(R.id.fragment_container, ScaleFragment())
                        addToBackStack(null)
                        commit()
                    }
                }
            }
        }
    }

}