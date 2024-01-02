package io.github.takusan23.himaridroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreenBottomBar(onClick: () -> Unit) {
    BottomAppBar {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = """
                しばらく時間がかかります。
                処理中はアプリを離れても大丈夫です。
                """.trimIndent()
            )
            Button(onClick = onClick) {
                Text(text = "開始")
            }
        }
    }
}