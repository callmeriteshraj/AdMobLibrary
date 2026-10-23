package com.example.ads.banner

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.example.ads.AdsConfig
import com.example.ads.AdsManager
import com.example.ads.util.AdsLogger
import com.example.ads.util.NetworkUtils
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import java.util.ArrayDeque

/**
 * Bounded pools of preloaded banner views. A view leaves the pool permanently when it is
 * attached to a placement; it is never reused for a second placement.
 *
 * All pool and AdView operations are serialized on the main thread because AdView is a UI view.
 */
object BannerPreloadCache {

    private data class PoolKey(
        val adUnitId: String,
        val bannerSize: BannerAdSize,
        val widthDp: Int,
        val heightDp: Int
    )

    private class Pool {
        val ready = ArrayDeque<AdView>()
        val waiters = mutableListOf<() -> Unit>()
        var loadingCount: Int = 0
        var capacity: Int = 0
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pools = mutableMapOf<PoolKey, Pool>()

    /**
     * Fills the pool up to the configured capacity. [onLoaded] is called when at least one
     * preloaded view is available (or immediately if one is already available).
     */
    fun preloadBanner(
        context: Context,
        config: AdsConfig,
        bannerSize: BannerAdSize,
        onLoaded: (() -> Unit)? = null
    ) {
        runOnMain {
            val capacity = config.effectiveBannerPreloadCount
            if (!config.enableBanner || !AdsManager.isAdsEnabled || capacity <= 0) {
                AdsLogger.d(AdsLogger.TAG_BANNER, "Banner preloading is disabled or capacity is zero.")
                return@runOnMain
            }

            if (!NetworkUtils.isNetworkAvailable(context)) {
                AdsLogger.w(AdsLogger.TAG_BANNER, "No network connection for preloading banner.")
                return@runOnMain
            }

            val adUnitId = config.getEffectiveBannerId()
            if (adUnitId.isBlank()) {
                AdsLogger.w(AdsLogger.TAG_BANNER, "Banner Ad Unit ID is blank.")
                return@runOnMain
            }

            val adSize = bannerSize.toAdSize(context)
            val key = PoolKey(adUnitId, bannerSize, adSize.width, adSize.height)
            val pool = pools.getOrPut(key) { Pool() }
            pool.capacity = capacity
            while (pool.ready.size > capacity) {
                pool.ready.removeLast().destroy()
            }

            if (pool.ready.isNotEmpty()) {
                onLoaded?.invoke()
            } else if (onLoaded != null) {
                pool.waiters += onLoaded
            }

            fillToCapacity(context.applicationContext, key, pool, adSize, capacity)
        }
    }

    /**
     * Consumes one preloaded view and starts a background refill. The returned AdView belongs
     * exclusively to the caller and must be destroyed when its placement is removed.
     */
    fun consumePreloadedBanner(context: Context, config: AdsConfig, bannerSize: BannerAdSize): AdView? {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Preloaded banner views must be consumed on the main thread."
        }

        if (!config.enableBanner || !AdsManager.isAdsEnabled || config.effectiveBannerPreloadCount <= 0) {
            return null
        }

        val adUnitId = config.getEffectiveBannerId()
        if (adUnitId.isBlank()) return null

        val adSize = bannerSize.toAdSize(context)
        val key = PoolKey(adUnitId, bannerSize, adSize.width, adSize.height)
        val pool = pools[key] ?: return null
        pool.capacity = config.effectiveBannerPreloadCount
        while (pool.ready.size > pool.capacity) {
            pool.ready.removeLast().destroy()
        }
        val adView = pool.ready.pollFirst() ?: return null

        AdsLogger.i(AdsLogger.TAG_BANNER, "Consuming preloaded banner for $bannerSize; ${pool.ready.size} remain.")
        (adView.parent as? ViewGroup)?.removeView(adView)
        fillToCapacity(context.applicationContext, key, pool, adSize, config.effectiveBannerPreloadCount)
        return adView
    }

    /**
     * Maintains the ready pool after a direct banner load succeeds. This warms later placements
     * even when startup preloading was disabled.
     */
    fun replenish(context: Context, config: AdsConfig, bannerSize: BannerAdSize) {
        preloadBanner(context.applicationContext, config, bannerSize)
    }

    private fun fillToCapacity(
        context: Context,
        key: PoolKey,
        pool: Pool,
        adSize: AdSize,
        capacity: Int
    ) {
        while (pool.ready.size + pool.loadingCount < capacity && AdsManager.isAdsEnabled) {
            pool.loadingCount++
            val adView = AdView(context).apply {
                adUnitId = key.adUnitId
                setAdSize(adSize)
            }

            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    pool.loadingCount = (pool.loadingCount - 1).coerceAtLeast(0)

                    // The pool may have been cleared or replaced while this request was running.
                    if (pools[key] !== pool || !AdsManager.isAdsEnabled || pool.ready.size >= pool.capacity) {
                        adView.destroy()
                        return
                    }

                    pool.ready.addLast(adView)
                    AdsLogger.i(
                        AdsLogger.TAG_BANNER,
                        "Preloaded ${key.bannerSize} banner; ${pool.ready.size} ready."
                    )
                    pool.waiters.toList().also { pool.waiters.clear() }.forEach { it.invoke() }
                    fillToCapacity(context, key, pool, adSize, pool.capacity)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    pool.loadingCount = (pool.loadingCount - 1).coerceAtLeast(0)
                    adView.destroy()
                    AdsLogger.w(
                        AdsLogger.TAG_BANNER,
                        "Banner preload failed [code: ${loadAdError.code}]: ${loadAdError.message}"
                    )
                    // Do not immediately retry a failed request. A later explicit preload or
                    // successful placement load can try again without creating a retry loop.
                }

                override fun onAdImpression() {
                    AdsLogger.adImpression("banner", key.adUnitId)
                }
            }

            adView.loadAd(AdRequest.Builder().build())
        }
    }

    /** Destroys all queued views. In-flight loads are destroyed when their callbacks return. */
    fun clear() {
        runOnMain {
            val oldPools = pools.values.toList()
            pools.clear()
            oldPools.forEach { pool ->
                pool.waiters.clear()
                while (pool.ready.isNotEmpty()) {
                    pool.ready.removeFirst().destroy()
                }
            }
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
}
