# Visio Ai — ProGuard / R8 rules (release builds)

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# App DB layer
-keep class com.oasismall.oasisai.data.db.** { *; }
-keep class com.oasismall.oasisai.data.model.** { *; }

# ML Kit barcode
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# NanoHTTPD (phone sync)
-keep class org.nanohttpd.** { *; }
-keepclassmembers class * extends org.nanohttpd.protocols.http.NanoHTTPD { *; }

# Coil
-dontwarn coil.**

# Kotlin coroutines / serialization metadata
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlin.Metadata { public *; }

# Compose — keep Composable method signatures
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.* <methods>;
}
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Timber
-dontwarn org.jetbrains.annotations.**
