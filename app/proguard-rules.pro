# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep the Inner Class responsible for JS Interface
-keepclassmembers class com.sun.alasbrowser.pwa.PwaActivity$MediaControlInterface {
    public *;
}

# Keep the class itself to avoid renaming if it's referenced
-keep class com.sun.alasbrowser.pwa.PwaActivity$MediaControlInterface

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Room entities often benefit from being kept if they are used in reflection or json serialization
-keep class com.sun.alasbrowser.data.** { *; }

# General Compose Rules (usually R8 handles this, but good to be safe if heavily optimized)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ML Kit Barcode (only keep what's needed for Play Services variant)
-keep class com.google.mlkit.vision.barcode.** { *; }
-dontwarn com.google.mlkit.**

# Media3 / ExoPlayer (keep only public API)
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# ZXing
-keep class com.google.zxing.** { *; }

# WebView JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Google Sign-In / Credential Manager
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# Coil
-dontwarn coil.**

# SLF4J (Supabase/Ktor requirement)
-dontwarn org.slf4j.impl.StaticLoggerBinder
