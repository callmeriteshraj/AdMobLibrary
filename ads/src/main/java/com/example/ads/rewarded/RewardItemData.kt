package com.example.ads.rewarded

/**
 * Encapsulates the reward granted by Google Mobile Ads when a user completes a rewarded ad.
 */
data class RewardItemData(
    val amount: Int,
    val type: String
)
