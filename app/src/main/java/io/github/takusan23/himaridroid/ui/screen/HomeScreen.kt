package io.github.takusan23.himaridroid.ui.screen

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.takusan23.himaridroid.EncoderService
import io.github.takusan23.himaridroid.R
import io.github.takusan23.himaridroid.data.EncoderParams
import io.github.takusan23.himaridroid.data.VideoFormat
import io.github.takusan23.himaridroid.ui.components.AudioInfo
import io.github.takusan23.himaridroid.ui.components.EncodingProgress
import io.github.takusan23.himaridroid.ui.components.HomeScreenBottomBar
import io.github.takusan23.himaridroid.ui.components.VideoEncoderSetting
import io.github.takusan23.himaridroid.ui.components.VideoSelect
import io.github.takusan23.himaridroid.ui.screen.viewmodel.HomeScreenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = viewModel(),
    onNavigate: (NavigationPaths) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val inputVideoFormat = viewModel.inputVideoFormat.collectAsState()
    val encoderParams = viewModel.encoderParams.collectAsState()

    // エンコーダーサービスとバインドする
    val encoderService = remember { EncoderService.bindService(context, lifecycleOwner.lifecycle) }.collectAsState(initial = null)
    val isEncoding = encoderService.value?.isEncoding?.collectAsState()
    val reEncodeProgressData = encoderService.value?.progressCurrentPositionMs?.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewModel.snackbarMessage.collect { message ->
            if (message == null) {
                snackbarState.currentSnackbarData?.dismiss()
            } else {
                snackbarState.showSnackbar(message)
                viewModel.dismissSnackbar()
            }
        }
    }

    if (isEncoding?.value == true && reEncodeProgressData?.value != null) {
        // エンコード中
        EncodingScreen(
            onStopClick = { encoderService.value?.stopEncode() },
            scrollBehavior = scrollBehavior,
            reEncodeProgressData = reEncodeProgressData.value!!
        )
    } else {
        // エンコードしてない
        EncoderScreen(
            snackbarHostState = snackbarState,
            scrollBehavior = scrollBehavior,
            encoderParams = encoderParams.value,
            inputVideoFormat = inputVideoFormat.value,
            onSettingClick = { onNavigate(NavigationPaths.Setting) },
            onInputVideoUri = { uri -> viewModel.setInputVideoUri(uri) },
            onResetInitialEncoderParams = { viewModel.setInitialEncoderParams() },
            onUpdateEncoderParams = { params -> viewModel.updateEncoderParams(params) },
            onEncodeClick = {
                // エンコーダー開始
                encoderService.value?.also { encoderService ->
                    viewModel.startEncoder(encoderService)
                }
            }
        )
    }
}

/** エンコード中画面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncodingScreen(
    onStopClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    reEncodeProgressData: EncoderService.ReEncodeProgressData
) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(text = stringResource(id = R.string.app_name)) }) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                EncodingProgress(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    reEncodeProgressData = reEncodeProgressData,
                    onStopClick = onStopClick
                )
            }
        }
    }
}

/** 編集画面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncoderScreen(
    snackbarHostState: SnackbarHostState,
    scrollBehavior: TopAppBarScrollBehavior,
    encoderParams: EncoderParams?,
    inputVideoFormat: VideoFormat?,
    onInputVideoUri: (Uri) -> Unit,
    onResetInitialEncoderParams: () -> Unit,
    onUpdateEncoderParams: (EncoderParams) -> Unit,
    onEncodeClick: () -> Unit,
    onSettingClick: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onSettingClick) {
                        Icon(painter = painterResource(id = R.drawable.settings_24px), contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (encoderParams != null && inputVideoFormat != null) {
                HomeScreenBottomBar(onClick = onEncodeClick)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = paddingValues
        ) {

            // 動画選択
            item {
                VideoSelect(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    videoFormat = inputVideoFormat,
                    onFileSelect = onInputVideoUri
                )
            }

            // エンコーダー設定項目
            if (encoderParams != null) {
                item {
                    VideoEncoderSetting(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth(),
                        tenBitHdrInfo = inputVideoFormat?.tenBitHdrInfo,
                        encoderParams = encoderParams,
                        onReset = onResetInitialEncoderParams,
                        onUpdate = onUpdateEncoderParams
                    )
                }
            }

            // 音声が再エンコードされるかどうか
            if (encoderParams != null && inputVideoFormat != null) {
                item {
                    AudioInfo(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth(),
                        isReEncode = encoderParams.codecContainerType.audioCodec != inputVideoFormat.codecContainerType.audioCodec
                    )
                }
            }

            // 最後にスペース欲しい、、、ほしくない？
            item {
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}