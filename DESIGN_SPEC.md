# One UI 9 设计灵魂规范 — NovaCare 研发设计文档

> 本文档是整个 App 的设计宪法。不是"长得像 One UI"，而是"用 One UI 的方式思考"。

## 一、设计哲学（灵魂三问）

One UI 的灵魂不是圆角和蓝色，而是三条底层哲学：

### 1. 上下分区：上面看，下面摸
大屏手机时代，人手握住的是屏幕下半部。因此：
- **上半屏 = 观看区**：大标题、数据可视化、说明文字
- **下半屏 = 操作区**：按钮、开关、列表交互全部下沉到拇指可及范围
- 每个页面的大标题在滚动时收缩为顶部小标题，形成"呼吸感"

### 2. 一次只做一件事（Focus Blocks）
- 每屏聚焦一个核心任务，减少同屏信息量
- 用"重点区块"引导视线，而不是用分割线切割内容
- 打开二级功能时，界面主动隐藏无关元素

### 3. 舒适体验（Comfort）
- 视觉负担最低化：留白优先于信息密度
- 夜间模式全局一致，不是简单反色
- 动效服务于"理解"而非"炫技"：线性插值、无拖沓

## 二、One UI 9 的视觉语法

### 色彩系统（六区色调表面 Tonal Surfaces）
One UI 9 基于 Android 17 的六区独立色调表面：
| 区 | 用途 | NovaCare 实现 |
|---|------|--------------|
| Zone 1 | 系统背景 | `surface`（#F7F7FA 亮 / #0B0B0F 暗） |
| Zone 2 | 导航栏 | `surfaceContainerLow` |
| Zone 3 | 快捷面板/控制区 | `surfaceContainer` |
| Zone 4 | 通知卡片 | `surfaceContainerHigh` |
| Zone 5 | 组件容器 | `surfaceContainerHighest` |
| Zone 6 | 强调色 | `primary`（动态取色 + 靛蓝默认） |

- 支持 Material You 动态取色（`dynamicDarkColorScheme`/`dynamicLightColorScheme`）
- 强调色克制：饱和度中低，大面积用中性色，强调色只做点睛
- 语义色：优化得分用**蓝→绿渐变**，警告用橙，危险用红

### 形状
- 卡片圆角：**26dp**（大卡）/ 20dp（小卡）
- 按钮全部胶囊形（pill, 999dp radius）
- 图标背景：**squircle 超椭圆**，与系统图标同曲率
- 无锐利直角，无多层投影（One UI 9 明确去掉厚重毛玻璃与多层阴影）

### 字体
- One UI Sans（降级方案：Roboto/Samsung Sans fallback）
- 大标题 34sp，页内区块标题 22sp，正文 15sp，注释 13sp
- 标题字重 Bold，正文 Regular，层级靠字号+留白而非颜色

### 动效
- 线性插值（linear interpolation）为主，告别"先快后慢"的拖沓感
- 页面切换：水平位移 350ms + 内容淡入
- 进度类动效（清理、扫描）：环形进度 + 数字滚动
- 全程 60fps 预算：避免重模糊层，卡片不叠加投影

### 布局栅格
- 屏幕左右边距：**20dp**（One UI 标准边距）
- 卡片间距：12dp；区块间距：24dp
- 底部预留导航栏安全区 + 16dp

## 三、功能架构（源自开源项目深度调研）

调研对象与结论：
| 项目 | 借鉴点 |
|------|--------|
| **SD Maid 2/SE**（d4rken, GPL-3.0） | AppCleaner（应用缓存清理）、CorpseFinder（残留文件）、StorageAnalyzer（存储分析）、Deduplicator（重复文件）、Scheduler（定时任务） |
| **UAD-NG**（Universal Android Debloater） | 去臃肿分级：Safe / Caution / Risky 三级安全评级 |
| **AppManager**（MuntashirAkon） | 应用生命周期管理：冻结/卸载/组件控制 |
| **AAO**（Magisk 模块） | fstrim、数据库 VACUUM、ZRAM 重置等系统级维护思路 |
| **PowerGuard / coptimizer** | 电池监控、规则引擎、端侧 ML 预测（隐私优先，全本地） |

NovaCare 功能矩阵：
1. **设备体检**（首页）— 四维得分：存储 / 内存 / 电池 / 应用防护
2. **一键优化** — 清缓存 + fstrim（Shizuku/Root 时）+ 内存整理
3. **存储管家** — 目录树形可视化、大文件、重复文件（哈希去重）
4. **垃圾清理** — 缓存 / 残留 / 空目录 / 可配置过滤器
5. **应用管理** — 冻结（免 Root 优先 Shizuku）、卸载残留
6. **电池卫士** — 耗电排行、唤醒锁提示、全本地分析（无云端）
7. **定时维护** — WorkManager 夜间自动 fstrim + 缓存清理

## 四、工程实现要点
- Kotlin + Jetpack Compose + Material 3（深度定制为 One UI 9 语法）
- 单 Activity + Navigation Compose
- Hilt 依赖注入；WorkManager 定时任务；DataStore 偏好
- 权限策略：基础功能零权限；Shizuku 可选增强（不强制 Root）
- 隐私红线：**所有分析 100% 本地完成，零网络上报**
