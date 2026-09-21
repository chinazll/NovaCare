plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.novacare.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.novacare.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // 版本号规则：每次发版 versionCode 严格 +1，versionName 按语义化版本推进。
        // 不是 GitHub 的要求 —— GitHub 只认 tag（用于触发发版 workflow），
        // versionCode/versionName 完全由本文件决定。前几轮改了代码却没推进版本号，
        // 导致一直停在 0.9.0-alpha，这里是纠正。
        versionCode = 18
        versionName = "0.20.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 签名信息全部来自环境变量（CI Secrets），**不含任何明文密码**
        // rootProject.file() 关键：在多模块工程里，app 子模块的 file() 会按子模块目录
        // 解析，导致 `KEYSTORE_FILE=.ci-keystore/novacare.jks` 变成
        // `app/.ci-keystore/novacare.jks`（找不到）。rootProject.file() 强制按根目录解析。
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (!ksFile.isNullOrBlank() && rootProject.file(ksFile).exists()) {
                storeFile = rootProject.file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // UniFFI/JNA 走反射 + Native.load。R8 全量优化下即使有 keep 规则，
            // 仍存在「类被保留但 <clinit> 里的 loadLibrary 被内联丢弃」的边界情况。
            // 工程上把 -dontoptimize 只作用于 keeps 命中的类不可行，
            // 因此这里保持 optimize 但确保规则完整（见 proguard-rules.pro）。
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            // .so 已是 strip 过的 release 产物（Cargo profile 里 strip = "symbols"）
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    lint {
        // NullSafeMutableLiveData 检测器在 lifecycle-lint + 当前 Kotlin 组合下会崩溃
        disable += listOf("NullSafeMutableLiveData")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:engine"))
    implementation(project(":core:ai"))
    implementation(project(":core:system"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:automation"))
    implementation(project(":ui:designsystem"))
    implementation(project(":feature:home"))
    implementation(project(":feature:clean"))
    implementation(project(":feature:freeze"))
    implementation(project(":feature:automation"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:guardian")) // 守护中心：存储 / 内存 / 电池

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
