package io.github.takusan23.himaridroid.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.processor.ReEncodeTool
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val videoUri = remember { mutableStateOf<Uri?>(null) }

    // ビットレート、初期値は 1Mbps
    val bitrate = remember { mutableStateOf(1_000_000.toString()) }
    val statusText = remember { mutableStateOf("待機中") }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> videoUri.value = uri }
    )

    fun start() {
        val uri = videoUri.value ?: return
        scope.launch {
            // 始める
            statusText.value = "処理中です"
            // せっかくなので時間を測ってみる
            val totalTime = measureTimeMillis {
                ReEncodeTool.start(
                    context = context,
                    inputUri = uri,
                    videoBitrate = bitrate.value.toIntOrNull() ?: 1_000_000
                )
            }
            statusText.value = "終わりました。時間 = ${totalTime / 1000} 秒"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "ひまりどろいど") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(20.dp)
        ) {

            Text(text = "動画の選択", fontSize = 20.sp)
            Button(onClick = {
                videoPicker.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly))
            }) { Text(text = "動画を選ぶ") }

            if (videoUri.value != null) {

                // ビットレート入力
                OutlinedTextField(
                    label = { Text(text = "ビットレート bps") },
                    value = bitrate.value,
                    onValueChange = { bitrate.value = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Text(text = videoUri.value.toString())
                Button(onClick = {
                    start()
                }) { Text(text = "処理を始める") }
                Text(text = statusText.value)
            }
        }
    }
}