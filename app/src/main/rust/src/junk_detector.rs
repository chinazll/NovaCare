//! 垃圾检测器
//!
//! 检测缓存、缩略图、临时文件、残留数据、重复文件

use crate::errors::{NovaError, NovaResult};
use crate::models::{JunkItem, JunkReport};
use rayon::prelude::*;
use std::path::{Path, PathBuf};
use std::time::Instant;
use walkdir::WalkDir;

/// 垃圾类型
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum JunkKind {
    /// 公共缓存
    Cache,
    /// 缩略图
    Thumbnail,
    /// 临时文件
    Tmp,
    /// 日志
    Log,
    /// 崩溃转储
    Crash,
    /// 应用残留
    Residual,
    /// 重复文件
    Duplicate,
}

impl JunkKind {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Cache => "cache",
            Self::Thumbnail => "thumbnail",
            Self::Tmp => "tmp",
            Self::Log => "log",
            Self::Crash => "crash",
            Self::Residual => "residual",
            Self::Duplicate => "duplicate",
        }
    }

    /// 是否安全可清理
    pub fn is_safe(self) -> bool {
        matches!(self, Self::Cache | Self::Thumbnail | Self::Tmp)
    }
}

/// 垃圾扫描选项
#[derive(Debug, Clone)]
pub struct JunkOptions {
    /// 根目录
    pub root: PathBuf,
    /// 已安装应用包名集合（用于检测残留）
    pub installed_packages: Vec<String>,
    /// 是否扫描重复文件
    pub detect_duplicates: bool,
    /// 重复文件最小大小
    pub duplicate_min_size: u64,
}

impl Default for JunkOptions {
    fn default() -> Self {
        Self {
            root: PathBuf::from("/storage/emulated/0"),
            installed_packages: vec![],
            detect_duplicates: false,
            duplicate_min_size: 1024 * 1024, // 1MB
        }
    }
}

/// 扫描垃圾
pub fn scan(opts: &JunkOptions) -> NovaResult<JunkReport> {
    let start = Instant::now();
    let mut items: Vec<JunkItem> = Vec::new();

    // 1. 公共缓存目录
    let cache_paths = [
        (opts.root.join("Android/data/cache"), "公共缓存", JunkKind::Cache),
        (opts.root.join("Download/.tmp"), "下载临时文件", JunkKind::Tmp),
        (opts.root.join("DCIM/.thumbnails"), "相册缩略图", JunkKind::Thumbnail),
        (opts.root.join("Pictures/.thumbnails"), "图片缩略图", JunkKind::Thumbnail),
        (opts.root.join("tombstones"), "系统崩溃转储", JunkKind::Crash),
        (opts.root.join("log"), "系统日志", JunkKind::Log),
    ];

    for (path, label, kind) in &cache_paths {
        if let Some(item) = scan_dir_item(path, label, *kind) {
            items.push(item);
        }
    }

    // 2. 残留应用数据：Android/data 下无对应已安装应用的目录
    if !opts.installed_packages.is_empty() {
        let android_data = opts.root.join("Android/data");
        if android_data.exists() {
            let installed: std::collections::HashSet<String> = opts
                .installed_packages
                .iter()
                .map(|s| s.to_lowercase())
                .collect();

            let entries: Vec<_> = WalkDir::new(&android_data)
                .max_depth(1)
                .into_iter()
                .filter_map(|e| e.ok())
                .filter(|e| e.file_type().is_dir() && e.depth() == 1)
                .par_bridge()
                .map(|e| e.path().to_path_buf())
                .collect();

            for dir in entries {
                if let Some(name) = dir.file_name().and_then(|n| n.to_str()) {
                    if !installed.contains(&name.to_lowercase()) {
                        let size = dir_size(&dir, 3);
                        if size > 512 * 1024 {
                            items.push(JunkItem {
                                path: dir.to_string_lossy().to_string(),
                                label: format!("残留数据：{}", name),
                                size,
                                kind: JunkKind::Residual.as_str().to_string(),
                                is_safe: false,
                                risk: "应用未安装，数据可能仍有价值".to_string(),
                            });
                        }
                    }
                }
            }
        }
    }

    // 3. 重复文件检测（基于 BLAKE3 哈希）
    if opts.detect_duplicates {
        items.extend(detect_duplicates(&opts.root, opts.duplicate_min_size)?);
    }

    // 汇总
    let total_size: u64 = items.iter().map(|i| i.size).sum();
    let safe_size: u64 = items.iter().filter(|i| i.is_safe).map(|i| i.size).sum();

    // 按大小倒序
    items.sort_by(|a, b| b.size.cmp(&a.size));

    Ok(JunkReport {
        items,
        total_size,
        safe_size,
        duration_ms: start.elapsed().as_millis() as u64,
    })
}

fn scan_dir_item(path: &Path, label: &str, kind: JunkKind) -> Option<JunkItem> {
    if !path.exists() {
        return None;
    }
    let size = dir_size(path, 4);
    if size == 0 {
        return None;
    }
    Some(JunkItem {
        path: path.to_string_lossy().to_string(),
        label: label.to_string(),
        size,
        kind: kind.as_str().to_string(),
        is_safe: kind.is_safe(),
        risk: if kind.is_safe() { String::new() } else {
            match kind {
                JunkKind::Log => "日志可能用于调试".to_string(),
                JunkKind::Crash => "崩溃日志，可清理".to_string(),
                JunkKind::Residual => "应用未安装，数据可能仍有价值".to_string(),
                _ => String::new(),
            }
        },
    })
}

fn dir_size(path: &Path, max_depth: usize) -> u64 {
    WalkDir::new(path)
        .max_depth(max_depth)
        .into_iter()
        .filter_map(|e| e.ok())
        .par_bridge()
        .map(|e| e.metadata().map(|m| m.len()).unwrap_or(0))
        .sum()
}

/// 重复文件检测：基于 BLAKE3 哈希
/// 1. 先按 size 分组（size 相同的才有可能是重复）
/// 2. 同一 size 组内计算哈希
/// 3. 哈希相同的为重复
fn detect_duplicates(root: &Path, min_size: u64) -> NovaResult<Vec<JunkItem>> {
    use std::collections::HashMap;

    // 第一遍：按 size 分组
    let size_groups: HashMap<u64, Vec<PathBuf>> = WalkDir::new(root)
        .max_depth(6)
        .into_iter()
        .filter_map(|e| e.ok())
        .par_bridge()
        .filter_map(|e| {
            let meta = e.metadata().ok()?;
            if meta.is_file() && meta.len() >= min_size {
                Some((meta.len(), e.path().to_path_buf()))
            } else {
                None
            }
        })
        .fold(
            || HashMap::new(),
            |mut acc: HashMap<u64, Vec<PathBuf>>, (size, path)| {
                acc.entry(size).or_default().push(path);
                acc
            },
        )
        .reduce(
            || HashMap::new(),
            |mut a, b| {
                for (k, v) in b {
                    a.entry(k).or_default().extend(v);
                }
                a
            },
        );

    // 找出 size 出现 >=2 次的组
    let mut dup_groups: Vec<(u64, Vec<PathBuf>)> = size_groups
        .into_iter()
        .filter(|(_, paths)| paths.len() >= 2)
        .collect();

    // 第二遍：对每个组计算哈希
    let mut items = Vec::new();
    for (size, paths) in dup_groups.iter() {
        let mut hash_groups: HashMap<String, Vec<PathBuf>> = HashMap::new();
        for p in paths.iter() {
            if let Ok(h) = blake3_file(p) {
                hash_groups.entry(h).or_default().push(p.clone());
            }
        }
        for (h, group) in hash_groups.iter() {
            if group.len() >= 2 {
                // 重复组：第一份保留，其余为重复
                for (i, p) in group.iter().enumerate() {
                    if i == 0 {
                        continue; // 保留第一份
                    }
                    items.push(JunkItem {
                        path: p.to_string_lossy().to_string(),
                        label: format!("重复文件 (BLAKE3:{})", &h[..8]),
                        size: *size,
                        kind: JunkKind::Duplicate.as_str().to_string(),
                        is_safe: true, // 重复文件可安全删除（保留一份）
                        risk: String::new(),
                    });
                }
            }
        }
    }

    Ok(items)
}

fn blake3_file(path: &Path) -> NovaResult<String> {
    use std::io::Read;
    let mut f = std::fs::File::open(path)?;
    let mut hasher = blake3::Hasher::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hasher.finalize().to_hex().to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_scan_junk() {
        let tmp = std::env::temp_dir().join("novacare_test_junk");
        let _ = fs::remove_dir_all(&tmp);
        fs::create_dir_all(tmp.join("Android/data/cache")).unwrap();
        fs::write(tmp.join("Android/data/cache/x.tmp"), b"junk").unwrap();

        let opts = JunkOptions { root: tmp.clone(), ..Default::default() };
        let r = scan(&opts).unwrap();
        assert!(r.items.iter().any(|i| i.kind == "cache"));
        let _ = fs::remove_dir_all(&tmp);
    }
}