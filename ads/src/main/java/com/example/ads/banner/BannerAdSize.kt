package com.example.ads.banner

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowMetrics
import com.google.android.gms.ads.AdSize

/**
 * Supported banner ad size modes.
 */
enum class BannerAdSize {
    ADAPTIVE,
    BANNER,
    LARGE_BANNER,
    MEDIUM_RECTANGLE;

    /**
     * Resolves the Google AdMob [AdSize] instance for the current context.
     */
    fun toAdSize(context: Context): AdSize {
        return when (this) {
            ADAPTIVE -> getAdaptiveAdSize(context)
            BANNER -> AdSize.BANNER
            LARGE_BANNER -> AdSize.LARGE_BANNER
            MEDIUM_RECTANGLE -> AdSize.MEDIUM_RECTANGLE
        }
    }

    companion object {
        /**
         * Calculates current-orientation adaptive banner size based on window width.
         */
        fun getAdaptiveAdSize(context: Context): AdSize {
            val activity = context as? Activity
            val adWidthPixels = if (activity != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
                    val bounds = windowMetrics.bounds
                    bounds.width().toFloat()
                } else {
                    val displayMetrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
                    displayMetrics.widthPixels.toFloat()
                }
            } else {
                context.resources.displayMetrics.widthPixels.toFloat()
            }

            val density = context.resources.displayMetrics.density
            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
        }
    }
}
