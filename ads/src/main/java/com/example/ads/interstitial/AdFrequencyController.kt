package com.example.ads.interstitial

import com.example.ads.util.AdsLogger

/**
 * Manages action-based frequency capping and cooldown intervals for Interstitial Ads.
 */
class AdFrequencyController(
    private var frequency: Int = 3,
    private var cooldownIntervalMillis: Long = 0L
) {
    private var currentCount: Int = 0
    private var lastAdShowTimeMillis: Long = 0L

    fun updateConfig(frequency: Int, cooldownMillis: Long) {
        this.frequency = frequency
        this.cooldownIntervalMillis = cooldownMillis
    }

    /**
     * Records an eligible user action and checks if the frequency cap and cooldown allow showing an interstitial.
     *
     * @return true if an interstitial should be presented; false otherwise.
     */
    @Synchronized
    fun recordAction(): Boolean {
        currentCount++
        AdsLogger.d(AdsLogger.TAG_INTERSTITIAL, "AdFrequencyController: Action count is now $currentCount/$frequency")

        if (frequency <= 1 || currentCount >= frequency) {
            if (isCooldownSatisfied()) {
                currentCount = 0
                return true
            } else {
                AdsLogger.d(AdsLogger.TAG_INTERSTITIAL, "AdFrequencyController: Frequency reached but cooldown active.")
            }
        }
        return false
    }

    /**
     * Checks whether the cooldown interval has passed without incrementing the counter.
     */
    fun isCooldownSatisfied(): Boolean {
        if (cooldownIntervalMillis <= 0L) return true
        val elapsed = System.currentTimeMillis() - lastAdShowTimeMillis
        return elapsed >= cooldownIntervalMillis
    }

    /**
     * Called when an interstitial ad is actually displayed to record timestamp.
     */
    @Synchronized
    fun recordAdShown() {
        lastAdShowTimeMillis = System.currentTimeMillis()
        currentCount = 0
    }

    fun getCurrentCount(): Int = currentCount
    fun getFrequency(): Int = frequency

    fun reset() {
        currentCount = 0
        lastAdShowTimeMillis = 0L
    }
}
