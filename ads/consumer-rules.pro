# ==============================================================================
# Google Mobile Ads (AdMob) & Mediation Rules
# ==============================================================================
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.android.ump.** {
   public *;
}
-keep class com.google.ads.mediation.** { *; }
-keep class com.google.android.gms.ads.mediation.** { *; }

-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.ump.**
-dontwarn com.google.ads.mediation.**

# Preserve JavaScript Interfaces for AdMob WebViews
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ==============================================================================
# Custom XML Views & Containers
# ==============================================================================
-keep public class com.example.ads.banner.BannerAdView { *; }
-keepclassmembers class com.example.ads.banner.BannerAdView { *; }

-keep public class com.example.ads.nativead.NativeAdViewContainer { *; }
-keepclassmembers class com.example.ads.nativead.NativeAdViewContainer { *; }

-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# ==============================================================================
# Ads SDK Public Facade, Managers & Configuration Models
# ==============================================================================
-keep class com.example.ads.AdsManager { *; }
-keep class com.example.ads.AdsConfig { *; }
-keep class com.example.ads.AdsConfig$Builder { *; }
-keep class com.example.ads.AdsError { *; }
-keep class com.example.ads.AdsCallbacks { *; }

# Managers
-keep class com.example.ads.appopen.AppOpenAdManager { *; }
-keep class com.example.ads.appopen.NoAdsScreen { *; }
-keep class com.example.ads.banner.BannerPreloadCache { *; }
-keep class com.example.ads.consent.AdsConsentManager { *; }
-keep class com.example.ads.interstitial.InterstitialAdManager { *; }
-keep class com.example.ads.interstitial.AdFrequencyController { *; }
-keep class com.example.ads.nativead.NativeAdManager { *; }
-keep class com.example.ads.rewarded.RewardedAdManager { *; }
-keep class com.example.ads.rewarded.CoinManager { *; }
-keep class com.example.ads.rewarded.RewardItemData { *; }
-keep class com.example.ads.rewardedinterstitial.RewardedInterstitialManager { *; }

# Utilities & Dialogs
-keep class com.example.ads.util.AdLoadingDialog { *; }
-keep class com.example.ads.util.AdsLogger { *; }
-keep class com.example.ads.util.NetworkUtils { *; }
-keep class com.example.ads.util.AdMobTestIds { *; }

# Enums
-keep class com.example.ads.banner.BannerAdSize { *; }
-keep class com.example.ads.consent.ConsentStatus { *; }
-keep class com.example.ads.nativead.NativeAdTemplate { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==============================================================================
# Lifecycle & Annotations
# ==============================================================================
-keep class * implements androidx.lifecycle.LifecycleObserver { *; }
-keep class * implements androidx.lifecycle.DefaultLifecycleObserver { *; }

-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
