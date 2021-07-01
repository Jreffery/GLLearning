package cc.appweb.gllearning.util

import android.content.res.Resources

class DensityUtil {

    companion object {

        private val density = Resources.getSystem().displayMetrics.density

        private val scaledDensity = Resources.getSystem().displayMetrics.scaledDensity

        /**
         * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
         */
        fun dp2px(dpValue: Float): Int {
            return (0.5f + dpValue * density).toInt()
        }

        /**
         * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
         */
        fun px2dp(pxValue: Float): Float {
            return pxValue / Resources.getSystem().displayMetrics.density
        }

        fun sp2px(spValue: Float): Float {
            return spValue * scaledDensity + 0.5f
        }
    }
}
