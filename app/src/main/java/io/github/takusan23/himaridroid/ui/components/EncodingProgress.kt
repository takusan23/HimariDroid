package io.github.takusan23.himaridroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.EncoderService
import io.github.takusan23.himaridroid.R

@Composable
fun EncodingProgress(
    modifier: Modifier = Modifier,
    reEncodeProgressData: EncoderService.ReEncodeProgressData,
    onStopClick: () -> Unit
) {
    val currentPositionSec = reEncodeProgressData.currentPositionMs / 1_000
    val videoDurationSec = reEncodeProgressData.videoDurationMs / 1_000

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
                    text = stringResource(id = R.string.encoder_progress_title),
                    fontSize = 20.sp
                )
            }
            Text(
                text = stringResource(id = R.string.encoder_progress_description),
                fontSize = 18.sp
            )

            if (currentPositionSec == 0L) {
                Text(text = stringResource(id = R.string.encoder_progress_progress_title_please_wait))
            } else {
                Column {
                    Text(text = stringResource(id = R.string.encoder_progress_progress_title))
                    Text(
                        text = "$currentPositionSec ${stringResource(id = R.string.seconds)}",
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${stringResource(id = R.string.encoder_progress_progress_video_duration)} : $videoDurationSec ${stringResource(id = R.string.seconds)}"
                    )
                }
            }

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { reEncodeProgressData.currentPositionMs / reEncodeProgressData.videoDurationMs.toFloat() }
            )

            OutlinedButton(
                modifier = Modifier.align(alignment = Alignment.End),
                onClick = onStopClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(text = stringResource(id = R.string.encoder_progress_cancel))
            }
        }
    }
}