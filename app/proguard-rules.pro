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

# 崩溃日志可读：保留源文件名与行号（否则上报的堆栈全是 B3.s.c 混淆名）
-keepattributes SourceFile,LineNumberTable
