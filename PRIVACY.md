# PRIVACY POLICY · 隐私政策

> 生效日期：2026-09-19
> 最后更新：2026-09-19

NovaCare 管家（以下简称"本应用"）由开发者 chinazll 在 GitHub 开源发布。本隐私政策说明本应用如何处理用户数据。

## 核心承诺：一行话总结

> **NovaCare 不申请网络权限（`android.permission.INTERNET`），从代码层面保证零网络通信。所有设备分析都在本机完成。**

## 1. 收集的数据

NovaCare **不收集、不存储、不传输**任何以下数据：
- ❌ 设备标识符（IMEI、Android ID、MAC 地址等）
- ❌ 用户位置信息
- ❌ 应用使用情况（除非用户主动授予 `PACKAGE_USAGE_STATS` 权限）
- ❌ 任何形式的分析或遥测
- ❌ 崩溃日志或诊断信息

## 2. 本地处理的数据

NovaCare 在**用户设备本地**处理以下数据，**仅在内存中临时使用，不持久化到可被外部读取的位置**：

| 数据 | 处理目的 | 存储位置 |
|------|---------|---------|
| 设备存储使用量（StatFs） | 存储管家页面 | 仅内存 |
| 内存使用量（ActivityManager） | 内存模块 | 仅内存 |
| 电池电量/温度（BroadcastReceiver） | 电池卫士 | 仅内存 |
| 应用列表（PackageManager） | 应用管理 | 仅内存 |
| 缓存目录大小（File.walkBottomUp） | 垃圾清理 | 仅内存 |

## 3. 网络通信

**NovaCare 在代码层面不进行任何网络通信**：
- `AndroidManifest.xml` 中**没有** `android.permission.INTERNET`
- 代码中**没有** `HttpURLConnection`、`OkHttp`、`Retrofit` 等网络库
- 代码中**没有** `WebView`
- 代码中**没有** `WebSocket` 或长连接

**任何对 NovaCare APK 进行的网络抓包分析都将证明零网络通信。**

## 4. 第三方数据共享

NovaCare **不与任何第三方共享数据**，因为 NovaCare 没有任何第三方 SDK：
- 无广告 SDK
- 无分析 SDK（如 Firebase Analytics）
- 无崩溃上报 SDK（如 Crashlytics）
- 无社交分享 SDK

## 5. 权限申请原则

NovaCare 严格遵循 Android 官方 [Permissions best practices](https://developer.android.com/training/permissions/usage-notes)：

1. **最小权限**：仅申请功能必需的权限
2. **动态申请**：敏感权限（`PACKAGE_USAGE_STATS`）只在用户主动触发相应功能时引导授权
3. **明确披露**：所有权限申请都有明确的功能目的说明
4. **可撤销**：用户随时可在系统设置中撤销所有授权

## 6. 关于 Shizuku 集成

NovaCare 集成 [Shizuku](https://github.com/RikkaApps/Shizuku)（Apache 2.0）作为可选高级功能，用于：
- 冻结/解冻系统应用（需用户设备已安装并运行 Shizuku）

Shizuku 由第三方 RikkaApps 提供，本隐私政策不涵盖 Shizuku 自身的数据处理。请参阅 [Shizuku 隐私政策](https://shizuku.rikka.app/)。

**如果用户未安装 Shizuku，NovaCare 的所有基础功能仍可正常使用**，仅失去"系统应用冻结"等高级功能。

## 7. 用户权利

用户拥有以下权利：
- **知情权**：本政策完整披露所有数据处理活动
- **拒绝权**：用户可拒绝授予任何可选权限
- **卸载权**：用户可随时卸载 NovaCare
- **审计权**：本应用源代码完全公开（[GitHub](https://github.com/chinazll/NovaCare)），用户可自行审计任何数据行为

## 8. 政策变更

本政策如有更新，将在 GitHub 仓库的 `PRIVACY.md` 中发布，并在 Release Notes 中提示。

## 9. 联系方式

如有隐私相关问题：
- 提交 [GitHub Issue](https://github.com/chinazll/NovaCare/issues)
- 项目作者：[chinazll](https://github.com/chinazll)

---

_本隐私政策采用 Markdown 格式存储于 `PRIVACY.md`，与源代码一起公开。_