package com.example.ads.config

import android.content.Context
import com.example.ads.AdsConfig
import com.example.ads.AdsManager
import com.example.ads.R
import com.example.ads.util.AdsLogger
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Manages fetching, caching, and mapping Firebase Remote Config parameters
 * into an [AdsConfig] model for dynamic real-time control of the Ads SDK.
 */
object AdsRemoteConfigManager {

    // Remote Config Parameter Keys matching remote_config_defaults.xml
    const val KEY_BANNER_AD_UNIT_ID = "banner_ad_unit_id"
    const val KEY_INTERSTITIAL_AD_UNIT_ID = "interstitial_ad_unit_id"
    const val KEY_REWARDED_AD_UNIT_ID = "rewarded_ad_unit_id"
    const val KEY_REWARDED_INTERSTITIAL_AD_UNIT_ID = "rewarded_interstitial_ad_unit_id"
    const val KEY_NATIVE_AD_UNIT_ID = "native_ad_unit_id"
    const val KEY_APP_OPEN_AD_UNIT_ID = "app_open_ad_unit_id"

    // 1. BANNER KEYS
    const val KEY_ENABLE_BANNER = "enable_banner_ads"
    const val KEY_PRELOAD_BANNER_ON_START = "preload_banner_on_start"
    const val KEY_AUTO_REPLENISH_BANNER = "auto_replenish_banner"

    // 2. INTERSTITIAL KEYS
    const val KEY_ENABLE_INTERSTITIAL = "enable_interstitial_ads"
    const val KEY_PRELOAD_INTERSTITIAL_ON_START = "preload_interstitial_on_start"
    const val KEY_AUTO_REPLENISH_INTERSTITIAL = "auto_replenish_interstitial"

    // 3. APP OPEN KEYS
    const val KEY_ENABLE_APP_OPEN = "enable_app_open_ads"
    const val KEY_PRELOAD_APP_OPEN_ON_START = "preload_app_open_on_start"
    const val KEY_AUTO_REPLENISH_APP_OPEN = "auto_replenish_app_open"

    // 4. NATIVE KEYS
    const val KEY_ENABLE_NATIVE = "enable_native_ads"
    const val KEY_PRELOAD_NATIVE_ON_START = "preload_native_on_start"
    const val KEY_AUTO_REPLENISH_NATIVE = "auto_replenish_native"

    // 5. REWARDED KEYS
    const val KEY_ENABLE_REWARDED = "enable_rewarded_ads"
    const val KEY_PRELOAD_REWARDED_ON_START = "preload_rewarded_on_start"
    const val KEY_AUTO_REPLENISH_REWARDED = "auto_replenish_rewarded"

    // OTHER KEYS
    const val KEY_ENABLE_REWARDED_INTERSTITIAL = "enable_rewarded_interstitial_ads"
    const val KEY_USE_TEST_ADS = "use_test_ads"
    const val KEY_ENABLE_LOGGING = "enable_ads_logging"
    const val KEY_INTERSTITIAL_FREQUENCY = "interstitial_frequency"
    const val KEY_INTERSTITIAL_COOLDOWN = "interstitial_cooldown_millis"
    const val KEY_APP_OPEN_COOLDOWN = "app_open_cooldown_millis"

    var isFetchedFromRemote: Boolean = false
        private set

    /**
     * Initializes Firebase Remote Config with default XML values and fetches the latest
     * parameters from the server to initialize [AdsManager].
     *
     * @param context Application context.
     * @param onConfigReady Callback invoked once configuration is loaded and applied.
     */
    fun fetchAndInitializeAds(
        context: Context,
        onConfigReady: ((AdsConfig) -> Unit)? = null
    ) {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()

            // 0. Set minimum fetch interval for development (0s for instant testing, 3600s in prod)
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            // 1. Set local fallback defaults from XML
            remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

            // Initial startup configuration from defaults (offline resilient)
            val initialConfig = buildAdsConfigFromRemote(remoteConfig)
            AdsManager.initialize(context, initialConfig)
            onConfigReady?.invoke(initialConfig)

            // 2. Fetch and activate latest configuration from Firebase Cloud
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val isUpdated = task.result
                        isFetchedFromRemote = true
                        AdsLogger.i(AdsLogger.TAG_REMOTE_CONFIG, "Firebase Remote Config fetch succeeded (updated=$isUpdated).")
                        val updatedConfig = buildAdsConfigFromRemote(remoteConfig)
                        AdsManager.initialize(context, updatedConfig)
                        onConfigReady?.invoke(updatedConfig)
                    } else {
                        AdsLogger.w(AdsLogger.TAG_REMOTE_CONFIG, "Firebase Remote Config fetch failed: ${task.exception?.message}")
                    }
                }

            // 3. Real-time config updates
            remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    AdsLogger.i(AdsLogger.TAG_REMOTE_CONFIG, "Firebase Remote Config real-time update: ${configUpdate.updatedKeys}")
                    remoteConfig.activate().addOnCompleteListener {
                        val updatedConfig = buildAdsConfigFromRemote(remoteConfig)
                        AdsManager.initialize(context, updatedConfig)
                        onConfigReady?.invoke(updatedConfig)
                    }
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    AdsLogger.w(AdsLogger.TAG_REMOTE_CONFIG, "Firebase Config update listener error: ${error.message}")
                }
            })

        } catch (e: Exception) {
            AdsLogger.e(AdsLogger.TAG_REMOTE_CONFIG, "Error initializing Firebase Remote Config: ${e.message}", e)
            val fallbackConfig = AdsConfig(useTestAds = true, enableLogging = true)
            AdsManager.initialize(context, fallbackConfig)
            onConfigReady?.invoke(fallbackConfig)
        }
    }

    /**
     * Builds an [AdsConfig] instance by reading values from [FirebaseRemoteConfig].
     */
    fun buildAdsConfigFromRemote(remoteConfig: FirebaseRemoteConfig): AdsConfig {
        val bannerId = remoteConfig.getString(KEY_BANNER_AD_UNIT_ID)
        val interstitialId = remoteConfig.getString(KEY_INTERSTITIAL_AD_UNIT_ID)
        val rewardedId = remoteConfig.getString(KEY_REWARDED_AD_UNIT_ID)
        val rewardedInterstitialId = remoteConfig.getString(KEY_REWARDED_INTERSTITIAL_AD_UNIT_ID)
        val nativeId = remoteConfig.getString(KEY_NATIVE_AD_UNIT_ID)
        val appOpenId = remoteConfig.getString(KEY_APP_OPEN_AD_UNIT_ID)

        // Banner
        val enableBanner = remoteConfig.getBoolean(KEY_ENABLE_BANNER)
        val preloadBannerOnStart = remoteConfig.getBoolean(KEY_PRELOAD_BANNER_ON_START)
        val autoReplenishBanner = remoteConfig.getBoolean(KEY_AUTO_REPLENISH_BANNER)

        // Interstitial
        val enableInterstitial = remoteConfig.getBoolean(KEY_ENABLE_INTERSTITIAL)
        val preloadInterstitialOnStart = remoteConfig.getBoolean(KEY_PRELOAD_INTERSTITIAL_ON_START)
            || remoteConfig.getBoolean("preload_interstitial_ads")
        val autoReplenishInterstitial = remoteConfig.getBoolean(KEY_AUTO_REPLENISH_INTERSTITIAL)

        // App Open
        val enableAppOpen = remoteConfig.getBoolean(KEY_ENABLE_APP_OPEN)
        val preloadAppOpenOnStart = remoteConfig.getBoolean(KEY_PRELOAD_APP_OPEN_ON_START)
        val autoReplenishAppOpen = remoteConfig.getBoolean(KEY_AUTO_REPLENISH_APP_OPEN)

        // Native
        val enableNative = remoteConfig.getBoolean(KEY_ENABLE_NATIVE)
        val preloadNativeOnStart = remoteConfig.getBoolean(KEY_PRELOAD_NATIVE_ON_START)
            || remoteConfig.getBoolean("preload_native_ads")
        val autoReplenishNative = remoteConfig.getBoolean(KEY_AUTO_REPLENISH_NATIVE)

        // Rewarded
        val enableRewarded = remoteConfig.getBoolean(KEY_ENABLE_REWARDED)
        val preloadRewardedOnStart = remoteConfig.getBoolean(KEY_PRELOAD_REWARDED_ON_START)
            || remoteConfig.getBoolean("preload_rewarded_ads")
        val autoReplenishRewarded = remoteConfig.getBoolean(KEY_AUTO_REPLENISH_REWARDED)

        val enableRewardedInterstitial = remoteConfig.getBoolean(KEY_ENABLE_REWARDED_INTERSTITIAL)

        val useTestAds = remoteConfig.getBoolean(KEY_USE_TEST_ADS)
        val enableLogging = remoteConfig.getBoolean(KEY_ENABLE_LOGGING)

        val frequency = remoteConfig.getLong(KEY_INTERSTITIAL_FREQUENCY).toInt().coerceAtLeast(1)
        val interstitialCooldown = remoteConfig.getLong(KEY_INTERSTITIAL_COOLDOWN)
        val appOpenCooldown = remoteConfig.getLong(KEY_APP_OPEN_COOLDOWN).takeIf { it > 0 }
            ?: (4 * 60 * 60 * 1000L)

        AdsLogger.i(
            AdsLogger.TAG_REMOTE_CONFIG,
            "Remote Config Parsed | enableLogging=$enableLogging | useTestAds=$useTestAds | Banner(enable=$enableBanner, start=$preloadBannerOnStart, replenish=$autoReplenishBanner) | Interstitial(enable=$enableInterstitial, start=$preloadInterstitialOnStart, replenish=$autoReplenishInterstitial) | AppOpen(enable=$enableAppOpen, start=$preloadAppOpenOnStart, replenish=$autoReplenishAppOpen) | Native(enable=$enableNative, start=$preloadNativeOnStart, replenish=$autoReplenishNative) | Rewarded(enable=$enableRewarded, start=$preloadRewardedOnStart, replenish=$autoReplenishRewarded)"
        )

        return AdsConfig(
            bannerAdUnitId = bannerId,
            interstitialAdUnitId = interstitialId,
            rewardedAdUnitId = rewardedId,
            rewardedInterstitialAdUnitId = rewardedInterstitialId,
            nativeAdUnitId = nativeId,
            appOpenAdUnitId = appOpenId,
            enableBanner = enableBanner,
            preloadBannerOnStart = preloadBannerOnStart,
            autoReplenishBanner = autoReplenishBanner,
            enableInterstitial = enableInterstitial,
            preloadInterstitialOnStart = preloadInterstitialOnStart,
            autoReplenishInterstitial = autoReplenishInterstitial,
            enableAppOpen = enableAppOpen,
            preloadAppOpenOnStart = preloadAppOpenOnStart,
            autoReplenishAppOpen = autoReplenishAppOpen,
            enableNative = enableNative,
            preloadNativeOnStart = preloadNativeOnStart,
            autoReplenishNative = autoReplenishNative,
            enableRewarded = enableRewarded,
            preloadRewardedOnStart = preloadRewardedOnStart,
            autoReplenishRewarded = autoReplenishRewarded,
            enableRewardedInterstitial = enableRewardedInterstitial,
            useTestAds = useTestAds,
            enableLogging = enableLogging,
            interstitialFrequency = frequency,
            interstitialIntervalMillis = interstitialCooldown,
            appOpenAdCooldownMillis = appOpenCooldown
        )
    }
}
