package io.github.takusan23.himaridroid.ui.screen.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.takusan23.himaridroid.data.EncoderParams
import io.github.takusan23.himaridroid.data.VideoFormat
import io.github.takusan23.himaridroid.processor.MediaTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
class HomeScreenViewModel(private val application: Application) : AndroidViewModel(application) {
    private val context: Context
        get() = application.applicationContext

    private val _inputVideoFormat = MutableStateFlow<VideoFormat?>(null)
    private val _encoderParams = MutableStateFlow<EncoderParams?>(null)

    private var inputUri: Uri? = null

    /** 入力した動画のフォーマット */
    val inputVideoFormat = _inputVideoFormat.asStateFlow()

    /** エンコーダー設定 */
    val encoderParams = _encoderParams.asStateFlow()

    /** ファイル選択時に呼ばれる */
    fun setInputVideoUri(uri: Uri) {
        viewModelScope.launch {
            inputUri = uri
            _inputVideoFormat.value = extractInputVideoFormat(uri)
            // エンコーダー設定をいい感じに作る
            setInitialEncoderParams()
        }
    }

    /** エンコーダー設定を更新 */
    fun updateEncoderParams(params: EncoderParams) {
        _encoderParams.value = params
    }

    /** エンコーダー設定をリセットする */
    fun setInitialEncoderParams() {
        val videoFormat = inputVideoFormat.value ?: return
        _encoderParams.value = EncoderParams(
            videoWidth = videoFormat.videoWidth,
            videoHeight = videoFormat.videoHeight,
            bitRate = videoFormat.bitRate,
            frameRate = videoFormat.frameRate,
            codecContainerType = EncoderParams.CodecContainerType.AVC_AAC_MPEG4
        )
    }

    private suspend fun extractInputVideoFormat(uri: Uri): VideoFormat = withContext(Dispatchers.IO) {
        val (extractor, mediaFormat) = MediaTool.createMediaExtractor(context, uri, MediaTool.Track.VIDEO)
        val videoFormat = VideoFormat(
            videoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT),
            videoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH),
            bitRate = runCatching { mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE) }.getOrNull() ?: 6_000_000,
            frameRate = runCatching { mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrNull() ?: 30 // 含まれていなければ適当に 30 fps
        )
        extractor.release()
        videoFormat
    }

    companion object {

    }

}