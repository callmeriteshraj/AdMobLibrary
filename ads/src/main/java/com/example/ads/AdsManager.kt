package com.example.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.ViewGroup
import com.example.ads.appopen.AppOpenAdManager
import com.example.ads.banner.BannerAdSize
import com.example.ads.banner.BannerAdView
import com.example.ads.banner.BannerPreloadCache
import com.example.ads.consent.AdsConsentManager
import com.example.ads.interstitial.InterstitialAdManager
import com.example.ads.nativead.NativeAdManager
import com.example.ads.nativead.NativeAdTemplate
import com.example.ads.nativead.NativeAdViewContainer
import com.example.ads.rewarded.RewardedAdManager
import com.example.ads.rewardedinterstitial.RewardedInterstitialManager
import com.example.ads.util.AdsLogger
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.initialization.InitializationStatus

/**
 * Primary centralized facade for the Google AdMob Ads SDK.
 *
 * Provides thread-safe, non-blocking APIs for initialization, consent management,
 * ad frequency control, premium user toggles, and all 6 AdMob formats.
 */
object AdsManager {

    private val lock = Any()
    @Volatile
    private var _isInitialized = false
    val isInitialized: Boolean
        get() = _isInitialized

    @Volatile
    var isAdsEnabled: Boolean = true
        private set

    @Volatile
    var isFullScreenAdShowing: Boolean = false
        internal set

    var config: AdsConfig? = null
        private set

    lateinit var consentManager: AdsConsentManager
        private set

    val interstitialManager = InterstitialAdManager()
    val rewardedManager = RewardedAdManager()
    val rewardedInterstitialManager = RewardedInterstitialManager()
    val appOpenManager = AppOpenAdManager()
    val nativeManager = NativeAdManager()
    val coinManager = com.example.ads.rewarded.CoinManager

    private val restrictedScreens = mutableSetOf<Class<out Activity>>()

    /**
     * Initializes the Google Mobile Ads SDK with the specified [AdsConfig].
     *
     * @param context Application or Activity context.
     * @param config The [AdsConfig] defining ad units, test mode, and privacy flags.
     * @param onInitialized Optional callback when SDK initialization finishes.
     */
    fun initialize(
        context: Context,
        config: AdsConfig,
        onInitialized: ((InitializationStatus) -> Unit)? = null
    ) {
        synchronized(lock) {
            this.config = config
            AdsLogger.isLoggingEnabled = config.enableLogging

            if (!::consentManager.isInitialized) {
                consentManager = AdsConsentManager(context.applicationContext)
            }

            interstitialManager.frequencyController.updateConfig(
                config.interstitialFrequency,
                config.interstitialIntervalMillis
            )

            // Register App Open lifecycle if application context is available
            val application = context.applicationContext as? Application
            if (application != null && config.enableAppOpen) {
                appOpenManager.initialize(application)
            }

            if (_isInitialized) {
                AdsLogger.i(AdsLogger.TAG_CORE, "Already initialized.")
                return
            }

            AdsLogger.i(AdsLogger.TAG_CORE, "Initializing Google Mobile Ads SDK (TestMode=${config.useTestAds})...")

            // Configure request settings: Test device IDs, child-directed treatment, content rating
            val requestConfigBuilder = RequestConfiguration.Builder()

            if (config.useTestAds) {
                val testDevices = mutableListOf<String>()
                testDevices.addAll(config.testDeviceHashedIds)
                // Add emulator default test device
                testDevices.add(AdRequest.DEVICE_ID_EMULATOR)
                requestConfigBuilder.setTestDeviceIds(testDevices)
            }

            config.childDirectedTreatment?.let {
                requestConfigBuilder.setTagForChildDirectedTreatment(it)
            }
            config.underAgeOfConsent?.let {
                requestConfigBuilder.setTagForUnderAgeOfConsent(it)
            }
            config.maxAdContentRating?.let {
                requestConfigBuilder.setMaxAdContentRating(it)
            }

            MobileAds.setRequestConfiguration(requestConfigBuilder.build())

            // Initialize MobileAds asynchronously
            MobileAds.initialize(context.applicationContext) { status: InitializationStatus ->
                synchronized(lock) {
                    _isInitialized = true
                }
                AdsLogger.i(AdsLogger.TAG_CORE, "Google Mobile Ads SDK initialized successfully.")

                // Pre-load enabled ad formats in advance if configured for start preload
                if (isAdsEnabled) {
                    if (config.enableBanner && config.preloadBannerOnStart) {
                        BannerPreloadCache.preloadBanner(context.applicationContext, config, BannerAdSize.ADAPTIVE)
                        BannerPreloadCache.preloadBanner(context.applicationContext, config, BannerAdSize.MEDIUM_RECTANGLE)
                    }
                    if (config.enableInterstitial && config.preloadInterstitialOnStart) {
                        interstitialManager.loadInterstitial(context.applicationContext, config)
                    }
                    if (config.enableAppOpen && config.preloadAppOpenOnStart) {
                        appOpenManager.loadAd(context.applicationContext, config)
                    }
                    if (config.enableNative && config.preloadNativeOnStart) {
                        nativeManager.preloadNativeAd(context.applicationContext, config)
                    }
                    if (config.enableRewarded && config.preloadRewardedOnStart) {
                        rewardedManager.loadRewarded(context.applicationContext, config)
                    }
                    if (config.enableRewardedInterstitial && config.preloadRewardedInterstitial) {
                        rewardedInterstitialManager.loadRewardedInterstitial(context.applicationContext, config)
                    }
                }

                onInitialized?.invoke(status)
            }
        }
    }

    /**
     * Centralized UMP consent flow: Requests consent update, presents consent form if required,
     * and initializes Mobile Ads once consent is handled and ads can be requested.
     *
     * @param activity The foreground Activity.
     * @param config The [AdsConfig].
     * @param onComplete Callback invoked with result of whether ads can be requested.
     */
    fun requestConsentAndInitialize(
        activity: Activity,
        config: AdsConfig,
        onComplete: ((canRequestAds: Boolean) -> Unit)? = null
    ) {
        if (!::consentManager.isInitialized) {
            consentManager = AdsConsentManager(activity.applicationContext)
        }

        consentManager.requestConsentAndShowFormIfRequired(activity, config) { canRequestAds ->
            if (canRequestAds) {
                initialize(activity, config) {
                    onComplete?.invoke(true)
                }
            } else {
                AdsLogger.w(AdsLogger.TAG_CORE, "Consent not obtained or ads cannot be requested.")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Enables or disables ads globally (e.g. for premium / ad-free subscribers).
     * When disabled, immediately purges all in-memory ad caches to free resources.
     */
    fun setAdsEnabled(enabled: Boolean) {
        this.isAdsEnabled = enabled
        AdsLogger.i(AdsLogger.TAG_CORE, "Ads enabled state changed to: $enabled")
        if (!enabled) {
            destroyAll()
        }
    }

    /**
     * Purges all in-memory cached ads across all formats (App Open, Interstitial, Rewarded, Native, Banners).
     */
    fun destroyAll() {
        appOpenManager.destroy()
        interstitialManager.destroy()
        rewardedManager.destroy()
        rewardedInterstitialManager.destroy()
        nativeManager.destroy()
        BannerPreloadCache.clear()
        AdsLogger.i(AdsLogger.TAG_CORE, "All in-memory ad caches destroyed and cleared.")
    }

    /**
     * Registers Activity classes that should be excluded from receiving ads (e.g. App Open ads).
     */
    fun registerRestrictedScreens(vararg classes: Class<out Activity>) {
        restrictedScreens.addAll(classes)
        AdsLogger.d(AdsLogger.TAG_CORE, "Registered ${classes.size} restricted screen(s).")
    }

    /**
     * Checks whether an Activity class is registered as restricted.
     */
    fun isScreenRestricted(clazz: Class<*>): Boolean {
        return restrictedScreens.contains(clazz)
    }

    /**
     * Shows an Interstitial Ad, automatically applying the configured [AdsConfig.interstitialFrequency] capping.
     * Guaranteed to ALWAYS call [onComplete] in all situations (closed, failed, disabled, or frequency skipped).
     *
     * If cached, presents immediately with 0ms delay. If not cached, shows a loading dialog while
     * fetching the ad on-demand, then transitions smoothly to the ad with zero flickering.
     *
     * @param activity The host Activity.
     * @param forceShow If true, bypasses the frequency counter and displays the ad immediately.
     * @param showLoadingDialog If true (default), displays a loading dialog when fetching on-demand.
     * @param onComplete Callback invoked when the ad flow is finished, allowing screen navigation or logic to continue.
     */
    fun showInterstitial(
        activity: Activity,
        forceShow: Boolean = false,
        showLoadingDialog: Boolean = true,
        onComplete: () -> Unit
    ) {
        val currentConfig = config
        if (currentConfig == null) {
            onComplete()
            return
        }
        interstitialManager.showInterstitial(
            activity = activity,
            config = currentConfig,
            forceShow = forceShow,
            showLoadingDialog = showLoadingDialog,
            onComplete = onComplete
        )
    }

    /**
     * Shows an Interstitial Ad with onComplete callback.
     */
    fun showInterstitial(
        activity: Activity,
        onComplete: () -> Unit
    ) {
        showInterstitial(
            activity = activity,
            forceShow = false,
            showLoadingDialog = true,
            onComplete = onComplete
        )
    }

    /**
     * Convenience method to show an Interstitial Ad directly, forcing display and bypassing frequency capping.
     */
    fun showInterstitialDirect(
        activity: Activity,
        onComplete: () -> Unit
    ) {
        showInterstitial(activity, forceShow = true, onComplete = onComplete)
    }

    /**
     * Evaluates frequency capping and shows an Interstitial Ad only when the action threshold is reached.
     * Guaranteed to ALWAYS call [onComplete].
     */
    fun showInterstitialWithFrequency(
        activity: Activity,
        onComplete: () -> Unit
    ) {
        showInterstitial(activity, forceShow = false, onComplete = onComplete)
    }

    /**
     * Records an eligible user action towards the interstitial frequency counter.
     * @return true if threshold reached and ready for interstitial presentation.
     */
    fun recordAction(): Boolean {
        return interstitialManager.frequencyController.recordAction()
    }

    /**
     * Shows a Rewarded Ad.
     *
     * If cached, presents immediately. If not cached, shows a loading progress dialog while
     * fetching the ad on-demand, then presents the ad once ready.
     *
     * @param activity The host Activity.
     * @param showLoadingDialog If true (default), displays a loading dialog while fetching on-demand.
     * @param onRewardEarned Optional callback invoked immediately when user watches the full ad to earn the reward.
     * @param onComplete Callback invoked when the ad flow is finished (dismissed or failed), passing whether reward was earned.
     */
    fun showRewarded(
        activity: Activity,
        showLoadingDialog: Boolean = true,
        onRewardEarned: (() -> Unit)? = null,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        val currentConfig = config
        if (currentConfig == null) {
            onComplete(false)
            return
        }
        rewardedManager.showRewarded(
            activity = activity,
            config = currentConfig,
            showLoadingDialog = showLoadingDialog,
            onRewardEarned = onRewardEarned,
            onComplete = onComplete
        )
    }

    /**
     * Shows a Rewarded Ad with reward and completion callbacks.
     */
    fun showRewarded(
        activity: Activity,
        onRewardEarned: (() -> Unit)? = null,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        showRewarded(
            activity = activity,
            showLoadingDialog = true,
            onRewardEarned = onRewardEarned,
            onComplete = onComplete
        )
    }

    /**
     * Shows a Rewarded Interstitial Ad.
     *
     * @param activity The host Activity.
     * @param onRewardEarned Optional callback invoked immediately when user earns the reward.
     * @param onComplete Callback invoked when the ad flow is finished (dismissed or failed), passing whether reward was earned.
     */
    fun showRewardedInterstitial(
        activity: Activity,
        onRewardEarned: (() -> Unit)? = null,
        onComplete: (rewardEarned: Boolean) -> Unit
    ) {
        val currentConfig = config
        if (currentConfig == null) {
            onComplete(false)
            return
        }
        rewardedInterstitialManager.showRewardedInterstitial(activity, currentConfig, onRewardEarned, onComplete)
    }

    /**
     * Shows an App Open Ad explicitly if available and conditions allow.
     */
    fun showAppOpenAd(
        activity: Activity,
        onDismissed: (() -> Unit)? = null
    ) {
        val currentConfig = config
        if (currentConfig == null) {
            onDismissed?.invoke()
            return
        }
        appOpenManager.showAdIfAvailable(activity, currentConfig, onDismissed)
    }

    /**
     * Pre-loads a Native Ad in advance in the background for instant 0 ms rendering.
     */
    fun preloadNativeAd(context: Context, onLoaded: (() -> Unit)? = null) {
        val currentConfig = config ?: return
        nativeManager.preloadNativeAd(context, currentConfig, onLoaded)
    }

    /**
     * Pre-loads a Banner or Medium Rectangle Ad in advance in the background.
     */
    fun preloadBanner(context: Context, bannerSize: BannerAdSize = BannerAdSize.ADAPTIVE, onLoaded: (() -> Unit)? = null) {
        val currentConfig = config ?: return
        BannerPreloadCache.preloadBanner(context, currentConfig, bannerSize, onLoaded)
    }

    /**
     * Shows the UMP privacy options form for GDPR/CPRA consent updates.
     */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: ((com.google.android.ump.FormError?) -> Unit)? = null
    ) {
        if (!::consentManager.isInitialized) {
            consentManager = AdsConsentManager(activity.applicationContext)
        }
        consentManager.showPrivacyOptionsForm(activity) { error ->
            onDismissed?.invoke(error)
        }
    }

    /**
     * Loads and displays a Banner Ad into the specified [container] ViewGroup with a single function call.
     *
     * @param container The ViewGroup (e.g. FrameLayout, LinearLayout, or RelativeLayout) where the banner will be displayed.
     * @param bannerSize Ad size format: [BannerAdSize.ADAPTIVE], [BannerAdSize.MEDIUM_RECTANGLE], [BannerAdSize.BANNER], or [BannerAdSize.LARGE_BANNER]. Default is [BannerAdSize.ADAPTIVE].
     * @param onLoaded Optional callback invoked when the banner is loaded and displayed.
     * @param onFailed Optional callback invoked if loading fails.
     * @return The created [BannerAdView] attached to the container.
     */
    fun showBannerAd(
        container: ViewGroup,
        bannerSize: BannerAdSize = BannerAdSize.ADAPTIVE,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ): BannerAdView {
        container.removeAllViews()
        val bannerAdView = BannerAdView(container.context, bannerSize).apply {
            this.onAdLoadedListener = onLoaded
            this.onAdFailedListener = onFailed
        }
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        container.addView(bannerAdView, layoutParams)
        bannerAdView.loadAd()
        return bannerAdView
    }

    /**
     * Loads and displays a Native Ad into the specified [container] ViewGroup with a single function call.
     *
     * @param container The ViewGroup where the native ad will be rendered.
     * @param template Template style: [NativeAdTemplate.Small] or [NativeAdTemplate.Medium]. Default is [NativeAdTemplate.Small].
     * @param onLoaded Optional callback invoked when the native ad is loaded and rendered.
     * @param onFailed Optional callback invoked if loading fails.
     * @return The created [NativeAdViewContainer] attached to the container.
     */
    fun showNativeAd(
        container: ViewGroup,
        template: NativeAdTemplate = NativeAdTemplate.Small,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ): NativeAdViewContainer {
        container.removeAllViews()
        val nativeContainer = NativeAdViewContainer(container.context).apply {
            setTemplate(template)
            this.onAdLoadedListener = onLoaded
            this.onAdFailedListener = onFailed
        }
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        container.addView(nativeContainer, layoutParams)
        nativeContainer.loadAd()
        return nativeContainer
    }

    /**
     * Loads and displays the Banner Ad directly on the provided [BannerAdView] from XML.
     * Automatically uses whatever ad size (Adaptive, Medium Rectangle, etc.) is configured in XML.
     *
     * @param bannerView The [BannerAdView] defined in your XML layout.
     * @param onLoaded Optional callback invoked when the ad is loaded.
     * @param onFailed Optional callback invoked if loading fails.
     */
    fun showBanner(
        bannerView: BannerAdView,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ) {
        onLoaded?.let { bannerView.onAdLoadedListener = it }
        onFailed?.let { bannerView.onAdFailedListener = it }
        bannerView.loadAd()
    }

    /**
     * Loads and displays the Native Ad directly on the provided [NativeAdViewContainer] from XML.
     * Automatically uses whatever template (Small, Medium) is configured in XML.
     *
     * @param nativeView The [NativeAdViewContainer] defined in your XML layout.
     * @param onLoaded Optional callback invoked when the ad is loaded.
     * @param onFailed Optional callback invoked if loading fails.
     */
    fun showNative(
        nativeView: NativeAdViewContainer,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ) {
        onLoaded?.let { nativeView.onAdLoadedListener = it }
        onFailed?.let { nativeView.onAdFailedListener = it }
        nativeView.loadAd()
    }

    /**
     * Returns the user's current virtual coin balance from persisted storage.
     */
    fun getCoins(context: Context): Int = coinManager.getCoins(context)

    /**
     * Adds coins to the user's balance (e.g. after earning a reward).
     */
    fun addCoins(context: Context, amount: Int = 10): Int = coinManager.addCoins(context, amount)

    /**
     * Deducts coins from the user's balance if sufficient balance is available.
     * @return `true` if deduction was successful, `false` if balance was insufficient.
     */
    fun useCoins(context: Context, amount: Int): Boolean = coinManager.useCoins(context, amount)
}
