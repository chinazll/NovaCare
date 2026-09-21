# NovaCare

> 安卓系统优化应用 · Rust 核心引擎 · 全程本机处理不联网

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
![Android](https://img.shields.io/badge/Android-8.0+-3DDC84)
![Rust](https://img.shields.io/badge/Rust-1.85+-orange)
![Status](https://img.shields.io/badge/status-v0.7.2--alpha-yellow)

**当前版本：`v0.7.2-alpha`**（已发布 [GitHub Releases](https://github.com/chinazll/NovaCare/releases)，实验性）

---

## 这是什么

NovaCare 是一个**完全本机运行**的安卓系统优化 App：用 Rust 写的核心引擎扫描存储分类、识别可清理项、应用冻结与回收站机制。**全程不上传任何数据**。

设计语言对标 One UI 9/9.5 的 Next-Gen 视觉语言（浮层胶囊底部导航、边缘光带、空间层次、群组飞入、Now Bar 体征条），不是 M3 的标准贴底导航。

---

## 当前能做什么（v0.7.2 实测）

| 能力 | 状态 | 备注 |
|------|------|------|
| **首页一键清理** | ✅ 闭环 | 扫描→清单→执行→写历史，不再是死路 |
| **深度清理页** | ✅ 完整 | 勾选/全选/分类筛选/路径白名单，回收站 7 天可撤销 |
| **冻结应用** | ⚠️ 需 Shizuku | 通过 `pm suspend` 可逆冻结，需 ADB 工作 |
| **自动化规则** | ✅ 可用 | 定时 / 电量 / 存储 / 空闲四种触发，**6 小时执行窗口**（不保证精确到分钟） |
| **AI 自然语言** | ✅ 本地 | "每周日 3 点清理垃圾" 类说法，本地确定性解析 |
| **云端 AI** | ⚠️ 需自配 Key | 默认关闭，未配置时一个网络请求都不发 |
| **夜间自动维护** | ✅ 已注册 | 由 WorkManager 周期任务驱动 |

---

## 已修复的真实 bug（v0.7.1 → v0.7.2）

每一项都在代码里被验证可复现 → 修复后已 commit：

- ❌ 启动崩溃：`Configuration.Provider` 与 `@Inject lateinit` 互相调用导致 `UninitializedPropertyAccessException` → 改用 Hilt `EntryPoint` 解耦
- ❌ 首页一键释放是死路：扫描完成没 CTA → 现在直接调用 `onConfirm()` 真正执行
- ❌ 自动化「开机时」是合同谎：当前调度没有 BOOT_COMPLETED 监听 → 选项已移除
- ❌ 通知权限跳错页：跳到应用详情页 → 跳到 `ACTION_APP_NOTIFICATION_SETTINGS`
- ❌ "反馈问题"静默失败：浏览器缺失时无反馈 → 显示 URL 让用户复制打开
- ❌ 时间解析丢分钟：`22:30` 被截成 `22` → 现在识别出分钟并告知「6h 窗口」
- ❌ 视觉不一致：仅首页用了 NowBar / SpatialLayer / StaggerFlyIn → 5 个二级页全部统一

---

## 架构

```
┌─────────────────────────────────────────────┐
│ UI 层  Kotlin + Compose + Material 3        │
│       One UI Next-Gen 浮层语言                │
├─────────────────────────────────────────────┤
│ 表现层  MVI（State + Intent, StateFlow）     │
├─────────────────────────────────────────────┤
│ 领域层  UseCase / Repository / Domain Models │
├─────────────────────────────────────────────┤
│ 系统层  SystemPermissions / ShizukuShell     │
├─────────────────────────────────────────────┤
│ FFI    UniFFI 0.28 + JNA（libjnidispatch.so） │
├─────────────────────────────────────────────┤
│ Rust   libuniffi_novacare.so × 4 ABI         │
│   scanner(rayon 并行) · storage · junk ·     │
│      · app_analyzer · battery_monitor        │
└─────────────────────────────────────────────┘
```

模块结构：

```
core/{model, common, engine, ai, system, data, domain, automation}
feature/{home, clean, freeze, automation, assistant}
ui/designsystem
app
```

---

## 技术栈

| 层 | 选型 |
|----|------|
| UI | Kotlin 2.0 · Jetpack Compose · Material 3 |
| 设计 | 浮动 dock · Now Bar · 边缘光带 · 群组飞入 · 空间浮层 |
| DI | Hilt 2.52 + KSP |
| 异步 | Coroutines + Flow |
| 引擎 | **Rust 1.85**（cdylib + UniFFI 0.28） |
| FFI | UniFFI Kotlin Bindings + JNA 5.14 |
| 调度 | WorkManager（6 小时周期任务） |
| 构建 | AGP 8.7 · Gradle 8.9 · cargo-ndk · NDK 26.1 |
| 提权 | Shizuku（可选，仅冻结/fstrim 用） |

SDK：`minSdk 26`（Android 8.0+）· `targetSdk 36` · `compileSdk 36`

---

## 构建

### 仅 Kotlin（无 Rust）

```bash
./gradlew :app:assembleDebug
```

Rust 引擎不会被打包，App 以降级模式运行（只能清理白名单内的基础项）。

### 完整构建（带 Rust 引擎）

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
./scripts/build-rust.sh
./gradlew :app:assembleDebug
```

CI 完整流程：[.github/workflows/android.yml](.github/workflows/android.yml)

---

## 隐私

**主动放弃**的权限：

- ❌ `INTERNET` —— `app/src/main/AndroidManifest.xml` 无此声明
- ❌ `QUERY_ALL_PACKAGES` —— Google Play 严控且侵犯隐私。代价是无法可靠判定「残留目录」，**默认关闭残留清理**
- ❌ `MANAGE_EXTERNAL_STORAGE` —— 不做全盘文件访问

**运行时可选**（用户主动开启才有效）：

- `PACKAGE_USAGE_STATS` —— 识别长期未用应用
- `MANAGE_EXTERNAL_STORAGE` —— 完整扫描存储
- `POST_NOTIFICATIONS` —— 长时间任务通知

---

## 已知限制

1. **冻结 / fstrim 需要 Shizuku**，且需用户主动授权 ADB 权限
2. **不做 root**。需要 sysfs 读取的指标显示为「未知」
3. **不检测残留目录**：分区存储下无法可靠枚举已装应用，强比对会误删数据，宁可不做
4. **清理范围受限**：只清白名单路径内的安全项
5. **自动化规则** 6 小时执行窗口，**不保证精确到分钟**。这是 WorkManager 周期任务的硬限制

---

## 协议

本项目自研代码 **MIT**。详见 [LICENSE](./LICENSE)。

未复制任何第三方项目源码。设计思路参考：

| 项目 | 借鉴点 |
|------|--------|
| [SD Maid 2/SE](https://github.com/d4rken-org/sdmaid-se) | 模块划分、安全分级、调度器 |
| [Canta](https://github.com/samolego/Canta) | Shizuku 集成、`pm suspend` 可逆卸载 |
| [UAD-NG](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) | 应用安全分级（Rust 全栈项目） |

运行时依赖均为 Apache-2.0 / MIT。

---

## 非官方声明

NovaCare **不是** Samsung Electronics 的官方产品，与 Samsung 无任何隶属或合作关系。
「One UI」为 Samsung Electronics 的商标，本项目仅将其作为**设计风格参考**进行描述，
不主张任何权利。

---

## 文档

- [INSTALL.md](./INSTALL.md) —— 各品牌 ROM 开启「未知来源」步骤
- [DISCLAIMER.md](./DISCLAIMER.md) —— 免责声明（**安装前请阅读**）
- [PRIVACY.md](./PRIVACY.md) —— 隐私政策