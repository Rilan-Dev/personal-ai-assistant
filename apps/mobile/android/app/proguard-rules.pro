# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# react-native-reanimated
-keep class com.swmansion.reanimated.** { *; }
-keep class com.facebook.react.turbomodule.** { *; }

# Add any project specific keep options here:

# Offline Vosk relies on JNA reflection to resolve native method names.
# Keep its native interface and JNA marshaling classes in release APKs.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }

# The Android JNA AAR contains an unused *desktop-only* AWT signature in
# Native.getWindowHandle0(). Android has no java.awt.Component.
# R8 otherwise rejects an otherwise-valid Android release bundle.
# Keep this suppression narrow; do not disable R8 or whole-package checks.
-dontwarn java.awt.Component
