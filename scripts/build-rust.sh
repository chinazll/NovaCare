#!/usr/bin/env bash
#
# NovaCare —— 编译 Rust 核心引擎为 Android .so
#
# 用法：
#   ./scripts/build-rust.sh                 # 三种 ABI 全编译
#   ./scripts/build-rust.sh arm64-v8a       # 只编译指定 ABI
#
# 前置条件：
#   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
#   cargo install cargo-ndk
#   export ANDROID_NDK_HOME=/path/to/android-sdk/ndk/26.1.10909125
#
# 说明：
#   本脚本用 cargo-ndk 而不是手写 CMake —— cargo-ndk 会自动推导 NDK 的
#   链接器与 ar 路径，避免手工拼 ${ANDROID_TOOLCHAIN_ROOT} 这类不存在的变量。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUST_DIR="${SCRIPT_DIR}/../app/src/main/rust"
JNI_DIR="${SCRIPT_DIR}/../app/src/main/jniLibs"

if [[ -z "${ANDROID_NDK_HOME:-}" && -z "${ANDROID_NDK_ROOT:-}" ]]; then
    echo "错误：请设置 ANDROID_NDK_HOME 或 ANDROID_NDK_ROOT"
    exit 1
fi

if ! command -v cargo-ndk >/dev/null 2>&1; then
    echo "错误：未找到 cargo-ndk，请先执行 cargo install cargo-ndk"
    exit 1
fi

TARGETS=("$@")
if [[ ${#TARGETS[@]} -eq 0 ]]; then
    TARGETS=("arm64-v8a" "armeabi-v7a" "x86_64")
fi

echo "=== NovaCare Rust 核心编译 ==="
echo "Rust 项目: ${RUST_DIR}"
echo "输出目录:  ${JNI_DIR}"
echo "目标 ABI:  ${TARGETS[*]}"
echo

mkdir -p "${JNI_DIR}"
cd "${RUST_DIR}"

NDK_ARGS=()
for t in "${TARGETS[@]}"; do
    NDK_ARGS+=("-t" "$t")
done

cargo ndk "${NDK_ARGS[@]}" -o "${JNI_DIR}" build --release

echo
echo "=== 产物 ==="
find "${JNI_DIR}" -name 'libnovacare_core.so' -exec ls -lh {} \;

# 校验：每个目标 ABI 都必须产出 .so
for t in "${TARGETS[@]}"; do
    if [[ ! -f "${JNI_DIR}/${t}/libnovacare_core.so" ]]; then
        echo "错误：缺少 ${t}/libnovacare_core.so"
        exit 1
    fi
done

echo "✅ Rust 核心编译完成，Gradle 会自动将 jniLibs 打包进 APK"
