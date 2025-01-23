package io.github.takusan23.himaridroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.R
import io.github.takusan23.himaridroid.data.EncoderParams

/** 説明 */
private data class TenBitHdrModeMenuDescription(
    val mode: EncoderParams.TenBitHdrOption.TenBitHdrMode,
    val title: String,
    val description: String
)

/** 10-bit HDR 選択シート */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenBitHdrOptionMenu(
    modifier: Modifier = Modifier,
    currentTenBitHdrMode: EncoderParams.TenBitHdrOption.TenBitHdrMode,
    onTenBitHdrModeChange: (EncoderParams.TenBitHdrOption.TenBitHdrMode) -> Unit
) {

    val tenBitHdrModeMenu = listOf(
        TenBitHdrModeMenuDescription(
            mode = EncoderParams.TenBitHdrOption.TenBitHdrMode.KEEP,
            title = stringResource(id = R.string.ten_bit_hdr_option_menu_keep_title),
            description = stringResource(id = R.string.ten_bit_hdr_option_menu_keep_description)
        ),
        TenBitHdrModeMenuDescription(
            mode = EncoderParams.TenBitHdrOption.TenBitHdrMode.TO_SDR,
            title = stringResource(id = R.string.ten_bit_hdr_option_menu_to_sdr_title),
            description = stringResource(id = R.string.ten_bit_hdr_option_menu_to_sdr_description)
        )
    )

    // 10-bit HDR 選択ボトムシート
    val isOpen = remember { mutableStateOf(false) }
    if (isOpen.value) {
        ModalBottomSheet(onDismissRequest = { isOpen.value = false }) {

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    text = stringResource(id = R.string.ten_bit_hdr_option_menu_title),
                    fontSize = 20.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    tenBitHdrModeMenu.forEachIndexed { index, menu ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = {
                                onTenBitHdrModeChange(menu.mode)
                                isOpen.value = false
                            },
                            shape = when (index) {
                                0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                                EncoderParams.TenBitHdrOption.TenBitHdrMode.entries.size - 1 -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                                else -> RoundedCornerShape(5.dp)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = menu.title,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = menu.description,
                                        fontSize = 14.sp
                                    )
                                }
                                if (menu.mode == currentTenBitHdrMode) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.done_24px),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }

                // 下にスペース欲しい、、ほしくない？
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {

        ExposedDropdownMenuBox(
            modifier = Modifier.fillMaxWidth(),
            expanded = isOpen.value,
            onExpandedChange = { isOpen.value = !isOpen.value }
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                onValueChange = { /* do nothing */ },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isOpen.value) },
                value = tenBitHdrModeMenu[currentTenBitHdrMode.ordinal].title,
                label = { Text(text = stringResource(id = R.string.ten_bit_hdr_option_menu_title)) }
            )
        }

        DescriptionCard(
            text = stringResource(id = R.string.ten_bit_hdr_option_menu_description),
            iconResId = R.drawable.android_hdr_icon
        )
    }
}