//! 存储分类分析
//!
//! 按公共目录归类媒体/文档/应用/系统

use crate::errors::NovaResult;
use crate::models::{StorageCategory, StorageReport};
use crate::scanner::{scan, ScanOptions};
use rayon::prelude::*;
use std::path::{Path, PathBuf};
use std::time::Instant;

/// 公共目录分类
#[derive(Debug, Clone, Copy)]
pub enum Category {
    Photos,
    Videos,
    Audio,
    Documents,
    Apps,
}

impl Category {
    pub fn name(self) -> &'static str {
        match self {
            Self::Photos => "照片",
            Self::Videos => "视频",
            Self::Audio => "音频",
            Self::Documents => "文档",
            Self::Apps => "应用与系统",
        }
    }

    pub fn color_id(self) -> i32 {
        match self {
            Self::Photos => 0xFF5B8DEF_u32 as i32,
            Self::Videos => 0xFF9B6BDF_u32 as i32,
            Self::Audio => 0xFFE8912D_u32 as i32,
            Self::Documents => 0xFF2FA36B_u32 as i32,
            Self::Apps => 0xFF8A8F98_u32 as i32,
        }
    }

    /// 公共目录子路径
    pub fn subdirs(self) -> &'static [&'static str] {
        match self {
            Self::Photos => &["DCIM", "Pictures"],
            Self::Videos => &["Movies"],
            Self::Audio => &["Music", "Podcasts", "Audiobooks", "Recordings"],
            Self::Documents => &["Documents", "Download", "Books"],
            Self::Apps => &[".android_secure", "Android"], // 不深入 Android/
        }
    }
}

/// 存储分析选项
#[derive(Debug, Clone)]
pub struct StorageOptions {
    /// 存储根目录（通常是 /storage/emulated/0 或外置 SD）
    pub root: PathBuf,
    /// 各类最大深度
    pub max_depth: u8,
}

impl Default for StorageOptions {
    fn default() -> Self {
        Self {
            // Android 默认外部存储根
            root: PathBuf::from("/storage/emulated/0"),
            max_depth: 5,
        }
    }
}

/// 分析存储
pub fn analyze(opts: &StorageOptions) -> NovaResult<StorageReport> {
    let start = Instant::now();
    let mut total_size: u64 = 0;
    let mut categories = Vec::new();

    let cats = [
        Category::Photos,
        Category::Videos,
        Category::Audio,
        Category::Documents,
    ];

    for cat in &cats {
        let mut cat_size: u64 = 0;
        for sub in cat.subdirs() {
            let p = opts.root.join(sub);
            if !p.exists() {
                continue;
            }
            cat_size = cat_size.saturating_add(safe_scan_size(&p, opts.max_depth));
        }
        total_size = total_size.saturating_add(cat_size);
        categories.push(StorageCategory {
            name: cat.name().to_string(),
            size: cat_size,
            ratio: 0.0, // 后填
            color_id: cat.color_id(),
        });
    }

    // 应用与系统：扫 Android 目录顶层（深度 1）
    let android_dir = opts.root.join("Android");
    let apps_size = if android_dir.exists() {
        safe_scan_size(&android_dir, 2)
    } else {
        0
    };
    total_size = total_size.saturating_add(apps_size);
    categories.push(StorageCategory {
        name: Category::Apps.name().to_string(),
        size: apps_size,
        ratio: 0.0,
        color_id: Category::Apps.color_id(),
    });

    // 计算占比
    let total = total_size as f64;
    for c in &mut categories {
        c.ratio = if total > 0.0 { c.size as f64 / total } else { 0.0 };
    }

    // 取最大文件
    let largest = match scan(&opts.root, &ScanOptions {
        max_depth: Some(4),
        follow_links: false,
        collect_tree: false,
        progress: None,
    }) {
        Ok(r) => r.top_n_files(20),
        Err(_) => vec![],
    };

    // 假设总容量（与 DataDirectory 配合使用，单独调用时用估算）
    let total_capacity = std::env::var("NOVACARE_TOTAL_BYTES")
        .ok()
        .and_then(|s| s.parse::<u64>().ok())
        .unwrap_or(total_size.max(1));

    Ok(StorageReport {
        total: total_capacity,
        used: total_size,
        categories,
        largest_files: largest,
        duration_ms: start.elapsed().as_millis() as u64,
    })
}

fn safe_scan_size(path: &Path, max_depth: u8) -> u64 {
    use walkdir::WalkDir;

    WalkDir::new(path)
        .max_depth(max_depth as usize)
        .into_iter()
        .filter_map(|e| e.ok())
        .par_bridge()
        .map(|e| e.metadata().map(|m| m.len()).unwrap_or(0))
        .sum()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_analyze() {
        let tmp = std::env::temp_dir().join("novacare_test_storage");
        let _ = fs::remove_dir_all(&tmp);
        fs::create_dir_all(tmp.join("DCIM")).unwrap();
        fs::write(tmp.join("DCIM").join("a.jpg"), vec![0u8; 100]).unwrap();
        fs::create_dir_all(tmp.join("Music")).unwrap();
        fs::write(tmp.join("Music").join("b.mp3"), vec![0u8; 200]).unwrap();

        let opts = StorageOptions { root: tmp.clone(), max_depth: 3 };
        let r = analyze(&opts).unwrap();
        assert!(r.used >= 300);
        let _ = fs::remove_dir_all(&tmp);
    }
}