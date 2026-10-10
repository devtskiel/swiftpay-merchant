# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\densh\AppData\Local\Android\Sdk/tools/proguard/proguard-android-optimize.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any custom rules here that might be needed for your libraries
-keep class com.example.myapplication.data.api.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okio.**
-dontwarn javax.annotation.**
