package cc.appweb.gllearning

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cc.appweb.gllearning.databinding.ActivityApi2TextureviewBinding
import cc.appweb.gllearning.util.StorageUtil
import java.io.File
import java.io.FileOutputStream

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class API2TextureViewActivity : AppCompatActivity(), View.OnClickListener, ImageReader.OnImageAvailableListener {

    companion object {
        const val TAG = "TAG_API2TextureView"
    }

    private lateinit var mActivityBinding: ActivityApi2TextureviewBinding

    private lateinit var mMainHandler: Handler

    // 相机服务，用于打开/关闭摄像头
    private lateinit var mCameraManager: CameraManager

    // 相机对象实例，Camera2在预览时会自动矫正角度
    private var mCameraDevice: CameraDevice? = null

    // 相机特征
    private var mCameraCharacteristics: CameraCharacteristics? = null

    private var mCameraCaptureSession: CameraCaptureSession? = null

    // 需要打开前置/后置摄像头，默认为前置
    private var mCameraFacing = CameraCharacteristics.LENS_FACING_FRONT

    // 拍照图像接收者
    private var mImageReader: ImageReader? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api2_textureview)
        mMainHandler = Handler(Looper.getMainLooper())

        mActivityBinding = ActivityApi2TextureviewBinding.bind(findViewById(R.id.api2_textureview_container))
        mActivityBinding.openCamera.setOnClickListener(this)
        mActivityBinding.closeCamera.setOnClickListener(this)
        mActivityBinding.takePicture.setOnClickListener(this)
        mActivityBinding.switchCamera.setOnClickListener(this)

        mCameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            API1SurfaceViewActivity.CAMERA_PERMISSION_RESULT -> {
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
        closeCamera()
    }

    override fun onClick(v: View?) {
        when (v) {
            mActivityBinding.openCamera -> {
                openCamera()
            }
            mActivityBinding.closeCamera -> {
                closeCamera()
            }
            mActivityBinding.takePicture -> {
                takePicture()
            }
            mActivityBinding.switchCamera -> {
                switchCamera()
            }
        }
    }

    private fun switchCamera() {
        mCameraFacing = if (mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT ) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
        mCameraCaptureSession?.let {
            closeCamera()
            openCamera()
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(API1GLSurfaceViewActivity.TAG, "permission granted")
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), API1GLSurfaceViewActivity.CAMERA_PERMISSION_RESULT)
            return
        }

        // 获取对应camera的id
        var cameraId: String? = null
        mCameraManager.cameraIdList.forEach {
            mCameraManager.getCameraCharacteristics(it).apply {
                // 获取对应cameraId的相机特征
                if (get(CameraCharacteristics.LENS_FACING) == mCameraFacing) {
                    cameraId = it
                    val sizeMap = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    sizeMap?.getOutputSizes(ImageFormat.JPEG)?.forEach {itt->
                        Log.i(TAG, "width=${itt.width} height=${itt.height}")
                    }
                    mCameraCharacteristics = this
                }
            }
        }

        // 创建图像接收者，指定width/height
        mImageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1).apply {
            setOnImageAvailableListener(this@API2TextureViewActivity, mMainHandler)
        }

        // 获取相机实例，实例连接状态回调为StateCallback，运行在mMainHandler线程
        mCameraManager.openCamera(cameraId!!, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.i(TAG, "openCamera onOpened")
                mCameraDevice = camera
                openPreview()
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.i(TAG, "openCamera onDisconnected")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.i(TAG, "openCamera onError error=$error")
            }

            override fun onClosed(camera: CameraDevice) {
                Log.i(TAG, "openCamera onClosed")
            }

        }, mMainHandler)
    }

    private fun closeCamera() {
        mCameraCaptureSession?.let {
            it.stopRepeating()
            mCameraCaptureSession = null
            mCameraCharacteristics = null
        }
        mCameraDevice?.let {
            it.close()
            mCameraDevice = null
        }
        mImageReader?.let {
            it.close()
            mImageReader = null
        }
    }

    // 开启预览
    private fun openPreview() {
        Log.i(TAG, "openPreview")
        if (mActivityBinding.preview.isAvailable) {
            mCameraDevice?.let {
                // 创建一个session
                val surface = Surface(mActivityBinding.preview.surfaceTexture)
                it.createCaptureSession(mutableListOf(surface, mImageReader!!.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.i(TAG, "onConfigured")
                        mCameraCaptureSession = session.apply {
                            // 创建一个系统预设好的（TEMPLATE_PREVIEW）预览的request builder
                            val previewBuilder = it.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            // 绑定表面
                            previewBuilder.addTarget(surface)
                            setRepeatingRequest(previewBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
//                                    Log.i(TAG, "onCaptureStarted")
                                }

                                override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) {
//                                    Log.i(TAG, "onCaptureProgressed")
                                }

                                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
//                                    Log.i(TAG, "onCaptureCompleted")
                                }

                                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
//                                    Log.i(TAG, "onCaptureFailed")
                                }

                                override fun onCaptureSequenceCompleted(session: CameraCaptureSession, sequenceId: Int, frameNumber: Long) {
//                                    Log.i(TAG, "onCaptureSequenceCompleted")
                                }

                                override fun onCaptureSequenceAborted(session: CameraCaptureSession, sequenceId: Int) {
//                                    Log.i(TAG, "onCaptureSequenceAborted")
                                }

                                override fun onCaptureBufferLost(session: CameraCaptureSession, request: CaptureRequest, target: Surface, frameNumber: Long) {
//                                    Log.i(TAG, "onCaptureBufferLost")
                                }
                            }, mMainHandler)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.i(TAG, "onConfigureFailed")
                    }

                }, mMainHandler)

            }
        }
    }

    private fun takePicture() {
        mCameraDevice?.let {
            // 创建一个适合于静态图像捕获的请求，图像质量优先于帧速率
            val captureBuilder = it.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            // 绑定表面，输出到ImageReader
            captureBuilder.addTarget(mImageReader!!.surface)
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) // 自动对焦
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270)
            mCameraCaptureSession?.apply {
                stopRepeating()
                capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        Log.i(TAG, "capture onCaptureCompleted!!")
                    }
                }, mMainHandler)
            }
        }
    }

    override fun onImageAvailable(reader: ImageReader?) {
        Log.i(TAG, "onImageAvailable")

        reader?.let {
            val image = it.acquireNextImage()
            val byteBuffer = image.planes[0].buffer
            val byteArray = ByteArray(byteBuffer.remaining())
            byteBuffer.get(byteArray)
            it.close()
            //拍照成功
            // 拍照成功
            Thread {
                val file = StorageUtil.getFile("${StorageUtil.PATH_LEARNING_PIC + File.separator}pic${System.currentTimeMillis()}.jpeg")
                try {
                    val fileOutput = FileOutputStream(file)
                    fileOutput.write(byteArray)
                    fileOutput.flush()
                    mActivityBinding.api2TextureviewContainer.post {
                        Toast.makeText(this, "保存成功，路径：${file.absolutePath}", Toast.LENGTH_SHORT).show()
                    }
                    fileOutput.close()
                    return@Thread
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
                mActivityBinding.api2TextureviewContainer.post {
                    Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }.start()

        }
    }
}