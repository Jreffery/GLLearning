package cc.appweb.gllearning

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cc.appweb.gllearning.databinding.ActivityApi1GlsurfaceviewBinding
import kotlin.math.abs

/**
 * 使用GLSurfaceView作为预览控件
 * 1. GLSurfaceView内部创建一个OpenGL TextureId并包装成SurfaceTexture
 * 2. 预览产生纹理触发SurfaceTexture回调
 * 3. 调用OpenGL渲染该Texture到EGL上
 * */
class API1GLSurfaceViewActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        const val TAG = "TAG_API1GLSurfaceView"
        const val CAMERA_PERMISSION_RESULT = 1
    }

    private lateinit var mActivityBinding: ActivityApi1GlsurfaceviewBinding
    // 抽象相机的对象
    private var mCamera: Camera? = null

    // 所打开相机的参数属性
    private var mCameraInfo: Camera.CameraInfo? = null

    // 需要打开前置/后置摄像头，默认为前置
    private var mFacing = Camera.CameraInfo.CAMERA_FACING_FRONT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api1_glsurfaceview)
        mActivityBinding = ActivityApi1GlsurfaceviewBinding.bind(findViewById(R.id.api1_glsurfaceview_container))
        mActivityBinding.openCamera.setOnClickListener(this)
        mActivityBinding.closeCamera.setOnClickListener(this)
        mActivityBinding.switchCamera.setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        mActivityBinding.preview.apply {
            onResume()
            // 如果CameraGlSurfaceView是可见的，则触发重新打开相机
            if (visibility == View.VISIBLE) {
                openCamera()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_RESULT -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "onActivityResult permission granted")
                    // 继续打开相机
                    openCamera()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mActivityBinding.preview.apply {
            onPause()
            // 如果CameraGlSurfaceView是可见的，则触发关闭相机
            if (visibility == View.VISIBLE) {
                closeCamera()
            }
        }
    }

    override fun onClick(v: View?) {
        when(v) {
            // 打开相机
            mActivityBinding.openCamera -> {
                // 设置GLSurfaceView可见
                mActivityBinding.preview.apply {
                    if (visibility != View.VISIBLE) {
                        visibility = View.VISIBLE
                        // Surface的创建封装在CameraGLSurfaceView内
                        post {
                            openCamera()
                        }
                    } else {
                        mCamera?.let {
                            it.startPreview()
                        }
                    }
                }
            }
            // 关闭相机
            mActivityBinding.closeCamera -> {
                // 设置CameraGLSurfaceView不可见
                mActivityBinding.preview.apply {
                    if (visibility != View.GONE) {
                        visibility = View.GONE
                        // Surface的销毁封装在CameraGLSurfaceView内
                        closeCamera()
                    }
                }
            }
            // 切换相机
            mActivityBinding.switchCamera -> {
                closeCamera()
                mFacing =  if (mFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) Camera.CameraInfo.CAMERA_FACING_BACK else Camera.CameraInfo.CAMERA_FACING_FRONT
                openCamera()
            }
        }
    }

    private fun openCamera() {
        // 打开相机需要权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "permission granted")
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_RESULT)
            return
        }

        // 遍历设备上所有的相机
        val cameraNum = Camera.getNumberOfCameras()
        mCameraInfo = null
        for (i in 0 until cameraNum) {
            // 获取对应相机的参数属性
            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(i, cameraInfo)
            Log.i(TAG, "camera index=$i, facing=${cameraInfo.facing}, orientation=${cameraInfo.orientation}")
            if (cameraInfo.facing == mFacing) {
                mCameraInfo = cameraInfo
                break
            }
        }

        mCameraInfo?.let {
            // 获取一个Camera对象，用于调用各种相机接口
            mCamera = Camera.open(it.facing)
            // 根据屏幕的方向调整预览的方向，所见即所得
            var degrees = 0
            var displayRotation: Int  // 预览角度调教
            // 获取显示屏幕的方向，与screenOrientation有关
            when (display!!.rotation) {
                Surface.ROTATION_0 -> {
                    degrees = 0
                }
                Surface.ROTATION_90 -> {
                    degrees = 90
                }
                Surface.ROTATION_180 -> {
                    degrees = 180
                }
                Surface.ROTATION_270 -> {
                    degrees = 270
                }
            }
            // 根据前置与后置摄像头的不同，设置预览方向，否则会发生预览图像倒过来的情况。
            if (it.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                displayRotation = (it.orientation + degrees) % 360
                displayRotation = (360 - displayRotation) % 360 // compensate
            } else {
                displayRotation = (it.orientation - degrees + 360) % 360
            }
            Log.i(TAG, "degrees=$degrees, displayRotation=$displayRotation")
            mCamera?.let { itt: Camera ->
                // 设置摄像头角度，这将会影响预览帧，这个调用在GLSurfaceView#SurfaceView不生效？？
                // --> 正确的做法是SurfaceTexture.getTransformMatrix()得到图像变换矩阵，并将变换矩阵传入顶点着色器中，让GPU来完成转换
//                itt.setDisplayOrientation(displayRotation)
                // 调整渲染时的角度
                mActivityBinding.preview.setPreviewOrientation(360 - it.orientation, it.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                val parameters = itt.parameters
                // 设置成像后数据的角度，这将会影响takePicture的数据导出
                parameters.setRotation((it.orientation - degrees + 360) % 360)
                // 获取最高帧率
                var min = 0
                var max = 0
                parameters.supportedPreviewFpsRange.forEach { fps ->
                    // 设置相机预览照片帧数
                    if (max < fps[1]) {
                        min = fps[0]
                        max = fps[1]
                    } else if (max == fps[1]) {
                        if (min < fps[0]) {
                            min = fps[0]
                            max = fps[1]
                        }
                    }
                }
                Log.i(TAG, "previewFpsRange min=${min / 1000}, max=${max / 1000}")
                parameters.setPreviewFpsRange(min, max)
                // 设置图片格式
                parameters.pictureFormat = ImageFormat.JPEG // ImageFormat.NV21 / ImageFormat.RGB_565
                // 设置图片的质量
                parameters.set("jpeg-quality", 90)
                // 设置预览与拍照的尺寸，相机默认方向顺时针90度，要以这个角度来设置尺寸，width>height
                // 主要三个尺寸，相机预览尺寸、拍照的尺寸、预览View的尺寸
                var viewWidth = mActivityBinding.preview.width
                var viewHeight = mActivityBinding.preview.height
                if (degrees == 0 || degrees == 180) {
                    val tmp = viewHeight
                    viewHeight = viewWidth
                    viewWidth = tmp
                }
                val ratio = viewWidth.toFloat() / viewHeight.toFloat()
                var curRatio = 100f
                var previewWidth = 640
                var previewHeight = 360
                parameters.supportedPreviewSizes.forEach { size ->
                    val tmpRatio = abs((size.width.toFloat() / size.height.toFloat()) - ratio)
                    // 找出宽高比例最相机的预览尺寸
                    if (tmpRatio < curRatio) {
                        curRatio = tmpRatio
                        previewWidth = size.width
                        previewHeight = size.height
                    }
                }
                Log.i(TAG, "viewWidth=${viewWidth} viewHeight=${viewHeight} previewWidth=${previewWidth} previewHeight=${previewHeight}")
                parameters.setPreviewSize(previewWidth, previewHeight)
                // 寻找一个与preview最相近的尺寸
                var picW = parameters.pictureSize.width
                var picH = parameters.pictureSize.height
                parameters.supportedPictureSizes.forEach { size ->
                    val diffNew = abs(size.width - previewWidth)
                    val diffNow = abs(picW - previewWidth)
                    if (diffNew < diffNow) {
                        picW = size.width
                        picH = size.height
                    }
                }
                Log.i(TAG, "PictureSize width=$picW height=$picH")
                parameters.setPictureSize(picW, picH)
                // 自动对焦模式
                val supportedFocusModes = parameters.supportedFocusModes
                if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                }
                // 使参数生效
                itt.parameters = parameters
                itt.setPreviewCallback { _, _ ->
//                    Log.i(TAG, "onPreviewFrame")
                }
                // 开始预览
                mActivityBinding.preview.getSurfaceTexture {
                    itt.setPreviewTexture(this)
                    itt.startPreview()
                }
            }
        }
    }

    private fun closeCamera() {
        Log.i(TAG, "closeCamera")
        mCamera?.let {
            // 需要取消previewcallback，否则会导致 RuntimeException: Camera is being used after Camera.release() was called
            it.setPreviewCallback(null)
            it.stopPreview()
            it.release()
            mCamera = null
        }
    }

}