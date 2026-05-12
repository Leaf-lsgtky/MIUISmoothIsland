# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\liu\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# libxposed keep rules
-keep class io.github.libxposed.api.** { *; }

# The module entry point is loaded by class name from META-INF/xposed/java_init.list.
-keep class com.example.smoothisland.XposedInit {
    public <init>(io.github.libxposed.api.XposedInterface, io.github.libxposed.api.XposedModuleInterface$ModuleInfo);
}
-keep class * extends io.github.libxposed.api.XposedModule {
    public <init>(io.github.libxposed.api.XposedInterface, io.github.libxposed.api.XposedModuleInterface$ModuleInfo);
}
