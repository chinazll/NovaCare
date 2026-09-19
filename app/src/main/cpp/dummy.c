// 占位 C 文件 — 实际功能由 Rust 编译的 libnovacare_core.so 提供
// 此文件存在是为了让 Gradle 的 externalNativeBuild 有 target
// Android 不会链接它，因为我们通过 jniLibs/ 提供真正的 Rust .so
int novacare_placeholder = 0;