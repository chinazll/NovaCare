cmake_minimum_required(VERSION 3.22.1)
project("novacare_jni" LANGUAGES C)

# NovaCare: 调用 cargo 编译 Rust 核心，输出到 jniLibs/

# === 配置 ===
set(CARGO_MANIFEST "${CMAKE_CURRENT_SOURCE_DIR}/../rust/Cargo.toml")
set(RUST_TARGET_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/rust")
set(JNI_LIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../src/main/jniLibs")

# Android NDK 提供的 cargo 工具
find_program(CARGO_BIN cargo
    HINTS $ENV{HOME}/.cargo/bin
    PATHS /usr/local/cargo/bin
    REQUIRED)

# === Rust 编译函数 ===
function(novacare_build_rust ABI)
    message(STATUS "NovaCare: Building Rust core for ${ABI}")

    set(RUST_TARGET "")
    if(ABI STREQUAL "arm64-v8a")
        set(RUST_TARGET "aarch64-linux-android")
    elseif(ABI STREQUAL "armeabi-v7a")
        set(RUST_TARGET "armv7-linux-androideabi")
    elseif(ABI STREQUAL "x86_64")
        set(RUST_TARGET "x86_64-linux-android")
    elseif(ABI STREQUAL "x86")
        set(RUST_TARGET "i686-linux-android")
    endif()

    if(NOT RUST_TARGET)
        message(WARNING "Unknown ABI: ${ABI}, skipping")
        return()
    endif()

    # 用 ndk 中的链接器
    set(ENV{CC_aarch64_linux_android} "${ANDROID_TOOLCHAIN_ROOT}/bin/aarch64-linux-android${ANDROID_PLATFORM_LEVEL}-clang")
    set(ENV{CC_armv7_linux_androideabi} "${ANDROID_TOOLCHAIN_ROOT}/bin/armv7a-linux-androideabi${ANDROID_PLATFORM_LEVEL}-clang")
    set(ENV{CC_x86_64_linux_android} "${ANDROID_TOOLCHAIN_ROOT}/bin/x86_64-linux-android${ANDROID_PLATFORM_LEVEL}-clang")
    set(ENV{CC_i686_linux_android} "${ANDROID_TOOLCHAIN_ROOT}/bin/i686-linux-android${ANDROID_PLATFORM_LEVEL}-clang")

    set(RUSTFLAGS_ENV "--sysroot=${ANDROID_SYSROOT}")

    # 运行 cargo build
    execute_process(
        COMMAND ${CARGO_BIN} build
            --target ${RUST_TARGET}
            --release
            --target-dir ${RUST_TARGET_DIR}
        WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/../rust"
        RESULT_VARIABLE CARGO_RESULT
        OUTPUT_VARIABLE CARGO_OUTPUT
        ERROR_VARIABLE CARGO_ERROR
    )

    if(NOT CARGO_RESULT EQUAL 0)
        message(FATAL_ERROR "Cargo build failed for ${ABI}:\n${CARGO_ERROR}")
    endif()

    # 复制 .so 到 jniLibs
    file(COPY
        "${RUST_TARGET_DIR}/${RUST_TARGET}/release/libnovacare_core.so"
        DESTINATION "${JNI_LIBS_DIR}/${ABI}")
endfunction()

# 收集支持的 ABI
set(ABI_LIST ${ANDROID_ABI})
message(STATUS "NovaCare: Target ABIs = ${ABI_LIST}")

# 关闭 CMake 的 native build 输出警告（我们只用 CMake 触发 cargo）
set(CMAKE_C_COMPILER_WORKS 1)

# 为每个 ABI 构建 Rust
foreach(ABI ${ABI_LIST})
    novacare_build_rust(${ABI})
endforeach()

# 由于我们只用 CMake 触发 cargo 编译，不需要实际的 C 库
# 但 Gradle 需要这个 target 存在
add_library(novacare_jni SHARED dummy.c)

# dummy 占位文件，Android 不会链接它（因为 jniLibs 已经包含 libnovacare_core.so）
target_link_libraries(novacare_jni log)