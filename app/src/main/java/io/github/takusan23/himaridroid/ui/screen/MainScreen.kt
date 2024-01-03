package io.github.takusan23.himaridroid.ui.screen

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

enum class NavigationPaths(val path: String) {
    Home("home"),
    Setting("setting"),
    License("license")
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = NavigationPaths.Home.path) {
        composable(NavigationPaths.Home.path) {
            HomeScreen(onNavigate = { navController.navigate(it.path) })
        }
        composable(NavigationPaths.Setting.path) {
            SettingScreen(
                onNavigate = { navController.navigate(it.path) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavigationPaths.License.path) {
            LicenseScreen(onBack = { navController.popBackStack() })
        }
    }
}