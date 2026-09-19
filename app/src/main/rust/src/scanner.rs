//! 文件扫描器
//!
//! 用 rayon 并行化目录遍历，返回目录树 + 大小统计

use crate::errors::{NovaError, NovaResult};
use crate::models::FileNode;
use rayon::prelude::*;
use std::path::{Path, PathBuf};
use std::time::Instant;
use walkdir::WalkDir;

/// 扫描结果（除 FileNode 外的元数据）
#[derive(Debug, Clone)]
pub struct ScanResult {
    pub root: FileNode,
    pub total_files: u64,
    pub total_size: u64,
    pub duration_ms: u64,
}

impl ScanResult {
    /// 返回最大的 N 个文件
    pub fn top_n_files(&self, n: usize) -> Vec<FileNode> {
        let mut all: Vec<&FileNode> = Vec::new();
        collect_files(&self.root, &mut all);
        let mut all: Vec<FileNode> = all.into_iter().cloned().collect();
        all.sort_by(|a, b| b.size.cmp(&a.size));
        all.truncate(n);
        all
    }
}

fn collect_files<'a>(node: &'a FileNode, out: &mut Vec<&'a FileNode>) {
    if !node.is_dir {
        out.push(node);
    } else {
        for c in &node.children {
            collect_files(c, out);
        }
    }
}

/// 扫描选项
#[derive(Debug, Clone)]
pub struct ScanOptions {
    /// 最大深度（None = 无限制）
    pub max_depth: Option<usize>,
    /// 是否跟随符号链接
    pub follow_links: bool,
    /// 是否收集子节点（false 时仅顶层统计）
    pub collect_tree: bool,
    /// 进度回调（可选）
    pub progress: Option<Box<dyn Fn(u64) + Send + Sync>>,
}

impl Default for ScanOptions {
    fn default() -> Self {
        Self {
            max_depth: Some(6),
            follow_links: false,
            collect_tree: false,
            progress: None,
        }
    }
}

/// 扫描入口
pub fn scan(path: &Path, opts: &ScanOptions) -> NovaResult<ScanResult> {
    if !path.exists() {
        return Err(NovaError::PathNotFound(path.to_path_buf()));
    }

    let start = Instant::now();

    // 第一遍：并行收集所有 (路径, 大小)
    let walker = WalkDir::new(path)
        .max_depth(opts.max_depth.unwrap_or(usize::MAX))
        .follow_links(opts.follow_links)
        .into_iter()
        .filter_map(|e| e.ok());

    let entries: Vec<(PathBuf, u64, bool, Option<i64>)> = walker
        .par_bridge()
        .map(|e| {
            let meta = e.metadata().ok();
            let size = meta.as_ref().map(|m| m.len()).unwrap_or(0);
            let is_dir = meta.as_ref().map(|m| m.is_dir()).unwrap_or(false);
            let modified = meta
                .and_then(|m| m.modified().ok())
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map(|d| d.as_secs() as i64);
            if let Some(ref p) = opts.progress {
                p(1);
            }
            (e.path().to_path_buf(), size, is_dir, modified)
        })
        .collect();

    // 第二遍：构建树
    let root = if opts.collect_tree {
        build_tree(path, &entries)
    } else {
        // 仅构建浅层节点
        let total: u64 = entries.iter().map(|(_, s, _, _)| s).sum();
        FileNode {
            path: path.to_string_lossy().to_string(),
            size: total,
            is_dir: path.is_dir(),
            modified: None,
            children: vec![],
        }
    };

    let total_files = entries.iter().filter(|(_, _, d, _)| !d).count() as u64;
    let total_size: u64 = entries.iter().map(|(_, s, _, _)| s).sum();

    Ok(ScanResult {
        root,
        total_files,
        total_size,
        duration_ms: start.elapsed().as_millis() as u64,
    })
}

fn build_tree(root: &Path, entries: &[(PathBuf, u64, bool, Option<i64>)]) -> FileNode {
    use std::collections::BTreeMap;

    #[derive(Default)]
    struct Node {
        size: u64,
        is_dir: bool,
        modified: Option<i64>,
        children: BTreeMap<String, Node>,
    }

    let mut root_node = Node::default();
    root_node.is_dir = true;
    root_node.modified = None;

    for (path, size, is_dir, modified) in entries {
        if path == root {
            continue;
        }
        if let Ok(rel) = path.strip_prefix(root) {
            let comps: Vec<_> = rel.components().collect();
            let mut cur = &mut root_node;
            for (i, c) in comps.iter().enumerate() {
                let key = c.as_os_str().to_string_lossy().to_string();
                let is_last = i == comps.len() - 1;
                let entry = cur.children.entry(key.clone()).or_insert_with(Node::default());
                if is_last {
                    entry.size = *size;
                    entry.is_dir = *is_dir;
                    entry.modified = *modified;
                } else {
                    entry.is_dir = true;
                }
                cur = entry;
            }
        }
    }

    // 转换为 FileNode 并计算目录 size
    fn to_file_node(name: &str, node: Node) -> FileNode {
        let mut children: Vec<FileNode> = node
            .children
            .into_iter()
            .map(|(k, v)| to_file_node(&k, v))
            .collect();
        let total_size = children.iter().map(|c| c.size).sum::<u64>() + node.size;
        children.sort_by(|a, b| b.size.cmp(&a.size));
        FileNode {
            path: name.to_string(),
            size: total_size,
            is_dir: node.is_dir,
            modified: node.modified,
            children,
        }
    }

    to_file_node(
        root.file_name()
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or_else(|| root.to_string_lossy().to_string())
            .as_str(),
        root_node,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_scan_empty() {
        let tmp = std::env::temp_dir().join("novacare_test_empty");
        let _ = fs::create_dir_all(&tmp);
        let result = scan(&tmp, &ScanOptions::default()).unwrap();
        assert_eq!(result.total_files, 0);
        let _ = fs::remove_dir_all(&tmp);
    }

    #[test]
    fn test_scan_with_files() {
        let tmp = std::env::temp_dir().join("novacare_test_files");
        let _ = fs::remove_dir_all(&tmp);
        fs::create_dir_all(&tmp).unwrap();
        fs::write(tmp.join("a.txt"), b"hello world").unwrap();
        fs::create_dir_all(tmp.join("sub")).unwrap();
        fs::write(tmp.join("sub").join("b.txt"), vec![0u8; 1024]).unwrap();
        let result = scan(&tmp, &ScanOptions::default()).unwrap();
        assert_eq!(result.total_files, 2);
        assert!(result.total_size >= 1024 + 11);
        let _ = fs::remove_dir_all(&tmp);
    }
}