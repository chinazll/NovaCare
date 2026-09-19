# v0.5.0-alpha — Module split + Rust engine binding

> ⚠️ **alpha · 实验性 · 未经充分实机测试**
> 下载前请阅读 [DISCLAIMER.md](https://github.com/chinazll/NovaCare/blob/main/DISCLAIMER.md) · [INSTALL.md](https://github.com/chinazll/NovaCare/blob/main/INSTALL.md) · [PRIVACY.md](https://github.com/chinazll/NovaCare/blob/main/PRIVACY.md)

## 概要

NovaCare 管家 v0.5.0-alpha 把工程从单 `app/` 平铺重构为多模块架构，并把上一版是死代码的 Rust 内核真正接进 APK。

| 维度 | 上版 (v0.1.0-alpha) | 本版 (v0.5.0-alpha) |
|---|---|---|
| 模块结构 | 单 `app/` 包 | **core/ 8 模块 + feature/ 5 模块 + ui/designsystem + app/** |
| Rust 内核 | `.so` 编了但加载不到 | **UniFFI 绑定 + 3 ABI 全打包** |
| APK 包大小（debug） | 17.4 MB | 20.6 MB（含 3 ABI 的 Rust 引擎） |
| Release APK | unsigned → 装不上 | 3.2 MB（R8 压缩 + CI 签名） |
| 单测 | Kotlin N 个 | Kotlin **44/44** + Rust **18/18** |

## 模块拆分

```
core/        8 模块
  ├─ model/        数据模型（App / Junk / Storage / Health / Ai / Automation / Clean / Freeze）
  ├─ common/       工具（Format / Outcome / DispatcherProvider）
  ├─ engine/       Rust 确定性内核（L1）的 Kotlin 门面，绑定 libuniffi_novacare.so
  ├─ ai/           端侧 AI（IntentParser / CleanAdvisor / FreezeAdvisor / CloudLlmProvider）
  ├─ system/       Android 系统调用（AppRepository / FreezeController / UsageStatsSource / StorageStatsSource）
  ├─ data/         Room + DataStore（NovaDatabase / RuleEntity / HistoryDao）
  ├─ domain/       业务用例（ScanDevice / HealthScore / BuildOptimizePlan / FreezeUseCase / ExecutePlan）
  └─ automation/   自动化引擎（RuleEngine / AutomationRunner / AutomationScheduler / AutomationWorker）

feature/     5 模块
  ├─ home/         首页（健康评分 + 一键优化）
  ├─ clean/        垃圾清理
  ├─ freeze/       应用冻结
  ├─ automation/   自动化规则
  └─ assistant/    AI 助手（自然语言 → 解析 → 待确认的计划）

ui/          1 模块
  └─ designsystem/  One UI 9 设计系统的 Compose 组件 + Theme

app/         薄壳
  └─ MainActivity + NovaCareApp + NavGraph + AppModule + SettingsScreen
```

## Rust 引擎（真）

`core/engine/rust/` 11 个 `.rs` 文件，UniFFI proc-macro 模式（`#[uniffi::Record]` / `#[uniffi::export]`），
绑定由 `uniffi 0.28.3` 自动生成到 `core/engine/src/main/java/uniffi/novacare/novacare.kt`。
Kotlin 侧只有一个入口：`core/engine/NovaEngine.kt`，所有方法在 `.so` 缺失或 JNI 失败时返回 `null`，
由上层显示「暂不可用」——**绝不返回伪造数据**。

- 18 个 Rust 单测全过（`cargo test --lib`）
- 3 ABI 编译产物：`lib/{arm64-v8a,armeabi-v7a,x86_64}/libuniffi_novacare.so`
- APK 里 3 ABI 的 `.so` 全部就位（实测 `unzip -l app-debug.apk | grep libuniffi_novacare.so` = 3）

## CI/CD

`build · test · sign · verify · auto-release` 全流程跑在 GitHub Actions（ubuntu-latest, JDK 17, NDK 26.1, Rust stable）：

1. 拉 Rust 工具链 + cargo-ndk
2. `cargo test --lib` + clippy（warning-only）
3. `cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../jniLibs build --release`
4. 校验 3 ABI 的 .so 都生成
5. Gradle testDebugUnitTest（44 个用例）
6. assembleDebug + assembleRelease（用 CI 生成的 keystore 签名）
7. apksigner verify 两个 APK + unzip 检查 `.so` 数量 ≥ 3
8. **tag 触发时自动创建 draft GitHub Release 并附两个 APK**（softprops/action-gh-release@v2）

## 下载

- `NovaCare-v0.5.0-alpha-debug.apk` — 20.6 MB，含 3 ABI 的 Rust 引擎，v2 签名
- `NovaCare-v0.5.0-alpha-release.apk` — 3.2 MB，R8 压缩，v2 签名

## 安装

参考 [INSTALL.md](https://github.com/chinazll/NovaCare/blob/main/INSTALL.md)（含各品牌 ROM 路径）。

## 隐私

零网络权限（Manifest 不含 `INTERNET`）。Rust 内核纯本地计算，AI 助手需要联网时**必须**用户在设置里主动开启并填入自己的 API Key —— 默认完全离线。

详见 [PRIVACY.md](https://github.com/chinazll/NovaCare/blob/main/PRIVACY.md)。

## 已知问题 / 风险

- **未经实机测试** — 当前 APK 未经真机/模拟器验证
- **Rust warnings** — `cargo build` 有 6 个未使用 import / mut 警告，不影响运行但下一版会清掉
- **CI strip warning** — Gradle NDK stripper 不能 strip `libuniffi_novacare.so`（符号结构复杂），`.so` 不被 strip 直接打包，体积略大

## 致谢

参考但未复制任何代码：SD Maid 2/SE、UAD-NG、AppManager、AAO (Magisk)、PowerGuard、coptimizer。
