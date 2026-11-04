-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class io.nekohasekai.libbox.** { *; }
-dontwarn io.nekohasekai.libbox.**

-keep class go.** { *; }
-dontwarn go.**

-keep class * implements io.nekohasekai.libbox.PlatformInterface { *; }
-keep class * implements io.nekohasekai.libbox.CommandServerHandler { *; }
-keep class * implements io.nekohasekai.libbox.CommandClientHandler { *; }
-keep class * implements io.nekohasekai.libbox.AutoRedirectHandler { *; }
-keep class com.sabifox.vpn.vpn.SabiPlatformInterface { *; }

-keepattributes JavascriptInterface, *Annotation*

-keep class com.sabifox.vpn.ui.screens.WebChannelBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.sabifox.vpn.data.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.sabifox.vpn.vpn.IpLocationInfo { *; }
-keep class com.sabifox.vpn.vpn.VpnLiveStats { *; }

