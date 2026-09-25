package com.example.ads

/**
 * Standardized ad error hierarchy across all Google Mobile Ads formats.
 */
sealed class AdsError(open val message: String) {

    object NotInitialized : AdsError("Ads SDK has not been initialized. Call AdsManager.initialize() first.")

    object AdsDisabled : AdsError("Ads are currently disabled (e.g. premium user or global toggle).")

    data class NotAvailable(val adType: String) : AdsError("No $adType ad is currently cached or ready to display.")

    object NetworkError : AdsError("Network is unavailable. Ad request cannot proceed.")

    data class LoadFailed(
        val adType: String,
        val errorCode: Int,
        override val message: String
    ) : AdsError("Failed to load $adType ad [code: $errorCode]: $message")

    data class ShowFailed(
        val adType: String,
        val errorCode: Int,
        override val message: String
    ) : AdsError("Failed to show $adType ad [code: $errorCode]: $message")

    data class ScreenRestricted(val screenName: String) :
        AdsError("Ad display is suppressed because screen '$screenName' is marked as restricted.")

    data class FrequencyCapNotReached(val currentCount: Int, val requiredCount: Int) :
        AdsError("Ad frequency cap not reached ($currentCount/$requiredCount actions).")

    data class CooldownActive(val remainingMillis: Long) :
        AdsError("Ad display is on cooldown. Remaining time: ${remainingMillis / 1000}s.")

    object ConsentRequired : AdsError("User consent is required before requesting ads under UMP/GDPR policy.")
}
