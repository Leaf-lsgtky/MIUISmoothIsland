# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\liu\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# The module entry point is loaded by class name from assets/xposed_init.
-keep class com.example.smoothisland.XposedInit { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
