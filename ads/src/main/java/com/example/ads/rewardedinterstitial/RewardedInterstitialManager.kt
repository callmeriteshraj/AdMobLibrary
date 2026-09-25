package com.example.ads.rewardedinterstitial

import android.app.Activity
import android.content.Context
import com.example.ads.AdsConfig
import com.example.ads.AdsError
import com.example.ads.AdsManager
import com.example.ads.util.AdsLogger
import com.example.ads.util.NetworkUtils
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

/**
 * Manages loading, caching, pre-fetching, and showing of Rewarded Interstitial Ads.
 */
class RewardedInterstitialManager {

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false

    val isAdAvailable: Boolean
        get() = rewardedInterstitialAd != null

    /**
     * Pre-loads a Rewarded Interstitial Ad.
     */
    fun loadRewardedInterstitial(
        context: Context,
        config: AdsConfig,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ) {
        if (!config.enableRewardedInterstitial) {
            AdsLogger.d(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Rewarded Interstitial ads are disabled in config.")
            onFailed?.invoke(AdsError.AdsDisabled)
            return
        }

        if (!AdsManager.isAdsEnabled && !config.allowRewardedForPremium) {
            AdsLogger.d(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Ads are globally disabled for premium user.")
            onFailed?.invoke(AdsError.AdsDisabled)
            return
        }

        if (isLoading) {
            AdsLogger.d(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Ad is already loading.")
            return
        }

        if (rewardedInterstitialAd != null) {
            AdsLogger.d(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Ad is already cached.")
            onLoaded?.invoke()
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            AdsLogger.w(AdsLogger.TAG_REWARDED_INTERSTITIAL, "No network connection.")
            onFailed?.invoke(AdsError.NetworkError)
            return
        }

        val adUnitId = config.getEffectiveRewardedInterstitialId()
        if (adUnitId.isBlank()) {
            AdsLogger.w(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Ad Unit ID is blank.")
            onFailed?.invoke(AdsError.NotInitialized)
            return
        }

        isLoading = true
        AdsLogger.i(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Requesting rewarded interstitial with ID: $adUnitId")

        RewardedInterstitialAd.load(
            context.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    AdsLogger.i(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Rewarded Interstitial loaded.")
                    rewardedInterstitialAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    AdsLogger.w(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Failed to load [code: ${loadAdError.code}]: ${loadAdError.message}")
                    rewardedInterstitialAd = null
                    isLoading = false
                    onFailed?.invoke(
                        AdsError.LoadFailed("RewardedInterstitial", loadAdError.code, loadAdError.message)
                    )
                }
            }
        )
    }

    /**
     * Shows the cached Rewarded Interstitial Ad.
     */
    fun showRewardedInterstitial(
        activity: Activity,
        config: AdsConfig,
        onRewardEarned: (() -> Unit)? = null,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        val ad = rewardedInterstitialAd
        AdsLogger.i(
            AdsLogger.TAG_REWARDED_INTERSTITIAL,
            "showRewardedInterstitial called | isCached=${ad != null} | isAdsEnabled=${AdsManager.isAdsEnabled} | enableRewardedInterstitial=${config.enableRewardedInterstitial}"
        )
        if (ad == null) {
            AdsLogger.d(AdsLogger.TAG_REWARDED_INTERSTITIAL, "No ad available in cache. Pre-loading next.")
            loadRewardedInterstitial(activity, config)
            onComplete(false)
            return
        }

        var rewardGranted = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsLogger.i(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Rewarded Interstitial displayed.")
                AdsManager.isFullScreenAdShowing = true
            }

            override fun onAdDismissedFullScreenContent() {
                AdsLogger.i(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Ad dismissed. Reward granted: $rewardGranted")
                rewardedInterstitialAd = null
                AdsManager.isFullScreenAdShowing = false
                // Auto pre-load next ad
                loadRewardedInterstitial(activity, config)
                onComplete(rewardGranted)
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                AdsLogger.e(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Failed to show ad [code: ${adError.code}]: ${adError.message}")
                rewardedInterstitialAd = null
                AdsManager.isFullScreenAdShowing = false
                // Auto pre-load next ad
                loadRewardedInterstitial(activity, config)
                onComplete(false)
            }

            override fun onAdImpression() {
                AdsLogger.d(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Impression recorded.")
            }

            override fun onAdClicked() {
                AdsLogger.d(AdsLogger.TAG_REWARDED_INTERSTITIAL, "Ad clicked.")
            }
        }

        ad.show(activity) { rewardItem ->
            AdsLogger.i(AdsLogger.TAG_REWARDED_INTERSTITIAL, "User earned reward: ${rewardItem.type} (${rewardItem.amount})")
            rewardGranted = true
            onRewardEarned?.invoke()
        }
    }

    /**
     * Clears cached rewarded interstitial ad to free memory.
     */
    fun destroy() {
        rewardedInterstitialAd = null
        isLoading = false
    }
}
