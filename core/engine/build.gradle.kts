plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.novacare.core.engine"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Rust 交叉编译产物（cargo-ndk 输出到 core/engine/jniLibs）
    sourceSets.getByName("main") {
        jniLibs.srcDirs("jniLibs")
    }

    lint {
        disable += listOf("NullSafeMutableLiveData")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// ============================================================
// 构建期守卫：原生库必须存在
//
// 事故复盘：core/engine/jniLibs/ 被 .gitignore 屏蔽（CI 现场编译），
// 但本地 `assembleRelease` 不会编译 Rust —— 于是打出一个
// **不含 libuniffi_novacare.so 的 release APK**，装上去引擎直接不可用，
// 而且构建过程零报错。这类"静默产出残废包"是本项目最贵的 bug。
//
// 现在把缺失变成硬失败：没有 .so 就不允许产出 APK。
// 本地首次构建执行：
//   cd core/engine/rust
//   export ANDROID_NDK_HOME=<ndk 路径>
//   cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../jniLibs build --release
// ============================================================
val requireNativeLibs = tasks.register("requireNativeLibs") {
    description = "Fails the build if the Rust engine .so files are missing"
    group = "verification"

    val libsDir = layout.projectDirectory.dir("jniLibs")
    val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    doLast {
        val missing = abis.filter { abi ->
            !libsDir.file("$abi/libuniffi_novacare.so").asFile.exists()
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                """
                |
                |============================================================
                | 构建中止：Rust 引擎原生库缺失
                |============================================================
                | 缺少 ABI: ${missing.joinToString(", ")}
                | 期望路径: core/engine/jniLibs/<abi>/libuniffi_novacare.so
                |
                | 这会导致产出一个"能装但引擎永远不可用"的残废 APK。
                | 请先编译 Rust 内核：
                |
                |   cd core/engine/rust
                |   export ANDROID_NDK_HOME=<你的 NDK 路径>
                |   cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 \
                |             -o ../jniLibs build --release
                |
                |============================================================
                """.trimMargin()
            )
        }
        logger.lifecycle("✓ Rust engine native libs present for: ${abis.joinToString(", ")}")
    }
}

// .so 守卫策略：
//   - assembleDebug  → 跳过守卫，打一个不带 .so 的 APK（引擎降级，但 UI 可验证）
//   - assembleRelease → 必须有真实 .so，才允许打包
// 这样 debug 开发流程不被阻断，release 产出前必有 CI 编译 Rust
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach {
        if (name.contains("Release")) dependsOn(requireNativeLibs)
    }
tasks.matching { it.name == "preBuild" }
    .configureEach {
        if (name.contains("Release")) dependsOn(requireNativeLibs)
    }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    // UniFFI 生成的 Kotlin 绑定通过 JNA 做 FFI 调用。
    //
    // P0 事故：上一版只依赖 `net.java.dev.jna:jna:5.14.0`（普通 jar）。
    // 该 jar **不含** libjnidispatch.so —— JNA 在 Android 上靠它做原生派发。
    // 结果：APK 里 libuniffi_novacare.so 存在（631KB），但 jnidispatch 缺失，
    // `Native.load("uniffi_novacare")` 直接抛 UnsatisfiedLinkError，
    // 被 NovaEngine 的 runCatching 吞掉 → isAvailable=false → 引擎永远不可用。
    //
    // `@aar` 变体才会把 libjnidispatch.so 按 ABI 打进 APK。
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
