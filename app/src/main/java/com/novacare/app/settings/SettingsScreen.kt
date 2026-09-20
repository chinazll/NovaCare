package com.novacare.app.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novacare.core.model.CloudModel
import com.novacare.ui.designsystem.AuroraBackground
import com.novacare.ui.designsystem.EmptyTone
import com.novacare.ui.designsystem.InlineNotice
import com.novacare.ui.designsystem.KeyValueRow
import com.novacare.ui.designsystem.NovaCard
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle
import com.novacare.ui.designsystem.NowBarChip
import com.novacare.ui.designsystem.SectionHeader
import com.novacare.ui.designsystem.SecondaryAction
import com.novacare.ui.designsystem.StaggerFlyIn

/**
 * 设置（L3）
 *
 * 【v0.7.2 视觉统一】与 HomeScreen 同一设计语言：
 *   - 顶部 NovaNowBar（替代老 BackAffordance + 大标题两行）
 *   - 返回按钮挪进 NowBar 的 leading 槽位（24dp 小圆形，与呼吸点共存）
 *   - 所有顶层 item 包 StaggerFlyIn
 *   - 圆角统一 22dp；LazyColumn padding：top 10dp / bottom 24dp
 *
 * 分组顺序刻意按「先风险、后信息」排列：
 *   1. 高级模式     —— 唯一会改变权限边界的开关，放在最上面
 *   2. 云端 AI      —— 唯一会离开本机数据的功能，紧随其后
 *   3. 权限状态     —— 当前每项能力的真实状态 + 直达系统设置
 *   4. 数据与隐私   —— 阅读入口，把 PRIVACY / DISCLAIMER 的承诺摊开给用户看
 *   5. 外观         —— 主题说明
 *   6. 关于         —— 版本号
 *
 * 关键：这一页不做「看起来很完整」的假象。每个开关都写明**开启后会发生什么**，
 * 每个权限都显示**此刻的真实状态**（不是"已优化"这种无信息量的字眼），
 * 而且每一条都能点进去处理。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val engineVersion by viewModel.engineVersion.collectAsStateWithLifecycle()
    val shizukuAvailable by viewModel.shizukuAvailable.collectAsStateWithLifecycle()

    var showPrivacy by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }

    // 从系统设置页返回时重查权限 —— 否则用户授了权回来发现状态没变
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AuroraBackground {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = 0.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- 顶栏 —— One UI 9/9.5 不规则圆角 TopAppBar ----------
            item(key = "topbar") {
                StaggerFlyIn(index = 0) {
                    SettingsTopBar(
                        onBack = {
                            NovaTap(view)
                            onBack()
                        },
                        subtitle = if (capabilities.none { !it.granted }) {
                            "所有必需权限均已授予"
                        } else {
                            "${capabilities.count { !it.granted }} 项权限尚未授予"
                        },
                        accent = if (capabilities.none { !it.granted }) {
                            NovaCareTheme.colors.healthGood
                        } else {
                            NovaCareTheme.colors.riskCaution
                        },
                    )
                }
            }

            // ---------- 1. 高级模式 ----------
            item(key = "advanced-header") {
                StaggerFlyIn(index = 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "高级模式")
                    }
                }
            }
            item(key = "advanced") {
                StaggerFlyIn(index = 2) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        AdvancedModeCard(
                            enabled = settings.advancedMode,
                            shizukuAvailable = shizukuAvailable,
                            onChange = viewModel::setAdvanced,
                            onRequestShizuku = viewModel::requestShizuku,
                        )
                    }
                }
            }

            // ---------- 2. 云端 AI ----------
            item(key = "cloud-header") {
                StaggerFlyIn(index = 3) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "云端 AI")
                    }
                }
            }
            item(key = "cloud") {
                StaggerFlyIn(index = 4) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        CloudAiCard(
                            enabled = settings.cloudAiEnabled,
                            model = settings.cloudModel,
                            apiKey = settings.cloudApiKey,
                            onToggle = viewModel::setCloudAi,
                            onModelChange = viewModel::setModel,
                            onApiKeyChange = viewModel::setApiKey,
                        )
                    }
                }
            }

            // ---------- 3. 权限状态 ----------
            item(key = "perms-header") {
                StaggerFlyIn(index = 5) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "权限状态")
                    }
                }
            }
            item(key = "perms") {
                StaggerFlyIn(index = 6) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        capabilities.forEach { cap ->
                            CapabilityCard(
                                capability = cap,
                                onGrant = {
                                    NovaTap(view)
                                    viewModel.grant(cap.capability)
                                },
                            )
                        }
                    }
                }
            }

            // ---------- 4. 数据与隐私 ----------
            item(key = "privacy-header") {
                StaggerFlyIn(index = 7) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "数据与隐私")
                    }
                }
            }
            item(key = "privacy") {
                StaggerFlyIn(index = 8) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        NovaCard {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = NovaCareTheme.colors.healthGood,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "零网络权限",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "应用的清单文件里没有声明 INTERNET 权限，" +
                                            "从代码层面保证不会发出任何网络请求。" +
                                            "所有扫描与分析都在本机完成。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            LinkRow(
                                title = "阅读完整隐私政策",
                                subtitle = "逐条列出本地处理的数据、权限原则与用户权利",
                                icon = Icons.Outlined.Lock,
                                onClick = {
                                    NovaTap(view)
                                    showPrivacy = true
                                },
                            )

                            Spacer(Modifier.height(8.dp))

                            LinkRow(
                                title = "阅读免责声明",
                                subtitle = "项目状态、数据责任边界、已知不做的事",
                                icon = Icons.Outlined.WarningAmber,
                                onClick = {
                                    NovaTap(view)
                                    showDisclaimer = true
                                },
                            )

                            Spacer(Modifier.height(8.dp))

                            LinkRow(
                                title = "反馈问题",
                                subtitle = "在 GitHub Issues 提交，附上机型和复现步骤",
                                icon = Icons.Outlined.BugReport,
                                onClick = {
                                    NovaTap(view)
                                    viewModel.openIssues()
                                },
                            )
                        }
                    }
                }
            }

            // ---------- 5. 外观 ----------
            item(key = "appearance-header") {
                StaggerFlyIn(index = 9) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "外观")
                    }
                }
            }
            item(key = "appearance") {
                StaggerFlyIn(index = 10) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        NovaCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.DarkMode,
                                    contentDescription = null,
                                    tint = NovaCareTheme.colors.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "跟随系统深浅色",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "深色与浅色两套配色均已适配。当前使用" +
                                            "系统设置，应用内不单独提供切换开关，避免与系统行为冲突。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "标准字号与动效",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "界面不锁定字号，跟随系统「显示大小与文字」设置缩放。" +
                                            "若你在系统里关闭了过渡动画，应用内的动画也会一并关闭。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------- 6. 关于 ----------
            item(key = "about-header") {
                StaggerFlyIn(index = 11) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "关于")
                    }
                }
            }
            item(key = "about") {
                StaggerFlyIn(index = 12) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        NovaCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(NovaCareTheme.colors.accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = null,
                                        tint = NovaCareTheme.colors.accent,
                                        modifier = Modifier.size(21.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "NovaCare 管家",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "开源 · 无广告 · 不联网",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            KeyValueRow(key = "应用版本", value = APP_VERSION)
                            KeyValueRow(
                                key = "分析内核",
                                value = if (engineAvailable) engineVersion.ifBlank { "已加载" } else "不可用",
                                valueColor = if (engineAvailable) {
                                    NovaCareTheme.colors.healthGood
                                } else {
                                    NovaCareTheme.colors.riskCaution
                                },
                            )
                            KeyValueRow(
                                key = "冻结通道",
                                value = if (shizukuAvailable) "Shizuku 已就绪" else "未检测到 Shizuku",
                                valueColor = if (shizukuAvailable) {
                                    NovaCareTheme.colors.healthGood
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )

                            if (!engineAvailable) {
                                Spacer(Modifier.height(12.dp))
                                InlineNotice(
                                    text = "分析内核未能加载（通常是该设备 ABI 缺少对应的原生库）。" +
                                        "此时所有数值仍为系统真实读数，但垃圾扫描与存储分析不可用，" +
                                        "不会用估算值替代。",
                                    tone = EmptyTone.Warning,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "footer") {
                StaggerFlyIn(index = 99) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = "本应用为实验性开源项目，请在使用前阅读免责声明。" +
                                "建议首次使用前备份重要数据。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // 长文阅读浮层 —— 用可读的排版呈现完整条文，而不是弹个对话框显示半句话
    if (showPrivacy) {
        TextDocumentSheet(
            title = "隐私政策",
            body = PRIVACY_TEXT,
            onDismiss = { showPrivacy = false },
        )
    }
    if (showDisclaimer) {
        TextDocumentSheet(
            title = "免责声明",
            body = DISCLAIMER_TEXT,
            onDismiss = { showDisclaimer = false },
        )
    }
}

// ------------------------------------------------------------
// 返回入口
// ------------------------------------------------------------

/**
 * One UI 9/9.5 风格的不规则圆角 TopAppBar。
 *
 * 上一版用的是 NovaNowBar —— 那是一个 44dp 高的体征浮条（呼吸点 + 状态色），
 * 适合放在屏幕中段表达"系统在做什么"，但放在页面顶部会显得太低调，
 * 用户看不出"我已经进入了设置页"。
 *
 * 这一版的关键差异：
 *   1. **真正的 AppBar 高度**（72dp + 状态栏） —— 跟内容建立明显的层级关系
 *   2. **不规则圆角的下边缘** —— 两侧 28dp 大圆角，中央无圆角
 *      （而不是四周同半径），模拟 One UI 的「软着陆」视觉语言
 *   3. **底部一条 1dp 强调色描边** —— 替代 NowBar 的彩色边缘高光
 *      仍然是状态色，但用横线而非发光
 */
@Composable
private fun SettingsTopBar(
    onBack: () -> Unit,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    val colors = NovaCareTheme.colors
    val view = androidx.compose.ui.platform.LocalView.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        // 不规则圆角：上边直角，下边两侧大圆角
        shape = RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = 28.dp,
            bottomEnd = 28.dp,
        ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.hairline),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            // 主行：返回按钮 + 大标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackAffordance(onBack = {
                    NovaTap(view)
                    onBack()
                })
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 副标题行：状态色短点 + 当前权限状态描述
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 全屏文档浮层用的返回按钮（44dp 大圆，仍由 TextDocumentSheet 调用） */
@Composable
private fun BackAffordance(onBack: () -> Unit) {
    val colors = NovaCareTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, colors.hairline, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onBack,
            )
            .semantics { contentDescription = "返回" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ------------------------------------------------------------
// 圆形滑块开关（One UI 9/9.5 风格）
// ------------------------------------------------------------

/**
 * One UI 9/9.5 风格的圆形滑块开关。
 *
 * 与 Material3 Switch 的关键差异：
 *   1. **圆形 thumb**：thumb 是正圆（26dp），而不是 M3 的胶囊
 *      —— 跟整套设计系统的圆形语言统一
 *   2. **颜色滑过阈值才渐变**：thumb 颜色从灰到 accent 的过渡不是瞬时，
 *      而是跟 thumb 的位移同步，让"我推过中点了"有视觉反馈
 *   3. **spring 物理**：toggle 后 thumb 用 spring 回到对应端点
 *
 * 这是一个全屏组件，影响整个设置页风险最高的两个开关（高级模式 / 云端 AI），
 * 因此需要确认感的「动作 + 状态变化」必须明显。
 */
@Composable
private fun RoundPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: androidx.compose.ui.graphics.Color = NovaCareTheme.colors.accent,
) {
    val colors = NovaCareTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val trackWidth = 56.dp
    val trackHeight = 32.dp
    val thumbSize = 26.dp

    // 用 0f..1f 表达「thumb 的归一化位置」—— 0 = 关，1 = 开
    // 滑动时跟着手指走，松手时按目标状态 spring 回位
    val target by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pillSwitch",
    )
    // 0..1 映射成 thumb 在轨道内的水平偏移（dp）
    val paddingInside = (trackHeight - thumbSize) / 2f
    val maxOffset = trackWidth - thumbSize - paddingInside * 2f

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.lerp(
                    colors.ringTrack,
                    activeColor.copy(alpha = 0.85f),
                    target,
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = {
                    val newVal = !checked
                    NovaToggle(context, newVal)
                    onCheckedChange(newVal)
                },
            )
            .semantics {
                contentDescription = if (checked) "已开启" else "已关闭"
                role = Role.Switch
            },
    ) {
        Box(
            modifier = Modifier
                .padding(start = paddingInside + maxOffset * target)
                .padding(vertical = paddingInside)
                .size(thumbSize)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onPrimary,
                        target,
                    )
                ),
        )
    }
}

// ------------------------------------------------------------
// 高级模式
// ------------------------------------------------------------

/**
 * 高级模式卡。
 *
 * 这是全应用风险最高的开关，因此：
 *   - 开启前必须让用户读到「会解锁什么」和「代价是什么」
 *   - Shizuku 未就绪时如实说明"开了也有一部分用不了"，而不是让开关变成摆设
 */
@Composable
private fun AdvancedModeCard(
    enabled: Boolean,
    shizukuAvailable: Boolean,
    onChange: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmPending by remember { mutableStateOf(false) }

    NovaCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            colors.riskCaution.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = if (enabled) colors.riskCaution else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "高级模式",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (enabled) "已开启" else "已关闭",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) colors.riskCaution else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RoundPillSwitch(
                checked = enabled,
                onCheckedChange = { next ->
                    // 开启方向需要一次确认：这是权限边界的变化，不该一点就生效
                    if (next) confirmPending = true else onChange(false)
                },
                modifier = Modifier.semantics {
                    contentDescription = if (enabled) "关闭高级模式" else "开启高级模式"
                },
                activeColor = colors.riskCaution,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "开启后解锁",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        BulletLine("通过 Shizuku 冻结系统应用（可逆，随时可解冻）")
        BulletLine("一键清除所有第三方应用的缓存（pm trim-caches）")
        BulletLine("执行闪存整理 fstrim")

        Spacer(Modifier.height(14.dp))

        Text(
            text = "代价",
            style = MaterialTheme.typography.labelSmall,
            color = NovaCareTheme.colors.riskCaution,
        )
        Spacer(Modifier.height(6.dp))
        BulletLine(
            "这些操作需要 ADB 级权限，可能影响系统稳定性，误操作后果由用户自行承担",
            tone = colors.riskCaution,
        )
        BulletLine(
            "部分厂商 ROM 的实现差异可能导致冻结后无法恢复（概率极低但存在）",
            tone = colors.riskCaution,
        )

        if (!shizukuAvailable) {
            Spacer(Modifier.height(12.dp))
            InlineNotice(
                text = "未检测到 Shizuku 授权。缓存自动化、冻结、闪存整理需要它。" +
                    "点击下方按钮发起授权（首次需在 Shizuku App 里先完成 ADB 激活）。",
                tone = EmptyTone.Warning,
            )
            Spacer(Modifier.height(10.dp))
            // 真正的授权入口：上一版只有一句"不可用"，用户无从操作
            SecondaryAction(
                text = "授权 Shizuku",
                onClick = {
                    NovaTap(view)
                    onRequestShizuku()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (confirmPending) {
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colors.riskCaution.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, colors.riskCaution.copy(alpha = 0.3f)),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(
                        text = "确认开启高级模式？",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "开启后应用将获得改动系统状态的能力。请确认你理解上述代价，" +
                            "并对重要数据做过备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfirmButton(
                            text = "确认开启",
                            tone = colors.riskCaution,
                            onClick = {
                                NovaSuccess(context)
                                onChange(true)
                                confirmPending = false
                            },
                        )
                        ConfirmButton(
                            text = "取消",
                            tone = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                NovaTap(view)
                                confirmPending = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletLine(
    text: String,
    tone: androidx.compose.ui.graphics.Color? = null,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = tone ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tone ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfirmButton(
    text: String,
    tone: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(tone.copy(alpha = 0.16f))
            .border(1.dp, tone.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tone,
        )
    }
}

// ------------------------------------------------------------
// 云端 AI
// ------------------------------------------------------------

@Composable
private fun CloudAiCard(
    enabled: Boolean,
    model: CloudModel,
    apiKey: String,
    onToggle: (Boolean) -> Unit,
    onModelChange: (CloudModel) -> Unit,
    onApiKeyChange: (String) -> Unit,
) {
    val colors = NovaCareTheme.colors
    var keyDraft by remember(apiKey) { mutableStateOf(apiKey) }

    NovaCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            colors.accent.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cloud,
                    contentDescription = null,
                    tint = if (enabled) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "使用云端模型解析指令",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (enabled) "已开启 · 指令文本会发送到所选服务商" else "默认关闭 · 不发任何网络请求",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) colors.riskCaution else colors.healthGood,
                )
            }
            RoundPillSwitch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics {
                    contentDescription = if (enabled) "关闭云端 AI" else "开启云端 AI"
                },
                activeColor = colors.riskCaution,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "关闭时助手使用本机规则解析，功能完整可用。开启后可以把更复杂的说法" +
                "（例如带条件的组合句）交给云端模型理解 —— 代价是你输入的指令文本会离开本机。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 模型与密钥只在开启后可编辑，避免用户对着必填项发呆
        if (enabled) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "模型服务商",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudModel.entries.forEach { m ->
                    ModelRow(
                        model = m,
                        selected = m == model,
                        onClick = { onModelChange(m) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "API KEY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedInput(
                value = keyDraft,
                onValueChange = {
                    keyDraft = it
                    onApiKeyChange(it)
                },
                placeholder = "粘贴服务商控制台生成的 Key",
                leadingIcon = Icons.Outlined.Key,
                masked = true,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Key 只保存在本机应用私有目录，不会上传到除所选服务商之外的任何位置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (apiKey.isBlank()) {
                Spacer(Modifier.height(10.dp))
                InlineNotice(
                    text = "尚未填写 Key，云端解析不会生效。此期间助手会自动回退到本地规则，" +
                        "功能不受影响。",
                    tone = EmptyTone.Neutral,
                )
            } else if (!model.isChinaCompliant) {
                Spacer(Modifier.height(10.dp))
                InlineNotice(
                    text = "你选择了境外服务商（${model.displayName}）。" +
                        "指令文本将发送到境外服务器，请自行评估合规与隐私风险。",
                    tone = EmptyTone.Warning,
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: CloudModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    colors.accent.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.5f) else colors.hairline,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (model.isChinaCompliant) {
                    "已完成国内备案 · 数据不出境"
                } else {
                    "境外服务 · 数据出境"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (model.isChinaCompliant) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    colors.riskCaution
                },
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ------------------------------------------------------------
// 权限状态
// ------------------------------------------------------------

@Composable
private fun CapabilityCard(
    capability: SettingsViewModel.CapabilityState,
    onGrant: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val icon = when (capability.icon) {
        SettingsViewModel.CapabilityIcon.USAGE -> Icons.Outlined.QueryStats
        SettingsViewModel.CapabilityIcon.FILES -> Icons.Outlined.Folder
        SettingsViewModel.CapabilityIcon.NOTIFICATIONS -> Icons.Outlined.Notifications
    }

    NovaCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (capability.granted) {
                            colors.healthGood.copy(alpha = 0.12f)
                        } else {
                            colors.riskCaution.copy(alpha = 0.14f)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (capability.granted) Icons.Outlined.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (capability.granted) colors.healthGood else colors.riskCaution,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = capability.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (capability.granted) {
                            colors.healthGood.copy(alpha = 0.14f)
                        } else {
                            colors.riskCaution.copy(alpha = 0.14f)
                        },
                    ) {
                        Text(
                            text = if (capability.granted) "已授予" else "未授予",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (capability.granted) colors.healthGood else colors.riskCaution,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = capability.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!capability.granted) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = capability.consequence,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.riskCaution,
                    )
                }

                if (!capability.granted) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(colors.accent.copy(alpha = 0.14f))
                            .border(1.dp, colors.accent.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = onGrant,
                            )
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = capability.actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accent,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------
// 通用小件
// ------------------------------------------------------------

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, colors.hairline, MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun OutlinedInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector? = null,
    masked: Boolean = false,
) {
    val colors = NovaCareTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, colors.hairline, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = androidx.compose.material3.LocalTextStyle.current
                    .merge(MaterialTheme.typography.bodyMedium)
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                singleLine = true,
                visualTransformation = if (masked) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 长文阅读浮层。
 *
 * 选择全屏盖层而不是对话框：隐私政策与免责声明是需要**真的读**的长文，
 * 塞进一个带滚动条的对话框会逼用户用 3 行可视区读 2000 字。
 * 这里的正文用 bodyMedium（行高 23sp）排版，保证中文可读性。
 */
@Composable
private fun TextDocumentSheet(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackAffordance(onBack = onDismiss)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(body.lines().size, key = { it }) { index ->
                    val line = body.lines()[index]
                    when {
                        line.isBlank() -> Spacer(Modifier.height(2.dp))

                        line.startsWith("## ") -> Text(
                            text = line.removePrefix("## "),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.accent,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        line.startsWith("# ") -> Text(
                            text = line.removePrefix("# "),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        line.startsWith("- ") || line.startsWith("· ") -> Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = line.removePrefix("- ").removePrefix("· "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        line.startsWith("> ") -> Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colors.accent.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = line.removePrefix("> "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp),
                            )
                        }

                        else -> Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------
// 正文内容
// ------------------------------------------------------------

/** 应用版本 —— 与 app/build.gradle.kts 的 versionName 保持一致 */
private const val APP_VERSION = "0.5.0-alpha (5)"

/**
 * 隐私政策摘要。
 *
 * 这里不是把 PRIVACY.md 整篇搬进来 —— 那是仓库文档，长度和语境都不适合在手机上读。
 * 屏幕里呈现的是**与用户当下行为直接相关**的核心承诺，并注明完整版在仓库里可查。
 */
private val PRIVACY_TEXT = """
    # 隐私政策（核心条款）

    > NovaCare 不在清单文件中声明网络权限（android.permission.INTERNET），从代码层面保证零网络通信。

    ## 收集的数据

    应用不收集、不存储、不传输以下任何数据：
    - 设备标识符（IMEI、Android ID、MAC 地址等）
    - 位置信息
    - 任何形式的分析或遥测数据
    - 崩溃日志或诊断信息

    ## 本地处理的数据

    NovaCare 只在你的设备上临时处理以下数据，且仅存在于内存中，不写入任何可被外部读取的位置：
    - 存储使用量（StatFs）—— 用于存储管家页面
    - 内存使用量（ActivityManager）—— 用于内存模块
    - 电量与温度（系统广播）—— 用于电池显示
    - 已安装应用列表（PackageManager）—— 用于应用管理
    - 缓存目录大小 —— 用于垃圾清理

    ## 网络通信

    应用在代码层面不进行任何网络通信：
    - 不声明 INTERNET 权限
    - 不集成 HttpURLConnection、OkHttp、Retrofit 等网络库
    - 不包含 WebView
    - 不建立 WebSocket 或长连接

    对安装包做网络抓包分析可验证零网络通信。

    ## 第三方数据共享

    不与任何第三方共享数据，因为没有集成任何第三方 SDK：
    - 无广告 SDK
    - 无分析 SDK
    - 无崩溃上报 SDK

    ## 权限原则

    - 最小权限：仅申请功能必需的权限
    - 动态申请：敏感权限只在你主动触发相应功能时引导授权
    - 明确披露：每项权限申请都附有功能目的说明
    - 可撤销：随时可在系统设置中撤销全部授权

    ## 关于 Shizuku

    高级模式下可选用 Shizuku 冻结系统应用。Shizuku 由第三方提供，
    其自身的数据处理不在本政策范围内。未安装 Shizuku 时，所有基础功能仍然可用。

    ## 你的权利

    - 知情权：本政策完整披露所有数据处理活动
    - 拒绝权：可以拒绝授予任何可选权限
    - 卸载权：可以随时卸载
    - 审计权：源码完全公开，可自行审计

    完整版本以仓库中的 PRIVACY.md 为准。
""".trimIndent()

/** 免责声明摘要 —— 重点保留"可能出什么事"与"我们明确不做的事" */
private val DISCLAIMER_TEXT = """
    # 免责声明（核心条款）

    > 请在仔细阅读后再使用本应用。

    ## 项目状态

    NovaCare 是一个实验性开源项目，由个人开发者利用业余时间开发，尚未经过充分的生产环境测试。

    - 当前状态：alpha / 预发布
    - 可能存在的风险：功能不稳定、数据丢失、与部分定制系统不兼容
    - 不建议用于生产设备或关键任务场景

    ## 数据责任

    清理与冻结类操作会影响设备状态，请了解各自的边界：

    - 垃圾清理：清理缓存、临时文件、缩略图、日志。清理范围限制在路径白名单内，
      但仍可能因系统差异导致误删
    - 应用冻结：通过 Shizuku 执行的可逆操作。未安装 Shizuku 时该功能不可用，
      应用会明确提示，不会假装成功
    - 闪存整理：需要 ADB 级权限。不具备权限时会跳过执行，不会谎报成功
    - 一键优化：自动清理安全垃圾。不会删除照片、文档、聊天记录
    - 存储分析：在本机扫描，不上传任何数据

    ## 明确不做的事

    本项目不做"已卸载应用的残留目录"检测。原因是 Android 11 以上的分区存储
    限制下无法可靠枚举已安装应用，强行比对会把仍在使用中的应用的数据目录
    误判为残留并删除。宁可缺失该能力，也不承担误删风险。

    ## 权限使用

    所有敏感权限都需要你在系统运行时授权，应用不会绕过系统的安全机制。
    不会申请 QUERY_ALL_PACKAGES，改用更小范围的 queries 声明。

    ## 隐私承诺

    应用不申请网络权限，因此不会上传任何设备数据、应用数据或使用行为。
    该承诺可在源码层面验证。

    ## 与 Samsung 的关系

    NovaCare 不是 Samsung Electronics 的官方产品。"One UI 风格"是设计风格的
    描述性用语，不构成商标侵权或官方合作暗示。应用未内置任何 Samsung 专有代码、
    图标、字体或 SDK，使用的是开源的 Material 3 + Jetpack Compose 实现。

    ## 无担保声明

    本软件按"现状"提供，不附带任何形式的明示或默示担保。作者不对任何索赔、
    损害或其他责任负责。

    ## 反馈

    发现问题请提交 GitHub Issue。不接受关于数据丢失的责任索赔，
    但开发者会尽力协助恢复。

    完整版本以仓库中的 DISCLAIMER.md 为准。
""".trimIndent()
