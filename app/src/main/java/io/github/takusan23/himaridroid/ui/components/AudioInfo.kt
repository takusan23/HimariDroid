package io.github.takusan23.himaridroid.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.takusan23.himaridroid.R

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
                Text(text = stringResource(id = R.string.audio_info_re_encode))
            } else {
                Text(text = stringResource(id = R.string.audio_info_not_re_encode))
            }
        }
    }
}