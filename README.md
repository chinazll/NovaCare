# NovaCare 管家

> **One UI 9 设计灵魂 · 安卓系统优化 App**
> 调研了 SD Maid 2/SE、UAD-NG、AppManager、AAO、PowerGuard 等开源项目后，从零设计的现代化安卓优化工具。

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](./LICENSE)

## 核心特性

- **One UI 9 灵魂设计**：上观看/下操作、六区色调表面、无阴影哲学、克制动效
- **设备管家**：四维健康得分（存储/内存/电池/应用防护），一键优化
- **存储管家**：分类可视化、大文件分析
- **垃圾清理**：借鉴 SD Maid + UAD 安全分级，安全项默认勾选
- **应用管理**：冻结（pm suspend 可逆）、搜索、缓存排行
- **电池卫士**：电量/温度/耗速，全本地分析
- **定时维护**：每天凌晨 3 点自动维护

## 隐私承诺

**全部分析在本机完成，不上传任何数据。** AndroidManifest 中**没有** `android.permission.INTERNET`。

## 快速开始

### 下载 APK

请前往 [Releases](https://github.com/chinazll/NovaCare/releases) 下载最新 APK。

### 本地构建

要求 JDK 17 / Android Studio Hedgehog+ / Android SDK 35。

```bash
git clone https://github.com/chinazll/NovaCare.git
cd NovaCare
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## CI/CD

仓库自带 GitHub Actions 工作流：

- 推送至 `main` / 创建 `v*` tag → 自动构建 Debug + Release APK
- 每个 PR → 自动运行编译校验

## 设计灵魂（不是像，是是）

1. **上观看/下操作** — 大标题在观看区，按钮下沉拇指区
2. **六区色调表面** — 背景/导航/卡片/组件独立着色
3. **无阴影哲学** — 26dp 圆角 + 留白分层，elevation 均为 0
4. **胶囊按钮 + squircle 图标背景** — 与 One UI 系统图标曲率一致
5. **Material You 动态取色** — 默认靛蓝强调色克制使用
6. **350ms 线性插值动效** — 告别拖沓，全程 60fps 预算
7. **隐私红线** — 零网络权限上报

详见 [DESIGN_SPEC.md](./DESIGN_SPEC.md)。

## 功能矩阵（对标开源标杆）

| 模块 | 功能 | 借鉴来源 |
|------|------|----------|
| 设备管家 | 四维健康评分 + 一键优化 | One UI Device Care 语法 |
| 存储管家 | 分类色带可视化 + 大文件分析 | SD Maid StorageAnalyzer |
| 垃圾清理 | 缓存/日志/残留，安全项默认勾选 | SD Maid + CorpseFinder |
| 应用管理 | 冻结（pm suspend 可逆）、搜索 | AppManager + UAD |
| 电池卫士 | 电量/温度/耗速 + 全本地建议 | PowerGuard（去云端化） |
| 定时维护 | 每天 3 点自动 fstrim + 缓存清理 | SD Maid Scheduler + AAO |

## 调研开源项目

- [SD Maid 2/SE](https://github.com/d4rken-org/sdmaid-se)（GPL-3.0）—— 标杆级清理与存储分析
- [Universal Android Debloater](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)（MIT）—— 应用分级思想
- [AppManager](https://github.com/MuntashirAkon/AppManager)（GPL-3.0）—— 应用生命周期管理
- [AAO](https://github.com/mehedihjoy0/AAO)（MIT）—— 夜间维护调度
- [PowerGuard](https://github.com/hiteshchopra11/PowerGuard)（Apache 2.0）—— 端侧分析思路

## License

MIT — 欢迎 fork、贡献、打造全球最好的安卓系统优化 App。