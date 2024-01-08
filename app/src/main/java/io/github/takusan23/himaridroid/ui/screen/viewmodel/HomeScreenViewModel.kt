package io.github.takusan23.himaridroid.ui.screen.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.takusan23.himaridroid.EncoderService
import io.github.takusan23.himaridroid.R
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
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    private var inputUri: Uri? = null

    /** 入力した動画のフォーマット */
    val inputVideoFormat = _inputVideoFormat.asStateFlow()

    /** エンコーダー設定 */
    val encoderParams = _encoderParams.asStateFlow()

    /** SnackBar */
    val snackbarMessage = _snackbarMessage.asStateFlow()

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
        val fileNameWithoutExtension = "$FILENAME_PREFIX${videoFormat.fileName.split(".").first()}"
        _encoderParams.value = EncoderParams(
            fileNameWithoutExtension = fileNameWithoutExtension,
            videoWidth = videoFormat.videoWidth,
            videoHeight = videoFormat.videoHeight,
            bitRate = videoFormat.bitRate,
            frameRate = videoFormat.frameRate,
            codecContainerType = EncoderParams.CodecContainerType.AVC_AAC_MPEG4
        )
    }

    /** Snackbar を消す */
    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    /** エンコーダーを開始する */
    fun startEncoder(service: EncoderService) {
        val inputUri = inputUri ?: return
        val encoderParams = encoderParams.value ?: return
        service.startEncode(inputUri, encoderParams)
    }

    /** 解析できない場合は null */
    private suspend fun extractInputVideoFormat(uri: Uri): VideoFormat? = withContext(Dispatchers.IO) {
        val (extractor, mediaFormat) = MediaTool.createMediaExtractor(context, uri, MediaTool.Track.VIDEO)

        // コーデックとコンテナを探す
        // 拡張子は嘘をつく可能性があるので、実際のバイナリから見る
        val codec = mediaFormat.getString(MediaFormat.KEY_MIME)
        val metadataRetriever = MediaMetadataRetriever().apply {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { setDataSource(it.fileDescriptor) }
        }
        // ビットレートとか MediaExtractor で取れないやつ
        val container = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val bitRate = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

        // TODO 音声コーデックは見てねえわ
        val codecContainerType = when (container) {
            MIME_TYPE_MP4 -> when (codec) {
                MediaFormat.MIMETYPE_VIDEO_AVC -> EncoderParams.CodecContainerType.AVC_AAC_MPEG4
                MediaFormat.MIMETYPE_VIDEO_HEVC -> EncoderParams.CodecContainerType.HEVC_AAC_MPEG4
                MediaFormat.MIMETYPE_VIDEO_AV1 -> EncoderParams.CodecContainerType.AV1_AAC_MPEG4
                else -> null
            }

            MIME_TYPE_WEBM -> when (codec) {
                MediaFormat.MIMETYPE_VIDEO_VP9 -> EncoderParams.CodecContainerType.VP9_OPUS_WEBM
                MediaFormat.MIMETYPE_VIDEO_AV1 -> EncoderParams.CodecContainerType.AV1_OPUS_WEBM
                else -> null
            }

            else -> null
        }

        // 面倒なのでエラーに倒す
        if (codecContainerType == null) {
            _snackbarMessage.value = "${context.getString(R.string.home_screen_error_codec_container)} $codec / $container"
            return@withContext null
        }

        val videoFormat = VideoFormat(
            codecContainerType = codecContainerType,
            fileName = MediaTool.getFileName(context, uri),
            videoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT),
            videoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH),
            bitRate = bitRate?.toIntOrNull() ?: 3_000_000,
            frameRate = runCatching { mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrNull() ?: 30 // 含まれていなければ適当に 30 fps
        )
        extractor.release()
        return@withContext videoFormat
    }

    companion object {
        private const val FILENAME_PREFIX = "HimariDroid_"
        private const val MIME_TYPE_MP4 = "video/mp4"
        private const val MIME_TYPE_WEBM = "video/webm"
    }
}