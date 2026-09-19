//! NovaCare 管家 — Rust 核心引擎
//!
//! 设计原则：
//! 1. **内存安全**：完全用 Rust 的所有权系统，无 unsafe（除 JNI 边界）
//! 2. **零成本抽象**：泛型 + trait，不为没用到的功能付出代价
//! 3. **并行优先**：CPU 密集任务用 rayon 并行化
//! 4. **错误显式**：所有可能失败的操作返回 `Result<T, NovaError>`
//! 5. **JNI 安全**：所有 JNI 边界 panic 都捕获，绝不破坏 JVM
//!
//! 模块：
//! - `scanner`：文件扫描（并行）
//! - `storage`：存储分类分析
//! - `junk_detector`：垃圾检测（缓存/残留/重复）
//! - `app_analyzer`：应用统计
//! - `battery_monitor`：电池分析
//! - `models`：数据结构
//! - `errors`：统一错误
//! - `jni_helpers`：JNI 工具
//! - `prelude`：统一导出

#![cfg_attr(
    target_os = "android",
    deny(unsafe_op_in_unsafe_fn)
)]
#![allow(missing_docs)]

pub mod scanner;
pub mod storage;
pub mod junk_detector;
pub mod app_analyzer;
pub mod battery_monitor;
pub mod models;
pub mod errors;
pub mod jni_helpers;
pub mod prelude;

// Android 平台才编译 JNI 入口
#[cfg(target_os = "android")]
mod jni_bindings;

// crate 元信息
pub const VERSION: &str = env!("CARGO_PKG_VERSION");
pub const NAME: &str = env!("CARGO_PKG_NAME");

/// 初始化（必须在调用任何函数前执行）
/// Android 上无操作（无日志后端），其他平台亦可调用。
pub fn init() {
    #[cfg(target_os = "android")]
    {
        use log::LevelFilter;
        let _ = android_logger::init_once(
            android_logger::Config::default()
                .with_min_level(LevelFilter::Info)
                .with_tag("NovaCore"),
        );
    }
    log::info!("{} v{} initialized", NAME, VERSION);
}