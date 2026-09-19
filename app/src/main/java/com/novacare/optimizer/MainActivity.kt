package com.novacare.optimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.novacare.optimizer.ui.apps.AppsScreen
import com.novacare.optimizer.ui.battery.BatteryScreen
import com.novacare.optimizer.ui.clean.CleanScreen
import com.novacare.optimizer.ui.home.HomeScreen
import com.novacare.optimizer.ui.storage.StorageScreen
import com.novacare.optimizer.ui.theme.NovaCareTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity + Navigation Compose
 * 页面切换动效遵循 One UI 9：水平位移 + 线性插值，无拖沓
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovaCareTheme {
                MainNavHost()
            }
        }
    }
}

data class Tab(val route: String, val label: String, val icon: ImageVector)

val TABS = listOf(
    Tab("home", "管家", Icons.Rounded.HealthAndSafety),
    Tab("storage", "存储", Icons.Rounded.PieChart),
    Tab("clean", "清理", Icons.Rounded.CleaningServices),
    Tab("apps", "应用", Icons.Rounded.Apps),
    Tab("battery", "电池", Icons.Rounded.BatteryFull),
)

@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow, // Zone 2 导航区
                tonalElevation = 0.dp,
            ) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = {
                slideInHorizontally(tween(350)) { it / 6 } + fadeIn(tween(350))
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(350)) },
            popExitTransition = {
                slideOutHorizontally(tween(300)) { it / 6 } + fadeOut(tween(300))
            },
        ) {
            composable("home") { HomeScreen() }
            composable("storage") { StorageScreen() }
            composable("clean") { CleanScreen() }
            composable("apps") { AppsScreen() }
            composable("battery") { BatteryScreen() }
        }
    }
}
