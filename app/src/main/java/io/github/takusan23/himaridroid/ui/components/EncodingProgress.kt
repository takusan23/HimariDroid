package io.github.takusan23.himaridroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EncodingProgress(
    modifier: Modifier = Modifier,
    currentPositionMs: Long,
    onStopClick: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "再エンコード中です",
                    fontSize = 20.sp
                )
            }
            Text(
                text = "しばらく時間がかかります。エンコード中はアプリを離れても大丈夫です。",
                fontSize = 18.sp
            )

            Column {
                Text(text = "再エンコードの進捗(エンコード済みの時間)")
                Text(
                    text = "${currentPositionMs / 1_000} 秒",
                    fontSize = 18.sp
                )
            }

            Button(
                modifier = Modifier.align(alignment = Alignment.End),
                onClick = onStopClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(text = "中止する")
            }
        }
    }
}