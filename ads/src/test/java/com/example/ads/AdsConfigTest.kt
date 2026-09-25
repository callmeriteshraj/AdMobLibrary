package com.example.ads

import com.example.ads.util.AdMobTestIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AdsConfig resolution and test-mode logic.
 */
class AdsConfigTest {

    @Test
    fun testDefaultConfig_useTestAdsFalse_returnsConfiguredIds() {
        val config = AdsConfig(
            bannerAdUnitId = "prod-banner-123",
            interstitialAdUnitId = "prod-interstitial-456",
            rewardedAdUnitId = "prod-rewarded-789",
            rewardedInterstitialAdUnitId = "prod-rewarded-int-101",
            nativeAdUnitId = "prod-native-202",
            appOpenAdUnitId = "prod-appopen-303",
            useTestAds = false
        )

        assertEquals("prod-banner-123", config.getEffectiveBannerId())
        assertEquals("prod-interstitial-456", config.getEffectiveInterstitialId())
        assertEquals("prod-rewarded-789", config.getEffectiveRewardedId())
        assertEquals("prod-rewarded-int-101", config.getEffectiveRewardedInterstitialId())
        assertEquals("prod-native-202", config.getEffectiveNativeId())
        assertEquals("prod-appopen-303", config.getEffectiveAppOpenId())
        assertFalse(config.useTestAds)
    }

    @Test
    fun testTestModeEnabled_returnsGoogleOfficialTestIds() {
        val config = AdsConfig(
            bannerAdUnitId = "prod-banner-123",
            interstitialAdUnitId = "prod-interstitial-456",
            rewardedAdUnitId = "prod-rewarded-789",
            rewardedInterstitialAdUnitId = "prod-rewarded-int-101",
            nativeAdUnitId = "prod-native-202",
            appOpenAdUnitId = "prod-appopen-303",
            useTestAds = true
        )

        assertEquals(AdMobTestIds.ADAPTIVE_BANNER_AD_UNIT_ID, config.getEffectiveBannerId())
        assertEquals(AdMobTestIds.INTERSTITIAL_AD_UNIT_ID, config.getEffectiveInterstitialId())
        assertEquals(AdMobTestIds.REWARDED_AD_UNIT_ID, config.getEffectiveRewardedId())
        assertEquals(AdMobTestIds.REWARDED_INTERSTITIAL_AD_UNIT_ID, config.getEffectiveRewardedInterstitialId())
        assertEquals(AdMobTestIds.NATIVE_AD_UNIT_ID, config.getEffectiveNativeId())
        assertEquals(AdMobTestIds.APP_OPEN_AD_UNIT_ID, config.getEffectiveAppOpenId())
        assertTrue(config.useTestAds)
    }

    @Test
    fun testBuilderPattern() {
        val config = AdsConfig.Builder()
            .setBannerAdUnitId("builder-banner")
            .setInterstitialFrequency(5)
            .setEnableBanner(true)
            .setPreloadInterstitial(true)
            .setPreloadRewarded(true)
            .setUseTestAds(false)
            .build()

        assertEquals("builder-banner", config.getEffectiveBannerId())
        assertEquals(5, config.interstitialFrequency)
        assertTrue(config.enableBanner)
        assertTrue(config.preloadInterstitial)
        assertTrue(config.preloadRewarded)
        assertFalse(config.useTestAds)
    }
}
