# NovaCare 管家

> ⚠️ **当前状态：v0.1.0-alpha · 实验性 · 未经充分实机测试**
> ⚠️ **下载前必读**：[DISCLAIMER.md](./DISCLAIMER.md) · [INSTALL.md](./INSTALL.md) · [PRIVACY.md](./PRIVACY.md)

**NovaCare 管家** —— 灵感源自 Samsung One UI 9 设计灵魂的安卓系统优化 App

[![Status](https://img.shields.io/badge/status-alpha-orange)](https://github.com/chinazll/NovaCare/releases)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](./LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

## ⚠️ 重要说明（请先阅读）

1. **不是 Samsung 官方产品** — "One UI 9 风格" 是设计参考描述，不构成商标关联
2. **未经实机测试** — 当前 APK 未经真机/模拟器验证，可能存在运行问题
3. **零网络权限** — AndroidManifest 不含 `INTERNET`，**可验证**
4. **开源协议兼容** — 自写代码 MIT，依赖全部 Apache 2.0/MIT，**无 GPL 代码**
5. **v0.1.0-alpha** — 数字 0.1.0 + alpha 标识明确告知"这是早期实验版"

## 快速开始

### 用户：安装 APK

请先**完整阅读** [INSTALL.md](./INSTALL.md)：

1. 从 [Releases](https://github.com/chinazll/NovaCare/releases) 下载 `NovaCare-debug.apk`
2. 在手机设置中开启"安装未知应用"权限
3. 安装 APK
4. 启动 NovaCare，按需授权"使用情况访问权限"

### 开发者：本地构建

```bash
git clone https://github.com/chinazll/NovaCare.git
cd NovaCare
gradle :app:assembleDebug    # 用 Gradle Wrapper 或系统 gradle
```

## 核心特性

- **One UI 9 设计灵魂**：上观看/下操作、六区色调表面、无阴影哲学
- **设备管家**：四维健康评分（存储/内存/电池/应用防护）+ 一键优化
- **存储管家**：分类可视化、大文件分析
- **垃圾清理**：安全项默认勾选，风险项需手动确认（借鉴 SD Maid）
- **应用管理**：搜索、缓存排行、可选 Shizuku 冻结
- **电池卫士**：电量/温度/耗速，全本地
- **定时维护**：每天 3 点自动 fstrim + 安全清理

## 隐私红线（可验证）

**代码层面**：
```bash
grep -i "INTERNET" app/src/main/AndroidManifest.xml
# 期望：无匹配（零网络权限）

grep -ri "OkHttp\|Retrofit\|HttpURLConnection" app/src/main/java
# 期望：无匹配（无网络库）
```

详见 [PRIVACY.md](./PRIVACY.md)。

## 技术栈

- Kotlin 2.0 + Jetpack Compose + Material 3
- Hilt 依赖注入 + WorkManager 定时任务
- Shizuku（可选）：免 Root 增强能力
- minSdk 26（Android 8.0）/ targetSdk 34

## 法律合规性

| 风险项 | 处理 |
|--------|------|
| `QUERY_ALL_PACKAGES` | **未在 Manifest 中声明**！改用 `<queries>` 标签缩小可见范围 |
| `PACKAGE_USAGE_STATS` | 仅运行时引导用户授权，**不强制申请** |
| 网络通信 | Manifest 无 `INTERNET`，代码无网络库，**代码可审计** |
| 商标 | "NovaCare" 自定名称，不含 "Samsung/One UI" 字样 |
| 包名冲突 | `com.novacare.optimizer` 自定，无冲突 |
| 开源协议 | 自写代码 MIT + 依赖 Apache 2.0/MIT，无 GPL |

## 调研的灵感来源

**仅借鉴设计思路，未复制任何代码**：
- [SD Maid 2/SE](https://github.com/d4rken-org/sdmaid-se)（GPL-3.0）—— 标杆级清理工具
- [Universal Android Debloater](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)（MIT）—— 安全分级思想
- [AppManager](https://github.com/MuntashirAkon/AppManager)（GPL-3.0）—— 应用生命周期
- [AAO](https://github.com/mehedihjoy0/AAO)（MIT）—— 夜间维护调度

**算法与设计思路不受版权保护，NovaCare 的所有代码均为原创。**

## 文档

- [INSTALL.md](./INSTALL.md) — 详细安装指南（含各品牌 ROM 路径）
- [DISCLAIMER.md](./DISCLAIMER.md) — 免责声明与责任界定
- [PRIVACY.md](./PRIVACY.md) — 隐私政策
- [DESIGN_SPEC.md](./DESIGN_SPEC.md) — One UI 9 设计灵魂规范

## License

MIT — 欢迎 fork、贡献、提 Issue。