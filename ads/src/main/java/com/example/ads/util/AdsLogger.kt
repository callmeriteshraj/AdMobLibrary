package com.example.ads.util

import android.util.Log

/**
 * Centralized logger for Google Mobile Ads SDK events and operations with unique filterable tags per format.
 * Unconditionally logs to both android.util.Log and stdout (System.out) during development so logs are
 * NEVER silenced by server-side Remote Config or multi-module configurations.
 */
object AdsLogger {

    // Unique Logcat Filter Keys for each Ad Format and Component
    const val TAG_CORE = "AdsSDK_Core"
    const val TAG_BANNER = "AdsSDK_Banner"
    const val TAG_MREC = "AdsSDK_MREC"
    const val TAG_INTERSTITIAL = "AdsSDK_Interstitial"
    const val TAG_REWARDED = "AdsSDK_Rewarded"
    const val TAG_REWARDED_INTERSTITIAL = "AdsSDK_RewardedInter"
    const val TAG_NATIVE = "AdsSDK_Native"
    const val TAG_APP_OPEN = "AdsSDK_AppOpen"
    const val TAG_CONSENT = "AdsSDK_Consent"
    const val TAG_REMOTE_CONFIG = "AdsSDK_RemoteConfig"
    const val TAG_AD_IMPRESSION = "ad_impression"

    @Volatile
    var isLoggingEnabled: Boolean = true

    fun d(message: String) = d(TAG_CORE, message)
    fun d(tag: String, message: String) {
        if (!isLoggingEnabled) return
        val logLine = "[$tag] 🔹 $message"
        try {
            Log.d(tag, logLine)
            Log.i(tag, logLine)
        } catch (_: Throwable) {
            // Safe fallback for standard JVM unit tests
        }
        println(logLine)
    }

    fun i(message: String) = i(TAG_CORE, message)
    fun i(tag: String, message: String) {
        if (!isLoggingEnabled) return
        val logLine = "[$tag] ℹ️ $message"
        try {
            Log.i(tag, logLine)
        } catch (_: Throwable) {
            // Safe fallback for standard JVM unit tests
        }
        println(logLine)
    }

    fun w(message: String) = w(TAG_CORE, message)
    fun w(tag: String, message: String) {
        if (!isLoggingEnabled) return
        val logLine = "[$tag] ⚠️ $message"
        try {
            Log.w(tag, logLine)
        } catch (_: Throwable) {
            // Safe fallback for standard JVM unit tests
        }
        println(logLine)
    }

    fun e(message: String, throwable: Throwable? = null) = e(TAG_CORE, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!isLoggingEnabled) return
        val logLine = "[$tag] ❌ $message"
        try {
            if (throwable != null) {
                Log.e(tag, logLine, throwable)
            } else {
                Log.e(tag, logLine)
            }
        } catch (_: Throwable) {
            // Safe fallback for standard JVM unit tests
        }
        if (throwable != null) {
            println("$logLine\n${throwable.stackTraceToString()}")
        } else {
            println(logLine)
        }
    }

    /** Logs an impression event to Logcat even when verbose SDK logging is disabled. */
    fun adImpression(format: String, adUnitId: String? = null) {
        val logLine = buildString {
            append("ad_impression format=")
            append(format)
            if (!adUnitId.isNullOrBlank()) {
                append(" ad_unit_id=")
                append(adUnitId)
            }
        }
        try {
            Log.i(TAG_AD_IMPRESSION, logLine)
        } catch (_: Throwable) {
            // Safe fallback for standard JVM unit tests.
        }
    }
}
