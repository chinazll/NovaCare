# ===== UniFFI / JNA：反射与 JNI 入口必须保留 =====
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**
-dontwarn javax.swing.**
-keep class uniffi.novacare.** { *; }

# ===== Room：schema 与 DAO 实现类保留 =====
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ===== kotlinx.serialization（云端 AI 的 JSON）=====
-keepclassmembers class com.novacare.core.ai.** { *** Companion; }
-keepclasseswithmembers class com.novacare.core.ai.** {
    kotlinx.serialization.KSerializer serializer(...);
}
