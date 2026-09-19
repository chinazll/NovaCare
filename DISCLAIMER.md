# DISCLAIMER · 免责声明

> ⚠️ **请仔细阅读后再使用本软件**

## 1. 项目状态

**NovaCare 管家 v0.1.0-alpha** 是一个**实验性开源项目**，由个人开发者（chinazll）在业余时间开发，**未经过充分生产环境测试**。

- 当前状态：**alpha / pre-release**
- 可能存在的风险：功能不稳定、数据丢失风险、与某些 ROM 不兼容
- **不建议**用于生产设备或关键任务场景
- 使用前请**完整阅读源码**（仅约 2000 行 Kotlin，全部公开）

## 2. 数据责任

NovaCare 提供「垃圾清理」「应用冻结」「一键优化」等功能，**这些操作可能影响用户数据**：

- **垃圾清理**：删除缓存、临时文件、缩略图、日志。理论上不会影响用户主动存储的数据，
  清理范围被限制在**路径白名单**内，但**仍可能因系统/ROM 差异导致误删**
- **应用冻结**：通过 Shizuku `pm suspend` 可逆操作（**需要 Shizuku 授权**；
  未安装 Shizuku 时该功能不可用，App 会明确提示，不会假装成功）。
  但**部分 ROM 厂商实现差异**可能导致冻结后无法恢复（极小概率）
- **fstrim 闪存整理**：需要 ADB 级权限（经 Shizuku）。**不具备权限时会跳过执行，
  不会谎报成功**
- **一键优化**：自动清理安全垃圾。**不会**删除用户照片、文档、聊天记录；
  **不包含**内存整理（该功能未实现，此前文档曾错误宣传）
- **电池卫士**：仅展示数据，不修改系统电源策略
- **存储分析**：由 Rust 引擎在本机扫描，不上传任何数据

**残留目录清理**：本项目**不做**「已卸载应用的残留目录」检测。
原因是 Android 11+ 分区存储下无法可靠枚举已安装应用，强行比对会把
**仍在使用中的应用**（如微信、游戏存档）的数据目录误判为残留并删除。
宁可缺失该能力，也不承担误删风险。

**用户应自行承担使用风险。建议首次使用前对重要数据做备份。**

## 3. 权限使用

NovaCare 申请的所有权限均**在 Android 运行时用户授权后方可使用**：

| 权限 | 用途 | 是否必选 |
|------|------|---------|
| `WAKE_LOCK` | 夜间定时维护期间保持 CPU 工作 | 自动 |
| `FOREGROUND_SERVICE` | 定时任务执行 | 自动 |
| `RECEIVE_BOOT_COMPLETED` | 开机自动恢复定时任务 | 自动 |
| `PACKAGE_USAGE_STATS` | 显示应用最近使用情况（用于识别"不常用应用"） | **可选**，需用户主动到系统设置授予 |
| `QUERY_ALL_PACKAGES` | 不申请！改用 `<queries>` 标签缩小可见范围 | ❌ 不申请 |

**所有敏感权限都是用户主动授予的，NovaCare 不会绕过系统安全机制。**

## 4. 隐私承诺

NovaCare **不申请任何网络权限**（AndroidManifest 中**没有** `android.permission.INTERNET`）。这意味着：
- ✅ 不会上传任何设备数据
- ✅ 不会上传任何应用数据
- ✅ 不会上传任何用户行为
- ✅ 所有分析都在本机完成

**本承诺在源码层面可验证**（参见 `AndroidManifest.xml`）。

## 5. 与 Samsung 的关系

NovaCare **不是 Samsung Electronics 的官方产品**。"One UI 9 风格"是设计风格的描述性用语，不构成商标侵权或官方合作暗示。
- "One UI"、"One UI 9" 是 Samsung Electronics 的注册商标
- NovaCare 在设计语言上参考 One UI 9，但**不内置任何 Samsung 专有代码、图标、字体、SDK**
- NovaCare 使用的是开源的 Material 3 + Jetpack Compose 实现

## 6. 第三方依赖

NovaCare 使用以下第三方依赖（均为 Apache 2.0 / MIT 协议）：
- Jetpack Compose（Apache 2.0）
- Material 3（Apache 2.0）
- Hilt（Apache 2.0）
- Shizuku（Apache 2.0）
- WorkManager（Apache 2.0）

完整列表参见 `app/build.gradle.kts`。

NovaCare 的灵感来自以下开源项目（**仅借鉴思路，未复制任何代码**）：
- [SD Maid 2/SE](https://github.com/d4rken-org/sdmaid-se)（GPL-3.0）
- [AppManager](https://github.com/MuntashirAkon/AppManager)（GPL-3.0）
- [Universal Android Debloater](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)（MIT）
- [AAO](https://github.com/mehedihjoy0/AAO)（MIT）

**算法与设计思路不受版权保护。NovaCare 的所有代码均为原创。**

## 7. 无担保声明

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
THE AUTHORS SHALL NOT BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY.

参见完整 [LICENSE (MIT)](./LICENSE)。

## 8. 反馈

发现问题请提交 [GitHub Issue](https://github.com/chinazll/NovaCare/issues)。
**不接受**关于数据丢失的责任索赔，但开发者会尽力协助恢复。

---

_最后更新：2026-09-19_