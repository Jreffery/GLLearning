package cc.appweb.gllearning

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cc.appweb.gllearning.databinding.ActivityCameraXBinding
import cc.appweb.gllearning.util.StorageUtil
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

/**
 * CameraX 是对 Camera 的封装，以 UseCase 的形式提供了开箱即用的几个功能
 * 使用比较简单，获取到 ProcessCameraProvider 单例对象后，调用 bindToLifecycle 绑定生命周期和指定功能 UseCase
 * 目前 CameraX 提供了以下四种功能，即 UseCase 的4个实现类
 * 1. Preview 用于预览
 * 2. ImageCapture 用于拍照
 * 3. ImageAnalysis 用于预览帧数据做实时分析的接口
 * 4. VideoCapture 录屏
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraXActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    /*
     * 拍照使用
     */
    private var imageCapture: ImageCapture? = null

    private lateinit var imageAnalysisExecutor: ExecutorService
    private lateinit var binding: ActivityCameraXBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraXBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 申请相机权限
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        binding.cameraCaptureButton.setOnClickListener { takePhoto() }
        imageAnalysisExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {

        val imageCapture = imageCapture ?: return

        val photoFile =
            StorageUtil.getFile("${StorageUtil.PATH_LEARNING_PIC + File.separator}pic${System.currentTimeMillis()}.jpeg")

        // output options 描述文件输出和 meta 信息
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(ImageCapture.Metadata().apply {
                this.isReversedHorizontal = false
                this.isReversedVertical = false
            })
            .build()

        // 拍照接口
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.v(TAG, msg)
                }
            })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // Future 执行完毕获取到一个 ProcessCameraProvider 单例对象
        cameraProviderFuture.addListener({

            // 主要用来绑定 activity 的生命周期然后自己开关预览，所以这套接口我们不用手动调 startPreview 和 stopPreview
            // 同时指定使用 CameraX 提供哪些的 UseCase，下面开了预览、拍照和图片分析回调 3 个 UseCase
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // UseCase : Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // UseCase : Take Photo
            imageCapture = ImageCapture.Builder().build()

            // UseCase : Image Analysis
            val imageAnalyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(imageAnalysisExecutor, LuminosityAnalyzer { luma ->
                    Log.d(TAG, "Average luminosity: $luma")
                })
            }

            // 使用永远选择后摄像头的 CameraSelector
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            // 也可以自己过滤符合条件的摄像头
//             val cameraSelector = CameraSelector.Builder().addCameraFilter {  cameraInfoList ->
//                return@addCameraFilter cameraInfoList.filter {it.hasFlashUnit() }
//            }

            try {
                // Unbind 所有之前绑定的 useCases
                cameraProvider.unbindAll()

                // 绑定生命周期到此 Activity，同时绑定上面构造的 3个 useCase
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, /* 这个 runnable 执行的线程，这里是主线程 */ContextCompat.getMainExecutor(this))
    }

    /**
     * 简单照片分析类，计算了平均亮度
     */
    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()
            listener(luma)
            image.close()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        imageAnalysisExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}