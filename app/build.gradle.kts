import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    // Rust NDK 支持
    id("org.gradle.android.ndk-version") apply false
}

android {
    namespace = "com.novacare.optimizer"
    // Android 16 (API 36) —— 2025 稳定版，One UI 9 基础
    compileSdk = 36
    ndkVersion = "27.0.12077973"  // Android 16 默认 NDK

    defaultConfig {
        applicationId = "com.novacare.optimizer"
        // minSdk 30 (Android 11)：覆盖 95%+ 设备，移除远古 API 兼容负担
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-alpha"
        vectorDrawables { useSupportLibrary = true }

        // Rust NDK 目标（多 ABI）
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    // 启用 Rust 编译（通过 cmake 调用 cargo）
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.maybeCreate("release").apply {
                val ksFile = System.getenv("KEYSTORE_FILE")
                if (ksFile != null) {
                    val resolved = rootProject.file(ksFile)
                    if (resolved.exists()) {
                        storeFile = resolved
                        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                        keyAlias = System.getenv("KEY_ALIAS") ?: ""
                        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                    }
                }
            }
        }
        debug {
            // debug 用 AGP 默认 debug.keystore 自动签名
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs {
            useLegacyPackaging = false  // 16KB 页对齐（Android 15+ 强制）
        }
    }
}

dependencies {
    // Compose BOM 2025.10.01：包含 Material 3 1.4 + Compose 1.9（支持 Expressive）
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)

    // AndroidX 核心
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.0")

    // Compose UI（含 Material 3 Expressive 1.4）
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")

    // 导航
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // 依赖注入
    implementation("com.google.dagger:hilt-android:2.55")
    ksp("com.google.dagger:hilt-compiler:2.55")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Shizuku：免 Root 增强能力（冻结/卸载系统应用）
    implementation("dev.rikka.shizuku:api:13.1.5")

    // 单元测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}