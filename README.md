# Google AdMob Ads SDK for Android (Kotlin + XML)

A production-ready, reusable Android Ads Module/SDK built with **Kotlin**, **XML Layouts**, and **Google Mobile Ads SDK (AdMob)**, with integrated **Google User Messaging Platform (UMP)** for global privacy compliance.

---

## 🚀 Key Features

* **Multi-Format Support**:
  * **Adaptive & Standard Banner Ads** (XML component with automatic sizing & collapsible support)
  * **Interstitial Ads** (Pre-loading, non-blocking callbacks & frequency capping)
  * **Rewarded Ads** (Strict reward callback verification)
  * **Rewarded Interstitial Ads** (Full-screen opt-in rewarded format)
  * **Native Ads** (Reusable XML templates: Small, Medium, Shimmer placeholder, compliant asset binding)
  * **App Open Ads** (Lifecycle-aware, foreground detection, 4h cache expiration & cooldown)
* **Zero Hardcoded App IDs**: Reusable across multiple apps (`App A`, `App B`) with distinct Firebase / AdMob configurations.
* **Safe Test Mode**: Automatically uses Google's official sample Ad Unit IDs when `useTestAds = true` to prevent invalid traffic penalties during development.
* **GDPR / UMP Privacy Consent**: Integrated User Messaging Platform consent collection and privacy options dialog.
* **Centralized Frequency Capping**: Host-controlled action threshold (e.g. show interstitial every 3 actions).
* **Ad-Free / Premium Mode**: Single switch (`AdsManager.setAdsEnabled(false)`) to globally disable ads without boilerplate checks on every screen.
* **Non-Blocking Architecture**: App flow is never interrupted if an ad fails or network is offline.
* **Restricted Screens**: Exclude sensitive screens (Login, Checkout, Splash) from App Open and Interstitial ads via `NoAdsScreen` or `@ExcludedFromAppOpen`.
* **Clean Lifecycle Management**: Safe `AdView` and `NativeAd` destruction to eliminate memory leaks.

---

## 📁 Architecture & Directory Structure

```text
AdsSDKXML/
├── ads/                                  <-- Reusable Android Library Module (:ads)
│   ├── build.gradle.kts
│   ├── consumer-rules.pro               <-- ProGuard / R8 consumer rules
│   └── src/main/
│       ├── AndroidManifest.xml          <-- Library permissions (INTERNET, ACCESS_NETWORK_STATE)
│       ├── java/com/example/ads/
│       │   ├── AdsManager.kt             <-- Primary unified facade API
│       │   ├── AdsConfig.kt              <-- Configuration & Builder
│       │   ├── AdsError.kt               <-- Standardized sealed error hierarchy
│       │   ├── AdsCallbacks.kt           <-- Listener interfaces & callbacks
│       │   ├── consent/
│       │   │   ├── AdsConsentManager.kt  <-- UMP Consent & Privacy Options flow
│       │   │   └── ConsentStatus.kt
│       │   ├── banner/
│       │   │   ├── BannerAdView.kt       <-- Custom XML View & Adaptive Banner loader
│       │   │   ├── BannerAdSize.kt       <-- ADAPTIVE, BANNER, LARGE_BANNER, MEDIUM_RECTANGLE
│       │   │   └── BannerPreloadCache.kt <-- In-memory preload cache
│       │   ├── interstitial/
│       │   │   ├── InterstitialAdManager.kt <-- Zero-flicker on-demand & preloading manager
│       │   │   └── AdFrequencyController.kt <-- Action counter & interval cooldown
│       │   ├── rewarded/
│       │   │   ├── RewardedAdManager.kt  <-- Rewarded ads with strict reward verification
│       │   │   ├── CoinManager.kt        <-- Built-in persisted coin & virtual reward manager
│       │   │   └── RewardItemData.kt
│       │   ├── rewardedinterstitial/
│       │   │   └── RewardedInterstitialManager.kt <-- Rewarded Interstitial format
│       │   ├── nativead/
│       │   │   ├── NativeAdViewContainer.kt  <-- XML container for Native Ads
│       │   │   ├── NativeAdManager.kt        <-- Native ad loader & asset binder
│       │   │   └── NativeAdTemplate.kt       <-- Small, Medium, Custom templates
│       │   ├── appopen/
│       │   │   ├── AppOpenAdManager.kt   <-- Foreground listener, cooldown & screening
│       │   │   └── NoAdsScreen.kt        <-- Marker interface / annotation
│       │   └── util/
│       │       ├── AdLoadingDialog.kt    <-- Smooth, non-blocking on-demand loading dialog
│       │       ├── AdsLogger.kt          <-- Safe debug logger
│       │       ├── AdMobTestIds.kt       <-- Google's official test ad unit constants
│       │       └── NetworkUtils.kt       <-- Non-blocking network connectivity checks
│       └── res/
│           ├── layout/
│           │   ├── layout_dialog_ad_loading.xml <-- On-demand loading dialog layout
│           │   ├── layout_native_ad_medium.xml  <-- Medium Native Ad XML layout
│           │   ├── layout_native_ad_small.xml   <-- Small Native Ad XML layout
│           │   └── layout_native_ad_shimmer.xml <-- Loading skeleton layout
│           ├── values/
│           │   ├── attrs.xml                    <-- Custom XML attributes
│           │   ├── colors.xml
│           │   └── strings.xml
│           └── drawable/
│               ├── bg_ad_badge.xml
│               ├── bg_cta_button.xml
│               ├── bg_native_card.xml
│               └── bg_shimmer_rect.xml
│
├── app/                                  <-- Demo Host Application (:app)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml          <-- AdMob App ID & Activity declarations
│       └── java/com/example/adssdkxml/
│           ├── MyApplication.kt         <-- Centralized initialization in Application class
│           ├── MainActivity.kt          <-- Showcase for all 6 ad formats
│           ├── RestrictedActivity.kt    <-- Demonstrates NoAdsScreen exclusion
│           └── NativeAdShowcaseActivity.kt <-- Demonstrates Native XML container
```

---

## 🛠️ Step 1: Add Dependencies

### 1. Include Module in `settings.gradle.kts`:
```kotlin
include(":ads")
include(":app")
```

### 2. In Host App's `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":ads"))
}
```

---

## ⚙️ Step 2: Configure AndroidManifest.xml

In your host application's `app/src/main/AndroidManifest.xml`, configure your **AdMob Application ID**:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".MyApplication"
        android:label="@string/app_name">

        <!-- Google AdMob Application ID (Never hardcode production IDs in the library) -->
        <!-- For testing, use Google's sample App ID: ca-app-pub-3940256099942544~3347511713 -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-3940256099942544~3347511713" />

    </application>
</manifest>
```

---

## 🚀 Step 3: Initialize the SDK

### Option A: Dynamic Configuration via Firebase Remote Config (Recommended)

When using Firebase Remote Config, all ad unit IDs, format switches, test mode, and frequency capping are loaded dynamically from Firebase Cloud with local XML fallbacks:

```kotlin
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Register screens where App Open ads and full-screen interstitials should be suppressed
        AdsManager.registerRestrictedScreens(
            LoginActivity::class.java,
            PaymentActivity::class.java
        )

        // Initialize Ads SDK dynamically from Firebase Remote Config
        AdsRemoteConfigManager.fetchAndInitializeAds(this) { config ->
            // SDK is ready with remote configuration
        }
    }
}
```

### Option B: In-Code Static Configuration

If not using Firebase Remote Config, initialize directly with `AdsConfig`:

```kotlin
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val adsConfig = AdsConfig.Builder()
            .setBannerAdUnitId("ca-app-pub-3940256099942544/6300978111")
            .setInterstitialAdUnitId("ca-app-pub-3940256099942544/1033173712")
            .setRewardedAdUnitId("ca-app-pub-3940256099942544/5224354917")
            .setRewardedInterstitialAdUnitId("ca-app-pub-3940256099942544/5354046379")
            .setNativeAdUnitId("ca-app-pub-3940256099942544/2247696110")
            .setAppOpenAdUnitId("ca-app-pub-3940256099942544/9257395921")
            .setEnableBanner(true)
            .setEnableInterstitial(true)
            .setEnableRewarded(true)
            .setEnableRewardedInterstitial(true)
            .setEnableNative(true)
            .setEnableAppOpen(true)
            .setUseTestAds(true)
            .setEnableLogging(true)
            .setInterstitialFrequency(3)
            .build()

        AdsManager.initialize(context = this, config = adsConfig)
        AdsManager.registerRestrictedScreens(LoginActivity::class.java)
    }
}
```

---

## 📖 Format Usage Guide

### 1. Banner Ads (XML Component)

Place the `BannerAdView` directly in any XML layout:

```xml
<com.example.ads.banner.BannerAdView
    android:id="@+id/bannerAd"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:bannerSize="adaptive"
    app:autoLoadBanner="true"
    app:collapsibleBanner="false" />
```

Available `bannerSize` attributes:
* `adaptive` (Default, fills container width with adaptive height)
* `banner` (320x50 standard banner)
* `largeBanner` (320x100)
* `mediumRectangle` (300x250)

Programmatic callbacks:
```kotlin
val bannerAd = findViewById<BannerAdView>(R.id.bannerAd)
bannerAd.onAdLoadedListener = {
    // Banner loaded
}
bannerAd.onAdFailedListener = { error ->
    // Handled gracefully without crash
}
```

---

### 2. Interstitial Ads

Show an Interstitial Ad (with automatic on-demand loading dialog and zero-flickering transition if un-cached):

```kotlin
// Shows an interstitial and automatically continues app navigation when dismissed or failed
AdsManager.showInterstitial(activity = this) {
    navigateToNextScreen()
}
```

#### Frequency-Capped Interstitials:
```kotlin
// Automatically checks configured frequency cap (e.g. shows on every 3rd action)
AdsManager.showInterstitial(
    activity = this,
    forceShow = false
) {
    navigateToNextScreen()
}
```

---

### 3. Rewarded Ads & Coin Manager

Present rewarded ads with strictly verified Google AdMob rewards and built-in SharedPreferences persistence:

```kotlin
AdsManager.showRewarded(
    activity = this,
    onRewardEarned = {
        // Automatically persist +10 coins using built-in CoinManager
        val updatedBalance = AdsManager.addCoins(this, 10)
        Toast.makeText(this, "🎉 Reward Granted! Balance: $updatedBalance", Toast.LENGTH_SHORT).show()
    },
    onComplete = { rewardEarned ->
        // Ad flow complete, continue game/feature
    }
)
```

#### Spending Virtual Coins (`useCoins`):
```kotlin
// Safely deduct coins for features/items (returns false if balance is insufficient)
val isPurchased = AdsManager.useCoins(this, 4)
if (isPurchased) {
    unlockLevel()
} else {
    showNotEnoughCoinsAlert()
}
```

---

### 4. Rewarded Interstitial Ads

```kotlin
AdsManager.showRewardedInterstitial(
    activity = this,
    onRewardEarned = {
        AdsManager.addCoins(this, 20)
    },
    onComplete = { isRewarded ->
        // Continue app flow
    }
)
```

---

### 5. Native Ads (XML Container)

Embed Native Ads into any layout or list item with automatic asset mapping:

```xml
<com.example.ads.nativead.NativeAdViewContainer
    android:id="@+id/nativeAd"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:autoLoadNative="true"
    app:nativeTemplate="medium" />
```

Templates:
* `medium`: Full card with Headline, Body, App Icon, MediaView (Image/Video), Rating, Price, Store, and CTA Button.
* `small`: Compact row without large media, ideal for Feed/List items.
* Custom layout: Programmatically pass `NativeAdTemplate.Custom(R.layout.my_custom_native_ad)`.

---

### 6. App Open Ads

App Open Ads are automatically managed across the entire application lifecycle using `ProcessLifecycleOwner`.

* **Automatic Foreground Detection**: Shows an ad when returning to the app from background.
* **4-Hour AdMob Expiration**: Expired ads are discarded and automatically refreshed.
* **Collision Suppression**: Suppresses App Open ads if an Interstitial or Rewarded ad was just displayed.
* **Restricted Screen Exclusion**: Automatically suppressed on screens implementing `NoAdsScreen` or marked with `@ExcludedFromAppOpen`.

To manually trigger an App Open Ad:
```kotlin
AdsManager.showAppOpenAd(activity = this)
```

---

## 🔒 Restricted & Ad-Free Screens

To prevent App Open Ads and full-screen interstitials from interrupting sensitive workflows (Login, Payment, Splash):

1. **Option A: Implement `NoAdsScreen`**:
```kotlin
class PaymentActivity : AppCompatActivity(), NoAdsScreen {
    // Ads suppressed while on this screen
}
```

2. **Option B: Annotate with `@ExcludedFromAppOpen`**:
```kotlin
@ExcludedFromAppOpen
class SplashActivity : AppCompatActivity() {
    // App Open ads suppressed
}
```

3. **Option C: Register in `AdsManager`**:
```kotlin
AdsManager.registerRestrictedScreens(CheckoutActivity::class.java)
```

---

## 💎 Ad-Free / Premium Users

When a user purchases an Ad-Free subscription or passes a premium check:

```kotlin
// Disable all ads globally
AdsManager.setAdsEnabled(false)
```

Effect:
* Banners are hidden and do not request network data.
* Interstitials and App Open ads are suppressed (instantly invoking `onDismissed` to ensure no UI hangs).
* Native ads are hidden.
* Rewarded ads can optionally remain enabled if `AdsConfig.allowRewardedForPremium = true`.

---

## 🛡️ Privacy, GDPR, CPRA & User Messaging Platform (UMP)

The SDK includes complete integration with Google's **User Messaging Platform (UMP)** for GDPR / CPRA compliance:

### Request Consent & Initialize:
```kotlin
AdsManager.requestConsentAndInitialize(
    activity = this,
    config = adsConfig
) { canRequestAds ->
    if (canRequestAds) {
        // Ads ready & consent granted
    }
}
```

### Show Privacy Options (Settings / GDPR Menu):
Google Play policy requires apps serving ads in the EEA/UK to provide an option for users to change their consent preferences at any time:

```kotlin
btnPrivacySettings.setOnClickListener {
    AdsManager.showPrivacyOptionsForm(this) { error ->
        // Preferences updated
    }
}
```

---

## 🧪 Official Google Test Ad Unit IDs

When `useTestAds = true`, the SDK automatically uses Google's official test ad unit IDs:

| Format | Official Test Ad Unit ID |
| :--- | :--- |
| **Adaptive Banner** | `ca-app-pub-3940256099942544/9214589741` |
| **Standard Banner** | `ca-app-pub-3940256099942544/6300978111` |
| **Interstitial** | `ca-app-pub-3940256099942544/1033173712` |
| **Rewarded** | `ca-app-pub-3940256099942544/5224354917` |
| **Rewarded Interstitial** | `ca-app-pub-3940256099942544/5354046379` |
| **Native Advanced** | `ca-app-pub-3940256099942544/2247696110` |
| **App Open** | `ca-app-pub-3940256099942544/9257395921` |

> [!WARNING]
> **Production Policy Warning**: Always set `useTestAds = true` during development and testing. Never click on your own production ads, as doing so violates Google AdMob policy and will lead to account suspension.

---

## 🛡️ ProGuard / R8 Rules

The `:ads` library includes consumer rules in `consumer-rules.pro` that are automatically applied to any host application:

```proguard
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.android.ump.** { public *; }

-keep public class com.example.ads.banner.BannerAdView { public <init>(...); }
-keep public class com.example.ads.nativead.NativeAdViewContainer { public <init>(...); }
-keep class com.example.ads.AdsConfig { *; }
-keep class com.example.ads.AdsError { *; }
-keep class * implements androidx.lifecycle.LifecycleObserver { *; }
```

---

## 📦 Standard Error Hierarchy (`AdsError`)

All callbacks return structured `AdsError` instances:

* `AdsError.NotInitialized`: SDK was not initialized before requesting ads.
* `AdsError.AdsDisabled`: Ads are disabled via global premium toggle.
* `AdsError.NotAvailable(adType)`: No ad currently cached.
* `AdsError.NetworkError`: Device is offline.
* `AdsError.LoadFailed(adType, errorCode, message)`: Google AdMob load error.
* `AdsError.ShowFailed(adType, errorCode, message)`: Full-screen display failure.
* `AdsError.ScreenRestricted(screenName)`: Suppressed on restricted screen.
* `AdsError.FrequencyCapNotReached`: Action count below threshold.
* `AdsError.CooldownActive(remainingMillis)`: Ad is on cooldown.
* `AdsError.ConsentRequired`: UMP consent is required before requesting ads.

---

## 🔍 Logcat Filter Keys (Unique per Ad Format)

To monitor or debug any ad format in Android Studio Logcat or terminal, use these unique filter keys:

| Ad Format / Component | Unique Logcat TAG / Filter Key | Description |
| :--- | :--- | :--- |
| **All SDK Logs** | `tag:AdsSDK` or `AdsSDK_` | Shows all ad events across all formats |
| **Adaptive & Standard Banner** | `tag:AdsSDK_Banner` | Banner ad requests, loads, clicks, impressions |
| **Medium Rectangle (300x250)** | `tag:AdsSDK_MREC` | MREC requests, pre-loading, render events |
| **Interstitial Ads** | `tag:AdsSDK_Interstitial` | Pre-loading, show, dismiss, frequency capping |
| **Rewarded Ads** | `tag:AdsSDK_Rewarded` | Pre-loading, show, reward verification, dismiss |
| **Rewarded Interstitial** | `tag:AdsSDK_RewardedInterstitial` | Rewarded interstitial lifecycle & reward callbacks |
| **Native Ads** | `tag:AdsSDK_Native` | Native ad pre-caching, asset binding, clicks |
| **App Open Ads** | `tag:AdsSDK_AppOpen` | Foreground detection, 4h expiry, screen restriction |
| **UMP Privacy & Consent** | `tag:AdsSDK_Consent` | Consent form loading, status changes, reset |
| **Firebase Remote Config** | `tag:AdsSDK_RemoteConfig` | Remote config fetch, updates, fallbacks |
| **Core SDK** | `tag:AdsSDK_Core` | MobileAds initialization, premium toggle |

**Terminal Example:**
```bash
# Filter only Interstitial ad logs
adb logcat -s AdsSDK_Interstitial

# Filter all Ads SDK logs
adb logcat | grep AdsSDK
```
