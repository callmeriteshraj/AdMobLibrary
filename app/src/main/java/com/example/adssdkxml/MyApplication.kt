package com.example.adssdkxml

import android.app.Application
import com.example.ads.AdsConfig
import com.example.ads.AdsManager
import com.example.ads.util.AdsLogger
import com.example.ads.config.AdsRemoteConfigManager
import com.example.adssdkxml.restricted.RestrictedActivity

/**
 * Host Application demonstrating centralized Google AdMob Ads SDK initialization.
 */
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Always enable logs by default
        AdsLogger.isLoggingEnabled = true
        AdsLogger.i(AdsLogger.TAG_CORE, "=======================================================")
        AdsLogger.i(AdsLogger.TAG_CORE, "🚀 MyApplication started: Initializing Ads SDK...")
        AdsLogger.i(AdsLogger.TAG_CORE, "=======================================================")

        // Register screens where App Open ads and interstitials should be suppressed
        AdsManager.registerRestrictedScreens(RestrictedActivity::class.java)

        // Initialize Ads SDK dynamically from Firebase Remote Config (with local XML defaults)
        AdsRemoteConfigManager.fetchAndInitializeAds(this) { config ->
            AdsLogger.i(AdsLogger.TAG_CORE, "✓ Ads SDK initialized and ready with config: TestMode=${config.useTestAds}")
        }
    }
}
