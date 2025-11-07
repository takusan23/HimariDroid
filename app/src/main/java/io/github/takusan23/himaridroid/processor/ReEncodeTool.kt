package io.github.takusan23.himaridroid.processor

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import io.github.takusan23.akaricore.audio.AudioEncodeDecodeProcessor
import io.github.takusan23.akaricore.audio.AudioSonicProcessor
import io.github.takusan23.akaricore.common.toAkariCoreInputOutputData
import io.github.takusan23.akaricore.muxer.AkariEncodeMuxerInterface
import io.github.takusan23.himaridroid.data.EncoderParams
import io.github.takusan23.himariwebm.HimariWebm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.nio.ByteBuffer

/** 再エンコーダー */
object ReEncodeTool {

    private const val TEMP_AUDIO_RAW_FILE = "temp_audio_raw_file"
    private const val TEMP_AUDIO_UPSAMPLING_FILE = "temp_audio_upsampling_file"
    private const val OPUS_AAC_SAMPLING_RATE = 48_000 // Opus は 44.1k 対応していないので、48k にアップサンプリングする

    suspend fun encoder(
        context: Context,
        inputUri: Uri,
        encoderParams: EncoderParams,
        onProgressCurrentPositionMs: (videoDurationMs: Long, currentPositionMs: Long) -> Unit
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
        onProgressCurrentPositionMs: (videoDurationMs: Long, currentPositionMs: Long) -> Unit
    ): File {
        // 一時的にファイルを置いておきたいので
        val tempFolder = context.getExternalFilesDir(null)!!.resolve("temp_folder").apply { mkdir() }
        // 出力先
        val resultFile = context.getExternalFilesDir(null)!!.resolve(encoderParams.fileNameAndExtension)
        val mediaMuxer = MediaMuxer(resultFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // 音声トラックがない場合は null
        val audioTrackPairOrNull = MediaTool.createMediaExtractor(context, inputUri, MediaTool.Track.AUDIO)

        coroutineScope {

            // 映像トラック・音声トラックが追加できたら両方 true になる Flow
            // MediaMuxer.start() はすべての addTrack() を待ち合わせる必要がありその制御
            // 両方 true で start() される予定
            val trackAddStateFlow = MutableStateFlow(false to false)

            /** [trackAddStateFlow]を更新する */
            fun updateTrackState(
                video: Boolean = trackAddStateFlow.value.first,
                audio: Boolean = trackAddStateFlow.value.second
            ) {
                trackAddStateFlow.update { video to audio }
            }

            /** 音声の追加を待つ */
            suspend fun awaitAddOrSkipAudioTrack() = trackAddStateFlow.first { (_, audio) -> audio /* == true */ }

            /** 映像・音声両方の追加を待つ（つまり start() された） */
            suspend fun awaitAllAddedTrack() = trackAddStateFlow.first { (video, audio) -> video && audio }

            // 音声トラック
            launch {

                // null の場合は即時
                if (audioTrackPairOrNull == null) {
                    updateTrackState(audio = true)
                    return@launch
                }

                val (mediaExtractor, mediaFormat) = audioTrackPairOrNull
                if (mediaFormat.getString(MediaFormat.KEY_MIME) == encoderParams.codecContainerType.audioCodec) {
                    // トラックを入れて
                    val audioTrack = mediaMuxer.addTrack(audioTrackPairOrNull.second)
                    // 追加完了
                    updateTrackState(audio = true)
                    // start() 待ち
                    awaitAllAddedTrack()
                    // mp4 -> mp4 の場合は入れ直すだけでいい
                    val byteBuffer = ByteBuffer.allocate(8192)
                    val bufferInfo = MediaCodec.BufferInfo()
                    // データが無くなるまで回す
                    while (true) {
                        // キャンセルチェック
                        yield()
                        // データを読み出す
                        val offset = byteBuffer.arrayOffset()
                        bufferInfo.size = mediaExtractor.readSampleData(byteBuffer, offset)
                        // もう無い場合
                        if (bufferInfo.size < 0) break
                        // 書き込む
                        bufferInfo.presentationTimeUs = mediaExtractor.sampleTime
                        bufferInfo.flags = mediaExtractor.sampleFlags // Lintがキレるけど黙らせる
                        mediaMuxer.writeSampleData(audioTrack, byteBuffer, bufferInfo)
                        // 次のデータに進める
                        mediaExtractor.advance()
                    }
                } else {
                    // 音声トラックも再エンコードする必要がある
                    // webm (Opus) -> mp4 (AAC) など
                    var audioIndex = -1

                    // 開始時間がとんでもない時間になる？
                    // 0 スタートになるように調整
                    var startPresentationTime = -1L
                    encodeAudio(
                        tempFolder = tempFolder,
                        context = context,
                        inputUri = inputUri,
                        codec = MediaFormat.MIMETYPE_AUDIO_AAC,
                        outputSamplingRate = OPUS_AAC_SAMPLING_RATE,
                        onOutputFormat = { mediaFormat ->
                            // 音声トラックを追加する
                            audioIndex = mediaMuxer.addTrack(mediaFormat)
                            // 完了にする
                            updateTrackState(audio = true)
                            // start() を待つ
                            awaitAllAddedTrack()
                        },
                        onOutputData = { byteBuffer, bufferInfo ->
                            if (startPresentationTime == -1L) {
                                startPresentationTime = bufferInfo.presentationTimeUs
                            }
                            bufferInfo.presentationTimeUs -= startPresentationTime
                            mediaMuxer.writeSampleData(audioIndex, byteBuffer, bufferInfo)
                        }
                    )
                }
                // あとしまつ
                tempFolder.deleteRecursively()
                mediaExtractor.release()
            }

            // 映像トラック
            launch {
                // 映像トラックを追加してエンコードする
                var videoIndex = -1
                // 再エンコードをする
                VideoProcessor.start(
                    context = context,
                    inputUri = inputUri,
                    encoderParams = encoderParams,
                    onOutputFormat = { mediaFormat ->
                        // 映像トラックを足す
                        videoIndex = mediaMuxer.addTrack(mediaFormat)
                        // start() するため音声トラックを待つ
                        awaitAddOrSkipAudioTrack()
                        // MediaMuxer を始める
                        mediaMuxer.start()
                        // 完了にする
                        updateTrackState(video = true)
                    },
                    onOutputData = { byteBuffer, bufferInfo ->
                        mediaMuxer.writeSampleData(videoIndex, byteBuffer, bufferInfo)
                    },
                    onProgressCurrentPositionMs = onProgressCurrentPositionMs
                )
            }
        }

        // 書き込み終わり
        mediaMuxer.stop()
        mediaMuxer.release()
        return resultFile
    }

    /** エンコードして webm に保存する */
    private suspend fun startEncodeToWebm(
        context: Context,
        inputUri: Uri,
        encoderParams: EncoderParams,
        onProgressCurrentPositionMs: (videoDurationMs: Long, currentPositionMs: Long) -> Unit
    ): File {
        // 一時的にファイルを置いておきたいので
        val tempFolder = context.getExternalFilesDir(null)!!.resolve("temp_folder").apply { mkdir() }
        // 出力先
        val resultFile = context.getExternalFilesDir(null)!!.resolve(encoderParams.fileNameAndExtension)
        // WebM は自前実装
        // himari-webm 参照
        val himariWebm = HimariWebm(tempFolder, resultFile)

        try {
            coroutineScope {

                // 映像の再エンコード
                launch {
                    VideoProcessor.start(
                        context = context,
                        inputUri = inputUri,
                        encoderParams = encoderParams,
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
                        },
                        onProgressCurrentPositionMs = onProgressCurrentPositionMs
                    )
                }

                // 音声の再エンコード
                // Opus にするため
                launch {
                    val (mediaExtractor, mediaFormat) = MediaTool.createMediaExtractor(context, inputUri, MediaTool.Track.AUDIO) ?: return@launch
                    val audioCodecMimeType = mediaFormat.getString(MediaFormat.KEY_MIME)
                    if (audioCodecMimeType == encoderParams.codecContainerType.audioCodec) {
                        // もともと Opus の場合は入れ直すだけにする
                        val samplingRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        himariWebm.setAudioTrack(
                            audioCodec = "A_OPUS",
                            audioSamplingRate = samplingRate.toFloat(),
                            audioChannelCount = channelCount
                        )
                        val byteBuffer = ByteBuffer.allocate(8192)
                        val bufferInfo = MediaCodec.BufferInfo()
                        // データが無くなるまで回す
                        while (true) {
                            // キャンセルチェック
                            yield()
                            // データを読み出す
                            val offset = byteBuffer.arrayOffset()
                            bufferInfo.size = mediaExtractor.readSampleData(byteBuffer, offset)
                            // もう無い場合
                            if (bufferInfo.size < 0) break
                            // 書き込む
                            bufferInfo.presentationTimeUs = mediaExtractor.sampleTime
                            bufferInfo.flags = mediaExtractor.sampleFlags // Lintがキレるけど黙らせる
                            himariWebm.writeAudio(
                                byteArray = byteBuffer.toByteArray(),
                                durationMs = bufferInfo.presentationTimeUs / 1_000,
                                isKeyFrame = true
                            )
                            // 次のデータに進める
                            mediaExtractor.advance()
                        }
                    } else {
                        // 再エンコードが必要
                        // 開始時間がとんでもない時間になる？
                        // 0 スタートになるように調整
                        var startPresentationTime = -1L
                        encodeAudio(
                            tempFolder = tempFolder,
                            context = context,
                            inputUri = inputUri,
                            outputSamplingRate = OPUS_AAC_SAMPLING_RATE,
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
                }
            }

            // 終わり
            himariWebm.stop()
        } finally {
            // 成功時・キャンセル時
            tempFolder.deleteRecursively()
        }

        return resultFile
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
        // 音声トラックがない場合は何もせず return
        val (mediaExtractor, inputAudioFormat) = MediaTool.createMediaExtractor(context, inputUri, MediaTool.Track.AUDIO) ?: return
        mediaExtractor.release()

        val rawFile = tempFolder.resolve(TEMP_AUDIO_RAW_FILE)
        val upsamplingFile = tempFolder.resolve(TEMP_AUDIO_UPSAMPLING_FILE)

        // 実はデコーダーから出てきた MediaCodec#getOutputFormat を信用する必要がある
        // 詳しくは AudioDecoder で
        var decoderOutputMediaFormat: MediaFormat? = null

        try {
            // PCM にする
            AudioEncodeDecodeProcessor.decode(
                input = inputUri.toAkariCoreInputOutputData(context),
                output = rawFile.toAkariCoreInputOutputData(),
                onOutputFormat = { decoderOutputMediaFormat = it }
            )

            // サンプリングレートの変換が必要ならやる
            // デコーダーからの MediaFormat を優先
            val inSamplingRate = (decoderOutputMediaFormat ?: inputAudioFormat).getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val inChannelCount = (decoderOutputMediaFormat ?: inputAudioFormat).getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val fixRawFile = if (inSamplingRate != outputSamplingRate) {
                AudioSonicProcessor.reSamplingBySonic(
                    input = rawFile.toAkariCoreInputOutputData(),
                    output = upsamplingFile.toAkariCoreInputOutputData(),
                    channelCount = inChannelCount,
                    inSamplingRate = inSamplingRate,
                    outSamplingRate = outputSamplingRate
                )
                upsamplingFile
            } else {
                // 修正不要
                rawFile
            }

            // サンプリングレートを変換したので再度エンコードする
            AudioEncodeDecodeProcessor.encode(
                input = fixRawFile.toAkariCoreInputOutputData(),
                muxerInterface = object : AkariEncodeMuxerInterface {
                    override suspend fun onOutputData(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
                        onOutputData(byteBuffer, bufferInfo)
                    }

                    override suspend fun onOutputFormat(mediaFormat: MediaFormat) {
                        onOutputFormat(mediaFormat)
                    }

                    override suspend fun stop() {
                        // do nothing
                    }
                },
                codecName = codec,
                samplingRate = outputSamplingRate,
                channelCount = inChannelCount
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