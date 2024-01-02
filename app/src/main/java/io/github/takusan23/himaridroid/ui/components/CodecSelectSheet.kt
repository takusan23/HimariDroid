package io.github.takusan23.himaridroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.data.EncoderParams

/** 説明 */
private data class CodecDescription(
    val codecContainerType: EncoderParams.CodecContainerType,
    val title: String,
    val description: String
)

private val codecDescriptionList = listOf(
    CodecDescription(
        codecContainerType = EncoderParams.CodecContainerType.AVC_AAC_MPEG4,
        title = "AVC (H.264) + AAC + MP4",
        description = "サイズ気にしないならコレ。大体どこでも再生できる。ビットレートを高くしないと画質があまり良くならない。"
    ),
    CodecDescription(
        codecContainerType = EncoderParams.CodecContainerType.HEVC_AAC_MPEG4,
        title = "HEVC (H.265) + AAC + MP4",
        description = "AVC の後継。理論値で AVC の半分のビットレートで同等の画質に出来るみたい（半分のファイルサイズになる）。特許問題のせいか再生できない端末もある。"
    ),
    CodecDescription(
        codecContainerType = EncoderParams.CodecContainerType.AV1_AAC_MPEG4,
        title = "AV1 + AAC + MP4",
        description = "HEVC の対抗馬。HEVC と同等かそれ以上の性能。特許問題をクリアした（？）期待の新星。新しいので普及はこれからだが、最近の端末なら再生できる。MP4 形式ではありますが、AV1 がデコードできるプレイヤーが必要です。"
    ),
    CodecDescription(
        codecContainerType = EncoderParams.CodecContainerType.VP9_OPUS_WEBM,
        title = "VP9 + Opus + WebM",
        description = "AV1 の一つ前に出たコーデック。AV1 が良いけど再生できないのが困るならこちらで。"
    ),
    CodecDescription(
        codecContainerType = EncoderParams.CodecContainerType.AV1_OPUS_WEBM,
        title = "AV1 + Opus + WebM",
        description = "AV1 + AAC + MP4 と同じですが、音声コーデックとコンテナが違います。WebM 形式で AV1 エンコードされた動画が欲しい場合に。拡張子 MP4 だと紛らわしい場合はこっちで。"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecSelectSheet(
    modifier: Modifier = Modifier,
    codecContainerType: EncoderParams.CodecContainerType,
    onSelectCodec: (EncoderParams.CodecContainerType) -> Unit
) {
    // コーデック選択ボトムシート
    val isOpen = remember { mutableStateOf(false) }

    if (isOpen.value) {
        ModalBottomSheet(onDismissRequest = { isOpen.value = false }) {

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    text = "コーデック(映像・音声) / コンテナ の選択",
                    fontSize = 20.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    codecDescriptionList.forEachIndexed { index, codecDescription ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = {
                                onSelectCodec(codecDescription.codecContainerType)
                                isOpen.value = false
                            },
                            shape = when (index) {
                                0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                                EncoderParams.CodecContainerType.entries.size - 1 -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                                else -> RoundedCornerShape(5.dp)
                            }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = codecDescription.title,
                                    fontSize = 20.sp
                                )

                                Text(
                                    text = codecDescription.description,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // 下にスペース欲しい、、ほしくない？
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = isOpen.value,
        onExpandedChange = { isOpen.value = !isOpen.value }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = codecContainerType.name,
            onValueChange = { /* do nothing */ },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isOpen.value) },
            label = { Text(text = "コーデックの選択") }
        )
    }

}