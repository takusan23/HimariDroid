package io.github.takusan23.himaridroid.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.takusan23.himaridroid.ui.components.InputVideo
import io.github.takusan23.himaridroid.ui.components.VideoEncoderSetting
import io.github.takusan23.himaridroid.ui.screen.viewmodel.HomeScreenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeScreenViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val inputVideoFormat = viewModel.inputVideoFormat.collectAsState()
    val encoderParams = viewModel.encoderParams.collectAsState()

    fun start() {
//        val uri = videoUri.value ?: return
//        scope.launch {
//            // 始める
//            statusText.value = "処理中です"
//            // せっかくなので時間を測ってみる
//            val totalTime = measureTimeMillis {
//                ReEncodeTool.start(
//                    context = context,
//                    inputUri = uri,
//                    videoBitrate = bitrate.value.toIntOrNull() ?: 1_000_000,
//                    codecName = MediaFormat.MIMETYPE_VIDEO_AV1
//                )
//            }
//            statusText.value = "終わりました。時間 = ${totalTime / 1000} 秒"
//        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "ひまりどろいど") }) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            item {
                InputVideo(
                    modifier = Modifier.fillMaxWidth(),
                    onFileSelect = { uri -> viewModel.setInputVideoUri(uri) }
                )
            }

            if (encoderParams.value != null) {
                item {
                    VideoEncoderSetting(
                        modifier = Modifier.fillMaxWidth(),
                        encoderParams = encoderParams.value!!,
                        onReset = { viewModel.setInitialEncoderParams() },
                        onUpdate = { params -> viewModel.updateEncoderParams(params) }
                    )
                }
            }
        }
    }
}