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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.R
import io.github.takusan23.himaridroid.data.EncoderParams

/** 説明 */
private data class CodecDescription(
    val codecContainerType: EncoderParams.CodecContainerType,
    val title: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecSelectSheet(
    modifier: Modifier = Modifier,
    codecContainerType: EncoderParams.CodecContainerType,
    onSelectCodec: (EncoderParams.CodecContainerType) -> Unit
) {
    val context= LocalContext.current

    // コーデック
    val codecDescriptionList = remember {
        listOf(
            CodecDescription(
                codecContainerType = EncoderParams.CodecContainerType.AVC_AAC_MPEG4,
                title = context.getString(R.string.codec_select_sheet_avc_mp4_title),
                description = context.getString(R.string.codec_select_sheet_avc_mp4_description)
            ),
            CodecDescription(
                codecContainerType = EncoderParams.CodecContainerType.HEVC_AAC_MPEG4,
                title = context.getString(R.string.codec_select_sheet_hevc_mp4_title),
                description = context.getString(R.string.codec_select_sheet_hevc_mp4_description)
            ),
            CodecDescription(
                codecContainerType = EncoderParams.CodecContainerType.AV1_AAC_MPEG4,
                title = context.getString(R.string.codec_select_sheet_av1_mp4_title),
                description = context.getString(R.string.codec_select_sheet_av1_mp4_description)
            ),
            CodecDescription(
                codecContainerType = EncoderParams.CodecContainerType.VP9_OPUS_WEBM,
                title = context.getString(R.string.codec_select_sheet_vp9_webm_title),
                description = context.getString(R.string.codec_select_sheet_vp9_webm_description)
            ),
            CodecDescription(
                codecContainerType = EncoderParams.CodecContainerType.AV1_OPUS_WEBM,
                title = context.getString(R.string.codec_select_sheet_av1_webm_title),
                description = context.getString(R.string.codec_select_sheet_av1_webm_description)
            )
        )
    }

    // コーデック選択ボトムシート
    val isOpen = remember { mutableStateOf(false) }

    if (isOpen.value) {
        ModalBottomSheet(onDismissRequest = { isOpen.value = false }) {

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    text = stringResource(id = R.string.code_select_sheet_title),
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
            label = { Text(text = stringResource(id = R.string.code_select_sheet_select_button)) }
        )
    }

}