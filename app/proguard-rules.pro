# Moshi
-keep class com.paperbox.app.data.api.models.** { *; }
-keepclassmembers class com.paperbox.app.data.api.models.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Hilt
-dontwarn dagger.hilt.**
