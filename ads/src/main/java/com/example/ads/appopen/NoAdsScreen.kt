package com.example.ads.appopen

/**
 * Marker interface for Activities where ads (specifically App Open and full-screen interstitials) should not be shown.
 *
 * Examples: LoginActivity, PaymentActivity, SplashActivity, PremiumPurchaseActivity.
 */
interface NoAdsScreen

/**
 * Annotation to mark Activities that should be excluded from App Open ads.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExcludedFromAppOpen
