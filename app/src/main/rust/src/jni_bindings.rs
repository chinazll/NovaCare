//! JNI 绑定层
//!
//! 把 Rust 核心能力暴露给 Kotlin：
//! - Java_com_novacare_optimizer_core_RustCore_version()
//! - Java_com_novacare_optimizer_core_RustCore_analyzeStorage()
//! - Java_com_novacare_optimizer_core_RustCore_scanJunk()
//! - Java_com_novacare_optimizer_core_RustCore_analyzeBattery()
//! - Java_com_novacare_optimizer_core_RustCore_analyzeApps()

#![cfg(target_os = "android")]

use crate::app_analyzer::{analyze as analyze_apps_fn, sort_apps, AppFilter, SortBy};
use crate::battery_monitor::analyze as analyze_battery_fn;
use crate::errors::NovaError;
use crate::junk_detector::{scan as scan_junk_fn, JunkOptions};
use crate::prelude::*;
use crate::storage::{analyze as analyze_storage_fn, StorageOptions};
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jstring, jlong, jint, jboolean};
use std::panic;

// === JNI 导出 ===

/// 获取版本
#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_version(
    _env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = panic::catch_unwind(|| {
        let v = format!("{} v{} (Rust core)", crate::NAME, crate::VERSION);
        Ok::<String, NovaError>(v)
    });
    match result {
        Ok(Ok(s)) => {
            let env = _env;
            let _ = env;
            match _env.new_string(&s) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        _ => std::ptr::null_mut(),
    }
}

/// 初始化
#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_init(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    panic::catch_unwind(|| {
        crate::init();
    })
    .map(|_| 1)
    .unwrap_or(0)
}

/// 存储分析
/// 入参：rootPath: String, totalBytes: jlong
/// 返回：JSON 字符串
#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_analyzeStorage(
    env: JNIEnv,
    _class: JClass,
    root_path: JString,
    total_bytes: jlong,
) -> jstring {
    let result = panic::catch_unwind(|| {
        let path_str = env.get_string(&root_path)?.into();
        let root = std::path::PathBuf::from(&path_str);
        let opts = StorageOptions { root, max_depth: 5 };
        let mut report = analyze_storage_fn(&opts)?;
        report.total = total_bytes as u64;
        let json = serde_json::to_string(&report)?;
        Ok::<String, NovaError>(json)
    });

    match result {
        Ok(Ok(s)) => match env.new_string(&s) {
            Ok(j) => j.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Ok(Err(_)) | Err(_) => std::ptr::null_mut(),
    }
}

/// 垃圾扫描
/// 入参：rootPath: String, installedPackagesCsv: String (逗号分隔)
/// 返回：JSON 字符串
#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_scanJunk(
    env: JNIEnv,
    _class: JClass,
    root_path: JString,
    installed_packages: JString,
    detect_duplicates: jboolean,
) -> jstring {
    let result = panic::catch_unwind(|| {
        let path_str = env.get_string(&root_path)?.into();
        let pkgs_str = env.get_string(&installed_packages)?.into();
        let root = std::path::PathBuf::from(&path_str);
        let packages: Vec<String> = if pkgs_str.is_empty() {
            vec![]
        } else {
            pkgs_str.split(',').map(|s| s.trim().to_string()).collect()
        };
        let opts = JunkOptions {
            root,
            installed_packages: packages,
            detect_duplicates: detect_duplicates != 0,
            duplicate_min_size: 1024 * 1024,
        };
        let report = scan_junk_fn(&opts)?;
        let json = serde_json::to_string(&report)?;
        Ok::<String, NovaError>(json)
    });

    match result {
        Ok(Ok(s)) => match env.new_string(&s) {
            Ok(j) => j.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Ok(Err(_)) | Err(_) => std::ptr::null_mut(),
    }
}

/// 电池分析
/// 入参：level: jint, temp: jint (10 倍), voltage: jint, current: jint,
///       status: String, health: String, plugged: jint
/// 返回：JSON 字符串
#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_analyzeBattery(
    env: JNIEnv,
    _class: JClass,
    level: jint,
    temperature_tenths: jint,
    voltage: jint,
    current: jint,
    status: JString,
    health: JString,
    plugged: jint,
) -> jstring {
    let result = panic::catch_unwind(|| {
        let status_str: String = env.get_string(&status)?.into();
        let health_str: String = env.get_string(&health)?.into();
        let input = BatteryInput {
            level: level as u32,
            temperature: temperature_tenths as f32 / 10.0,
            voltage,
            current,
            status: status_str,
            health: health_str,
            plugged,
        };
        let report = analyze_battery_fn(&input);
        let json = serde_json::to_string(&report)?;
        Ok::<String, NovaError>(json)
    });

    match result {
        Ok(Ok(s)) => match env.new_string(&s) {
            Ok(j) => j.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Ok(Err(_)) | Err(_) => std::ptr::null_mut(),
    }
}

/// 应用分析（统计 + 排序）
/// 入参：appsJson: String （应用列表 JSON）, sortBy: jint, ascending: jboolean
/// 返回：JSON 字符串 { stats: AppStats, sorted_apps: [...] }
/// sortBy: 0=Size, 1=Name, 2=InstallTime, 3=UpdateTime
#[no_mangle]
pub extern "system" fn Java_com_novacare_optimizer_core_RustCore_analyzeApps(
    env: JNIEnv,
    _class: JClass,
    apps_json: JString,
    sort_by: jint,
    ascending: jboolean,
) -> jstring {
    use serde_json::json;

    let result = panic::catch_unwind(|| {
        let s: String = env.get_string(&apps_json)?.into();
        let mut apps: Vec<AppInfo> = serde_json::from_str(&s)?;
        let filter = AppFilter {
            only_user: false,
            sort_by: match sort_by {
                1 => SortBy::Name,
                2 => SortBy::InstallTime,
                3 => SortBy::UpdateTime,
                _ => SortBy::Size,
            },
            ascending: ascending != 0,
        };
        let stats = analyze_apps_fn(&apps, &filter);
        sort_apps(&mut apps, &filter);
        let out = json!({
            "stats": stats,
            "sorted_apps": apps,
        });
        Ok::<String, NovaError>(out.to_string())
    });

    match result {
        Ok(Ok(s)) => match env.new_string(&s) {
            Ok(j) => j.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Ok(Err(_)) | Err(_) => std::ptr::null_mut(),
    }
}