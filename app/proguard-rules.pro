# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\tools\adt-bundle-windows-x86_64-20131030\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keepattributes InnerClasses

-keep class io.github.rosemoe**
-keepclassmembers class io.github.rosemoe** { *; }

-keep class com.tyron**
-keepclassmembers class com.tyron** { *; }

-keep class org.eclipse**
-keepclassmembers class org.eclipse** { *; }

-keep class java.lang**
-keepclassmembers class java.lang** { *; }
-keep class org.h2.** { *; }
-dontwarn org.h2.**
