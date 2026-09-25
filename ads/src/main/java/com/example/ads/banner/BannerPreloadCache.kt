package com.example.ads.banner

import android.content.Context
import android.view.ViewGroup
import com.example.ads.AdsConfig
import com.example.ads.AdsManager
import com.example.ads.util.AdsLogger
import com.example.ads.util.NetworkUtils
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages pre-loading and caching of Banner AdViews (Adaptive, Small/Standard, Medium Rectangle)
 * so they can be attached and displayed with 0 ms loading delay.
 */
object BannerPreloadCache {

    private val cachedBanners = ConcurrentHashMap<BannerAdSize, AdView>()
    private val loadingStates = ConcurrentHashMap<BannerAdSize, Boolean>()

    /**
     * Pre-loads a Banner AdView into memory in advance.
     */
    fun preloadBanner(
        context: Context,
        config: AdsConfig,
        bannerSize: BannerAdSize,
        onLoaded: (() -> Unit)? = null
    ) {
        val logTag = if (bannerSize == BannerAdSize.MEDIUM_RECTANGLE) AdsLogger.TAG_MREC else AdsLogger.TAG_BANNER

        if (!config.enableBanner || !AdsManager.isAdsEnabled) {
            AdsLogger.d(logTag, "Banner ads are disabled.")
            return
        }

        if (loadingStates[bannerSize] == true || cachedBanners.containsKey(bannerSize)) {
            AdsLogger.d(logTag, "Banner for size $bannerSize is already cached or loading.")
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            AdsLogger.w(logTag, "No network connection for preloading banner.")
            return
        }

        val adUnitId = config.getEffectiveBannerId()
        if (adUnitId.isBlank()) {
            AdsLogger.w(logTag, "Banner Ad Unit ID is blank.")
            return
        }

        loadingStates[bannerSize] = true
        AdsLogger.i(logTag, "Pre-loading backup banner for size: $bannerSize")

        val adView = AdView(context.applicationContext).apply {
            this.adUnitId = adUnitId
            setAdSize(bannerSize.toAdSize(context.applicationContext))
        }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                AdsLogger.i(logTag, "Backup banner ($bannerSize) preloaded successfully.")
                loadingStates[bannerSize] = false
                cachedBanners[bannerSize] = adView
                onLoaded?.invoke()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                AdsLogger.w(logTag, "Preload banner failed [code: ${loadAdError.code}]: ${loadAdError.message}")
                loadingStates[bannerSize] = false
                cachedBanners.remove(bannerSize)
            }
        }

        adView.loadAd(AdRequest.Builder().build())
    }

    /**
     * Consumes the preloaded banner AdView for the specified size, detaches it from any previous parent,
     * and triggers preloading a new backup banner in the background.
     */
    fun consumePreloadedBanner(context: Context, config: AdsConfig, bannerSize: BannerAdSize): AdView? {
        val logTag = if (bannerSize == BannerAdSize.MEDIUM_RECTANGLE) AdsLogger.TAG_MREC else AdsLogger.TAG_BANNER
        val adView = cachedBanners.remove(bannerSize)
        if (adView != null) {
            AdsLogger.i(logTag, "Consuming preloaded banner for size: $bannerSize")
            // Detach from previous parent if any
            (adView.parent as? ViewGroup)?.removeView(adView)
            // Replenish backup banner in background for next screen if configured
            if (config.autoReplenishBanner) {
                preloadBanner(context, config, bannerSize)
            }
            return adView
        }
        return null
    }

    /**
     * Clears all preloaded banner caches.
     */
    fun clear() {
        for ((_, adView) in cachedBanners) {
            try {
                adView.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }
        cachedBanners.clear()
        loadingStates.clear()
    }
}
