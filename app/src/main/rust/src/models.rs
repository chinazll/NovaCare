//! 数据模型（与 Kotlin 端通过 JSON 通信）

use serde::{Deserialize, Serialize};

/// 文件节点（扫描结果）
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileNode {
    /// 完整路径
    pub path: String,
    /// 字节大小
    pub size: u64,
    /// 是否是目录
    pub is_dir: bool,
    /// 修改时间戳（Unix epoch seconds）
    pub modified: Option<i64>,
    /// 子节点（仅目录）
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub children: Vec<FileNode>,
}

/// 存储分类
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StorageCategory {
    /// 分类名（照片/视频/音频/文档/应用）
    pub name: String,
    /// 字节大小
    pub size: u64,
    /// 占比（0.0 ~ 1.0）
    pub ratio: f64,
    /// 颜色 ID（与 Android 端约定）
    pub color_id: i32,
}

/// 存储分析结果
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StorageReport {
    /// 总字节
    pub total: u64,
    /// 已用字节
    pub used: u64,
    /// 各类
    pub categories: Vec<StorageCategory>,
    /// N 个最大文件
    pub largest_files: Vec<FileNode>,
    /// 扫描耗时（毫秒）
    pub duration_ms: u64,
}

/// 垃圾项
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JunkItem {
    /// 完整路径
    pub path: String,
    /// 显示名
    pub label: String,
    /// 字节大小
    pub size: u64,
    /// 分类（cache / thumbnail / log / tmp / residual / duplicate）
    pub kind: String,
    /// 是否安全可清理
    pub is_safe: bool,
    /// 风险描述
    #[serde(skip_serializing_if = "String::is_empty")]
    pub risk: String,
}

/// 垃圾扫描结果
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JunkReport {
    pub items: Vec<JunkItem>,
    pub total_size: u64,
    pub safe_size: u64,
    pub duration_ms: u64,
}

/// 应用信息
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppInfo {
    pub package_name: String,
    pub label: String,
    /// 是否系统应用
    pub is_system: bool,
    /// 字节大小
    pub size: u64,
    /// 安装时间
    pub install_time: i64,
    /// 最后更新时间
    pub update_time: i64,
    /// 缓存大小
    pub cache_size: u64,
    /// 数据大小
    pub data_size: u64,
    /// 版本名
    pub version: String,
    /// targetSdk
    pub target_sdk: i32,
    /// 最后使用时间（Unix 秒）。0 = 未知（未获使用情况权限或未采集）。
    /// P2-6：此前 app_analyzer::recommend_cleanup 的 unused_days 参数是死参数，
    ///       因为模型里根本没有这个字段可供判断。
    #[serde(default)]
    pub last_used_time: i64,
}

/// 电池报告
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BatteryReport {
    /// 健康度评分（0-100）
    pub health_score: u32,
    /// 充电周期估算。
    /// **None = 未知**：真实周期需读取 `/sys/class/power_supply/battery/cycle_count`（需 root），
    /// 本项目不做 root，因此明确表示为「未知」，而不是返回一个伪造的 0。
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cycle_count: Option<u32>,
    /// 电池温度
    pub temperature: f32,
    /// 电压 (mV)
    pub voltage: i32,
    /// 当前电量百分比
    pub level: u32,
    /// 状态（charging / discharging / full）
    pub status: String,
    /// 优化建议
    pub suggestions: Vec<String>,
}
