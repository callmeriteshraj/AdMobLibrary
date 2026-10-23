package com.example.ads.nativead

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.example.ads.AdsConfig
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

    // Production callbacks always resolve through AdsManager's one global manager instance.
    // Internal overrides make the cache-first behavior testable without a real ad request.
    internal var configProvider: () -> AdsConfig? = { AdsManager.config }
    internal var adsEnabledProvider: () -> Boolean = { AdsManager.isAdsEnabled }
    internal var consumePreloadedAd: (Context, AdsConfig) -> NativeAd? = { loadContext, config ->
        AdsManager.nativeManager.consumePreloadedNativeAd(
            loadContext,
            config,
            replenishAfterConsume = true
        )
    }
    internal var requestNativeAd: (Context, AdsConfig, (NativeAd) -> Unit, (AdsError) -> Unit) -> Unit =
        { loadContext, config, onLoaded, onFailed ->
            AdsManager.nativeManager.loadNativeAdDirect(loadContext, config, onLoaded, onFailed)
        }
    internal var populateNativeAd: (NativeAd, NativeAdView) -> Unit = { ad, adView ->
        AdsManager.nativeManager.populateNativeAdView(ad, adView)
    }
    private var template: NativeAdTemplate = NativeAdTemplate.Medium
    private var isAutoLoad: Boolean = false
    private var shimmerView: NativeAdShimmerLayout? = null
    private var ctaShimmer: NativeAdCtaShimmerDrawable? = null
    private var nativeAdView: NativeAdView? = null
    private var ownedNativeAd: NativeAd? = null
    private var isAdLoaded = false
    private var isLoading = false
    private var requestGeneration = 0

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
        if (isLoading || isAdLoaded) {
            AdsLogger.d(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Duplicate loadAd() ignored (loading=$isLoading, loaded=$isAdLoaded).")
            return
        }
        val config = configProvider()
        if (config == null) {
            AdsLogger.w(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Ads SDK not initialized.")
            visibility = View.GONE
            onAdFailedListener?.invoke(AdsError.NotInitialized)
            return
        }

        val adsEnabled = adsEnabledProvider()
        if (!adsEnabled || !config.enableNative) {
            AdsLogger.d(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Native ads are disabled (global=$adsEnabled, format=${config.enableNative}). Hiding container.")
            visibility = View.GONE
            onAdFailedListener?.invoke(AdsError.AdsDisabled)
            return
        }

        val generation = ++requestGeneration
        // Check if preloaded native ad is ready for instant 0ms rendering
        val preloadedAd = consumePreloadedAd(context.applicationContext, config)
        if (preloadedAd != null) {
            AdsLogger.i(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Cached native ad consumed; rendering immediately.")
            visibility = View.VISIBLE
            isAdLoaded = renderNativeAd(preloadedAd)
            if (isAdLoaded) onAdLoadedListener?.invoke()
            return
        }

        isLoading = true
        isAdLoaded = false
        showShimmerPlaceholder()
        visibility = View.VISIBLE

        AdsLogger.i(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Cache empty; starting direct network request.")
        requestNativeAd(
            context.applicationContext,
            config,
            loaded@ { nativeAd: NativeAd ->
                if (generation != requestGeneration) {
                    nativeAd.destroy()
                    return@loaded
                }
                isLoading = false
                isAdLoaded = renderNativeAd(nativeAd)
                if (isAdLoaded) onAdLoadedListener?.invoke()
            },
            failed@ { error: AdsError ->
                if (generation != requestGeneration) return@failed
                isLoading = false
                hideShimmerPlaceholder()
                visibility = View.GONE
                onAdFailedListener?.invoke(error)
            }
        )
    }

    private fun showShimmerPlaceholder() {
        hideShimmerPlaceholder()
        nativeAdView?.visibility = View.GONE
        // Inflate the selected template for measurement so custom app layouts keep their geometry.
        val placeholder = LayoutInflater.from(context).inflate(template.layoutResId, this, false)
        shimmerView = NativeAdShimmerLayout(context, placeholder).also {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun hideShimmerPlaceholder() {
        shimmerView?.let {
            it.dispose()
            removeView(it)
        }
        shimmerView = null
    }

    private fun renderNativeAd(nativeAd: NativeAd): Boolean {
        // Inflate the selected template
        val inflater = LayoutInflater.from(context)
        val inflatedAdView = inflater.inflate(template.layoutResId, this, false) as? NativeAdView
        if (inflatedAdView != null) {
            populateNativeAd(nativeAd, inflatedAdView)
            nativeAdView?.let {
                stopCtaShimmer()
                it.destroy()
                removeView(it)
            }
            ownedNativeAd?.destroy()
            ownedNativeAd = null
            nativeAdView = inflatedAdView
            ownedNativeAd = nativeAd
            addView(inflatedAdView)
            // Replace the skeleton only after the real ad's assets have been bound.
            hideShimmerPlaceholder()
            inflatedAdView.visibility = View.VISIBLE
            inflatedAdView.findViewById<View>(R.id.ad_call_to_action)?.let { cta ->
                NativeAdCtaShimmerDrawable().also { shimmer ->
                    cta.foreground = shimmer
                    ctaShimmer = shimmer
                    shimmer.start()
                }
            }
            visibility = View.VISIBLE
            return true
        }
        nativeAd.destroy()
        hideShimmerPlaceholder()
        visibility = View.GONE
        onAdFailedListener?.invoke(
            AdsError.ShowFailed("Native", 0, "Native ad layout must have a NativeAdView root.")
        )
        return false
    }

    fun destroy() {
        requestGeneration++
        isLoading = false
        hideShimmerPlaceholder()
        stopCtaShimmer()
        try {
            nativeAdView?.destroy()
            nativeAdView = null
            ownedNativeAd?.destroy()
            ownedNativeAd = null
            removeAllViews()
            shimmerView = null
            isAdLoaded = false
            AdsLogger.i(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Container destroyed; owned ad released.")
        } catch (e: Exception) {
            AdsLogger.w(AdsLogger.TAG_NATIVE, "NativeAdViewContainer: Exception during destroy: ${e.message}")
        }
    }

    private fun stopCtaShimmer() {
        ctaShimmer?.stop()
        nativeAdView?.findViewById<View>(R.id.ad_call_to_action)?.foreground = null
        ctaShimmer = null
    }
}
