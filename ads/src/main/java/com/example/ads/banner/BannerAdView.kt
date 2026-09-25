package com.example.ads.banner

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.example.ads.AdsError
import com.example.ads.AdsManager
import com.example.ads.R
import com.example.ads.util.AdsLogger
import com.example.ads.util.NetworkUtils
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Production-ready XML Banner Ad View component.
 *
 * Can be placed directly in XML layouts:
 * ```xml
 * <com.example.ads.banner.BannerAdView
 *     android:id="@+id/bannerAd"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:adSize="adaptive"
 *     app:autoLoad="true"
 *     app:collapsible="false" />
 * ```
 */
class BannerAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), DefaultLifecycleObserver {

    constructor(context: Context, bannerSize: BannerAdSize) : this(context) {
        this.bannerSize = bannerSize
        this.isAutoLoad = false
    }

    private var adView: AdView? = null
    private var bannerSize: BannerAdSize = BannerAdSize.ADAPTIVE
    private var isAutoLoad: Boolean = false
    private var isCollapsible: Boolean = false
    private var customAdUnitId: String? = null
    private var isAdLoaded: Boolean = false
    private var lifecycleBound = false

    fun setBannerSize(bannerSize: BannerAdSize) {
        this.bannerSize = bannerSize
    }

    fun setCollapsible(collapsible: Boolean) {
        this.isCollapsible = collapsible
    }

    var onAdLoadedListener: (() -> Unit)? = null
    var onAdFailedListener: ((AdsError) -> Unit)? = null
    var onAdClickListener: (() -> Unit)? = null

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BannerAdView)
            try {
                val sizeIndex = typedArray.getInt(R.styleable.BannerAdView_bannerSize, 0)
                bannerSize = when (sizeIndex) {
                    0 -> BannerAdSize.ADAPTIVE
                    1 -> BannerAdSize.BANNER
                    2 -> BannerAdSize.LARGE_BANNER
                    3 -> BannerAdSize.MEDIUM_RECTANGLE
                    else -> BannerAdSize.ADAPTIVE
                }
                isAutoLoad = typedArray.getBoolean(R.styleable.BannerAdView_autoLoadBanner, false)
                isCollapsible = typedArray.getBoolean(R.styleable.BannerAdView_collapsibleBanner, false)
            } finally {
                typedArray.recycle()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bindLifecycle()
        if (isAutoLoad && !isAdLoaded) {
            post { loadAd() }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }

    private fun bindLifecycle() {
        if (!lifecycleBound) {
            val lifecycleOwner = findViewTreeLifecycleOwner()
            lifecycleOwner?.lifecycle?.addObserver(this)
            lifecycleBound = true
        }
    }

    /**
     * Loads the banner ad into this view.
     *
     * @param adUnitId Optional custom ad unit ID override. If null or blank, uses [AdsManager.config].
     */
    fun loadAd(adUnitId: String? = null) {
        if (adUnitId != null) {
            this.customAdUnitId = adUnitId
        }

        val logTag = if (bannerSize == BannerAdSize.MEDIUM_RECTANGLE) AdsLogger.TAG_MREC else AdsLogger.TAG_BANNER

        if (!AdsManager.isAdsEnabled) {
            AdsLogger.d(logTag, "Ads are globally disabled. Hiding banner.")
            visibility = View.GONE
            onAdFailedListener?.invoke(AdsError.AdsDisabled)
            return
        }

        val effectiveAdUnitId = customAdUnitId
            ?: AdsManager.config?.getEffectiveBannerId()
            ?: ""

        if (effectiveAdUnitId.isBlank()) {
            AdsLogger.w(logTag, "No Ad Unit ID configured. Call AdsManager.initialize() or provide adUnitId.")
            visibility = View.GONE
            onAdFailedListener?.invoke(AdsError.NotInitialized)
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            AdsLogger.w(logTag, "No network connection available.")
            visibility = View.GONE
            onAdFailedListener?.invoke(AdsError.NetworkError)
            return
        }

        // Check if a pre-loaded backup banner is already available for this size
        val config = AdsManager.config
        if (config != null && !isCollapsible && customAdUnitId == null) {
            val preloadedAdView = BannerPreloadCache.consumePreloadedBanner(context, config, bannerSize)
            if (preloadedAdView != null) {
                AdsLogger.i(logTag, "Attaching pre-loaded banner ($bannerSize) for instant display.")
                this.adView = preloadedAdView
                val layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                addView(preloadedAdView, layoutParams)
                isAdLoaded = true
                visibility = View.VISIBLE
                onAdLoadedListener?.invoke()
                return
            }
        }

        // Clean up previous AdView if any
        adView?.let {
            it.destroy()
            removeView(it)
        }

        val newAdView = AdView(context).apply {
            this.adUnitId = effectiveAdUnitId
            setAdSize(bannerSize.toAdSize(context))
        }

        val requestBuilder = AdRequest.Builder()

        // Handle collapsible banner if enabled
        if (isCollapsible) {
            val extras = Bundle().apply {
                putString("collapsible", "bottom")
            }
            requestBuilder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }

        newAdView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                AdsLogger.i(logTag, "Ad loaded successfully for ID: $effectiveAdUnitId ($bannerSize)")
                isAdLoaded = true
                visibility = View.VISIBLE
                onAdLoadedListener?.invoke()
                // Automatically preload next backup banner in background if configured
                if (config != null && !isCollapsible && config.autoReplenishBanner) {
                    BannerPreloadCache.preloadBanner(context, config, bannerSize)
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                AdsLogger.w(logTag, "Ad failed to load [code: ${loadAdError.code}]: ${loadAdError.message}")
                isAdLoaded = false
                visibility = View.GONE
                onAdFailedListener?.invoke(
                    AdsError.LoadFailed("Banner", loadAdError.code, loadAdError.message)
                )
            }

            override fun onAdClicked() {
                AdsLogger.d(logTag, "Ad clicked.")
                onAdClickListener?.invoke()
            }

            override fun onAdImpression() {
                AdsLogger.d(logTag, "Ad impression recorded.")
            }
        }

        this.adView = newAdView
        val layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        addView(newAdView, layoutParams)

        AdsLogger.d(logTag, "Requesting banner ad for ID: $effectiveAdUnitId ($bannerSize)")
        newAdView.loadAd(requestBuilder.build())
    }

    fun pause() {
        adView?.pause()
    }

    fun resume() {
        adView?.resume()
    }

    fun destroy() {
        try {
            adView?.destroy()
            adView = null
            removeAllViews()
            isAdLoaded = false
        } catch (e: Exception) {
            val logTag = if (bannerSize == BannerAdSize.MEDIUM_RECTANGLE) AdsLogger.TAG_MREC else AdsLogger.TAG_BANNER
            AdsLogger.w(logTag, "Exception during destroy: ${e.message}")
        }
    }

    // Lifecycle Observer implementations
    override fun onResume(owner: LifecycleOwner) {
        resume()
    }

    override fun onPause(owner: LifecycleOwner) {
        pause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        destroy()
        owner.lifecycle.removeObserver(this)
        lifecycleBound = false
    }
}
