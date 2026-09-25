package com.example.ads.appopen

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.ads.AdsConfig
import com.example.ads.AdsError
import com.example.ads.AdsManager
import com.example.ads.util.AdsLogger
import com.example.ads.util.NetworkUtils
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.lang.ref.WeakReference

/**
 * Manages the complete lifecycle, background pre-caching, cooldown intervals, and display
 * of Google AdMob App Open Ads when the application enters the foreground.
 */
class AppOpenAdManager : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    companion object {
        /**
         * AdMob App Open ads expire after 4 hours according to official Google policy.
         */
        private const val AD_EXPIRATION_HOURS = 4L
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val AD_EXPIRATION_WINDOW_MILLIS = AD_EXPIRATION_HOURS * MILLIS_PER_HOUR
    }

    private var cachedAppOpenAd: AppOpenAd? = null
    @Volatile
    private var isLoadingAd: Boolean = false
    @Volatile
    private var isShowingAd: Boolean = false

    private var adLoadedTimestampMillis: Long = 0L
    private var lastDismissedTimestampMillis: Long = 0L

    private var foregroundActivityReference: WeakReference<Activity?> = WeakReference(null)
    private var isLifecycleRegistered: Boolean = false

    /**
     * Initializes the manager with the Application instance, registering activity lifecycle
     * and process foreground observers.
     */
    fun initialize(application: Application) {
        if (isLifecycleRegistered) return
        isLifecycleRegistered = true

        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        AdsLogger.i(AdsLogger.TAG_APP_OPEN, "AppOpenAdManager initialized with Activity & Process lifecycle observers.")
    }

    /**
     * Checks if a valid, non-expired App Open Ad is currently cached in memory.
     */
    val isAdAvailable: Boolean
        get() = cachedAppOpenAd != null && !isAdExpired()

    /**
     * Checks whether the currently cached ad has passed the 4-hour AdMob validity threshold.
     */
    private fun isAdExpired(): Boolean {
        if (adLoadedTimestampMillis == 0L) return true
        val timeElapsedMillis = System.currentTimeMillis() - adLoadedTimestampMillis
        return timeElapsedMillis >= AD_EXPIRATION_WINDOW_MILLIS
    }

    /**
     * Checks if the ad show cooldown interval is currently active.
     */
    private fun isCooldownActive(cooldownMillis: Long): Boolean {
        if (cooldownMillis <= 0L || lastDismissedTimestampMillis == 0L) return false
        val timeSinceLastDismissMillis = System.currentTimeMillis() - lastDismissedTimestampMillis
        return timeSinceLastDismissMillis < cooldownMillis
    }

    /**
     * Checks if the given Activity should be excluded from receiving App Open ads.
     */
    private fun isScreenExcludedFromAppOpen(activity: Activity): Boolean {
        if (activity is NoAdsScreen) return true
        if (activity.javaClass.isAnnotationPresent(ExcludedFromAppOpen::class.java)) return true
        return AdsManager.isScreenRestricted(activity.javaClass)
    }

    /**
     * Pre-loads an App Open Ad into memory in the background.
     *
     * @param context Application context or Activity context.
     * @param config The current [AdsConfig].
     * @param onLoaded Optional callback invoked when the ad finishes loading.
     * @param onFailed Optional callback invoked if loading fails.
     */
    fun loadAppOpenAd(
        context: Context,
        config: AdsConfig,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ) {
        if (!config.enableAppOpen || !AdsManager.isAdsEnabled) {
            AdsLogger.d(AdsLogger.TAG_APP_OPEN, "App Open ads are disabled in config or globally.")
            onFailed?.invoke(AdsError.AdsDisabled)
            return
        }

        if (isLoadingAd || isAdAvailable) {
            AdsLogger.d(AdsLogger.TAG_APP_OPEN, "App Open ad is already loading or a valid cached ad is available.")
            onLoaded?.invoke()
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            AdsLogger.w(AdsLogger.TAG_APP_OPEN, "No network connection available to load App Open ad.")
            onFailed?.invoke(AdsError.NetworkError)
            return
        }

        val adUnitId = config.getEffectiveAppOpenId()
        if (adUnitId.isBlank()) {
            AdsLogger.w(AdsLogger.TAG_APP_OPEN, "App Open Ad Unit ID is blank.")
            onFailed?.invoke(AdsError.NotInitialized)
            return
        }

        isLoadingAd = true
        AdsLogger.i(AdsLogger.TAG_APP_OPEN, "Requesting App Open ad from AdMob (AdUnitId: $adUnitId)...")

        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context.applicationContext,
            adUnitId,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    AdsLogger.i(AdsLogger.TAG_APP_OPEN, "App Open ad loaded successfully and cached (Valid for 4 hours).")
                    cachedAppOpenAd = ad
                    adLoadedTimestampMillis = System.currentTimeMillis()
                    isLoadingAd = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    AdsLogger.w(
                        AdsLogger.TAG_APP_OPEN,
                        "App Open ad failed to load [code: ${loadAdError.code}]: ${loadAdError.message}"
                    )
                    cachedAppOpenAd = null
                    isLoadingAd = false
                    onFailed?.invoke(
                        AdsError.LoadFailed("AppOpen", loadAdError.code, loadAdError.message)
                    )
                }
            }
        )
    }

    // Backward compatibility alias for loadAppOpenAd
    fun loadAd(
        context: Context,
        config: AdsConfig,
        onLoaded: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ) = loadAppOpenAd(context, config, onLoaded, onFailed)

    /**
     * Checks all display conditions and presents the cached App Open Ad if eligible.
     *
     * @param activity The host Activity currently in the foreground.
     * @param config The current [AdsConfig].
     * @param onDismissed Always invoked when the ad completes or if display was suppressed.
     * @param onShown Invoked when the ad opens on screen.
     * @param onFailed Invoked if ad display fails.
     */
    fun showAppOpenAdIfAvailable(
        activity: Activity,
        config: AdsConfig,
        onDismissed: (() -> Unit)? = null,
        onShown: (() -> Unit)? = null,
        onFailed: ((AdsError) -> Unit)? = null
    ) {
        val screenName = activity.javaClass.simpleName
        AdsLogger.i(
            AdsLogger.TAG_APP_OPEN,
            "showAppOpenAdIfAvailable called on screen '$screenName' | isAvailable=$isAdAvailable | isShowing=$isShowingAd"
        )

        if (!config.enableAppOpen || !AdsManager.isAdsEnabled) {
            AdsLogger.d(AdsLogger.TAG_APP_OPEN, "App Open ads are disabled. Suppressing ad display.")
            onDismissed?.invoke()
            return
        }

        if (isShowingAd) {
            AdsLogger.d(AdsLogger.TAG_APP_OPEN, "An App Open ad is already active on screen. Suppressing duplicate show.")
            onDismissed?.invoke()
            return
        }

        if (AdsManager.isFullScreenAdShowing) {
            AdsLogger.d(AdsLogger.TAG_APP_OPEN, "Another full-screen ad (Interstitial/Rewarded) is active. Suppressing App Open ad.")
            onDismissed?.invoke()
            return
        }

        if (isScreenExcludedFromAppOpen(activity)) {
            AdsLogger.d(AdsLogger.TAG_APP_OPEN, "Screen '$screenName' is marked as restricted/excluded. Suppressing App Open ad.")
            onDismissed?.invoke()
            return
        }

        if (isCooldownActive(config.appOpenAdCooldownMillis)) {
            val elapsedMillis = System.currentTimeMillis() - lastDismissedTimestampMillis
            AdsLogger.d(
                AdsLogger.TAG_APP_OPEN,
                "App Open ad cooldown active ($elapsedMillis/${config.appOpenAdCooldownMillis} ms). Suppressing ad."
            )
            onDismissed?.invoke()
            return
        }

        val ad = cachedAppOpenAd
        if (ad == null || isAdExpired()) {
            AdsLogger.d(AdsLogger.TAG_APP_OPEN, "No valid cached ad (cached=${ad != null}, expired=${isAdExpired()}). Triggering background load.")
            loadAppOpenAd(activity, config)
            onDismissed?.invoke()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsLogger.i(AdsLogger.TAG_APP_OPEN, "App Open ad displayed full screen on '$screenName'.")
                isShowingAd = true
                AdsManager.isFullScreenAdShowing = true
                onShown?.invoke()
            }

            override fun onAdDismissedFullScreenContent() {
                AdsLogger.i(AdsLogger.TAG_APP_OPEN, "App Open ad dismissed. Auto-loading next backup ad in background...")
                cachedAppOpenAd = null
                isShowingAd = false
                AdsManager.isFullScreenAdShowing = false
                lastDismissedTimestampMillis = System.currentTimeMillis()
                // Automatically pre-load next ad in background if configured
                if (config.autoReplenishAppOpen) {
                    loadAppOpenAd(activity, config)
                }
                onDismissed?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                AdsLogger.e(
                    AdsLogger.TAG_APP_OPEN,
                    "Failed to show App Open ad [code: ${adError.code}]: ${adError.message}"
                )
                cachedAppOpenAd = null
                isShowingAd = false
                AdsManager.isFullScreenAdShowing = false
                // Pre-load next ad if configured
                if (config.autoReplenishAppOpen) {
                    loadAppOpenAd(activity, config)
                }
                onFailed?.invoke(
                    AdsError.ShowFailed("AppOpen", adError.code, adError.message)
                )
                onDismissed?.invoke()
            }

            override fun onAdImpression() {
                AdsLogger.d(AdsLogger.TAG_APP_OPEN, "App Open ad impression recorded.")
            }

            override fun onAdClicked() {
                AdsLogger.d(AdsLogger.TAG_APP_OPEN, "App Open ad clicked.")
            }
        }

        ad.show(activity)
    }

    // Backward compatibility alias for showAppOpenAdIfAvailable
    fun showAdIfAvailable(
        activity: Activity,
        config: AdsConfig,
        onDismissed: (() -> Unit)? = null
    ) = showAppOpenAdIfAvailable(activity, config, onDismissed)

    /**
     * Lifecycle observer callback triggered when the application enters the foreground from the background.
     */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        AdsLogger.d(AdsLogger.TAG_APP_OPEN, "App entered foreground (ProcessLifecycleOwner.onStart).")

        val currentActivity = foregroundActivityReference.get()
        val currentConfig = AdsManager.config

        if (currentActivity != null && currentConfig != null) {
            showAppOpenAdIfAvailable(currentActivity, currentConfig)
        }
    }

    // Application.ActivityLifecycleCallbacks implementation
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (!isShowingAd) {
            foregroundActivityReference = WeakReference(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isShowingAd) {
            foregroundActivityReference = WeakReference(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (foregroundActivityReference.get() == activity) {
            foregroundActivityReference.clear()
        }
    }

    /**
     * Cleans up memory and cached references.
     */
    fun destroy() {
        cachedAppOpenAd = null
        foregroundActivityReference.clear()
        isLoadingAd = false
        isShowingAd = false
        adLoadedTimestampMillis = 0L
        lastDismissedTimestampMillis = 0L
    }
}
