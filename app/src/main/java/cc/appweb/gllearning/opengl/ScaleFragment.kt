package cc.appweb.gllearning.opengl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import cc.appweb.gllearning.componet.ComputeRender
import cc.appweb.gllearning.componet.ComputeTestRender
import cc.appweb.gllearning.databinding.ScaleFragmentBinding
/**
 * @description: 缩小图片-色值统计示例
 * @date: 2022/4/21.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP_MR1)
class ScaleFragment: Fragment() {

    private lateinit var binding: ScaleFragmentBinding
    private lateinit var computeRender: ComputeRender
    private lateinit var computeTestRender: ComputeTestRender
    
    private var bitmapOrigin: Bitmap? = null
    private var bitmapHeight: Int = 0
    private var bitmapWidth: Int = 0
    
    companion object {
        private const val TAG = "ScaleFragment"
        private const val SCALE_HEIGHT = 100
        private const val SCALE_DRAW = 1
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ScaleFragmentBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.bitmapScale.setOnClickListener {
            ensureOriginBitmap()
            bitmapOrigin?.let {
                val scaleHeight = bitmapHeight * SCALE_HEIGHT / 100
                val canvasWidth = bitmapWidth / SCALE_DRAW
                val canvasHeight = scaleHeight / SCALE_DRAW
                Log.i(TAG, "scaleH=$scaleHeight canvasW=$canvasWidth canvasH=$canvasHeight")
                val canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(canvasBitmap)
                val bitmapRect = Rect().apply { 
                    top = bitmapHeight - scaleHeight
                    bottom = bitmapHeight
                    left = 0
                    right = bitmapWidth
                }
                val canvasRect = Rect().apply { 
                    top = 0
                    bottom = canvasHeight
                    left = 0
                    right = canvasWidth
                }
                canvas.drawBitmap(it, bitmapRect, canvasRect, null)
                val start = System.currentTimeMillis()
                val palette = Palette.from(canvasBitmap).clearFilters().generate()
                var max = 0f
                palette.swatches.forEach { itt ->
                    max += itt.population 
                    Log.i(TAG, "one swatch rgb=${itt.rgb} population=${itt.population}")
                }
                val rate: Float = (palette.dominantSwatch?.population ?: 0) / max
                val isBlank = rate >= 0.95 //大于95%的纯色默认为白屏
                val isSuccess = palette.dominantSwatch != null
                val pureColorArgb = palette.dominantSwatch?.rgb ?: -1
                val colorCounts = (rate * 100).toInt()
                val cost = System.currentTimeMillis() - start
                Log.i(TAG, "bitmap draw cost=$cost ms " +
                    "isBlank=$isBlank isSuccess=$isSuccess pureColorArgb=$pureColorArgb " +
                    "colorCounts=$colorCounts")
                binding.scaleShowView.setImageBitmap(canvasBitmap)
                binding.bitmapCostTimeTv.text = "Bitmap detect cost $cost ms"
            }
        }
        binding.glesScale.setOnClickListener { 
            ensureOriginBitmap()
            computeRender.detectBlank(binding.detectView, 10, 100)
//            computeTestRender.test()
        }
        computeRender = ComputeRender()
        computeTestRender = ComputeTestRender()
        computeRender.initRender()
        computeTestRender.initRender()
    }

    override fun onDestroy() {
        super.onDestroy()
        bitmapOrigin?.recycle()
        bitmapOrigin = null
        computeRender.destroy()
        computeTestRender.destroy()
    }
    
    private fun ensureOriginBitmap() {
        bitmapOrigin ?: let {
            val option = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmapOrigin = BitmapFactory.decodeStream(context!!.assets.open("png/webviewtop-293.png"), null, option)
            bitmapHeight = option.outHeight
            bitmapWidth = option.outWidth
            Log.d(TAG, "ensureOriginBitmap width=$bitmapWidth height=$bitmapHeight")
        }
    }
    
}