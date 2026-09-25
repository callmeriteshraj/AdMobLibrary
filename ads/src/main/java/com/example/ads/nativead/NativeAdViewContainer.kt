package com.example.ads.nativead

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.example.ads.AdsError
import com.example.ads.AdsManager
import com.example.ads.R
import com.example.ads.util.AdsLogger
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Production-ready XML Native Ad View container component.
 *
 * Can be placed directly in XML layouts:
 * ```xml
 * <com.example.ads.nativead.NativeAdViewContainer
 *     android:id="@+id/nativeAdContainer"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:templateType="medium"
 *     app:autoLoadNative="true" />
 * ```
 */
class NativeAdViewContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val nativeAdManager get() = AdsManager.nativeManager
    private var template: NativeAdTemplate = NativeAdTemplate.Medium
    private var isAutoLoad: Boolean = false
    private var shimmerView: View? = null
    private var nativeAdView: NativeAdView? = null
    private var isAdLoaded = false

    var onAdLoadedListener: (() -> Unit)? = null
    var onAdFailedListener: ((AdsError) -> Unit)? = null

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.NativeAdViewContainer)
            try {
                val templateIndex = typedArray.getInt(R.styleable.NativeAdViewContainer_nativeTemplate, 1)
                template = when (templateIndex) {
                    0 -> NativeAdTemplate.Small
                    1 -> NativeAdTemplate.Medium
                    2 -> NativeAdTemplate.Big
                    else -> NativeAdTemplate.Medium
                }
                isAutoLoad = typedArray.getBoolean(R.styleable.NativeAdViewContainer_autoLoadNative, false)
            } finally {
                typedArray.recycle()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isAutoLoad && !isAdLoaded) {
            loadAd()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }

    /**
     * Sets the template style before loading (e.g. [NativeAdTemplate.Small] or [NativeAdTemplate.Medium]).
     */
    fun setTemplate(template: NativeAdTemplate) {
        this.template = template
    }

    /**
     * Loads and renders a native ad into this container.
     */
    fun loadAd() {
        if (!AdsManager.isAdsEnabled) {
            AdsLogger.d(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Ads are globally disabled. Hiding container.")
            visibility = View.GONE
            onAdFailedListener?.invoke(AdsError.AdsDisabled)
            return
        }

        val config = AdsManager.config
        if (config == null) {
            AdsLogger.w(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Ads SDK not initialized.")
            visibility = View.GONE
            onAdFailedListener?.invoke(AdsError.NotInitialized)
            return
        }

        // Check if preloaded native ad is ready for instant 0ms rendering
        val preloadedAd = nativeAdManager.consumePreloadedNativeAd(context, config)
        if (preloadedAd != null) {
            AdsLogger.i(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Rendering preloaded Native Ad instantly.")
            visibility = View.VISIBLE
            renderNativeAd(preloadedAd)
            isAdLoaded = true
            onAdLoadedListener?.invoke()
            return
        }

        showShimmerPlaceholder()
        visibility = View.VISIBLE

        nativeAdManager.loadNativeAd(
            context,
            config,
            onLoaded = { nativeAd: NativeAd ->
                renderNativeAd(nativeAd)
                isAdLoaded = true
                onAdLoadedListener?.invoke()
            },
            onFailed = { error: AdsError ->
                hideShimmerPlaceholder()
                visibility = View.GONE
                onAdFailedListener?.invoke(error)
            }
        )
    }

    private fun showShimmerPlaceholder() {
        if (shimmerView == null) {
            val inflater = LayoutInflater.from(context)
            shimmerView = inflater.inflate(R.layout.layout_native_ad_shimmer, this, false)
            addView(shimmerView)
        }
        shimmerView?.visibility = View.VISIBLE
        nativeAdView?.visibility = View.GONE
    }

    private fun hideShimmerPlaceholder() {
        shimmerView?.visibility = View.GONE
    }

    private fun renderNativeAd(nativeAd: NativeAd) {
        hideShimmerPlaceholder()

        // Inflate the selected template
        val inflater = LayoutInflater.from(context)
        val inflatedAdView = inflater.inflate(template.layoutResId, this, false) as? NativeAdView
        if (inflatedAdView != null) {
            nativeAdView?.let { removeView(it) }
            nativeAdView = inflatedAdView
            addView(inflatedAdView)
            nativeAdManager.populateNativeAdView(nativeAd, inflatedAdView)
            inflatedAdView.visibility = View.VISIBLE
        }
    }

    fun destroy() {
        try {
            nativeAdView?.destroy()
            nativeAdView = null
            removeAllViews()
            shimmerView = null
            isAdLoaded = false
        } catch (e: Exception) {
            AdsLogger.w(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Exception during destroy: ${e.message}")
        }
    }
}
