package io.github.takusan23.himaridroid.processor

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import io.github.takusan23.himaridroid.data.EncoderParams
import io.github.takusan23.himaridroid.processor.audio.AudioProcessor
import io.github.takusan23.himaridroid.processor.video.VideoProcessor
import io.github.takusan23.himariwebm.HimariWebm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** 再エンコーダー */
object ReEncodeTool {

    private const val TEMP_AUDIO_RAW_FILE = "temp_audio_raw_file"
    private const val TEMP_AUDIO_UPSAMPLING_FILE = "temp_audio_upsampling_file"

    suspend fun encoder(
        context: Context,
        inputUri: Uri,
        encoderParams: EncoderParams,
        onProgressCurrentPositionMs: (Long) -> Unit
    ): Unit = withContext(Dispatchers.Default) {
        val resultFile = if (encoderParams.codecContainerType.containerType == EncoderParams.ContainerType.MPEG_4) {
            // MP4 なら MediaMuxer
            startEncodeToMp4(
                context = context,
                inputUri = inputUri,
                onProgressCurrentPositionMs = onProgressCurrentPositionMs,
                encoderParams = encoderParams
            )
        } else {
            // WebM は自前実装
            startEncodeToWebm(
                context = context,
                inputUri = inputUri,
                onProgressCurrentPositionMs = onProgressCurrentPositionMs,
                encoderParams = encoderParams
            )
        }
        // 端末の動画フォルダにコピーする
        MediaTool.saveToVideoFolder(
            context,
            resultFile,
            encoderParams.codecContainerType.containerType
        )
        resultFile.delete()
    }

    @SuppressLint("WrongConstant")
    private suspend fun startEncodeToMp4(
        context: Context,
        inputUri: Uri,
        encoderParams: EncoderParams,
        onProgressCurrentPositionMs: (Long) -> Unit
    ) = withContext(Dispatchers.Default) {
        // 出力先
        val resultFile = context.getExternalFilesDir(null)!!.resolve(encoderParams.fileNameAndExtension)
        val mediaMuxer = MediaMuxer(resultFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // 音声トラックを追加
        val (audioMediaExtractor, audioFormat) = MediaTool.createMediaExtractor(context, inputUri, MediaTool.Track.AUDIO)
        val audioIndex = mediaMuxer.addTrack(audioFormat)

        // 映像トラックを追加してエンコードする
        var videoIndex = -1
        VideoProcessor.start(
            context = context,
            inputUri = inputUri,
            encoderParams = encoderParams,
            onProgressCurrentPositionMs = onProgressCurrentPositionMs,
            onOutputFormat = { mediaFormat ->
                videoIndex = mediaMuxer.addTrack(mediaFormat)
                mediaMuxer.start()
            },
            onOutputData = { byteBuffer, bufferInfo ->
                mediaMuxer.writeSampleData(videoIndex, byteBuffer, bufferInfo)
            }
        )

        // 終わったら音声
        audioMediaExtractor.also { extractor ->
            val byteBuffer = ByteBuffer.allocate(8192)
            val bufferInfo = MediaCodec.BufferInfo()
            // データが無くなるまで回す
            while (isActive) {
                // データを読み出す
                val offset = byteBuffer.arrayOffset()
                bufferInfo.size = extractor.readSampleData(byteBuffer, offset)
                // もう無い場合
                if (bufferInfo.size < 0) break
                // 書き込む
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags // Lintがキレるけど黙らせる
                mediaMuxer.writeSampleData(audioIndex, byteBuffer, bufferInfo)
                // 次のデータに進める
                extractor.advance()
            }
            // あとしまつ
            extractor.release()
        }

        // 書き込み終わり
        mediaMuxer.stop()
        mediaMuxer.release()
        return@withContext resultFile
    }

    /** エンコードして webm に保存する */
    private suspend fun startEncodeToWebm(
        context: Context,
        inputUri: Uri,
        encoderParams: EncoderParams,
        onProgressCurrentPositionMs: (Long) -> Unit,
    ) = withContext(Dispatchers.Default) {
        // 一時的にファイルを置いておきたいので
        val tempFolder = context.getExternalFilesDir(null)!!.resolve("temp_folder").apply { mkdir() }
        // 出力先
        val resultFile = context.getExternalFilesDir(null)!!.resolve(encoderParams.fileNameAndExtension)
        // WebM は自前実装
        // himari-webm 参照
        val himariWebm = HimariWebm(tempFolder, resultFile)

        try {
            listOf(
                // 映像の再エンコード
                launch {
                    // 再エンコードをする
                    VideoProcessor.start(
                        context = context,
                        inputUri = inputUri,
                        encoderParams = encoderParams,
                        onProgressCurrentPositionMs = onProgressCurrentPositionMs,
                        onOutputFormat = { mediaFormat ->
                            // VP9 か AV1 のみ
                            val codecName = when (mediaFormat.getString(MediaFormat.KEY_MIME)) {
                                MediaFormat.MIMETYPE_VIDEO_AV1 -> "V_AV1"
                                MediaFormat.MIMETYPE_VIDEO_VP9 -> "V_VP9"
                                else -> throw RuntimeException("対応してないです")
                            }
                            val videoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
                            val videoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
                            himariWebm.setVideoTrack(
                                videoCodec = codecName,
                                videoWidth = videoWidth,
                                videoHeight = videoHeight
                            )
                        },
                        onOutputData = { byteBuffer, bufferInfo ->
                            val positionMs = bufferInfo.presentationTimeUs / 1_000
                            val isKeyFrame = bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME
                            himariWebm.writeVideo(
                                byteArray = byteBuffer.toByteArray(),
                                durationMs = positionMs,
                                isKeyFrame = isKeyFrame
                            )
                        }
                    )
                },
                // 音声の再エンコード
                // Opus にするため
                launch {
                    // 開始時間がとんでもない時間になる？
                    // 0 スタートになるように調整
                    var startPresentationTime = -1L
                    encodeAudio(
                        tempFolder = tempFolder,
                        context = context,
                        inputUri = inputUri,
                        outputSamplingRate = AudioProcessor.OPUS_SAMPLING_RATE,
                        codec = MediaFormat.MIMETYPE_AUDIO_OPUS,
                        onOutputFormat = { mediaFormat ->
                            val samplingRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            val channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            himariWebm.setAudioTrack(
                                audioCodec = "A_OPUS",
                                audioSamplingRate = samplingRate.toFloat(),
                                audioChannelCount = channelCount
                            )
                        },
                        onOutputData = { byteBuffer, bufferInfo ->
                            if (startPresentationTime == -1L) {
                                startPresentationTime = bufferInfo.presentationTimeUs
                            }
                            val durationMs = (bufferInfo.presentationTimeUs - startPresentationTime) / 1_000
                            himariWebm.writeAudio(
                                byteArray = byteBuffer.toByteArray(),
                                durationMs = durationMs,
                                isKeyFrame = true // Opus は常にキーフレーム？
                            )
                        }
                    )
                }
            ).joinAll()
            // 終わり
            himariWebm.stop()
        } finally {
            // 成功時・キャンセル時
            tempFolder.deleteRecursively()
        }
        return@withContext resultFile
    }

    private suspend fun encodeAudio(
        tempFolder: File,
        context: Context,
        inputUri: Uri,
        codec: String,
        outputSamplingRate: Int,
        onOutputFormat: suspend (MediaFormat) -> Unit,
        onOutputData: suspend (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val (videoExtractor, inputAudioFormat) = MediaTool.createMediaExtractor(context, inputUri, MediaTool.Track.AUDIO)
        val rawFile = tempFolder.resolve(TEMP_AUDIO_RAW_FILE)
        val upsamplingFile = tempFolder.resolve(TEMP_AUDIO_UPSAMPLING_FILE)

        // 実はデコーダーから出てきた MediaCodec#getOutputFormat を信用する必要がある
        // 詳しくは AudioDecoder で
        var decoderOutputMediaFormat: MediaFormat? = null

        try {
            // PCM にする
            AudioProcessor.decodeAudio(
                onOutputFormat = { decoderOutputMediaFormat = it },
                mediaExtractor = videoExtractor,
                mediaFormat = inputAudioFormat,
                outputFile = rawFile
            )

            // サンプリングレートの変換が必要ならやる
            // デコーダーからの MediaFormat を優先
            val inSamplingRate = (decoderOutputMediaFormat ?: inputAudioFormat).getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val inChannelCount = (decoderOutputMediaFormat ?: inputAudioFormat).getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            AudioProcessor.upsamplingBySonic(
                inFile = rawFile,
                outFile = upsamplingFile,
                channelCount = inChannelCount,
                inSamplingRate = inSamplingRate,
                outSamplingRate = outputSamplingRate
            )

            // サンプリングレートを変換したので再度エンコードする
            AudioProcessor.encodeAudio(
                rawFile = upsamplingFile,
                codec = codec,
                sampleRate = outputSamplingRate,
                onOutputFormat = onOutputFormat,
                onOutputData = onOutputData
            )
        } finally {
            // コンプリート！
            // いらないので消す
            rawFile.delete()
            upsamplingFile.delete()
        }
    }

    /** [ByteBuffer]を[ByteArray]に変換する */
    private fun ByteBuffer.toByteArray() = ByteArray(this.remaining()).also { byteArray ->
        this.get(byteArray)
    }
}