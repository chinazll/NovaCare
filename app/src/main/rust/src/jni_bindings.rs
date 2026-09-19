//! JNI 绑定层
//!
//! 把 Rust 核心能力暴露给 Kotlin：
//! - Java_com_novacare_optimizer_core_RustCore_version()
//! - Java_com_novacare_optimizer_core_RustCore_init()
//! - Java_com_novacare_optimizer_core_RustCore_analyzeStorage()
//! - Java_com_novacare_optimizer_core_RustCore_scanJunk()
//! - Java_com_novacare_optimizer_core_RustCore_analyzeBattery()
//! - Java_com_novacare_optimizer_core_RustCore_analyzeApps()
//!
//! ## 关键设计：panic 绝不跨过 JNI 边界
//!
//! Rust 的 panic 一旦穿过 `extern "system"` 边界就是 UB（会让 App 直接闪退）。
//! 本文件对每个导出函数采用**三段式**结构：
//!
//! 1. **入站**：在 JNI 边界把 `JString` 参数转成 Rust `String`（`env` 的可变借用到此为止）
//! 2. **计算**：把纯 Rust 逻辑放进 `panic::catch_unwind`，闭包**不捕获 env**
//!    （`&mut JNIEnv` 不满足 `UnwindSafe`，捕获它会编译失败）
//! 3. **出站**：回到 JNI 边界，用 `env` 生成返回值
//!
//! 任何 panic 都会在这里被捕获并转成 `null`，由 Kotlin 侧的 `RustCore.safeCall` 处理。

#![cfg(target_os = "android")]

use crate::app_analyzer::{analyze as analyze_apps_fn, sort_apps, AppFilter, SortBy};
use crate::battery_monitor::analyze as analyze_battery_fn;
use crate::junk_detector::{scan as scan_junk_fn, JunkOptions};
use crate::prelude::*;
use crate::storage::{analyze as analyze_storage_fn, StorageOptions};
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use std::panic;

/// 统一的失败返回
fn null() -> jstring {
    std::ptr::null_mut()
}

/// 把 Rust 字符串写回 Java；失败返回 null
fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(v) => v.into_raw(),
        Err(_) => null(),
    }
}

// ==================== 版本 ====================

#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_version(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let v = format!("{} v{} (Rust core)", crate::NAME, crate::VERSION);
    to_jstring(&mut env, &v)
}

// ==================== 初始化 ====================

#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_init(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    panic::catch_unwind(|| crate::init())
        .map(|_| 1)
        .unwrap_or(0)
}

// ==================== 存储分析 ====================

#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_analyzeStorage(
    mut env: JNIEnv,
    _class: JClass,
    root_path: JString,
    total_bytes: jlong,
) -> jstring {
    // 1. 入站
    let path_str: String = match env.get_string(&root_path) {
        Ok(s) => String::from(s),
        Err(_) => return null(),
    };
    let total = total_bytes.max(0) as u64;

    // 2. 计算（闭包不捕获 env）
    let result = panic::catch_unwind(move || -> Result<String, String> {
        let opts = StorageOptions {
            root: std::path::PathBuf::from(&path_str),
            max_depth: 5,
        };
        let mut report = analyze_storage_fn(&opts).map_err(|e| e.to_string())?;
        report.total = total;
        serde_json::to_string(&report).map_err(|e| e.to_string())
    });

    // 3. 出站
    match result {
        Ok(Ok(json)) => to_jstring(&mut env, &json),
        _ => null(),
    }
}

// ==================== 垃圾扫描 ====================

#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_scanJunk(
    mut env: JNIEnv,
    _class: JClass,
    root_path: JString,
    installed_packages: JString,
    detect_duplicates: jboolean,
) -> jstring {
    // 1. 入站
    let path_str: String = match env.get_string(&root_path) {
        Ok(s) => String::from(s),
        Err(_) => return null(),
    };
    let pkgs_str: String = match env.get_string(&installed_packages) {
        Ok(s) => String::from(s),
        Err(_) => return null(),
    };
    let dup = detect_duplicates != 0;

    // 2. 计算
    let result = panic::catch_unwind(move || -> Result<String, String> {
        // installed_packages 为空 = Kotlin 侧不具备完整包可见性 → 跳过残留检测，
        // 避免在 Android 11+ 分区存储下把在用应用的 Android/data 误判为残留
        let packages: Vec<String> = if pkgs_str.trim().is_empty() {
            vec![]
        } else {
            pkgs_str.split(',').map(|s| s.trim().to_string()).collect()
        };
        let opts = JunkOptions {
            root: std::path::PathBuf::from(&path_str),
            installed_packages: packages,
            detect_duplicates: dup,
            duplicate_min_size: 1024 * 1024,
        };
        let report = scan_junk_fn(&opts).map_err(|e| e.to_string())?;
        serde_json::to_string(&report).map_err(|e| e.to_string())
    });

    // 3. 出站
    match result {
        Ok(Ok(json)) => to_jstring(&mut env, &json),
        _ => null(),
    }
}

// ==================== 电池分析 ====================

#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_analyzeBattery(
    mut env: JNIEnv,
    _class: JClass,
    level: jint,
    temperature_tenths: jint,
    voltage: jint,
    current: jint,
    status: JString,
    health: JString,
    plugged: jint,
) -> jstring {
    // 1. 入站
    let status_str: String = match env.get_string(&status) {
        Ok(s) => String::from(s),
        Err(_) => return null(),
    };
    let health_str: String = match env.get_string(&health) {
        Ok(s) => String::from(s),
        Err(_) => return null(),
    };
    let input = BatteryInput {
        level: level.max(0) as u32,
        temperature: temperature_tenths as f32 / 10.0,
        voltage,
        current,
        status: status_str,
        health: health_str,
        plugged,
    };

    // 2. 计算（电池分析为纯计算，不会 panic，但仍统一走 catch_unwind）
    let result = panic::catch_unwind(move || -> Result<String, String> {
        let report = analyze_battery_fn(&input);
        serde_json::to_string(&report).map_err(|e| e.to_string())
    });

    // 3. 出站
    match result {
        Ok(Ok(json)) => to_jstring(&mut env, &json),
        _ => null(),
    }
}

// ==================== 应用分析 ====================

#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_analyzeApps(
    mut env: JNIEnv,
    _class: JClass,
    apps_json: JString,
    sort_by: jint,
    ascending: jboolean,
) -> jstring {
    // 1. 入站
    let json_str: String = match env.get_string(&apps_json) {
        Ok(s) => String::from(s),
        Err(_) => return null(),
    };
    let sort_by = sort_by;
    let asc = ascending != 0;

    // 2. 计算
    let result = panic::catch_unwind(move || -> Result<String, String> {
        let mut apps: Vec<AppInfo> =
            serde_json::from_str(&json_str).map_err(|e| e.to_string())?;
        let filter = AppFilter {
            only_user: false,
            sort_by: match sort_by {
                1 => SortBy::Name,
                2 => SortBy::InstallTime,
                3 => SortBy::UpdateTime,
                _ => SortBy::Size,
            },
            ascending: asc,
        };
        let stats = analyze_apps_fn(&apps, &filter);
        sort_apps(&mut apps, &filter);
        let out = serde_json::json!({
            "stats": stats,
            "sorted_apps": apps,
        });
        Ok(out.to_string())
    });

    // 3. 出站
    match result {
        Ok(Ok(json)) => to_jstring(&mut env, &json),
        _ => null(),
    }
}
