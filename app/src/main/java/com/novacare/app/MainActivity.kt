package com.novacare.app

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.novacare.app.navigation.NovaCareNavHost
import com.novacare.app.navigation.Routes
import com.novacare.core.data.SettingsRepository
import com.novacare.core.data.ThemeMode
import com.novacare.ui.designsystem.NovaCareTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 边到边：内容绘制到系统栏之下，系统栏保持透明。
        // 配合 values/themes.xml 里的 transparent statusBarColor，
        // 深色模式下不会再出现"启动闪白"。
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // 系统「移除动画」无障碍开关 —— Compose 侧 prefers-reduced-motion 的等价物。
            // 前庭功能障碍用户开启后，健康环的呼吸动画与入场序列都会停止。
            val reduceMotion = rememberReduceMotionSetting()

            // initialValue = null：等 DataStore 的第一帧真正到达再决定首屏。
            // 如果用默认构造值当首帧，已完成引导的老用户会先闪一帧引导页再跳走。
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = null,
            )
            val darkTheme = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            NovaCareTheme(darkTheme = darkTheme, reduceMotion = reduceMotion) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (settings == null) {
                        // 首帧未到：空背景等待，不做任何导航决策
                        Box(modifier = Modifier.fillMaxSize())
                    } else {
                        NovaCareNavHost(
                            startDestination = if (settings?.onboardingCompleted == true) {
                                Routes.HOME
                            } else {
                                Routes.ONBOARDING
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 读取系统「过渡动画比例」。
 * 值为 0 表示用户在开发者选项/无障碍里关闭了动画 —— 此时全应用动效降级。
 */
@Composable
private fun rememberReduceMotionSetting(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reduce by remember { mutableStateOf(context.readReduceMotion()) }

    // 用户可能在系统设置里改了开关，回到前台时重新读取
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            reduce = context.readReduceMotion()
            kotlinx.coroutines.delay(1_500)
        }
    }
    return reduce
}

private fun Context.readReduceMotion(): Boolean = runCatching {
    Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}.getOrDefault(false)
