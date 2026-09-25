package com.example.ads.interstitial

import android.app.Activity
import android.content.Context
import com.example.ads.AdsConfig
import com.example.ads.AdsError
import com.example.ads.AdsManager
import com.example.ads.util.AdLoadingDialog
import com.example.ads.util.AdsLogger
import com.example.ads.util.NetworkUtils
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages loading, caching, pre-fetching, on-demand loading dialogs, and showing of Interstitial Ads.
 * Designed with a zero-flicker transition mechanism between loading dialog and fullscreen ad.
 */
class InterstitialAdManager {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    val frequencyController = AdFrequencyController()

    val isAdAvailable: Boolean
        get() = interstitialAd != null

    /**
     * Loads/Pre-loads an Interstitial Ad in advance.
     */
    fun loadInterstitial(
        context: Context,
        config: AdsConfig,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ) {
        if (!config.enableInterstitial || !AdsManager.isAdsEnabled) {
            AdsLogger.d(AdsLogger.TAG_INTERSTITIAL, "Interstitials are disabled.")
            onFailed?.invoke(AdsError.AdsDisabled)
            return
        }

        if (isLoading) {
            AdsLogger.d(AdsLogger.TAG_INTERSTITIAL, "Ad is already loading.")
            return
        }

        if (interstitialAd != null) {
            AdsLogger.d(AdsLogger.TAG_INTERSTITIAL, "Ad is already cached.")
            onLoaded?.invoke()
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            AdsLogger.w(AdsLogger.TAG_INTERSTITIAL, "No network connection.")
            onFailed?.invoke(AdsError.NetworkError)
            return
        }

        val adUnitId = config.getEffectiveInterstitialId()
        if (adUnitId.isBlank()) {
            AdsLogger.w(AdsLogger.TAG_INTERSTITIAL, "Interstitial Ad Unit ID is blank.")
            onFailed?.invoke(AdsError.NotInitialized)
            return
        }

        isLoading = true
        AdsLogger.i(AdsLogger.TAG_INTERSTITIAL, "Requesting interstitial ad with ID: $adUnitId")

        InterstitialAd.load(
            context.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    AdsLogger.i(AdsLogger.TAG_INTERSTITIAL, "Interstitial ad loaded successfully.")
                    interstitialAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    AdsLogger.w(AdsLogger.TAG_INTERSTITIAL, "Interstitial failed to load [code: ${loadAdError.code}]: ${loadAdError.message}")
                    interstitialAd = null
                    isLoading = false
                    onFailed?.invoke(
                        AdsError.LoadFailed("Interstitial", loadAdError.code, loadAdError.message)
                    )
                }
            }
        )
    }

    /**
     * Shows an Interstitial Ad.
     *
     * If pre-loaded, presents immediately. If not pre-loaded, displays a smooth loading dialog
     * while loading on-demand, and dismisses the dialog with zero-flickering once the ad is rendered.
     *
     * @param activity The host Activity.
     * @param config The current [AdsConfig].
     * @param forceShow If true, bypasses the frequency counter and displays the ad immediately.
     * @param showLoadingDialog If true, displays a loading dialog when fetching on-demand.
     * @param onComplete Always invoked when the ad closes, fails, or is skipped by frequency cap.
     */
    fun showInterstitial(
        activity: Activity,
        config: AdsConfig,
        forceShow: Boolean = false,
        showLoadingDialog: Boolean = true,
        onComplete: () -> Unit
    ) {
        if (!config.enableInterstitial || !AdsManager.isAdsEnabled) {
            AdsLogger.d(AdsLogger.TAG_INTERSTITIAL, "Interstitials are disabled in config. Calling onComplete().")
            onComplete()
            return
        }

        // Check frequency capping when not forced
        if (!forceShow) {
            val shouldShow = frequencyController.recordAction()
            if (!shouldShow) {
                val current = frequencyController.getCurrentCount()
                val target = frequencyController.getFrequency()
                AdsLogger.i(
                    AdsLogger.TAG_INTERSTITIAL,
                    "Action recorded ($current/$target). Frequency cap not reached yet. Continuing app flow immediately."
                )
                onComplete()
                return
            }
        }

        val cachedAd = interstitialAd
        AdsLogger.i(
            AdsLogger.TAG_INTERSTITIAL,
            "showInterstitial triggered | isCached=${cachedAd != null} | preload=${config.preloadInterstitial}"
        )

        // Case 1: Already cached -> show immediately (0ms delay)
        if (cachedAd != null) {
            showLoadedAd(activity, config, cachedAd, null, onComplete)
            return
        }

        // Validate network and configuration before showing loading dialog
        if (!NetworkUtils.isNetworkAvailable(activity) || config.getEffectiveInterstitialId().isBlank()) {
            AdsLogger.w(AdsLogger.TAG_INTERSTITIAL, "Network unavailable or blank ID. Skipping on-demand interstitial.")
            onComplete()
            return
        }

        // Case 2: Not cached -> show loading dialog & load on demand
        val loadingDialog = if (showLoadingDialog) AdLoadingDialog(activity) else null
        loadingDialog?.show(timeoutMillis = 10000L) {
            AdsLogger.w(AdsLogger.TAG_INTERSTITIAL, "On-demand interstitial load timed out.")
            onComplete()
        }

        loadInterstitial(
            activity,
            config,
            onLoaded = {
                val newlyLoadedAd = interstitialAd
                if (newlyLoadedAd != null && !activity.isFinishing && !activity.isDestroyed) {
                    // Pass loadingDialog to dismiss seamlessly inside onAdShowedFullScreenContent
                    showLoadedAd(activity, config, newlyLoadedAd, loadingDialog, onComplete)
                } else {
                    loadingDialog?.dismiss()
                    onComplete()
                }
            },
            onFailed = { error ->
                loadingDialog?.dismiss()
                AdsLogger.w(AdsLogger.TAG_INTERSTITIAL, "On-demand interstitial failed to load: ${error.message}")
                onComplete()
            }
        )
    }

    private fun showLoadedAd(
        activity: Activity,
        config: AdsConfig,
        ad: InterstitialAd,
        loadingDialog: AdLoadingDialog?,
        onComplete: () -> Unit
    ) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsLogger.i(AdsLogger.TAG_INTERSTITIAL, "Interstitial displayed full screen.")
                AdsManager.isFullScreenAdShowing = true
                frequencyController.recordAdShown()
                // ZERO-FLICKERING GUARANTEE: Dismiss dialog only AFTER ad is rendered on screen
                loadingDialog?.dismiss()
            }

            override fun onAdDismissedFullScreenContent() {
                AdsLogger.i(AdsLogger.TAG_INTERSTITIAL, "Interstitial dismissed by user. Continuing app flow.")
                interstitialAd = null
                AdsManager.isFullScreenAdShowing = false
                if (config.autoReplenishInterstitial) {
                    loadInterstitial(activity, config)
                }
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                AdsLogger.e(AdsLogger.TAG_INTERSTITIAL, "Failed to show interstitial [code: ${adError.code}]: ${adError.message}")
                loadingDialog?.dismiss()
                interstitialAd = null
                AdsManager.isFullScreenAdShowing = false
                if (config.autoReplenishInterstitial) {
                    loadInterstitial(activity, config)
                }
                onComplete()
            }

            override fun onAdImpression() {
                AdsLogger.d(AdsLogger.TAG_INTERSTITIAL, "Interstitial impression recorded.")
            }

            override fun onAdClicked() {
                AdsLogger.d(AdsLogger.TAG_INTERSTITIAL, "Interstitial clicked.")
            }
        }

        ad.show(activity)
    }

    /**
     * Checks frequency capping and presents the interstitial only when eligible.
     * Guaranteed to ALWAYS call [onComplete].
     */
    fun showInterstitialIfEligible(
        activity: Activity,
        config: AdsConfig,
        onComplete: () -> Unit
    ) {
        showInterstitial(activity, config, forceShow = false, onComplete = onComplete)
    }

    /**
     * Cleans up references to prevent memory leaks.
     */
    fun destroy() {
        interstitialAd = null
        isLoading = false
    }
}
