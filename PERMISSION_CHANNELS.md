# NovaCare 权限通道调研：清理缓存 / 冻结应用

> 背景：Android 平台**没有**「只清单个第三方 App 缓存」的公开 API。本文梳理所有可行 / 不可行的通道，说明各自的能力边界、可用性与风险，作为缓存清理与冻结功能的选型依据。
>
> 调研日期：2026-09-20 · 项目：NovaCare（minSdk 26 / targetSdk 36）

## 0. 一句话结论

- **无特殊授权（纯公开 API）**：只能「读」缓存大小，**不能清**。
- **Shizuku / ADB shell**：能一键清全部缓存（`pm trim-caches`）、能冻结；**不能**只清单个 App 的缓存（`pm clear` 会连数据一起清）。
- **无障碍（AccessibilityService）**：能自动化「点击系统设置页的『清除缓存』按钮」，实现单 App 清缓存；需用户手动开启无障碍。
- **root**：能精确删除 `/data/data/<pkg>/cache`，全能力；风险最高。
- **Device Owner**：能清数据（非仅缓存）、能隐藏冻结；企业场景，部署成本高。

## 1. 通道总览

| 通道 | 清单 App 缓存 | 清全部缓存 | 冻结 | 需 root | 需特殊授权 | 风险 | 适用性 |
|---|---|---|---|---|---|---|---|
| StorageStatsManager（公开 API） | ❌ 只读 | ❌ | ❌ | 否 | 无 | 无 | 仅统计 |
| deleteApplicationCacheFiles（隐藏 API） | ✅（历史） | — | ❌ | 否 | signature 权限 | 已弃用 | 不可用 |
| ActivityManager.clearApplicationUserData（隐藏 API） | ⚠️ 清的是数据 | — | ❌ | 否 | signature\|privileged | 清数据危险 | 不可用 |
| Shizuku | ❌（pm clear 清数据） | ✅ trim-caches | ✅ disable-user | 否 | Shizuku 授权 | 中 | ✅ 首选自动化 |
| 无障碍 AccessibilityService | ✅ 模拟点击 | 部分 | ✅ 模拟点击 | 否 | 无障碍授权 | 中（隐私/政策） | ✅ 单 App 降级 |
| root（Magisk/su） | ✅ 删 cache 目录 | ✅ | ✅ | 是 | root | 高 | 高级用户 |
| Device Owner / Profile Owner | ⚠️ 清数据 | ❌ | ✅ setApplicationHidden | 否 | 设备所有者 | 高（部署） | 企业 |
| ADB shell（有线） | 同 Shizuku | 同 | 同 | 否 | USB 调试 | 低（临时） | 开发/一次性 |

## 2. 各通道详解

### 2.1 公开 API（无需任何授权，但能力最弱）

- **`StorageStatsManager.queryStatsForPackage(...)`**（API 26+）：能精确读到某个包的 `cacheBytes` / `dataBytes` / `apkBytes`，但**只读**，无任何清除方法。NovaCare 已用它（`core/system/StorageStatsSource.kt`）做「缓存大小」展示。
- **`UsageStatsManager`**：只读「使用时长」，用于判定「不常用」，与清理无关。
- **结论**：公开 API 只能「量」，不能「清」。这就是为什么需要下面的通道。

### 2.2 隐藏 / 系统 API（普通 App 不可用）

- **`PackageManager.deleteApplicationCacheFiles(pkg, observer)`**、**`getPackageSizeInfo(...)`**：均为 `@hide` 隐藏 API，需要 `android.permission.DELETE_CACHE_FILES`（signature 级）。历史上曾能清单个 App 缓存，但已从公开 SDK 移除，普通三方 App **无法调用**。
- **`ActivityManager.clearApplicationUserData(pkg, observer)`**：隐藏 API，需要 `android.permission.CLEAR_APP_USER_DATA`（signature|privileged）+ `FORCE_STOP_PACKAGES`，仅系统/特权应用可用。且它清的是**「数据 + 缓存」**（等于恢复出厂），不是「仅缓存」，语义与需求不符。
- **结论**：这两类都不构成可用方案，仅作「为什么做不到」的说明。

### 2.3 Shizuku（ADB shell 级权限，无需 root）✅ 当前首选

- **原理**：Shizuku 以 adb 权限跑一个系统服务，三方 App 绑定后获得 `shell` 权限（约等于 `adb shell`），但**不是 root**。
- **能做什么**：
  - `pm trim-caches <极大值>`：一键清**所有** App 的可清缓存（Android 8.0+ 官方命令）。
  - `am force-stop <pkg>`：强停单个 App（释放运行态缓存/内存）。
  - `pm disable-user --user 0 <pkg>` / `pm enable <pkg>`：**冻结 / 解冻**。
  - `pm clear <pkg>`：清数据+缓存（**危险**，等同恢复出厂，本项目明确弃用）。
- **不能做什么**：不能只清单个 App 的缓存（无对应命令）；不能绕过 SELinux 直接删其他 App 的私有目录。
- **风险**：需要用户先安装 Shizuku 并通过无线调试 / root 启动它，再授权本 App；门槛中等。Shizuku 版本升级可能改变内部 API（本项目用反射调 `Shizuku.newProcess`）。
- **是否需要 root**：否（可用无线调试 ADB 启动 Shizuku）。
- **NovaCare 现状**：`core/system/ShizukuShell.kt` + `CacheCleanController.trimAllCaches()` 已实现。

### 2.4 无障碍 AccessibilityService ✅ 单 App 清理的降级方案

- **原理**：用户手动在系统设置开启无障碍后，服务可读取屏幕内容、模拟点击。
- **能做什么**：自动导航到「应用详情页」并点击「清除缓存」按钮，实现**单 App 清缓存**（这是 Sam Helper / AppManager 的常见做法）；也可模拟点击「强制停止」/「禁用」实现冻结。
- **不能做什么**：不是真正的权限通道，只是 UI 自动化——依赖具体 ROM 的按钮文案/布局，遇到异形 ROM（MIUI/ColorOS 等自定义设置页）可能找不到按钮。
- **风险**：
  - **隐私**：无障碍能读取屏幕内容，Play 政策对「无障碍 + 自动化」审查严格（需明确服务于无障碍用途，否则可能下架）。
  - **误点**：自动点击有风险，必须「用户明确发起 + 明确提示」才可操作（本项目采用 `pendingPackage` 待办标志 + 仅点击一次 + 防重入的设计）。
- **是否需要 root**：否，仅需用户在系统设置开启。
- **NovaCare 现状**：本任务新增 `CacheCleanAccessibilityService`。

### 2.5 root（Magisk / su）— 全能力但高风险

- **能做什么**：直接删除 `/data/data/<pkg>/cache` 或 `/data/user/0/<pkg>/cache`（真正的单 App 清缓存）；`pm disable` 冻结；一切 shell 操作。
- **风险**：解锁 bootloader + root 会破坏 SafetyNet / Play Integrity（银行、支付、部分游戏检测到会拒用），且有安全与保修风险。
- **是否需要 root**：是。
- **结论**：能力最强，但只适合高级用户；本项目**不做 root**，只引导。

### 2.6 Device Owner / Profile Owner（企业设备管理）

- **原理**：通过 ADB `dpm set-device-owner` 或 NFC 配置，把 App 设为「设备所有者」。
- **能做什么**：
  - `DevicePolicyManager.clearApplicationUserData(admin, pkg)`：清数据（**非仅缓存**）。
  - `setApplicationHidden(admin, pkg, hidden)` / `setPackagesSuspended(...)`：**冻结 / 隐藏**。
- **风险**：设置 Device Owner 通常需要**恢复出厂**或专用配置流程，普通用户基本走不通；且清的是数据。
- **是否需要 root**：否，但部署门槛极高。
- **结论**：面向企业 MDM，不适合消费级清理 App。

### 2.7 ADB shell（有线）

- **能力**：与 Shizuku 完全一致（本来就是同一套 shell 权限）。
- **区别**：需要 USB 连接 + 开发者选项，每次插线，不适合普通用户日常使用。
- **风险**：低，一次性。
- **结论**：作为开发调试 / 一次性救急，不能作为产品功能。

## 3. 冻结通道专项小结

| 方式 | 命令 / API | 是否移除桌面入口 | 备注 |
|---|---|---|---|
| Shizuku / ADB | `pm disable-user --user 0 <pkg>` | 是 | 恢复用 `pm enable` |
| Shizuku / ADB | `pm suspend <pkg>` | 否（保留入口但不可启动） | API 24+，较轻量 |
| 无障碍 | 模拟点击设置页「停用」 | 视 ROM | 依赖 UI |
| Device Owner | `setApplicationHidden` | 是 | 企业 |

## 4. 结论与建议

1. **单 App 清缓存**：无公开 API → 主推 **无障碍**（本任务），Shizuku 用户仍以「引导系统设置页 + 手动点」为主。
2. **一键清全部缓存**：**Shizuku `pm trim-caches`** 是唯一干净、无需 root 的自动化方案（已实现）。
3. **冻结**：**Shizuku `pm disable-user`**（已实现）；无障碍/Device Owner 作为补充。
4. **root** 仅作「能力说明」，不做产品路径（Play Integrity 风险不可接受）。
5. 无障碍通道务必遵守：**用户明确发起 + 明确提示 + 仅点击一次 + 防重入**，并把用途如实写进 `accessibility_service_config.xml` 的 description。
