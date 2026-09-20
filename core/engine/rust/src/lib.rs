//! NovaCare 确定性内核（L1）
//!
//! 职责边界（严格遵守蓝图）：
//! - 只做**确定性计算**：扫描、去重（BLAKE3 精确哈希）、分类、排序、电池评分
//! - **不做任何删除决策**，也不做任何「猜测」 —— 语义理解交给 L2 端侧 AI
//! - 不持有状态：输入 → 计算 → 一次性返回不可变 DTO（避免跨语言状态同步）
//!
//! ## 为什么用 proc-macro 模式而不是 UDL 文件
//! uniffi 0.28 的 UDL 模式（build.rs + include_scaffolding!）在 rustc 1.95 下
//! 生成的 scaffolding 无法编译（生成的 struct r#AppInfo 被报
//! "cannot find type AppInfo in this scope"，0.28.3 与 0.32.1 均复现）。
//! 因此改用官方推荐的 proc-macro 模式：类型与函数直接在 Rust 里标注，
//! 绑定由编译产物（cdylib 内嵌元数据）反向生成 —— 同样消灭签名漂移，且少一层解析。

uniffi::setup_scaffolding!("novacare");

pub mod errors;
pub mod models;
pub mod scanner;
pub mod storage;
pub mod junk_detector;
pub mod app_analyzer;
pub mod battery_monitor;

use std::path::PathBuf;
use std::time::Instant;

pub const NAME: &str = "NovaCare Core";
pub const VERSION: &str = "0.6.0";

// ============================================================
// 跨语言 DTO（UniFFI 自动生成 Kotlin data class）
// ============================================================

#[derive(uniffi::Record)]
pub struct FileNode {
    pub path: String,
    pub bytes: u64,
    pub is_dir: bool,
    pub modified: Option<i64>,
    pub children: Vec<FileNode>,
}

#[derive(uniffi::Record)]
pub struct StorageCategory {
    pub name: String,
    pub bytes: u64,
    pub ratio: f64,
    pub color_id: i32,
}

#[derive(uniffi::Record)]
pub struct StorageReport {
    pub total: u64,
    pub used: u64,
    pub categories: Vec<StorageCategory>,
    pub largest_files: Vec<FileNode>,
    pub duration_ms: u64,
}

#[derive(uniffi::Record)]
pub struct JunkItem {
    pub path: String,
    pub label: String,
    pub bytes: u64,
    pub kind: String,
    /// safe | caution | risky —— 确定性判定，见 risk_of()
    pub risk: String,
    pub risk_note: String,
}

#[derive(uniffi::Record)]
pub struct JunkReport {
    pub items: Vec<JunkItem>,
    pub total_bytes: u64,
    pub safe_bytes: u64,
    pub duration_ms: u64,
}

#[derive(uniffi::Record)]
pub struct BatteryReport {
    pub health_score: u32,
    /// None = 未知（真实周期需 root 读 sysfs，本项目不伪造）
    pub cycle_count: Option<u32>,
    pub temperature: f32,
    pub voltage: i32,
    pub level: u32,
    pub status: String,
    pub suggestions: Vec<String>,
}

#[derive(uniffi::Record)]
pub struct AppInfo {
    pub package_name: String,
    pub label: String,
    pub is_system: bool,
    pub size_bytes: u64,
    pub install_time: i64,
    pub update_time: i64,
    pub cache_bytes: u64,
    pub data_bytes: u64,
    pub version: String,
    pub target_sdk: i32,
    /// 0 = 未知
    pub last_used_time: i64,
}

#[derive(uniffi::Record)]
pub struct AppStats {
    pub total_apps: i32,
    pub system_apps: i32,
    pub user_apps: i32,
    pub total_size: u64,
    pub total_cache: u64,
    pub total_data: u64,
}

#[derive(uniffi::Record)]
pub struct AppsReport {
    pub stats: AppStats,
    pub sorted_apps: Vec<AppInfo>,
}

// ============================================================
// 导出函数
// ============================================================

#[uniffi::export]
pub fn engine_version() -> String {
    format!("{} v{}", NAME, VERSION)
}

#[uniffi::export]
pub fn analyze_storage(root_path: String, total_bytes: u64) -> Option<StorageReport> {
    let start = Instant::now();
    let opts = storage::StorageOptions {
        root: PathBuf::from(&root_path),
        max_depth: 5,
    };

    let internal = match storage::analyze(&opts) {
        Ok(r) => r,
        Err(e) => {
            log::warn!("analyze_storage failed: {}", e);
            return None;
        }
    };

    let categories = internal
        .categories
        .into_iter()
        .map(|c| StorageCategory {
            name: c.name,
            bytes: c.size,
            ratio: c.ratio,
            color_id: c.color_id,
        })
        .collect();

    let largest_files = internal
        .largest_files
        .into_iter()
        .map(conv_file_node)
        .collect();

    Some(StorageReport {
        // total 由 Kotlin 侧 StatFs 给出（真实容量）；内核不得改写
        total: if total_bytes > 0 { total_bytes } else { internal.total },
        used: internal.used,
        categories,
        largest_files,
        duration_ms: start.elapsed().as_millis() as u64,
    })
}

#[uniffi::export]
pub fn scan_junk(
    root_path: String,
    installed_packages: String,
    detect_duplicates: bool,
) -> Option<JunkReport> {
    let start = Instant::now();

    // 空串 = Kotlin 侧不具备完整包可见性 → 跳过残留检测，防误删在用应用数据
    let packages: Vec<String> = if installed_packages.trim().is_empty() {
        vec![]
    } else {
        installed_packages
            .split(',')
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .collect()
    };

    let opts = junk_detector::JunkOptions {
        root: PathBuf::from(&root_path),
        installed_packages: packages,
        detect_duplicates,
        duplicate_min_size: 1024 * 1024,
    };

    let internal = match junk_detector::scan(&opts) {
        Ok(r) => r,
        Err(e) => {
            log::warn!("scan_junk failed: {}", e);
            return None;
        }
    };

    // 先做一次统一分级，再据此生成 FFI DTO 与 safe_bytes。
    // 必须先把分级结果落到一个 Vec 里，否则 items 被 move 后无法再统计 safe_bytes。
    let graded: Vec<(models::JunkItem, &'static str)> = internal
        .items
        .into_iter()
        .map(|i| {
            // 统一由 risk_of() 做确定性分级，不采信 detector 侧的 is_safe 布尔值 ——
            // 后者只是粗粒度提示，两处若不一致会出现"标着安全却重分级为 risky"的漂移。
            let risk = risk_of(&i.kind, i.is_safe);
            (i, risk)
        })
        .collect();

    // safe_bytes 的语义必须是「用户不确认也可以直接清掉的量」。
    // 上一版直接用 detector 的 safe_size（按 is_safe 累加），把 duplicate /
    // residual 这类需要用户判断的项也算了进去 —— 首页那行「可安全释放 X」
    // 会显著高估。这里改为按最终 risk 重新累计。
    let safe_bytes: u64 = graded
        .iter()
        .filter(|(_, risk)| *risk == "safe")
        .map(|(i, _)| i.size)
        .sum();

    let items = graded
        .into_iter()
        .map(|(i, risk)| JunkItem {
            path: i.path,
            label: i.label,
            bytes: i.size,
            kind: i.kind,
            risk: risk.to_string(),
            risk_note: i.risk_note,
        })
        .collect();

    Some(JunkReport {
        items,
        total_bytes: internal.total_size,
        safe_bytes,
        duration_ms: start.elapsed().as_millis() as u64,
    })
}

#[uniffi::export]
pub fn analyze_battery(
    level: u32,
    temperature_tenths: i32,
    voltage: i32,
    current: i32,
    status: String,
    health: String,
    plugged: i32,
) -> Option<BatteryReport> {
    let input = battery_monitor::BatteryInput {
        level,
        temperature: temperature_tenths as f32 / 10.0,
        voltage,
        current,
        status,
        health,
        plugged,
    };

    let r = battery_monitor::analyze(&input);

    Some(BatteryReport {
        health_score: r.health_score,
        cycle_count: r.cycle_count,
        temperature: r.temperature,
        voltage: r.voltage,
        level: r.level,
        status: r.status,
        suggestions: r.suggestions,
    })
}

#[uniffi::export]
pub fn analyze_apps(apps_json: String, sort_by: i32, ascending: bool) -> Option<AppsReport> {
    let mut apps: Vec<models::AppInfo> = match serde_json::from_str(&apps_json) {
        Ok(v) => v,
        Err(e) => {
            log::warn!("analyze_apps parse failed: {}", e);
            return None;
        }
    };

    // ---------------------------------------------------------------------
    // JSON 契约（P1 修复）
    //
    // 入参 `apps_json` 的字段名必须用 `models::AppInfo` 的 Rust 原名：
    //   size / cache_size / data_size / install_time / update_time / last_used_time
    //   / package_name / label / is_system / version / target_sdk
    //
    // 返回值用的是下面这个 `AppInfo`（uniffi::Record）的字段名：
    //   size_bytes / cache_bytes / data_bytes / last_used_time …
    //
    // 两套名字**故意不同**，因为用途不同：入参是 JSON（Kotlin 侧构造），
    // 返回值是 UniFFI 记录（跨 FFI 走二进制编码，不经过 JSON）。
    //
    // 注意：analyze_apps 目前**没有生产调用方** —— NovaEngine.analyzeApps() 存在，
    // 但全工程无人调用它，首页/清理页走的是 scanJunk / analyzeStorage。
    // 这里不伪造调用方；接入真实入口（"应用分析"视图）之前，该能力保持未接线状态。
    // 历史缺陷：曾经 Kotlin 侧反序列化假定的字段名与上面不一致，一旦真的接上调用方
    // 会静默解析失败（serde 报错被 log::warn 吞掉后返回 None）。
    // ---------------------------------------------------------------------
    let filter = app_analyzer::AppFilter {
        only_user: false,
        sort_by: match sort_by {
            1 => app_analyzer::SortBy::Name,
            2 => app_analyzer::SortBy::InstallTime,
            3 => app_analyzer::SortBy::UpdateTime,
            _ => app_analyzer::SortBy::Size,
        },
        ascending,
    };

    let stats = app_analyzer::analyze(&apps, &filter);
    let total_cache = app_analyzer::total_cache(&apps);
    let total_data = app_analyzer::total_data(&apps);
    app_analyzer::sort_apps(&mut apps, &filter);

    let sorted_apps = apps
        .into_iter()
        .map(|a| AppInfo {
            package_name: a.package_name,
            label: a.label,
            is_system: a.is_system,
            size_bytes: a.size,
            install_time: a.install_time,
            update_time: a.update_time,
            cache_bytes: a.cache_size,
            data_bytes: a.data_size,
            version: a.version,
            target_sdk: a.target_sdk,
            last_used_time: a.last_used_time,
        })
        .collect();

    Some(AppsReport {
        stats: AppStats {
            total_apps: stats.total as i32,
            system_apps: stats.system_apps as i32,
            user_apps: stats.user_apps as i32,
            total_size: stats.total_size,
            total_cache,
            total_data,
        },
        sorted_apps,
    })
}

// ============================================================
// 内部工具
// ============================================================

fn conv_file_node(n: models::FileNode) -> FileNode {
    FileNode {
        path: n.path,
        bytes: n.size,
        is_dir: n.is_dir,
        modified: n.modified,
        children: n.children.into_iter().map(conv_file_node).collect(),
    }
}

/// 风险分级：按类型确定性判定，不靠「猜」
fn risk_of(kind: &str, is_safe: bool) -> &'static str {
    match kind {
        "cache" | "thumbnail" | "tmp" => "safe",
        "log" | "crash" | "residual" => "caution",
        "duplicate" => "risky",
        _ => {
            if is_safe {
                "safe"
            } else {
                "caution"
            }
        }
    }
}

// ============================================================
// 单元测试（Rust 侧测试金字塔底座）
// ============================================================

#[cfg(test)]
mod tests {
    use super::*;

    fn write_file(dir: &std::path::Path, name: &str, bytes: usize) {
        let p = dir.join(name);
        std::fs::write(p, vec![b'x'; bytes]).unwrap();
    }

    #[test]
    fn engine_version_contains_name() {
        assert!(engine_version().contains("NovaCare"));
    }

    #[test]
    fn analyze_storage_on_real_temp_dir() {
        let tmp = tempfile::tempdir().unwrap();
        let root = tmp.path().to_path_buf();
        // 内核按真实分类目录扫描（DCIM / Music / ...），测试必须落在这些目录里
        std::fs::create_dir_all(root.join("DCIM")).unwrap();
        std::fs::create_dir_all(root.join("Music")).unwrap();
        write_file(&root.join("DCIM"), "a.jpg", 4_096);
        write_file(&root.join("Music"), "b.mp3", 2_048);

        let report = analyze_storage(root.to_string_lossy().to_string(), 1_000_000).unwrap();
        assert_eq!(report.total, 1_000_000);
        assert!(
            report.used >= 6_144,
            "used should count real bytes, got {}",
            report.used
        );
    }

    #[test]
    fn analyze_storage_missing_dir_returns_null() {
        assert!(analyze_storage("/definitely/not/a/real/path/xyz".to_string(), 0).is_none());
    }

    #[test]
    fn scan_junk_empty_packages_skips_residual() {
        let tmp = tempfile::tempdir().unwrap();
        let root = tmp.path().to_path_buf();
        // 内核扫描的是 Download/.tmp 这类真实临时目录
        std::fs::create_dir_all(root.join("Download").join(".tmp")).unwrap();
        write_file(&root.join("Download").join(".tmp"), "junk.tmp", 512);

        let report = scan_junk(root.to_string_lossy().to_string(), String::new(), false).unwrap();
        assert!(report.total_bytes >= 512);
        assert!(
            report.items.iter().all(|i| i.kind != "residual"),
            "empty package list must skip residual detection"
        );
    }

    #[test]
    fn risk_classification_is_deterministic() {
        assert_eq!(risk_of("cache", true), "safe");
        assert_eq!(risk_of("log", false), "caution");
        assert_eq!(risk_of("duplicate", false), "risky");
    }

    #[test]
    fn analyze_apps_sorts_by_size() {
        let json = r#"[
            {"package_name":"a","label":"A","is_system":false,"size":10,
             "install_time":1,"update_time":2,"cache_size":1,"data_size":2,
             "version":"1","target_sdk":34,"last_used_time":0},
            {"package_name":"b","label":"B","is_system":true,"size":999,
             "install_time":1,"update_time":2,"cache_size":3,"data_size":4,
             "version":"1","target_sdk":34,"last_used_time":0}
        ]"#;
        let report = analyze_apps(json.to_string(), 0, false).unwrap();
        assert_eq!(report.stats.total_apps, 2);
        assert_eq!(report.stats.system_apps, 1);
        assert_eq!(report.sorted_apps[0].package_name, "b");
        assert_eq!(report.stats.total_cache, 4);
        assert_eq!(report.stats.total_data, 6);
    }

    #[test]
    fn analyze_apps_rejects_garbage() {
        assert!(analyze_apps("not json".to_string(), 0, false).is_none());
    }

    #[test]
    fn battery_never_fakes_cycle_count() {
        let r = analyze_battery(
            80,
            300,
            4000,
            -100,
            "discharging".to_string(),
            "good".to_string(),
            0,
        )
        .unwrap();
        assert_eq!(r.level, 80);
        assert!(r.cycle_count.is_none());
        assert!(r.health_score <= 100);
    }
}
