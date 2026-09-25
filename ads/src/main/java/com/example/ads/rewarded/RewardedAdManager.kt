package com.example.ads.rewarded

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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Manages loading, caching, pre-fetching, on-demand loading dialogs, and showing of Rewarded Ads.
 * Ensures rewards are granted ONLY when verified by Google Mobile Ads SDK callback.
 */
class RewardedAdManager {

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    val isAdAvailable: Boolean
        get() = rewardedAd != null

    /**
     * Loads/Pre-loads a Rewarded Ad.
     */
    fun loadRewarded(
        context: Context,
        config: AdsConfig,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ) {
        if (!config.enableRewarded) {
            AdsLogger.d(AdsLogger.TAG_REWARDED, "Rewarded ads are disabled in config.")
            onFailed?.invoke(AdsError.AdsDisabled)
            return
        }

        if (!AdsManager.isAdsEnabled && !config.allowRewardedForPremium) {
            AdsLogger.d(AdsLogger.TAG_REWARDED, "Ads are globally disabled for premium user.")
            onFailed?.invoke(AdsError.AdsDisabled)
            return
        }

        if (isLoading) {
            AdsLogger.d(AdsLogger.TAG_REWARDED, "Rewarded ad is already loading.")
            return
        }

        if (rewardedAd != null) {
            AdsLogger.d(AdsLogger.TAG_REWARDED, "Rewarded ad is already cached.")
            onLoaded?.invoke()
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            AdsLogger.w(AdsLogger.TAG_REWARDED, "No network connection.")
            onFailed?.invoke(AdsError.NetworkError)
            return
        }

        val adUnitId = config.getEffectiveRewardedId()
        if (adUnitId.isBlank()) {
            AdsLogger.w(AdsLogger.TAG_REWARDED, "Rewarded Ad Unit ID is blank.")
            onFailed?.invoke(AdsError.NotInitialized)
            return
        }

        isLoading = true
        AdsLogger.i(AdsLogger.TAG_REWARDED, "Requesting rewarded ad with ID: $adUnitId")

        RewardedAd.load(
            context.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    AdsLogger.i(AdsLogger.TAG_REWARDED, "Rewarded ad loaded successfully.")
                    rewardedAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    AdsLogger.w(AdsLogger.TAG_REWARDED, "Rewarded ad failed to load [code: ${loadAdError.code}]: ${loadAdError.message}")
                    rewardedAd = null
                    isLoading = false
                    onFailed?.invoke(
                        AdsError.LoadFailed("Rewarded", loadAdError.code, loadAdError.message)
                    )
                }
            }
        )
    }

    /**
     * Shows the Rewarded Ad.
     *
     * If an ad is already cached, presents it immediately.
     * If not cached and [showLoadingDialog] is true, displays a loading dialog while fetching
     * the ad on demand, then dismisses the dialog and shows the ad once ready.
     *
     * @param activity The host Activity.
     * @param config The current [AdsConfig].
     * @param showLoadingDialog If true, shows a progress dialog when loading on-demand.
     * @param onRewardEarned Callback invoked when the user watches enough of the ad to earn the reward.
     * @param onComplete Callback invoked when ad is dismissed or fails, passing whether reward was earned.
     */
    fun showRewarded(
        activity: Activity,
        config: AdsConfig,
        showLoadingDialog: Boolean = true,
        onRewardEarned: (() -> Unit)? = null,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        val cachedAd = rewardedAd
        AdsLogger.i(
            AdsLogger.TAG_REWARDED,
            "showRewarded called | isCached=${cachedAd != null} | isAdsEnabled=${AdsManager.isAdsEnabled} | enableRewarded=${config.enableRewarded} | preloadRewarded=${config.preloadRewarded}"
        )

        // Case 1: Preloaded/Cached ad is ready -> show immediately
        if (cachedAd != null) {
            showLoadedAd(activity, config, cachedAd, null, onRewardEarned, onComplete)
            return
        }

        // Check if ads or network is disabled before showing loading dialog
        if (!config.enableRewarded || (!AdsManager.isAdsEnabled && !config.allowRewardedForPremium)) {
            AdsLogger.d(AdsLogger.TAG_REWARDED, "Rewarded ads are disabled. Cannot load on-demand.")
            onComplete(false)
            return
        }

        if (!NetworkUtils.isNetworkAvailable(activity)) {
            AdsLogger.w(AdsLogger.TAG_REWARDED, "No network connection for on-demand rewarded ad.")
            onComplete(false)
            return
        }

        // Case 2: Ad is not cached -> Show loading dialog and load on-demand
        val loadingDialog = if (showLoadingDialog) AdLoadingDialog(activity) else null
        loadingDialog?.show(timeoutMillis = 10000L) {
            AdsLogger.w(AdsLogger.TAG_REWARDED, "On-demand rewarded ad load timed out.")
            onComplete(false)
        }

        loadRewarded(
            activity,
            config,
            onLoaded = {
                val newlyLoadedAd = rewardedAd
                if (newlyLoadedAd != null && !activity.isFinishing && !activity.isDestroyed) {
                    showLoadedAd(activity, config, newlyLoadedAd, loadingDialog, onRewardEarned, onComplete)
                } else {
                    loadingDialog?.dismiss()
                    onComplete(false)
                }
            },
            onFailed = { error ->
                loadingDialog?.dismiss()
                AdsLogger.w(AdsLogger.TAG_REWARDED, "On-demand rewarded ad failed to load: ${error.message}")
                onComplete(false)
            }
        )
    }

    private fun showLoadedAd(
        activity: Activity,
        config: AdsConfig,
        ad: RewardedAd,
        loadingDialog: AdLoadingDialog?,
        onRewardEarned: (() -> Unit)?,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        var rewardGranted = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsLogger.i(AdsLogger.TAG_REWARDED, "Rewarded ad displayed.")
                AdsManager.isFullScreenAdShowing = true
                // ZERO-FLICKERING GUARANTEE: Dismiss dialog only AFTER ad is rendered on screen
                loadingDialog?.dismiss()
            }

            override fun onAdDismissedFullScreenContent() {
                AdsLogger.i(AdsLogger.TAG_REWARDED, "Rewarded ad dismissed. Reward granted: $rewardGranted")
                rewardedAd = null
                AdsManager.isFullScreenAdShowing = false
                // Auto pre-load next ad ONLY if autoReplenishRewarded is enabled in config
                if (config.autoReplenishRewarded) {
                    loadRewarded(activity, config)
                }
                onComplete(rewardGranted)
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                AdsLogger.e(AdsLogger.TAG_REWARDED, "Failed to show rewarded ad [code: ${adError.code}]: ${adError.message}")
                rewardedAd = null
                AdsManager.isFullScreenAdShowing = false
                if (config.autoReplenishRewarded) {
                    loadRewarded(activity, config)
                }
                onComplete(false)
            }

            override fun onAdImpression() {
                AdsLogger.d(AdsLogger.TAG_REWARDED, "Rewarded ad impression recorded.")
            }

            override fun onAdClicked() {
                AdsLogger.d(AdsLogger.TAG_REWARDED, "Rewarded ad clicked.")
            }
        }

        ad.show(activity) { rewardItem ->
            AdsLogger.i(AdsLogger.TAG_REWARDED, "User earned reward: ${rewardItem.type} (${rewardItem.amount})")
            rewardGranted = true
            onRewardEarned?.invoke()
        }
    }

    /**
     * Clears cached rewarded ad to free memory.
     */
    fun destroy() {
        rewardedAd = null
        isLoading = false
    }
}
