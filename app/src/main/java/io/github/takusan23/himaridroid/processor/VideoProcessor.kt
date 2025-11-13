package io.github.takusan23.himaridroid.processor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.Uri
import io.github.takusan23.akaricore.common.toAkariCoreInputOutputData
import io.github.takusan23.akaricore.graphics.AkariGraphicsProcessor
import io.github.takusan23.akaricore.graphics.AkariGraphicsSurfaceTexture
import io.github.takusan23.akaricore.graphics.data.AkariGraphicsProcessorColorSpaceType
import io.github.takusan23.akaricore.graphics.data.AkariGraphicsProcessorRenderingPrepareData
import io.github.takusan23.akaricore.graphics.mediacodec.AkariVideoDecoder
import io.github.takusan23.akaricore.graphics.mediacodec.AkariVideoEncoder
import io.github.takusan23.akaricore.muxer.AkariEncodeMuxerInterface
import io.github.takusan23.himaridroid.data.EncoderParams
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/** akaricore とかいう MediaCodec を代わりに叩くライブラリを使い再エンコードを行う */
object VideoProcessor {

    /** 再エンコードする */
    suspend fun start(
        context: Context,
        inputUri: Uri,
        encoderParams: EncoderParams,
        onOutputFormat: suspend (MediaFormat) -> Unit,
        onOutputData: suspend (ByteBuffer, MediaCodec.BufferInfo) -> Unit,
        onProgressCurrentPositionMs: (videoDurationMs: Long, currentPositionMs: Long) -> Unit
    ) {

        // エンコーダー。中身は MediaCodec
        val akariVideoEncoder = AkariVideoEncoder().apply {
            prepare(
                muxerInterface = object : AkariEncodeMuxerInterface {
                    override suspend fun onOutputFormat(mediaFormat: MediaFormat) {
                        onOutputFormat(mediaFormat)
                    }

                    override suspend fun onOutputData(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
                        onOutputData(byteBuffer, bufferInfo)
                    }

                    override suspend fun stop() {
                        // do nothing
                    }
                },
                outputVideoWidth = encoderParams.videoWidth,
                outputVideoHeight = encoderParams.videoHeight,
                frameRate = encoderParams.frameRate,
                bitRate = encoderParams.bitRate,
                keyframeInterval = 1,
                codecName = when {
                    // ドルビービジョンはよくわからないので HEVC で、ガンマカーブは HLG か PQ を利用する
                    encoderParams.codecContainerType == EncoderParams.CodecContainerType.DOLBY_VISION -> MediaFormat.MIMETYPE_VIDEO_HEVC

                    // HDR 動画を指定しているが、コーデックが対応していない場合も HEVC
                    encoderParams.tenBitHdrOptionOrNull?.mode == EncoderParams.TenBitHdrOption.TenBitHdrMode.KEEP && !encoderParams.codecContainerType.isAvailableHdr -> MediaFormat.MIMETYPE_VIDEO_HEVC

                    // その他は大丈夫なはず
                    else -> encoderParams.codecContainerType.videoCodec
                },
                // 10-bit HDR を維持する場合。SDR 動画と SDR に変換する場合は null
                tenBitHdrParametersOrNullSdr = if (encoderParams.tenBitHdrOptionOrNull?.mode == EncoderParams.TenBitHdrOption.TenBitHdrMode.KEEP) {
                    val (colorStandard, colorTransfer) = encoderParams.tenBitHdrOptionOrNull.tenBitHdrInfo

                    val colorProfile = when (encoderParams.codecContainerType) {
                        EncoderParams.CodecContainerType.AVC_AAC_MPEG4,
                        EncoderParams.CodecContainerType.VP9_OPUS_WEBM,
                        EncoderParams.CodecContainerType.DOLBY_VISION -> null // 来ない

                        // HEVC
                        EncoderParams.CodecContainerType.HEVC_AAC_MPEG4 -> when (colorTransfer) {
                            MediaFormat.COLOR_TRANSFER_HLG -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                            MediaFormat.COLOR_TRANSFER_ST2084 -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                            else -> null // ここには来ない
                        }

                        // AV1
                        EncoderParams.CodecContainerType.AV1_AAC_MPEG4,
                        EncoderParams.CodecContainerType.AV1_OPUS_WEBM -> when (colorTransfer) {
                            MediaFormat.COLOR_TRANSFER_HLG -> MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
                            MediaFormat.COLOR_TRANSFER_ST2084 -> MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10
                            else -> null // ここには来ない
                        }
                    }

                    AkariVideoEncoder.TenBitHdrParameters(
                        colorStandard = colorStandard,
                        colorTransfer = colorTransfer,
                        codecProfile = colorProfile!!
                    )
                } else null
            )
        }

        // OpenGL ES で描画する基盤
        // デコーダーの出力 Surface にこれを指定して、エンコーダーに OpenGL ES で描画した映像データが Surface 経由で行くようにする
        // 本当は OpenGL ES なんて使わずともデコーダーでデコードした映像フレームをエンコーダーに渡すだけでいいはずなのだが、OpenGL ES を経由しておくのが安牌？
        val akariGraphicsProcessor = AkariGraphicsProcessor(
            renderingPrepareData = AkariGraphicsProcessorRenderingPrepareData.SurfaceRendering(
                surface = akariVideoEncoder.getInputSurface(),
                width = encoderParams.videoWidth,
                height = encoderParams.videoHeight
            ),
            colorSpaceType = if (encoderParams.tenBitHdrOptionOrNull?.mode == EncoderParams.TenBitHdrOption.TenBitHdrMode.KEEP) {
                val (_, colorTransfer) = encoderParams.tenBitHdrOptionOrNull.tenBitHdrInfo
                when (colorTransfer) {
                    MediaFormat.COLOR_TRANSFER_HLG -> AkariGraphicsProcessorColorSpaceType.TEN_BIT_HDR_BT2020_HLG
                    MediaFormat.COLOR_TRANSFER_ST2084 -> AkariGraphicsProcessorColorSpaceType.TEN_BIT_HDR_BT2020_PQ
                    else -> AkariGraphicsProcessorColorSpaceType.SDR_BT709 // ここには来ないはず
                }
            } else {
                AkariGraphicsProcessorColorSpaceType.SDR_BT709
            }
        ).apply { prepare() }

        // デコーダー
        // デコードした映像の向き先は OpenGL ES のテクスチャ（SurfaceTexture）
        val akariGraphicsSurfaceTexture = akariGraphicsProcessor.genTextureId { texId -> AkariGraphicsSurfaceTexture(texId) }
        val akariVideoDecoder = AkariVideoDecoder().apply {
            // トーンマッピング機能に対応している機種であれば利用。非対応なら白っぽくなるかも。
            prepare(
                input = inputUri.toAkariCoreInputOutputData(context),
                outputSurface = akariGraphicsSurfaceTexture.surface,
                isSdrToneMapping = encoderParams.tenBitHdrOptionOrNull?.mode == EncoderParams.TenBitHdrOption.TenBitHdrMode.TO_SDR
            )
        }

        coroutineScope {

            // エンコーダー開始
            val encoderJob = launch { akariVideoEncoder.start() }

            // 描画も開始
            val graphicsJob = launch {
                try {
                    val loopContinueData = AkariGraphicsProcessor.LoopContinueData(isRequestNextFrame = true, currentFrameNanoSeconds = 0)

                    // 1フレーム分のミリ秒と再生位置
                    val oneFrameMs = 1_000 / encoderParams.frameRate
                    var currentPositionMs = 0L

                    akariGraphicsProcessor.drawLoop {
                        // シークして動画フレームを描画する
                        // 既存のフレームを使い回す場合（つまり 30fps の動画で 60fps 指定で再エンコードを要求されている時）は nullOrTextureUpdateTimeoutMs を null にする
                        val seekResult = akariVideoDecoder.seekTo(currentPositionMs)
                        drawSurfaceTexture(
                            akariSurfaceTexture = akariGraphicsSurfaceTexture,
                            nullOrTextureUpdateTimeoutMs = if (seekResult.isNewFrame) 500 else null
                        )
                        onProgressCurrentPositionMs(akariVideoDecoder.videoDurationMs, currentPositionMs)

                        // 次フレームがあるかとループ続行か
                        loopContinueData.currentFrameNanoSeconds = currentPositionMs * AkariGraphicsProcessor.LoopContinueData.MILLI_SECONDS_TO_NANO_SECONDS
                        loopContinueData.isRequestNextFrame = seekResult.isSuccessful

                        // 動画時間を進める
                        currentPositionMs += oneFrameMs

                        loopContinueData
                    }
                } finally {
                    akariGraphicsProcessor.destroy()
                    akariVideoDecoder.destroy()
                    akariGraphicsSurfaceTexture.destroy()
                }
            }

            // 描画が終わるまで待ってその後エンコーダーも止める
            graphicsJob.join()
            encoderJob.cancelAndJoin()
        }
    }

}