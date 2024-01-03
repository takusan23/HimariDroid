package io.github.takusan23.himaridroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.takusan23.himaridroid.ui.screen.MainScreen
import io.github.takusan23.himaridroid.ui.theme.HimariDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HimariDroidTheme {
                MainScreen()
            }
        }
    }
}
