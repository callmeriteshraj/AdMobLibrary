package com.example.ads.nativead

import androidx.annotation.LayoutRes
import com.example.ads.R

/**
 * Defines the layout template style for Native Ads.
 */
sealed class NativeAdTemplate {
    abstract val layoutResId: Int

    object Small : NativeAdTemplate() {
        override val layoutResId: Int = R.layout.layout_native_ad_small
    }

    object Medium : NativeAdTemplate() {
        override val layoutResId: Int = R.layout.layout_native_ad_medium
    }

    object Big : NativeAdTemplate() {
        override val layoutResId: Int = R.layout.layout_native_ad_big
    }

    data class Custom(@get:LayoutRes override val layoutResId: Int) : NativeAdTemplate()
}
