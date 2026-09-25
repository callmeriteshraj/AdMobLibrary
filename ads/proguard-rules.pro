# ProGuard / R8 rules for building the ads library module with isMinifyEnabled = true
-include consumer-rules.pro

# Preserve all public SDK classes and members so consumer apps can access them
-keep public class com.example.ads.** {
    public *;
    protected *;
}

# Preserve ViewBinding generated classes within ads module
-keep class com.example.ads.databinding.** { *; }

# Keep data class constructors and fields
-keepclassmembers class com.example.ads.** {
    public <init>(...);
}
