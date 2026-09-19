# NovaCare 管家

> One UI 9 设计灵魂 · Rust 核心引擎 · 零网络权限的安卓系统优化 App

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84)
![Rust](https://img.shields.io/badge/Rust-核心引擎-orange)
![Status](https://img.shields.io/badge/status-alpha(实验性)-red)

**当前版本：`v0.4.0-alpha`（实验性，未上架任何应用商店）**

---

## 一、这个项目的真实状态（请先读这段）

这是一份**如实描述**的 README。此前版本曾夸大功能，现已全部修正。
下表是「文档宣称」与「代码现实」的逐条对照：

| 能力 | 状态 | 说明 |
|------|------|------|
| 设备健康评分 | ✅ 可用 | 四维加权：存储 35% / 内存 30% / 电池 20% / 应用 15% |
| 存储分类分析 | ✅ 真实扫描 | 由 Rust 引擎并行扫描得出，**不是**估算比例 |
| 大文件分析 | ✅ 可用 | Rust 引擎返回体积最大的文件列表 |
| 垃圾清理 | ✅ 可用（受限） | 仅清理 Rust 判定为 `is_safe` 且位于路径白名单内的项 |
| 应用列表与体积 | ✅ 可用 | 缓存/数据大小通过 `StorageStatsManager` 真实读取 |
| 重复文件检测 | ⚠️ 引擎已实现，UI 未开放 | Rust `junk_detector` 支持 BLAKE3 去重，默认关闭 |
| **应用冻结** | ⚠️ **需要 Shizuku** | 通过 `pm suspend`（可逆，不删数据）。未装 Shizuku 时会明确提示不可用 |
| **fstrim 闪存整理** | ⚠️ **需要 Shizuku** | 需 ADB 级权限。不可用时**跳过且不谎报成功** |
| 夜间自动维护 | ✅ 已注册 | 每天凌晨 3 点，设备空闲且电量充足时执行 |
| 耗电排行 / 唤醒锁 | ❌ **未实现** | 此前文档提及但代码中不存在，已从文档移除 |
| 内存整理 | ❌ **未实现** | 同上，已移除宣传 |

---

## 二、架构

```
┌──────────────────────────────────────────────────┐
│ UI 层  Kotlin + Jetpack Compose + Material 3     │
│       One UI 9 灵魂：大标题 / 六区色调 / 无阴影   │
├──────────────────────────────────────────────────┤
│ 表现层  MVI：State + Intent（StateFlow）          │
├──────────────────────────────────────────────────┤
│ 仓储层  DeviceRepository / AppFreezeManager       │
│       （只做 Android 系统 API 调用与副作用）      │
├──────────────────────────────────────────────────┤
│ JNI 桥接层  RustCore.kt（org.json 解析）          │
├──────────────────────────────────────────────────┤
│ Rust 核心引擎  libnovacare_core.so                │
│   scanner(rayon 并行) · storage · junk_detector   │
│   (BLAKE3) · app_analyzer · battery_monitor       │
└──────────────────────────────────────────────────┘
```

**单一数据源原则**：所有重量级计算（扫描、分类、去重、排序、电池评分）
一律由 Rust 完成。Kotlin 侧**不存在**任何重复的评分或统计算法——
这是本项目最重要的架构约束（曾因 Kotlin 与 Rust 各有两套电池算法而产生矛盾数据）。

**优雅降级**：若 `.so` 未被打包（例如纯 Kotlin 的本地构建），
`RustCore.isAvailable == false`，App 依然可用，但存储分类会**留空并说明原因**，
而不是展示估算出来的假数据。

---

## 三、技术栈

| 层 | 技术 |
|----|------|
| UI | Kotlin 2.0 · Jetpack Compose · Material 3 (BOM 2025.10) |
| DI | Hilt + KSP |
| 异步 | Coroutines + Flow |
| 引擎 | **Rust 1.85**（`cdylib` → JNI） |
| 构建 | AGP 8.7 · Gradle 8.9 · **cargo-ndk** · NDK 26.1 |
| 可选提权 | Shizuku（仅冻结 / fstrim） |

SDK：`minSdk 26`（Android 8.0+）· `targetSdk 36` · `compileSdk 36`

---

## 四、构建

### 方式 A：只构建（不需要 Rust 工具链）

```bash
./gradlew :app:assembleDebug
```

Rust 引擎不会被打包，`RustCore.isAvailable == false`，App 以降级模式运行。
这让你可以在没有 NDK 的环境里快速迭代 UI。

### 方式 B：完整构建（含 Rust 引擎）

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
./scripts/build-rust.sh     # 编译 .so 到 app/src/main/jniLibs/
./gradlew :app:assembleDebug
```

CI 完整流程见 [.github/workflows/android.yml](.github/workflows/android.yml)。

---

## 五、隐私与权限

### Manifest 实际声明的权限

| 权限 | 用途 |
|------|------|
| `WAKE_LOCK` | 夜间维护期间保持唤醒 |
| `FOREGROUND_SERVICE` | 长时间扫描任务 |
| `RECEIVE_BOOT_COMPLETED` | 重启后恢复定时任务 |

**主动放弃的权限**：

- ❌ `INTERNET` —— 见下方验证命令
- ❌ `QUERY_ALL_PACKAGES` —— Google Play 严格管控且侵犯隐私。
  代价是无法可靠判定「残留目录」，因此**默认关闭残留清理**（防误删在用应用数据）
- ❌ `MANAGE_EXTERNAL_STORAGE` —— 不做全盘文件访问

`PACKAGE_USAGE_STATS` 为**可选的运行时授权**，未授权时仅「不常用应用识别」不可用，
其余功能不受影响。首页会在未授权时给出明确的授权引导卡片。

### 零网络验证

```bash
# 1. Android 层
grep -i "INTERNET" app/src/main/AndroidManifest.xml      # 应无输出

# 2. Kotlin 层
grep -riE "HttpURLConnection|OkHttp|Retrofit|Socket" app/src/main/java   # 应无输出

# 3. Rust 依赖层（此前 README 的验证命令漏了这一层，现已补上）
grep -rE "reqwest|hyper|tokio|ureq|curl" app/src/main/rust/Cargo.toml    # 应无输出
```

Rust 依赖仅为：`serde` / `serde_json` / `rayon` / `walkdir` / `thiserror` /
`blake3` / `log` / `jni` / `android_logger` / `once_cell`，**全部无网络能力**。

---

## 六、依赖与开源协议

本项目自研代码采用 **MIT**。

**未复制任何第三方项目的源码**。以下项目仅作为设计思路的参考被研究与引用
（算法与思路不受版权保护）：

| 项目 | 协议 | 借鉴点 |
|------|------|--------|
| [SD Maid 2/SE](https://github.com/d4rken-org/sdmaid-se) | GPL-3.0 | 模块划分、安全分级模型、调度器设计 |
| [Canta](https://github.com/samolego/Canta) | Apache-2.0 | Shizuku 集成方式、`pm suspend` 可逆卸载思路 |
| [UAD-NG](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) | MIT | 应用安全分级（其本身完全用 Rust 编写） |
| [AppManager](https://github.com/MuntashirAkon/AppManager) | GPL-3.0 | 应用信息展示维度 |

运行时依赖均为宽松协议（Apache-2.0 / MIT）：Compose、Hilt、WorkManager、Shizuku、DataStore。

---

## 七、已知限制

1. **冻结 / fstrim 需要 Shizuku**，且需用户主动授权 ADB 权限。
2. **不做 root**。充电周期等需读取 sysfs 的指标显示为「未知」，而不是凭空猜测。
3. **不检测「残留目录」**：Android 11+ 分区存储下无法可靠枚举已安装应用，
   强行比对会误删在用应用（微信、游戏存档）的数据。宁可不做。
4. **清理范围受限**：只清理白名单路径内的安全项，不触及用户文档与照片。
5. 处于 alpha 阶段，**不建议作为主力清理工具**，请自行评估风险后使用。

---

## 八、文档

- [INSTALL.md](./INSTALL.md) —— 安装步骤与各品牌 ROM 的「未知来源」开启路径
- [DESIGN_SPEC.md](./DESIGN_SPEC.md) —— One UI 9 设计规范
- [DISCLAIMER.md](./DISCLAIMER.md) —— 免责声明（**安装前请阅读**）
- [PRIVACY.md](./PRIVACY.md) —— 隐私政策

---

## 九、非官方声明

NovaCare **不是** Samsung Electronics 的官方产品，与 Samsung 无任何隶属或合作关系。
「One UI」为 Samsung Electronics 的商标，本项目仅将其作为**设计风格参考**进行描述，
不主张任何权利。
