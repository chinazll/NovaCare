# NovaCare v0.6.0-alpha

> 这个版本是一次**根因修复**，不是功能叠加。上一版"装上了但什么都不能用"，
> 原因是三个独立缺陷同时生效 —— 它们各自都足以让引擎彻底失效。

## 为什么上一版什么都用不了

### 缺陷 1：JNA 原生库从未被打进 APK（引擎必然加载失败）

UniFFI 生成的 Kotlin 绑定通过 **JNA** 调用 Rust 的 `.so`，而 JNA 自身依赖
`libjnidispatch.so` 做原生派发。上一版只声明了 JNA 的普通 jar：

```kotlin
implementation("net.java.dev.jna:jna:5.14.0")   // ❌ 不含 libjnidispatch.so
```

普通 jar **不包含**任何原生库。于是 APK 里 `libuniffi_novacare.so`（631KB）齐全，
但 `libjnidispatch.so` 在所有 ABI 中都不存在 —— `Native.load("uniffi_novacare")`
必然抛 `UnsatisfiedLinkError`。

**修复**：改用 `@aar` 变体，把 `libjnidispatch.so` 按 ABI 打进 APK。
已在构建产物中验证：`arm64-v8a / armeabi-v7a / x86 / x86_64` 四个 ABI 均存在。

### 缺陷 2：R8 裁掉 UniFFI/JNA 入口类（release 包专属）

`isMinifyEnabled = true` 生效，但 `app/proguard-rules.pro` 的 keep 规则不完整，
且 `uniffi.novacare.**` 的字段与构造器没有被保留。R8 把反射入口裁掉后，
同样表现为 `UnsatisfiedLinkError` / `ClassNotFoundException` —— release 包
比 debug 包更容易踩到，而构建过程**零报错**。

**修复**：重写 `proguard-rules.pro`，补全 UniFFI / JNA / Room / Hilt /
kotlinx.serialization 的 keep 规则，并保留行号便于线上定位。

### 缺陷 3：`null` 被静默翻译成"没有东西可清"

引擎返回 `null` 时，`BuildOptimizePlanUseCase` 里的：

```kotlin
snapshot.junk?.items?.filter { ... }   // ❌ null → 空列表
```

让"引擎彻底坏了"和"设备很干净"在 UI 上长得**一模一样** ——
都是那句 `没有发现可安全清理的项目`。用户看不到任何异常，只看到一个
有分数、有按钮、但永远说没事的界面。

**修复**：把「引擎降级」提升为一等状态，首屏显式告知并给出重试入口。

### 缺陷 4：全工程没有任何权限请求入口

`SystemPermissions` 只有 `hasXxx()` 查询，**没有任何一处**调用
`requestPermissions` / `ACTION_USAGE_ACCESS_SETTINGS`。
系统从未询问过任何权限，所有依赖权限的功能静默降级。

**修复**：新增完整的授权引导 —— `missingCapabilities()` 返回缺失清单，
首屏渲染为可点击的授权卡，一键直达系统设置页；回到前台自动重新检查。

### 缺陷 5：构建期无守卫，会静默产出残废包

`core/engine/jniLibs/` 被 `.gitignore` 屏蔽（CI 现场编译），
但本地 `assembleRelease` 不会编译 Rust —— 于是打出一个不含 `.so` 的
release APK，**构建零报错**。

**修复**：新增 `requireNativeLibs` Gradle 守卫任务，`.so` 缺失时直接
中止构建并打印编译指引。

---

## 内核修复

- **重复文件检测的删除语义**：原先把重复文件标为"可安全删除"且不给风险说明。
  重复文件不是垃圾，是用户数据的冗余副本 —— 现在标为 `risky`，并附上
  "与哪些文件完全相同"的完整说明，交由用户决定。
- **`safe_bytes` 虚报**：原先按 detector 的粗粒度 `is_safe` 累加，
  把 duplicate / residual 也算进"可安全释放"。现在按最终 `risk` 重新累计 ——
  首页那行数字不再高估。
- **风险分级统一**：FFI 层与 detector 层各自分级会漂移，现在统一由
  `risk_of()` 单点判定。
- **`risk_note` 补全**：每一项垃圾类型都给出"为什么是这一级、删除前该注意什么"。

Rust 单测 **18/18 通过**。

## 界面

`ui/designsystem` 从 224 行扩充为完整设计系统：

- **令牌层**：`NovaCareColors` 补充健康度三档、风险分级、环轨色、发丝描边、
  氛围渐变；Material3 角色全量映射；深浅双套色板。
- **字阶**：16 级完整字阶。健康分数字用 64sp / W200 / −0.04em 字距作为页面锚点；
  中文正文行高 ≥ 1.6，字距 0.01em。
- **组件**：`HealthRing`（带呼吸光晕与分数入场动画）、`MiniRing`、`NovaCard`、
  `StatCard`（含禁用态原因）、`PrimaryAction`（加载/禁用/副标题三态）、
  `SecondaryAction`、`RingSpinner`、`RiskChip`、`EmptyState`（四种语义色调 + 行动按钮）、
  `InlineNotice`、`SectionHeader`、`KeyValueRow`、`NovaProgressBar`、`AuroraBackground`。
- **首页**：健康环作为视觉主角 + 设备概览四宫格 + 授权引导卡 + 单一主 CTA。
- **动效**：入场序列错峰；页面转场 320ms 横向滑动 + 淡入；
  `LocalReduceMotion` 接系统"移除动画"开关，全套动效可降级。
- **无障碍**：所有交互 ≥ 48dp；语义化 `contentDescription`（评分环会朗读
  "设备健康评分 N 分，满分 100 分"）。
- **深色优先**：冷调近黑底（非纯黑，避免 OLED 上浮层边界消失），
  卡片靠亮度分层 + 发丝描边浮起，不依赖深色下几乎不可见的阴影。

## 安装

下载 `NovaCare-v0.6.0-alpha-signed.apk` 直接安装（需允许未知来源）。
最低支持 Android 8.0（API 26），目标 Android 16（API 36）。

首次启动请按首页提示授予：

| 权限 | 用途 | 不授予的后果 |
|---|---|---|
| 所有文件访问 | 内核完整扫描存储 | 扫描结果明显偏少 |
| 使用情况访问 | 识别长期未使用的应用 | 无法给出冻结建议 |
| 通知 | 长任务进度与结果 | 后台清理无反馈 |

## 校验

```
Rust 单测      18 passed / 0 failed
Kotlin 单测    44 passed / 0 failed
APK ABI        arm64-v8a · armeabi-v7a · x86_64
原生库         libuniffi_novacare.so ✓   libjnidispatch.so ✓
```

---

**这是 alpha 版本。** 清理功能会把文件移入应用内回收站（可撤销），
不会直接永久删除。
