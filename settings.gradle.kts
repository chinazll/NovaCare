pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NovaCare"

// ============================================================
// 多模块结构：模块边界 = 业务边界
// ============================================================

// ---- core：与 UI 无关的能力层 ----
include(":core:model")        // 纯领域模型（零 Android 依赖，可单测）
include(":core:common")       // Result / 调度器 / 格式化工具
include(":core:engine")       // Rust 内核 + UniFFI 生成绑定
include(":core:ai")           // 三层 AI 能力栈（L1/L2/L3）
include(":core:system")       // 系统控制（冻结 / 包管理 / 权限）
include(":core:data")         // Room + DataStore 持久化
include(":core:domain")       // 用例层（ViewModel 与 Repository 之间）
include(":core:automation")   // 可编程自动化引擎

// ---- ui：设计系统 ----
include(":ui:designsystem")   // 主题 / 组件 / 图标

// ---- feature：按业务能力拆分 ----
include(":feature:home")      // 一键优化首页
include(":feature:clean")     // 清理（含 AI 语义）
include(":feature:freeze")    // 冻结
include(":feature:automation")// 自动化规则
include(":feature:assistant") // AI 助手（对话式）

// ---- app：应用壳 ----
include(":app")
