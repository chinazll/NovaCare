cmake_minimum_required(VERSION 3.22.1)
project(novacare_jni LANGUAGES C)

# NovaCare 管家 —— 真正的 Rust NDK 集成
# CMake 调用 cargo-ndk 编译 Rust 核心，输出 .so 到 jniLibs/

set(CARGO_MANIFEST "${CMAKE_CURRENT_SOURCE_DIR}/../rust/Cargo.toml")
set(RUST_PROJECT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../rust")
set(JNI_LIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../src/main/jniLibs")

# 查找 cargo 工具
find_program(CARGO_BIN cargo
    HINTS $ENV{HOME}/.cargo/bin /root/.cargo/bin
    PATHS /usr/local/cargo/bin
    REQUIRED
)
find_program(CARGO_NDK_BIN cargo-ndk
    HINTS $ENV{HOME}/.cargo/bin /root/.cargo/bin
    PATHS /usr/local/cargo/bin
)

# 编译单个 ABI 的 Rust 库
function(novacare_build_rust_for_abi ABI)
    set(RUST_TARGET "")
    if(ABI STREQUAL "arm64-v8a")
        set(RUST_TARGET "aarch64-linux-android")
        set(NDK_TRIPLE "aarch64-linux-android24")
    elseif(ABI STREQUAL "armeabi-v7a")
        set(RUST_TARGET "armv7-linux-androideabi")
        set(NDK_TRIPLE "armv7a-linux-androideabi24")
    elseif(ABI STREQUAL "x86_64")
        set(RUST_TARGET "x86_64-linux-android")
        set(NDK_TRIPLE "x86_64-linux-android24")
    elseif(ABI STREQUAL "x86")
        set(RUST_TARGET "i686-linux-android")
        set(NDK_TRIPLE "i686-linux-android24")
    endif()

    if(NOT RUST_TARGET)
        message(WARNING "Unknown ABI: ${ABI}, skipping")
        return()
    endif()

    # 设置 NDK 工具链
    set(ENV{CC_${RUST_TARGET}} "${ANDROID_TOOLCHAIN_ROOT}/bin/${NDK_TRIPLE}-clang")
    set(ENV{AR_${RUST_TARGET}} "${ANDROID_TOOLCHAIN_ROOT}/bin/llvm-ar")
    set(ENV{CARGO_TARGET_${RUST_TARGET}_LINKER} "${ANDROID_TOOLCHAIN_ROOT}/bin/${NDK_TRIPLE}-clang")
    set(ENV{RUSTFLAGS} "-C link-arg=-Wl,-z,max-page-size=16384")  # 16KB 页对齐 (Android 15+)

    message(STATUS "NovaCare Rust: Building for ${ABI} (target=${RUST_TARGET})")
    message(STATUS "  Using linker: $ENV{CARGO_TARGET_${RUST_TARGET}_LINKER}")

    # 调用 cargo build（直接调用更可靠，不依赖 cargo-ndk）
    execute_process(
        COMMAND ${CARGO_BIN} build
            --target ${RUST_TARGET}
            --release
            --manifest-path ${CARGO_MANIFEST}
        RESULT_VARIABLE CARGO_RESULT
        OUTPUT_VARIABLE CARGO_OUTPUT
        ERROR_VARIABLE CARGO_ERROR
        WORKING_DIRECTORY ${RUST_PROJECT_DIR}
    )

    if(NOT CARGO_RESULT EQUAL 0)
        message(FATAL_ERROR "Cargo build failed for ${ABI}:\n${CARGO_ERROR}\n${CARGO_OUTPUT}")
    endif()

    # 复制 .so 到 jniLibs（让 Gradle 把它打包进 APK）
    set(SOURCE_SO "${RUST_PROJECT_DIR}/target/${RUST_TARGET}/release/libnovacare_core.so")
    set(DEST_DIR "${JNI_LIBS_DIR}/${ABI}")
    file(MAKE_DIRECTORY ${DEST_DIR})
    file(COPY ${SOURCE_SO} DESTINATION ${DEST_DIR})
    message(STATUS "NovaCare Rust: Copied ${SOURCE_SO} -> ${DEST_DIR}")
endfunction()

# 收集当前构建的 ABI 列表
set(ABI_LIST ${ANDROID_ABI})
message(STATUS "NovaCare: Target ABIs = ${ABI_LIST}")

# 关闭 CMake 的 native build 输出检查（我们用 CMake 触发 cargo）
set(CMAKE_C_COMPILER_WORKS 1)
set(CMAKE_CXX_COMPILER_WORKS 1)

# 为每个 ABI 编译 Rust
foreach(ABI ${ABI_LIST})
    novacare_build_rust_for_abi(${ABI})
endforeach()

# Gradle 需要一个实际的 native library target
# 由于真正的 .so 由 cargo 生成并放到 jniLibs/，这里只创建占位符
add_library(novacare_jni SHARED dummy.c)
target_link_libraries(novacare_jni log)