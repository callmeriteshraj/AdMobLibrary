package com.example.ads

/**
 * Callbacks for ad load events.
 */
fun interface AdLoadCallback {
    fun onAdLoaded()
}

/**
 * Callbacks for full-screen ad presentation and dismissal.
 */
interface AdShowCallback {
    fun onAdShown() {}
    fun onAdDismissed()
    fun onAdFailedToShow(error: AdsError) {}
}

/**
 * Listener for rewarded ads where user earned a reward.
 */
fun interface OnRewardEarnedListener {
    fun onRewardEarned()
}

/**
 * General ad interaction events (clicks, impressions, opens).
 */
interface AdEventListener {
    fun onAdClicked() {}
    fun onAdImpression() {}
    fun onAdOpened() {}
    fun onAdClosed() {}
}
