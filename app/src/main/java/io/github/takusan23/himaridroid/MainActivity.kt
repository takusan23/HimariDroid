package io.github.takusan23.himaridroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.takusan23.himaridroid.ui.screen.HomeScreen
import io.github.takusan23.himaridroid.ui.theme.HimariDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HimariDroidTheme {
                HomeScreen()
            }
        }
    }
}
