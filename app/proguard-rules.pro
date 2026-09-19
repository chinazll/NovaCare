# Hilt + WorkManager
-keep class androidx.hilt.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Shizuku
-keep class dev.rikka.shizuku.** { *; }

# Compose
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Kotlinx Serialization (若后续引入)
-keep,includedescriptorclasses class com.novacare.optimizer.**$$serializer { *; }
-keepclassmembers class com.novacare.optimizer.** {
    *** Companion;
}
-keepclasseswithmembers class com.novacare.optimizer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}