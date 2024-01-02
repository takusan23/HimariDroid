package io.github.takusan23.himaridroid.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AudioInfo(
    modifier: Modifier = Modifier,
    isReEncode: Boolean
) {
    OutlinedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isReEncode) {
                Text(text = "音声トラックは WebM コンテナに入れるために、Opus へ再エンコードされます。")
            } else {
                Text(text = "音声トラックは元データのを利用します。")
            }
        }
    }
}