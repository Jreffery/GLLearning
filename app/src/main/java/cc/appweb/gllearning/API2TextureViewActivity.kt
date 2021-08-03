package cc.appweb.gllearning

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cc.appweb.gllearning.componet.CommonGLRender
import cc.appweb.gllearning.databinding.ActivityApi2TextureviewBinding
import cc.appweb.gllearning.mediacodec.VideoEncoder
import cc.appweb.gllearning.util.StorageUtil
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Camera API2 和 TextureView实现预览、拍照、录制
 * 1. 比Camera1，Camera2加入了Session与回调线程的特性
 * 2. 在创建session时需要提供后续操作的所有目的Output Surface
 * 3. setRepeatingRequest(预览) capture(拍照)都需要选定上述的部分Surface作为output
 * */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class API2TextureViewActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        const val TAG = "TAG_API2TextureView"
        const val CAMERA_PERMISSION_RESULT = 1
    }

    private lateinit var mActivityBinding: ActivityApi2TextureviewBinding

    // 主线程handler
    private lateinit var mMainHandler: Handler

    private lateinit var mImageHandler: Handler

    // 相机服务，用于打开/关闭摄像头
    private lateinit var mCameraManager: CameraManager

    // 相机对象实例，Camera2在预览时会自动矫正角度
    private var mCameraDevice: CameraDevice? = null

    // 相机特征
    private var mCameraCharacteristics: CameraCharacteristics? = null

    // 相机请求会话
    private var mCameraCaptureSession: CameraCaptureSession? = null

    // 需要打开前置/后置摄像头，默认为前置
    private var mCameraFacing = CameraCharacteristics.LENS_FACING_FRONT

    // 拍照图像接收者
    private var mImageReader: ImageReader? = null

    // 图像回调线程
    private var mViewThread: HandlerThread? = null

    // 录制ImageReader
    private var mRecordReader: ImageReader? = null

    // 是否正在录制
    private var mRecording = false

    // 视频编码器
    private var mEncoder: VideoEncoder? = null

    private var mPictureSize: Size? = null

    // 录像帧率
    private var mRecordFps: Range<Int>? = null

    // 录像分辨率
    private var mRecordSize: Size? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api2_textureview)
        mMainHandler = Handler(Looper.getMainLooper())

        mActivityBinding = ActivityApi2TextureviewBinding.bind(findViewById(R.id.api2_textureview_container))
        mActivityBinding.openCamera.setOnClickListener(this)
        mActivityBinding.closeCamera.setOnClickListener(this)
        mActivityBinding.takePicture.setOnClickListener(this)
        mActivityBinding.switchCamera.setOnClickListener(this)
        mActivityBinding.videoRecorder.setOnClickListener(this)

        mCameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

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
        closeCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        mViewThread?.let {
            it.quit()
            mViewThread = null
        }
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
            mActivityBinding.videoRecorder -> {
                takeRecorder()
            }
        }
    }

    private fun switchCamera() {
        mCameraFacing = if (mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
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
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_RESULT)
            return
        }

        // 获取对应camera的id
        var cameraId: String? = null
        mCameraManager.cameraIdList.find {
            mCameraManager.getCameraCharacteristics(it).apply {
                // 获取对应cameraId的相机特征
                if (get(CameraCharacteristics.LENS_FACING) == mCameraFacing) {
                    cameraId = it
                    mCameraCharacteristics = this

                    var fps: Range<Int>? = null
                    // 帧率，遍历可用帧率，找到最接近15的帧率
                    get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.forEach { fpsRange ->
                        fps?.let {
                            if (abs(fps!!.upper - 15) > abs(fpsRange.upper - 15)) {
                                fps = fpsRange
                            }
                        } ?: let {
                            fps = fpsRange
                        }
                    }
                    mRecordFps = fps

                    // 拍照与录像的分辨率，最接近1280*720
                    get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.apply {
                        val prefectSize = 1280 * 720
                        mPictureSize = null
                        mRecordSize = null
                        getOutputSizes(ImageFormat.YUV_420_888)?.forEach { size ->
                            Log.d(TAG, "support yuv420 output size width=${size.width} height=${size.height}")
                            mRecordSize?.let { recordSize ->
                                if (abs(prefectSize - recordSize.width * recordSize.height) > abs(prefectSize - size.width * size.height)) {
                                    mRecordSize = size
                                    mPictureSize = size
                                }
                            } ?: let {
                                mRecordSize = size
                                mPictureSize = size
                            }
                        }
                    }

                    Log.i(TAG, "picture size{w=${mPictureSize!!.width} h=${mPictureSize!!.height}} " +
                            "record size{w=${mRecordSize!!.width} h=${mRecordSize!!.height}}")
                }
            }
            return@find mCameraCharacteristics != null
        }

        mViewThread ?: let {
            mViewThread = HandlerThread("ViewThread").apply {
                start()
            }
        }

        mImageHandler = Handler(mViewThread!!.looper)

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
                mImageHandler.post {
                    if (mRecording) {
                        mRecording = false
                        mEncoder?.let {
                            it.stop()
                            mEncoder = null
                        }
                        mMainHandler.post {
                            mActivityBinding.videoRecorder.text = "开始录制"
                        }
                    }
                }
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
        mRecordReader?.let {
            it.close()
            mRecordReader = null
        }
    }

    // 开启预览
    private fun openPreview() {
        Log.i(TAG, "openPreview")
        if (mActivityBinding.preview.isAvailable) {
            mCameraDevice?.let {

                // 创建图像接收者，指定width/height
                mImageReader = ImageReader.newInstance(mPictureSize!!.width, mPictureSize!!.height, ImageFormat.YUV_420_888, 1).apply {
                    setOnImageAvailableListener(PictureReaderCallback(), mImageHandler)
                }
                // 录制图像接收者
                mRecordReader = ImageReader.newInstance(mRecordSize!!.width, mRecordSize!!.height, ImageFormat.YUV_420_888, 5).apply {
                    setOnImageAvailableListener(RecordReaderCallback(), mImageHandler)
                }
                // 创建一个session
                val surface = Surface(mActivityBinding.preview.surfaceTexture)
                // 三个surface：预览、录制、图片
                it.createCaptureSession(mutableListOf(surface, mRecordReader!!.surface, mImageReader!!.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.i(TAG, "onConfigured")
                        mCameraCaptureSession = session.apply {
                            // 创建一个系统预设好的（TEMPLATE_PREVIEW）预览的request builder
                            val previewBuilder = it.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            // 设置帧率
                            mRecordFps?.let {
                                previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
                            }

                            // 绑定表面：预览与录制
                            previewBuilder.addTarget(surface)
                            previewBuilder.addTarget(mRecordReader!!.surface)
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
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, if (mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT) 270 else 90)
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

    private fun takeRecorder() {
        mCameraCaptureSession?.let {
            if (mRecording) {
                Toast.makeText(this, "停止录制", Toast.LENGTH_SHORT).show()
                mActivityBinding.videoRecorder.text = "开始录制"
            } else {
                Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show()
                mActivityBinding.videoRecorder.text = "停止录制"
            }
            mImageHandler.post {
                mRecording = !mRecording
            }
        }
    }

    // 接收拍照图片的回调，回调非主线程
    private inner class PictureReaderCallback : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            Log.i(TAG, "onImageAvailable")

            reader?.let {
                it.acquireNextImage()?.apply {
                    // NV21
                    val byteBuffer1 = planes[0].buffer
                    val byteBuffer2 = planes[1].buffer
                    val byteArray = ByteArray(byteBuffer1.limit() + byteBuffer2.limit())
                    byteBuffer1.get(byteArray, 0, byteBuffer1.limit())
                    byteBuffer2.get(byteArray, byteBuffer1.limit(), byteBuffer2.limit())
                    // 需要关闭image
                    close()
                    //拍照成功
                    // 拍照成功
                    Thread {
                        val file = StorageUtil.getFile("${StorageUtil.PATH_LEARNING_RAW + File.separator}${System.currentTimeMillis()}nv21.yuv")
                        try {
                            val fileOutput = FileOutputStream(file)
                            fileOutput.write(byteArray)
                            fileOutput.flush()
                            mActivityBinding.api2TextureviewContainer.post {
                                Toast.makeText(this@API2TextureViewActivity, "保存成功，路径：${file.absolutePath}", Toast.LENGTH_SHORT).show()
                            }
                            fileOutput.close()
                            return@Thread
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                        mActivityBinding.api2TextureviewContainer.post {
                            Toast.makeText(this@API2TextureViewActivity, "保存失败", Toast.LENGTH_SHORT).show()
                        }
                    }.start()
                }
            }
        }
    }

    // 录制图像接收者，回调非主线程
    private inner class RecordReaderCallback : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            reader?.let {
                it.acquireNextImage()?.apply {
                    Log.i(TAG, "RecordReaderCallback acquire image")
                    if (mRecording) {
                        mEncoder ?: let {
                            mEncoder = VideoEncoder(mRecordSize!!.width, mRecordSize!!.height, mRecordFps!!.upper,
                                    3 * 1024 * 1024, 1,
                                    if (mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT) CommonGLRender.ROTATE_270 else CommonGLRender.ROTATE_90)
                        }

                        // YUV420
                        val inputBuffer = ByteBuffer.allocateDirect(mRecordSize!!.width * mRecordSize!!.height * 3 / 2)
                        // plane[0] + plane[1] = NV21
                        // plane[0] + plane[2] = NV21
                        inputBuffer.put(planes[0].buffer)
                        inputBuffer.put(planes[1].buffer)
                        inputBuffer.flip()
                        mEncoder!!.offerImage(inputBuffer)
                    } else {
                        mEncoder?.apply {
                            stop()
                            mEncoder = null
                        }
                    }

                    // 需要关闭image
                    close()
                }
            }
        }

    }

}