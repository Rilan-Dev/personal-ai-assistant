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

# JNA bundles unused desktop-only AWT helper signatures (Component, Window,
# GraphicsEnvironment, HeadlessException, etc.). Android has no java.awt APIs.
# Keep R8 and all other missing-class checks enabled. Scope this suppression
# ONLY to AWT, not to all JNA or application classes.
-dontwarn java.awt.**
