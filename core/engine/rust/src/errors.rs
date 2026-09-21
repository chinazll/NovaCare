//! 统一错误类型
//!
//! 使用 `thiserror` 派生，所有错误都可显示转换。

use std::path::PathBuf;
use thiserror::Error;

/// NovaCare 核心引擎的统一错误类型
#[derive(Debug, Error)]
pub enum NovaError {
    /// 路径不存在
    #[error("路径不存在: {0}")]
    PathNotFound(PathBuf),

    /// 权限不足
    #[error("权限被拒绝: {0}")]
    PermissionDenied(PathBuf),

    /// IO 错误
    #[error("IO 错误: {0}")]
    Io(#[from] std::io::Error),

    /// JSON 序列化错误
    #[error("JSON 序列化错误: {0}")]
    Json(#[from] serde_json::Error),

    /// 路径遍历错误（含非法 UTF-8）
    #[error("路径无效: {0}")]
    InvalidPath(String),

    /// WalkDir 错误
    #[error("遍历错误: {0}")]
    Walk(String),

    /// 数据格式错误
    #[error("数据格式错误: {0}")]
    Format(String),

    /// 超时
    #[error("操作超时")]
    Timeout,

    /// 内部断言失败
    #[error("内部错误: {0}")]
    Internal(String),

    /// FFI 层错误（UniFFI 边界）
    #[error("FFI 错误: {0}")]
    Ffi(String),
}

pub type NovaResult<T> = Result<T, NovaError>;