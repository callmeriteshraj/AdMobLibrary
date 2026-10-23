package com.example.ads.nativead

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.example.ads.AdsConfig
import com.example.ads.AdsError
import com.example.ads.AdsManager
import com.example.ads.R
import com.example.ads.util.AdsLogger
import com.example.ads.util.NetworkUtils
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MediaAspectRatio
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Manages loading, pre-caching/pre-loading, asset mapping, and lifecycle destruction for Google AdMob Native Ads.
 */
class NativeAdManager {

    private val preloadLock = Any()
    private var preloadedNativeAd: NativeAd? = null
    private var preloadedAdUnitId: String? = null
    private var isPreloading = false
    private var preloadGeneration = 0

    val isPreloadedAdAvailable: Boolean
        get() = synchronized(preloadLock) { preloadedNativeAd != null }

    /**
     * Pre-loads a Native Ad in advance in the background so it can be displayed instantly with 0 ms delay.
     */
    fun preloadNativeAd(
        context: Context,
        config: AdsConfig,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ) {
        if (!config.enableNative || !AdsManager.isAdsEnabled) {
            AdsLogger.d(AdsLogger.TAG_NATIVE, "Native ads are disabled.")
            onFailed?.invoke(AdsError.AdsDisabled)
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            AdsLogger.w(AdsLogger.TAG_NATIVE, "No network connection for preloading.")
            onFailed?.invoke(AdsError.NetworkError)
            return
        }

        val adUnitId = config.getEffectiveNativeId()
        if (adUnitId.isBlank()) {
            AdsLogger.w(AdsLogger.TAG_NATIVE, "Native Ad Unit ID is blank.")
            onFailed?.invoke(AdsError.NotInitialized)
            return
        }

        val generation = synchronized(preloadLock) {
            if (preloadedNativeAd != null && preloadedAdUnitId == adUnitId) {
                AdsLogger.i(AdsLogger.TAG_NATIVE, "Preload cache available for native ad ID: $adUnitId")
                onLoaded?.invoke()
                return
            }
            if (isPreloading) {
                AdsLogger.d(AdsLogger.TAG_NATIVE, "Native preload already in progress.")
                return
            }

            // A config/ad-unit change must never serve an ad loaded for the old unit.
            if (preloadedNativeAd != null) {
                preloadedNativeAd?.destroy()
                preloadedNativeAd = null
                preloadedAdUnitId = null
            }
            isPreloading = true
            ++preloadGeneration
        }
        AdsLogger.i(AdsLogger.TAG_NATIVE, "Native preload started with ID: $adUnitId")

        val adLoader = AdLoader.Builder(context.applicationContext, adUnitId)
            .forNativeAd { nativeAd: NativeAd ->
                val accepted = synchronized(preloadLock) {
                    if (generation != preloadGeneration) {
                        false
                    } else {
                        preloadedNativeAd?.destroy()
                        preloadedNativeAd = nativeAd
                        preloadedAdUnitId = adUnitId
                        isPreloading = false
                        true
                    }
                }
                if (accepted) {
                    AdsLogger.i(AdsLogger.TAG_NATIVE, "Native preload completed; cache available for ID: $adUnitId")
                    onLoaded?.invoke()
                } else {
                    nativeAd.destroy()
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    AdsLogger.w(AdsLogger.TAG_NATIVE, "Preload native ad failed [code: ${loadAdError.code}]: ${loadAdError.message}")
                    synchronized(preloadLock) {
                        if (generation != preloadGeneration) return
                        isPreloading = false
                    }
                    onFailed?.invoke(
                        AdsError.LoadFailed("Native", loadAdError.code, loadAdError.message)
                    )
                }

                override fun onAdImpression() {
                    AdsLogger.adImpression("native", adUnitId)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setMediaAspectRatio(MediaAspectRatio.LANDSCAPE)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Consumes the preloaded Native Ad for immediate display, and automatically triggers
     * pre-loading the next backup ad in the background.
     */
    fun consumePreloadedNativeAd(
        context: Context,
        config: AdsConfig,
        replenishAfterConsume: Boolean = config.autoReplenishNative
    ): NativeAd? {
        if (!config.enableNative || !AdsManager.isAdsEnabled) {
            AdsLogger.d(AdsLogger.TAG_NATIVE, "Native ads are disabled. Suppressing preloaded native ad consumption.")
            return null
        }
        val adUnitId = config.getEffectiveNativeId()
        val ad = synchronized(preloadLock) {
            val cachedAd = preloadedNativeAd
            if (cachedAd == null) {
                AdsLogger.d(AdsLogger.TAG_NATIVE, "Native preload cache unavailable for ID: $adUnitId")
                null
            } else if (preloadedAdUnitId != adUnitId) {
                cachedAd.destroy()
                preloadedNativeAd = null
                preloadedAdUnitId = null
                AdsLogger.w(AdsLogger.TAG_NATIVE, "Discarded native preload because the ad unit ID changed.")
                null
            } else {
                preloadedNativeAd = null
                preloadedAdUnitId = null
                cachedAd
            }
        }
        if (ad != null) {
            AdsLogger.i(AdsLogger.TAG_NATIVE, "Cached native ad consumed for ID: $adUnitId")
            if (replenishAfterConsume) {
                // The consumed ad now belongs to its view container. Refill only the shared cache.
                preloadNativeAd(context, config)
            }
        }
        return ad
    }

    /**
     * Loads a Native Ad directly and delivers it to the callback.
     */
    fun loadNativeAd(
        context: Context,
        config: AdsConfig,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (AdsError) -> Unit
    ) {
        val cachedAd = consumePreloadedNativeAd(context, config)
        if (cachedAd != null) {
            AdsLogger.i(AdsLogger.TAG_NATIVE, "NativeAdManager: Returning cached native ad before direct request.")
            onLoaded(cachedAd)
            return
        }
        loadNativeAdDirect(context, config, onLoaded, onFailed)
    }

    /** Starts a network request after the caller has already confirmed that the cache is empty. */
    internal fun loadNativeAdDirect(
        context: Context,
        config: AdsConfig,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (AdsError) -> Unit
    ) {
        if (!config.enableNative || !AdsManager.isAdsEnabled) {
            AdsLogger.d(AdsLogger.TAG_NATIVE, "Native ads are disabled.")
            onFailed(AdsError.AdsDisabled)
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            AdsLogger.w(AdsLogger.TAG_NATIVE, "No network connection.")
            onFailed(AdsError.NetworkError)
            return
        }

        val adUnitId = config.getEffectiveNativeId()
        if (adUnitId.isBlank()) {
            AdsLogger.w(AdsLogger.TAG_NATIVE, "Native Ad Unit ID is blank.")
            onFailed(AdsError.NotInitialized)
            return
        }

        AdsLogger.i(AdsLogger.TAG_NATIVE, "Direct native network request started with ID: $adUnitId")

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd: NativeAd ->
                AdsLogger.i(AdsLogger.TAG_NATIVE, "Native ad loaded successfully.")
                onLoaded(nativeAd)
                if (config.preloadNative) {
                    // Automatically pre-load next backup native ad in background
                    preloadNativeAd(context, config)
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    AdsLogger.w(AdsLogger.TAG_NATIVE, "Native ad failed to load [code: ${loadAdError.code}]: ${loadAdError.message}")
                    onFailed(
                        AdsError.LoadFailed("Native", loadAdError.code, loadAdError.message)
                    )
                }

                override fun onAdClicked() {
                    AdsLogger.d(AdsLogger.TAG_NATIVE, "Native ad clicked.")
                }

                override fun onAdImpression() {
                    AdsLogger.adImpression("native", config.getEffectiveNativeId())
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setMediaAspectRatio(MediaAspectRatio.LANDSCAPE)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Binds native ad assets to the corresponding views in [NativeAdView] according to Google AdMob policy.
     */
    fun populateNativeAdView(nativeAd: NativeAd, nativeAdView: NativeAdView) {
        val headlineView = nativeAdView.findViewById<TextView>(R.id.ad_headline)
        val bodyView = nativeAdView.findViewById<TextView>(R.id.ad_body)
        val callToActionView = nativeAdView.findViewById<View>(R.id.ad_call_to_action)
        val iconView = nativeAdView.findViewById<ImageView>(R.id.ad_app_icon)
        val starsView = nativeAdView.findViewById<RatingBar>(R.id.ad_stars)
        val advertiserView = nativeAdView.findViewById<TextView>(R.id.ad_advertiser)
        val storeView = nativeAdView.findViewById<TextView>(R.id.ad_store)
        val priceView = nativeAdView.findViewById<TextView>(R.id.ad_price)
        val mediaView = nativeAdView.findViewById<MediaView>(R.id.ad_media)

        // Headline (Required)
        if (headlineView != null) {
            nativeAdView.headlineView = headlineView
            headlineView.text = nativeAd.headline
            headlineView.visibility = if (nativeAd.headline.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        // Body
        if (bodyView != null) {
            nativeAdView.bodyView = bodyView
            bodyView.text = nativeAd.body
            bodyView.visibility = if (nativeAd.body.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        // Call to action button
        if (callToActionView != null) {
            nativeAdView.callToActionView = callToActionView
            if (callToActionView is TextView) {
                callToActionView.text = nativeAd.callToAction
            }
            callToActionView.visibility = if (nativeAd.callToAction.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        // Icon
        if (iconView != null) {
            nativeAdView.iconView = iconView
            val icon = nativeAd.icon
            if (icon?.drawable != null) {
                iconView.setImageDrawable(icon.drawable)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }
        }

        // Star rating
        if (starsView != null) {
            nativeAdView.starRatingView = starsView
            val starRating = nativeAd.starRating
            if (starRating != null && starRating > 0) {
                starsView.rating = starRating.toFloat()
                starsView.visibility = View.VISIBLE
            } else {
                starsView.visibility = View.GONE
            }
        }

        // Advertiser
        if (advertiserView != null) {
            nativeAdView.advertiserView = advertiserView
            advertiserView.text = nativeAd.advertiser
            advertiserView.visibility = if (nativeAd.advertiser.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        // Store
        if (storeView != null) {
            nativeAdView.storeView = storeView
            storeView.text = nativeAd.store
            storeView.visibility = if (nativeAd.store.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        // Price
        if (priceView != null) {
            nativeAdView.priceView = priceView
            priceView.text = nativeAd.price
            priceView.visibility = if (nativeAd.price.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        // MediaView
        if (mediaView != null) {
            nativeAdView.mediaView = mediaView
            val mediaContent = nativeAd.mediaContent
            if (mediaContent != null) {
                mediaView.mediaContent = mediaContent
                mediaView.visibility = View.VISIBLE
            } else {
                mediaView.visibility = View.GONE
            }
        }

        // Assign the native ad object to the NativeAdView container
        nativeAdView.setNativeAd(nativeAd)
        AdsLogger.i(
            AdsLogger.TAG_NATIVE,
            "Native ad view bound successfully: Headline='${nativeAd.headline}' | CTA='${nativeAd.callToAction}' | Rating=${nativeAd.starRating ?: "N/A"}"
        )
    }

    /**
     * Cleans up the native ad and releases resources.
     */
    fun destroy() {
        synchronized(preloadLock) {
            ++preloadGeneration
            preloadedNativeAd?.destroy()
            preloadedNativeAd = null
            preloadedAdUnitId = null
            isPreloading = false
        }
        AdsLogger.i(AdsLogger.TAG_NATIVE, "Shared native preload cache destroyed.")
    }
}
