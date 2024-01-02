package io.github.takusan23.himaridroid.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.data.VideoFormat

/** 動画選択 */
@Composable
fun VideoSelect(
    modifier: Modifier = Modifier,
    videoFormat: VideoFormat?,
    onFileSelect: (Uri) -> Unit
) {

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onFileSelect(uri)
            }
        }
    )

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

            Text(
                text = "動画を選ぶ",
                fontSize = 20.sp
            )

            if (videoFormat != null) {
                VideoTrackInfo(
                    videoFormat = videoFormat,
                    onClick = { videoPicker.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                )
            } else {
                Text(
                    text = "変換したい動画を選んでください",
                    fontSize = 18.sp
                )
                Button(
                    modifier = Modifier.align(alignment = Alignment.End),
                    onClick = { videoPicker.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                ) {
                    Text(text = "動画を選ぶ")
                }
            }
        }
    }
}

/** 動画トラック情報 */
@Composable
private fun VideoTrackInfo(
    videoFormat: VideoFormat,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        Text(text = "選択動画", fontSize = 18.sp)
        Text(text = "動画の縦横 ${videoFormat.videoHeight} x ${videoFormat.videoWidth}")
        Text(text = "ビットレート ${videoFormat.bitRate}")
        Text(text = "フレームレート ${videoFormat.frameRate}")

        OutlinedButton(
            modifier = Modifier.align(alignment = Alignment.End),
            onClick = onClick
        ) {
            Text(text = "動画を選び直す")
        }
    }
}