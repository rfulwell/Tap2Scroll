# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep accessibility service
-keep class com.tapscroll.service.TapScrollService { *; }

# Keep data classes for DataStore
-keep class com.tapscroll.data.** { *; }

# Compose
-dontwarn androidx.compose.**
