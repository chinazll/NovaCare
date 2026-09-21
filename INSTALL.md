# INSTALL · 安装指南

> NovaCare 管家 v0.1.0-alpha 安卓安装分步指南

## ⚠️ 重要前提

**NovaCare v0.1.0-alpha 是实验性版本，未经充分实机测试，可能存在以下问题：**
- 安装失败、启动崩溃
- 部分功能不可用
- 与某些定制 ROM 不兼容

**请仔细阅读 [DISCLAIMER.md](./DISCLAIMER.md) 后再继续。**

---

## 一、下载 APK

### 方式一：从 CI 构建产物下载（当前推荐）

1. 打开 [Actions 页面](https://github.com/chinazll/NovaCare/actions)
2. 点击最新一次成功的构建（标题含 `Build & Sign APK`）
3. 在页面底部 **Artifacts** 区域下载：
   - `NovaCare-debug` —— 解压后内部文件名为 **`app-debug.apk`**
   - `NovaCare-release-signed` —— 解压后内部文件名为 **`app-release.apk`**

> 注意：此前本文件写成 `NovaCare-debug.apk` / `NovaCare-release-signed.apk`，
> 那是 **Artifact 压缩包的名字**，不是里面 APK 的真实文件名，现予以更正。

### 方式二：从 Releases 下载

> https://github.com/chinazll/NovaCare/releases

（仅在打 tag 时才会生成 Release；日常构建请从 Actions 下载。）

**两个 APK 均已签名，可直接安装。**
签名密钥由 CI 生成并**跨构建缓存复用**，因此后续版本可以直接覆盖安装，不会因签名不一致而失败。

---

## 二、开启"安装未知应用"权限

> ⚠️ 这是 Android 安全机制。不开启将无法安装非 Play 商店来源的 APK。

### 2.1 通用步骤

1. 下载 APK 后，**不要直接点击打开**
2. 进入手机 **设置**（齿轮图标）
3. 搜索"**安装未知应用**"或"**特殊应用权限**"
4. 找到**你用来打开 APK 的应用**（通常是"文件管理"或"浏览器"）
5. 允许"**安装未知应用**"

### 2.2 各品牌定制 ROM 路径

| 品牌 | 路径 |
|------|------|
| **华为/荣耀 (HarmonyOS)** | 设置 → 安全 → 更多安全设置 → 安装未知应用 → 选择来源应用 |
| **小米 (HyperOS/MIUI)** | 设置 → 隐私保护 → 特殊权限 → 安装未知应用 → 选择来源应用 |
| **三星 (One UI)** | 设置 → 应用 → 选择"文件管理/浏览器" → 安装未知应用 |
| **OPPO (ColorOS)** | 设置 → 安全 → 安装未知应用 → 选择来源应用 |
| **vivo (OriginOS)** | 设置 → 安全与隐私 → 更多设置 → 安装未知应用 |
| **一加 (OxygenOS)** | 设置 → 应用 → 特殊应用权限 → 安装未知应用 |
| **Pixel (原生)** | 设置 → 应用 → 所有应用 → 找到"文件"→ 安装未知应用 |
| **魅族 (Flyme)** | 设置 → 隐私管理 → 权限管理 → 安装未知应用 |

### 2.3 通过 ADB 安装（最稳定，推荐开发者）

```bash
# 用 USB 数据线连接手机，开启 USB 调试
adb devices                                    # 确认设备已连接
adb install NovaCare-debug.apk                  # 安装
adb shell am start -n com.novacare.optimizer/.MainActivity  # 启动
```

---

## 三、首次启动配置

### 3.1 授予"使用情况访问权限"（可选）

NovaCare 部分功能（如识别"不常用应用"）需要此权限。

1. 进入 NovaCare 首页
2. 在"健康状况"卡片下会看到 **"需要使用情况权限"** 提示
3. 点击按钮 → 跳转到系统设置
4. 找到 **NovaCare** → 开启"允许访问使用情况"
5. 返回 NovaCare，自动刷新

> **不授权也能用**：仅"识别不常用应用"功能不可用，其余全部功能正常。

### 3.2 开启 Shizuku 增强（高级用户，可选）

如需使用"系统应用冻结"等高级功能：

1. 安装 [Shizuku](https://shizuku.rikka.app/)（从其官网下载）
2. 通过 ADB 或 root 启动 Shizuku 服务
3. NovaCare 自动检测 Shizuku 可用性
4. 开启后即可冻结系统应用

> **不开启也能用**：所有基础功能（清理/存储分析/电池）完全可用。

---

## 四、验证 APK 完整性（可选）

### 4.1 校验 SHA-256

```bash
# 在下载目录运行
sha256sum NovaCare-debug.apk
# 比对 GitHub Release 页面的 SHA-256
```

### 4.2 验证 APK 签名

```bash
# 需要 Android SDK build-tools
apksigner verify --verbose NovaCare-debug.apk
# 期望输出："Verifies" + "Verified using v1 scheme"
```

---

## 五、常见问题 FAQ

### Q1: 安装时提示"应用未安装"

**原因**：
- 之前安装过旧版本但签名不同 → 卸载后重装
- 系统版本低于 Android 8.0 → 本 App 要求 minSdk 26
- 设备存储空间不足 → 清理后重试

### Q2: 启动后立即闪退

**排查**：
```bash
# 抓取崩溃日志
adb logcat -d -b crash | grep -i "novacare"
```
将日志提交到 [Issue](https://github.com/chinazll/NovaCare/issues)，附上：
- 设备型号
- Android 版本
- ROM 类型（MIUI/HyperOS/One UI/...）
- 完整崩溃日志

### Q3: 看不到应用扫描结果

**原因**：Android 11+ 出于隐私保护，应用默认只能看到 `<queries>` 中声明的应用。
- NovaCare 已声明可见"启动器中的应用"
- 若仍然看不到，请检查 NovaCare 是否被电池优化策略限制后台运行

### Q4: 清理按钮没反应

**原因**：NovaCare 仅清理**安全项**（缓存、临时文件）。风险项（如系统日志）需用户手动确认勾选。
- 在"垃圾清理"页面，每个项目左侧有开关
- 红色标识的是"建议检查后清理"项
- 仅勾选"安全"项才能点击清理按钮

### Q5: 卸载后数据还在吗？

**卸载 NovaCare 不会影响任何已存在的应用或文件**。NovaCare 仅删除**自己创建的缓存**（在 App 沙箱目录内）和**用户主动授权删除的垃圾项**。

---

## 六、获取帮助

- 📖 阅读源码：[github.com/chinazll/NovaCare](https://github.com/chinazll/NovaCare)
- 🐛 报告问题：[GitHub Issues](https://github.com/chinazll/NovaCare/issues)
- 💬 设计与架构讨论：[GitHub Discussions](https://github.com/chinazll/NovaCare/discussions)

---

_本指南最后更新于 2026-09-19_