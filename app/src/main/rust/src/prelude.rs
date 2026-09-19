//! 统一导出（prelude）
//!
//! 使用 `use novacare_core::prelude::*;` 一行导入所有常用项

pub use crate::errors::{NovaError, NovaResult};
pub use crate::models::{
    AppInfo, BatteryReport, FileNode, JunkItem, JunkReport, StorageCategory, StorageReport,
};
pub use crate::scanner::{scan, ScanOptions, ScanResult};
pub use crate::storage::{analyze as analyze_storage, StorageOptions, Category};
pub use crate::junk_detector::{scan as scan_junk, JunkOptions, JunkKind};
pub use crate::app_analyzer::{analyze as analyze_apps, AppStats, AppFilter, SortBy};
pub use crate::battery_monitor::{analyze as analyze_battery, BatteryInput};
