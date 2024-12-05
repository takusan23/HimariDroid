package io.github.takusan23.himaridroid.ui.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.R
import io.github.takusan23.himaridroid.data.EncoderParams
import io.github.takusan23.himaridroid.data.VideoFormat
import io.github.takusan23.himaridroid.ui.tool.NumberFormat

@Composable
fun VideoEncoderSetting(
    modifier: Modifier = Modifier,
    encoderParams: EncoderParams,
    tenBitHdrInfo: VideoFormat.TenBitHdrInfo?,
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
                text = stringResource(id = R.string.video_encoder_setting_title),
                fontSize = 20.sp
            )

            // ファイル名
            SuffixStringTextField(
                modifier = Modifier.fillMaxWidth(),
                value = encoderParams.fileNameWithoutExtension,
                onValueChange = { name -> update { it.copy(fileNameWithoutExtension = name) } },
                label = stringResource(id = R.string.video_encoder_setting_file_name),
                suffix = ".${encoderParams.codecContainerType.containerType.extension}"
            )

            // コーデック選択ボトムシート
            CodecSelectSheet(
                modifier = Modifier.fillMaxWidth(),
                onSelectCodec = { type -> update { it.copy(codecContainerType = type) } },
                // TODO 10Bit HDR の場合は HEVC 固定にしている
                isEnable = encoderParams.tenBitHdrOptionOrNull?.mode != EncoderParams.TenBitHdrOption.TenBitHdrMode.KEEP,
                codecContainerType = if (encoderParams.tenBitHdrOptionOrNull?.mode == EncoderParams.TenBitHdrOption.TenBitHdrMode.KEEP) {
                    EncoderParams.CodecContainerType.HEVC_AAC_MPEG4
                } else {
                    encoderParams.codecContainerType
                }
            )

            NumberInputField(
                modifier = Modifier.fillMaxWidth(),
                value = encoderParams.bitRate,
                onValueChange = { bitRate -> update { it.copy(bitRate = bitRate) } },
                label = stringResource(id = R.string.video_encoder_setting_bit_rate),
                suffix = NumberFormat.formatBit(encoderParams.bitRate),
                description = stringResource(id = R.string.video_encoder_setting_bit_rate_description)
            )

            NumberInputField(
                modifier = Modifier.fillMaxWidth(),
                value = encoderParams.frameRate,
                onValueChange = { bitRate -> update { it.copy(frameRate = bitRate) } },
                label = stringResource(id = R.string.video_encoder_setting_frame_rate)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                NumberInputField(
                    modifier = Modifier.weight(1f),
                    value = encoderParams.videoWidth,
                    onValueChange = { bitRate -> update { it.copy(videoWidth = bitRate) } },
                    label = stringResource(id = R.string.video_encoder_setting_width)
                )

                NumberInputField(
                    modifier = Modifier.weight(1f),
                    value = encoderParams.videoHeight,
                    onValueChange = { bitRate -> update { it.copy(videoHeight = bitRate) } },
                    label = stringResource(id = R.string.video_encoder_setting_height)
                )
            }

            // 10Bit HDR 動画のときのみ
            if (encoderParams.tenBitHdrOptionOrNull != null) {
                TenBitHdrOptionMenu(
                    modifier = Modifier.fillMaxWidth(),
                    currentTenBitHdrMode = encoderParams.tenBitHdrOptionOrNull.mode,
                    onTenBitHdrModeChange = { mode ->
                        update {
                            it.copy(tenBitHdrOptionOrNull = encoderParams.tenBitHdrOptionOrNull.copy(mode = mode))
                        }
                    }
                )
            }

            OutlinedButton(
                modifier = Modifier.align(alignment = Alignment.End),
                onClick = onReset
            ) {
                Text(text = stringResource(id = R.string.video_encoder_setting_reset))
            }

        }
    }
}

/** Suffix 付き文字列 TextField */
@Composable
private fun SuffixStringTextField(
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
            DescriptionCard(
                text = description,
                iconResId = R.drawable.info_24px
            )
        }
    }
}