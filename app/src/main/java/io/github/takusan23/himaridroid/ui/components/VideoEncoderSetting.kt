package io.github.takusan23.himaridroid.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.data.EncoderParams
import io.github.takusan23.himaridroid.ui.tool.NumberFormat

@Composable
fun VideoEncoderSetting(
    modifier: Modifier = Modifier,
    encoderParams: EncoderParams,
    onReset: () -> Unit,
    onUpdate: (EncoderParams) -> Unit
) {

    fun update(update: (EncoderParams) -> EncoderParams) {
        onUpdate(update(encoderParams))
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp)
    ) {

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Text(
                modifier = Modifier,
                text = "エンコーダーの設定",
                fontSize = 20.sp
            )

            // ファイル名
            SuffixStringTextField(
                modifier = Modifier.fillMaxWidth(),
                value = encoderParams.fileNameWithoutExtension,
                onValueChange = { name -> update { it.copy(fileNameWithoutExtension = name) } },
                label = "ファイル名",
                suffix = ".${encoderParams.codecContainerType.containerType.extension}"
            )

            // コーデック選択ボトムシート
            CodecSelectSheet(
                modifier = Modifier.fillMaxWidth(),
                codecContainerType = encoderParams.codecContainerType,
                onSelectCodec = { type ->
                    update { it.copy(codecContainerType = type) }
                }
            )

            NumberInputField(
                modifier = Modifier.fillMaxWidth(),
                value = encoderParams.bitRate,
                onValueChange = { bitRate -> update { it.copy(bitRate = bitRate) } },
                label = "ビットレートの設定（単位は Bit）",
                suffix = NumberFormat.formatBit(encoderParams.bitRate),
                description = """
                    1 秒間にどれだけデータを入れるかです。
                    H.264 (AVC) と H.265 (HEVC) / VP9 / AV1 を比較すると、前者と比べて後者は半分のビットレートで同等の画質に出来ると言われています。
                """.trimIndent()
            )

            NumberInputField(
                modifier = Modifier.fillMaxWidth(),
                value = encoderParams.frameRate,
                onValueChange = { bitRate -> update { it.copy(frameRate = bitRate) } },
                label = "フレームレート（fps）"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                NumberInputField(
                    modifier = Modifier.weight(1f),
                    value = encoderParams.videoHeight,
                    onValueChange = { bitRate -> update { it.copy(videoHeight = bitRate) } },
                    label = "動画の縦のサイズ"
                )

                NumberInputField(
                    modifier = Modifier.weight(1f),
                    value = encoderParams.videoWidth,
                    onValueChange = { bitRate -> update { it.copy(videoWidth = bitRate) } },
                    label = "動画の横のサイズ"
                )
            }

            OutlinedButton(
                modifier = Modifier.align(alignment = Alignment.End),
                onClick = onReset
            ) {
                Text(text = "リセットする")
            }

        }
    }
}

/** Suffix 付き文字列 TextField */
@Composable
fun SuffixStringTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String? = null,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        suffix = if (suffix != null) {
            {
                Text(
                    text = suffix,
                    color = LocalContentColor.current.copy(alpha = 0.5f)
                )
            }
        } else null
    )
}

/** 数字の TextField */
@Composable
private fun NumberInputField(
    modifier: Modifier = Modifier,
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    suffix: String? = null,
    description: String? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            value = value.toString(),
            onValueChange = { text ->
                val number = text.toIntOrNull() ?: 0
                onValueChange(number)
            },
            label = { Text(text = label) },
            suffix = if (suffix != null) {
                {
                    Text(
                        text = suffix,
                        color = LocalContentColor.current.copy(alpha = 0.5f)
                    )
                }
            } else null
        )

        if (description != null) {
            Row(
                modifier = Modifier.border(
                    width = 1.dp,
                    color = LocalContentColor.current,
                    shape = RoundedCornerShape(5.dp)
                )
            ) {
                Text(
                    modifier = Modifier.padding(5.dp),
                    text = description,
                    fontSize = 14.sp
                )
            }
        }
    }
}