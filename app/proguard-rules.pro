# ============================================================
# NovaCare R8 / ProGuard 规则
#
# 背景（P0 事故复盘）：
#   上一版 release APK 的 `libuniffi_novacare.so`（631KB）确实被打进了包，
#   但运行时永远走不到它 —— 因为 R8 把 UniFFI/JNA 的入口类裁掉了，
#   `Native.load("uniffi_novacare")` 抛 UnsatisfiedLinkError 后被
#   NovaEngine 的 runCatching 吞掉 → isAvailable = false →
#   所有功能静默返回 null，UI 上表现为「引擎不可用、什么功能都没有」。
#
#   本文件是**功能可用性的必要条件**，不是可选优化项。
# ============================================================

# ---- UniFFI 生成的 Kotlin 绑定 ----
# 命名空间由 Rust 侧 uniffi::setup_scaffolding!("novacare") 决定，固定为 uniffi.novacare
-keep class uniffi.novacare.** { *; }
-keep interface uniffi.novacare.** { *; }
-keepclassmembers class uniffi.novacare.** {
    <fields>;
    <init>(...);
}

# ---- JNA：UniFFI 的 Kotlin 绑定通过 JNA 做 FFI 调用 ----
# JNA 依赖反射 + 动态代理 + Native.load，任何裁剪都会导致
# UnsatisfiedLinkError / ClassNotFoundException
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
    <init>();
}
-keepclassmembers class * extends com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * extends com.sun.jna.Pointer { *; }
-keep class * extends com.sun.jna.ptr.PointerByReference { *; }
-keep class * extends com.sun.jna.IntegerType { *; }

# ---- 引擎门面（被 Hilt 反射注入）----
-keep class com.novacare.core.engine.** { *; }

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers,allowobfuscation class * {
    @javax.inject.* <fields>;
    @javax.inject.* <init>(...);
}
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ---- kotlinx.serialization ----
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class com.novacare.core.ai.** { *** Companion; }
-keepclasseswithmembers class com.novacare.core.ai.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.novacare.core.ai.**$$serializer { *; }

# ---- 忽略 JNA 的桌面端可选项 ----
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# 保留行号便于线上崩溃定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
