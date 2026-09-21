//! 电池监控器
//!
//! 基于传入的电池数据（来自 Android BroadcastReceiver）计算健康度评分与优化建议
//!
//! 健康度评分算法（0-100）：
//! - 电量 < 20%：扣 30 分
//! - 温度 > 40℃：扣 20 分
//! - 温度 > 45℃：扣 30 分
//! - 充电中且电量 > 80%：建议停止涓流
//! - 未充电且温度 > 38℃：建议降低负载

use crate::models::BatteryReport;
use serde::{Deserialize, Serialize};

/// 电池原始数据
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BatteryInput {
    pub level: u32,
    pub temperature: f32,
    pub voltage: i32,
    pub current: i32,
    pub status: String,
    pub health: String,
    pub plugged: i32,
}

/// 计算电池报告
pub fn analyze(input: &BatteryInput) -> BatteryReport {
    let mut score: u32 = 100;
    let mut suggestions = Vec::new();

    // 电量扣分
    if input.level < 20 {
        score = score.saturating_sub(30);
        suggestions.push("电量偏低，建议开启省电模式".to_string());
    } else if input.level < 40 {
        score = score.saturating_sub(10);
        suggestions.push("电量中等，建议合理使用".to_string());
    }

    // 温度扣分
    if input.temperature >= 45.0 {
        score = score.saturating_sub(30);
        suggestions.push("电池温度过高，建议停止高负载任务并通风散热".to_string());
    } else if input.temperature >= 40.0 {
        score = score.saturating_sub(20);
        suggestions.push("电池温度偏高，建议暂停游戏等高负载".to_string());
    } else if input.temperature >= 38.0 {
        score = score.saturating_sub(8);
    }

    // 充电状态建议
    let is_charging = matches!(input.status.as_str(), "charging" | "full");
    if is_charging && input.level >= 80 {
        suggestions.push("电量已 80%+，可断开充电器延长电池寿命".to_string());
    }
    if is_charging && input.temperature >= 38.0 {
        suggestions.push("充电时温度偏高，建议摘下保护壳".to_string());
    }

    // 健康度评价
    if matches!(input.health.as_str(), "good" | "Good" | "GOOD") {
        // 健康
    } else if matches!(input.health.as_str(), "overheating" | "Overheating") {
        score = score.saturating_sub(15);
        suggestions.push("电池报告过热，建议检查".to_string());
    } else if matches!(input.health.as_str(), "dead" | "Dead") {
        score = score.saturating_sub(50);
        suggestions.push("⚠️ 电池已损坏，请前往售后".to_string());
    } else {
        suggestions.push(format!("电池健康度: {}", input.health));
    }

    // 电压异常
    if input.voltage < 3500 {
        suggestions.push("电池电压偏低，可能电量不足".to_string());
    } else if input.voltage > 4400 {
        suggestions.push("电池电压偏高（充电中属正常）".to_string());
    }

    BatteryReport {
        health_score: score,
        cycle_count: estimate_cycles(),
        temperature: input.temperature,
        voltage: input.voltage,
        level: input.level,
        status: input.status.clone(),
        suggestions,
    }
}

/// 充电周期估算。
///
/// **明确不做**：真实周期需 root 读取 `/sys/class/power_supply/battery/cycle_count`，
/// 本项目坚持无 root 路线，因此不猜测、不伪造。调用方得到 `None`，UI 显示「未知」。
/// 若未来接入 root/Shizuku，可在此读取 sysfs 并返回 `Some(n)`，
/// 接口签名（Option<u32>）已为此预留，无需改动 Kotlin 侧。
pub fn estimate_cycles() -> Option<u32> {
    None
}

/// 评分等级描述
pub fn score_to_verdict(score: u32) -> &'static str {
    match score {
        90..=100 => "状态极佳",
        75..=89 => "状态良好",
        60..=74 => "状态一般",
        40..=59 => "需要关注",
        _ => "建议立即优化",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_healthy_battery() {
        let input = BatteryInput {
            level: 80,
            temperature: 30.0,
            voltage: 4100,
            current: -500,
            status: "discharging".to_string(),
            health: "Good".to_string(),
            plugged: 0,
        };
        let r = analyze(&input);
        assert!(r.health_score >= 90);
        assert_eq!(score_to_verdict(r.health_score), "状态极佳");
    }

    #[test]
    fn test_overheating() {
        let input = BatteryInput {
            level: 50,
            temperature: 46.0,
            voltage: 4200,
            current: 0,
            status: "discharging".to_string(),
            health: "Overheating".to_string(),
            plugged: 0,
        };
        let r = analyze(&input);
        assert!(r.health_score < 60);
        assert!(!r.suggestions.is_empty());
    }

    #[test]
    fn test_low_battery() {
        let input = BatteryInput {
            level: 15,
            temperature: 30.0,
            voltage: 3600,
            current: 0,
            status: "discharging".to_string(),
            health: "Good".to_string(),
            plugged: 0,
        };
        let r = analyze(&input);
        assert!(r.health_score <= 70);
        assert!(r.suggestions.iter().any(|s| s.contains("省电")));
    }
}