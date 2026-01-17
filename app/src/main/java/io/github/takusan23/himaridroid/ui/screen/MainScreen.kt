package io.github.takusan23.himaridroid.ui.screen

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

/** 画面遷移先 */
sealed interface NavigationPaths : NavKey {
    @Serializable
    data object Home : NavigationPaths

    @Serializable
    data object Setting : NavigationPaths

    @Serializable
    data object License : NavigationPaths
}

@Composable
fun MainScreen() {
    val backStack = rememberNavBackStack(NavigationPaths.Home)
    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<NavigationPaths.Home> {
                HomeScreen(onNavigate = { backStack += it })
            }
            entry<NavigationPaths.Setting> {
                SettingScreen(
                    onNavigate = { backStack += it },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<NavigationPaths.License> {
                LicenseScreen(onBack = { backStack.removeLastOrNull() })
            }
        }
    )
}