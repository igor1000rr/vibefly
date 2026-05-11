# Правила ProGuard/R8 для release-сборки.
# Основные правила берутся из proguard-android-optimize.txt.

# Kotlinx Serialization.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class by.vibefly.app.**$$serializer { *; }
-keepclassmembers class by.vibefly.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# JNI-обёртка над namespace-runtime.
-keep class by.vibefly.app.runtime.** { native <methods>; }
