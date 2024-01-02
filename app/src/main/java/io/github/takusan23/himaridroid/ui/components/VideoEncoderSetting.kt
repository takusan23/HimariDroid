package io.github.takusan23.himaridroid.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.data.EncoderParams
import io.github.takusan23.himaridroid.processor.MediaTool

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "エンコーダーの設定",
                    fontSize = 20.sp
                )

                Button(onClick = onReset) {
                    Text(text = "リセットする")
                }
            }

            // コーデック選択ボトムシート
            CodecSelect(
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
                suffix = MediaTool.normalizeByte(encoderParams.bitRate),
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

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodecSelect(
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
                    text = "コーデックの選択",
                    fontSize = 20.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    EncoderParams.CodecContainerType.entries.forEachIndexed { index, codecContainerType ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = {
                                onSelectCodec(codecContainerType)
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
                                    text = codecContainerType.name,
                                    fontSize = 20.sp
                                )

                                Text(
                                    text = "説明だよ～",
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
            onValueChange = { it.toIntOrNull()?.also { onValueChange(it) } },
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