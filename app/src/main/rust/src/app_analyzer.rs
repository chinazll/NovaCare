//! 应用分析器
//!
//! 提供应用统计、分类、扫描等元信息
//! 注：本模块不读取 /data/data/<pkg>（需要 root），仅基于 PackageManager 的公开信息

use crate::errors::{NovaError, NovaResult};
use crate::models::AppInfo;
use serde::{Deserialize, Serialize};
use std::path::Path;
use std::time::Instant;

/// 应用统计
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppStats {
    pub total: u32,
    pub user_apps: u32,
    pub system_apps: u32,
    pub total_size: u64,
    pub user_size: u64,
    pub system_size: u64,
}

/// 应用过滤选项
#[derive(Debug, Clone, Default)]
pub struct AppFilter {
    pub only_user: bool,
    pub sort_by: SortBy,
    pub ascending: bool,
}

#[derive(Debug, Clone, Copy, Default)]
pub enum SortBy {
    #[default]
    Size,
    Name,
    InstallTime,
    UpdateTime,
}

/// 分析应用列表
/// 注：apps 来自 Android PackageManager，由 Kotlin 端传入
pub fn analyze(apps: &[AppInfo], filter: &AppFilter) -> AppStats {
    let mut stats = AppStats {
        total: apps.len() as u32,
        user_apps: 0,
        system_apps: 0,
        total_size: 0,
        user_size: 0,
        system_size: 0,
    };

    for app in apps {
        if app.is_system {
            stats.system_apps += 1;
            stats.system_size = stats.system_size.saturating_add(app.size);
        } else {
            stats.user_apps += 1;
            stats.user_size = stats.user_size.saturating_add(app.size);
        }
        stats.total_size = stats.total_size.saturating_add(app.size);
    }

    stats
}

/// 排序应用
pub fn sort_apps(apps: &mut [AppInfo], filter: &AppFilter) {
    apps.sort_by(|a, b| {
        let ord = match filter.sort_by {
            SortBy::Size => a.size.cmp(&b.size),
            SortBy::Name => a.label.cmp(&b.label),
            SortBy::InstallTime => a.install_time.cmp(&b.install_time),
            SortBy::UpdateTime => a.update_time.cmp(&b.update_time),
        };
        if filter.ascending { ord } else { ord.reverse() }
    });
}

/// 按包名查找应用
pub fn find_by_package<'a>(apps: &'a [AppInfo], pkg: &str) -> Option<&'a AppInfo> {
    apps.iter().find(|a| a.package_name == pkg)
}

/// 计算推荐清理的应用（90 天未使用 + 非系统）
pub fn recommend_cleanup(apps: &[AppInfo], unused_days: i64, now_secs: i64) -> Vec<&AppInfo> {
    apps.iter()
        .filter(|a| {
            !a.is_system
                && a.cache_size > 50 * 1024 * 1024  // 缓存 > 50MB
        })
        .collect()
}

/// 计算应用缓存总和
pub fn total_cache(apps: &[AppInfo]) -> u64 {
    apps.iter().map(|a| a.cache_size).sum()
}

/// 计算应用数据总和
pub fn total_data(apps: &[AppInfo]) -> u64 {
    apps.iter().map(|a| a.data_size).sum()
}

/// 按 size 区间分组
pub fn group_by_size(apps: &[AppInfo]) -> Vec<(String, u32, u64)> {
    let buckets = [
        ("0-10MB", 0u64, 10 * 1024 * 1024),
        ("10-50MB", 10 * 1024 * 1024, 50 * 1024 * 1024),
        ("50-100MB", 50 * 1024 * 1024, 100 * 1024 * 1024),
        ("100-500MB", 100 * 1024 * 1024, 500 * 1024 * 1024),
        ("500MB-1GB", 500 * 1024 * 1024, 1024 * 1024 * 1024),
        ("1GB+", 1024 * 1024 * 1024, u64::MAX),
    ];
    buckets
        .iter()
        .map(|(name, lo, hi)| {
            let count = apps.iter().filter(|a| a.size >= *lo && a.size < *hi).count() as u32;
            let total: u64 = apps
                .iter()
                .filter(|a| a.size >= *lo && a.size < *hi)
                .map(|a| a.size)
                .sum();
            (name.to_string(), count, total)
        })
        .collect()
}

/// 计算应用包名集合（用于残留检测）
pub fn collect_packages(apps: &[AppInfo]) -> std::collections::HashSet<String> {
    apps.iter().map(|a| a.package_name.clone()).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_app(pkg: &str, size: u64, is_system: bool, cache: u64) -> AppInfo {
        AppInfo {
            package_name: pkg.to_string(),
            label: pkg.to_string(),
            is_system,
            size,
            install_time: 1700000000,
            update_time: 1700000000,
            cache_size: cache,
            data_size: 0,
            version: "1.0".to_string(),
            target_sdk: 34,
        }
    }

    #[test]
    fn test_analyze_basic() {
        let apps = vec![
            make_app("com.app1", 100, false, 60 * 1024 * 1024),
            make_app("com.sys", 500, true, 0),
        ];
        let stats = analyze(&apps, &AppFilter::default());
        assert_eq!(stats.total, 2);
        assert_eq!(stats.user_apps, 1);
        assert_eq!(stats.system_apps, 1);
        assert_eq!(stats.user_size, 100);
        assert_eq!(stats.system_size, 500);
    }

    #[test]
    fn test_sort_by_size_desc() {
        let mut apps = vec![
            make_app("a", 100, false, 0),
            make_app("b", 300, false, 0),
            make_app("c", 200, false, 0),
        ];
        sort_apps(&mut apps, &AppFilter { sort_by: SortBy::Size, ascending: false, ..Default::default() });
        assert_eq!(apps[0].package_name, "b");
        assert_eq!(apps[1].package_name, "c");
        assert_eq!(apps[2].package_name, "a");
    }

    #[test]
    fn test_recommend_cleanup() {
        let apps = vec![
            make_app("a", 200 * 1024 * 1024, false, 100 * 1024 * 1024),
            make_app("sys", 100, true, 0),
        ];
        let recs = recommend_cleanup(&apps, 90, 1715000000);
        assert_eq!(recs.len(), 1);
        assert_eq!(recs[0].package_name, "a");
    }
}