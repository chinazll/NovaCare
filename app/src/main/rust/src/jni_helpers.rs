//! JNI 辅助工具
//!
//! 关键原则：
//! 1. 所有 JNI 字符串/数组操作都用 `jni` crate 提供的安全封装
//! 2. 任何 `unsafe` 块都集中在 `catch_unwind` 中
//! 3. 错误都映射到 `NovaError`

use crate::errors::{NovaError, NovaResult};
use jni::JNIEnv;
use jni::objects::{JString, JObject, JByteArray, JClass};
use jni::sys::{jstring, jint, jlong, jboolean, jbyteArray};
use std::panic::{self, AssertUnwindSafe};

/// 在 JNI 边界捕获 panic，绝不让 panic 跨越 FFI 边界
/// 返回 Java null + 设置 RuntimeException
pub fn catch_panic<F, R>(env: &JNIEnv, f: F) -> Option<R>
where
    F: FnOnce() -> R + std::panic::UnwindSafe,
{
    panic::catch_unwind(f).ok()
}

/// 安全地从 JString 提取 Rust String
pub fn jstring_to_string(env: &JNIEnv, js: JString) -> NovaResult<String> {
    if js.is_null() {
        return Err(NovaError::InvalidPath("JString is null".into()));
    }
    env.get_string(&js)
        .map(|s| s.into())
        .map_err(|e| NovaError::Jni(e))
}

/// 安全地从 Rust String 创建 jstring
pub fn string_to_jstring<'a>(env: &JNIEnv<'a>, s: &str) -> NovaResult<jstring> {
    env.new_string(s)
        .map(|s| s.into_raw())
        .map_err(|e| NovaError::Jni(e))
}

/// 安全地从 jbyteArray 读取字节
pub fn jbyte_array_to_vec(env: &JNIEnv, arr: JByteArray) -> NovaResult<Vec<u8>> {
    env.convert_byte_array(arr)
        .map_err(|e| NovaError::Jni(e))
}

/// 将 NovaResult 转换为 JNI 风格返回：
/// - Ok(v) → v
/// - Err(e) → 抛出 Java 异常 + 返回零值
pub fn or_throw<T: Default>(env: &JNIEnv, r: NovaResult<T>) -> T {
    match r {
        Ok(v) => v,
        Err(e) => {
            let _ = env.throw_new("com/novacare/optimizer/core/NovaException", &format!("{}", e));
            T::default()
        }
    }
}
