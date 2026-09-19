//! JNI 辅助工具
//!
//! 注意 jni 0.21 的 API 约定：
//! - `JNIEnv` 的可变方法（new_string / get_string / throw_new …）需要 `&mut self`
//! - 因此这些辅助函数统一接收 `&mut JNIEnv`
//! - `&mut JNIEnv` **不满足 UnwindSafe**，不能在 `panic::catch_unwind` 的闭包里被捕获，
//!   所以调用方必须「先在 JNI 边界取出参数 → 再在 catch_unwind 里跑纯 Rust 逻辑 → 最后回写」

#![cfg(target_os = "android")]

use jni::objects::JString;
use jni::sys::jstring;
use jni::JNIEnv;

use crate::errors::NovaResult;

/// 安全地从 JString 提取 Rust String
pub fn jstring_to_string(env: &mut JNIEnv, js: &JString) -> NovaResult<String> {
    let s = env.get_string(js)?;
    Ok(String::from(s))
}

/// 安全地从 Rust &str 创建 jstring
pub fn string_to_jstring(env: &mut JNIEnv, s: &str) -> NovaResult<jstring> {
    let out = env.new_string(s)?;
    Ok(out.into_raw())
}

/// 把 NovaResult 转成 JNI 风格返回：
/// - Ok(v) → v
/// - Err(e) → 抛出 Java 异常并返回零值
pub fn or_throw<T: Default>(env: &mut JNIEnv, r: NovaResult<T>) -> T {
    match r {
        Ok(v) => v,
        Err(e) => {
            let _ = env.throw_new(
                "com/novacare/optimizer/core/NovaException",
                &format!("{}", e),
            );
            T::default()
        }
    }
}
