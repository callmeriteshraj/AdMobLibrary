package com.example.ads.nativead

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ads.AdsConfig
import com.google.android.gms.ads.MediaContent
import com.google.android.gms.ads.MuteThisAdListener
import com.google.android.gms.ads.MuteThisAdReason
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeAdViewContainerPreloadTest {

    @Test
    fun preloadedAd_isConsumedAndRendered_withoutDirectRequest() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = AdsConfig(enableNative = true, autoReplenishNative = true)
        val cachedAd = FakeNativeAd()
        val fakeCache = FakePreloadCache().apply { preload(cachedAd) }
        var directRequestCount = 0
        var loadedCallbackCount = 0
        var boundAd: NativeAd? = null

        val container = NativeAdViewContainer(context).apply {
            configProvider = { config }
            adsEnabledProvider = { true }
            consumePreloadedAd = { _, _ -> fakeCache.consume() }
            requestNativeAd = { _, _, _, _ -> directRequestCount++ }
            populateNativeAd = { ad, _ -> boundAd = ad }
            onAdLoadedListener = { loadedCallbackCount++ }
        }

        container.loadAd()
        container.loadAd() // Duplicate calls must not consume/request another ad.

        assertSame(cachedAd, boundAd)
        assertFalse(fakeCache.isAvailable)
        assertEquals(0, directRequestCount)
        assertEquals(1, loadedCallbackCount)
        assertEquals(1, container.childCount)
        assertTrue(container.getChildAt(0) is NativeAdView)

        container.destroy()
        assertTrue(cachedAd.wasDestroyed)
    }

    private class FakePreloadCache {
        private var ad: NativeAd? = null
        val isAvailable: Boolean get() = ad != null

        fun preload(nativeAd: NativeAd) {
            ad = nativeAd
        }

        fun consume(): NativeAd? = ad.also { ad = null }
    }

    private class FakeNativeAd : NativeAd() {
        var wasDestroyed = false

        override fun getExtras() = Bundle.EMPTY
        override fun getMediaContent(): MediaContent? = null
        override fun getResponseInfo(): ResponseInfo? = null
        override fun getAdChoicesInfo(): AdChoicesInfo? = null
        override fun getIcon(): Image? = null
        override fun getStarRating(): Double? = null
        override fun getAdvertiser(): String? = null
        override fun getBody(): String? = null
        override fun getCallToAction(): String? = null
        override fun getHeadline(): String = "Cached native ad"
        override fun getPrice(): String? = null
        override fun getStore(): String? = null
        override fun getImages(): List<Image> = emptyList()
        override fun getMuteThisAdReasons(): List<MuteThisAdReason> = emptyList()
        override fun cancelUnconfirmedClick() = Unit
        override fun destroy() { wasDestroyed = true }
        override fun enableCustomClickGesture() = Unit
        override fun muteThisAd(reason: MuteThisAdReason) = Unit
        override fun performClick(clickData: Bundle) = Unit
        override fun recordCustomClickGesture() = Unit
        override fun recordEvent(eventData: Bundle): Unit = Unit
        override fun reportTouchEvent(touchEventData: Bundle) = Unit
        override fun setMuteThisAdListener(listener: MuteThisAdListener) = Unit
        override fun setOnPaidEventListener(listener: OnPaidEventListener?) = Unit
        override fun setUnconfirmedClickListener(listener: UnconfirmedClickListener) = Unit
        override fun isCustomClickGestureEnabled() = false
        override fun isCustomMuteThisAdEnabled() = false
        override fun recordImpression(impressionData: Bundle) = true
        override fun zza(): Any = this
    }
}
