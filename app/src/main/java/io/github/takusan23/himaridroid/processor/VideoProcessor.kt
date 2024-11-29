package io.github.takusan23.himaridroid.processor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import io.github.takusan23.akaricore.common.toAkariCoreInputOutputData
import io.github.takusan23.akaricore.graphics.AkariGraphicsProcessor
import io.github.takusan23.akaricore.graphics.AkariGraphicsSurfaceTexture
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
                // TODO 10Bit HDR の場合は HEVC 固定にしている
                codecName = if (encoderParams.tenBitHdrOptionOrNull?.mode == EncoderParams.TenBitHdrOption.TenBitHdrMode.KEEP) {
                    MediaFormat.MIMETYPE_VIDEO_HEVC
                } else {
                    encoderParams.codecContainerType.videoCodec
                },
                // 10Bit HDR を維持する場合。SDR 動画と SDR に変換する場合は null
                tenBitHdrParametersOrNullSdr = if (encoderParams.tenBitHdrOptionOrNull?.mode == EncoderParams.TenBitHdrOption.TenBitHdrMode.KEEP) {
                    AkariVideoEncoder.TenBitHdrParameters(
                        colorStandard = encoderParams.tenBitHdrOptionOrNull.tenBitHdrInfo.colorStandard,
                        colorTransfer = encoderParams.tenBitHdrOptionOrNull.tenBitHdrInfo.colorTransfer
                    )
                } else null
            )
        }

        // OpenGL ES で描画する基盤
        // デコーダーの出力 Surface にこれを指定して、エンコーダーに OpenGL ES で描画した映像データが Surface 経由で行くようにする
        // 本当は OpenGL ES なんて使わずともデコーダーでデコードした映像フレームをエンコーダーに渡すだけでいいはずなのだが、OpenGL ES を経由しておくのが安牌？
        val akariGraphicsProcessor = AkariGraphicsProcessor(
            outputSurface = akariVideoEncoder.getInputSurface(),
            width = encoderParams.videoWidth,
            height = encoderParams.videoHeight,
            isEnableTenBitHdr = false // TODO 10Bit HDR 対応
        ).apply { prepare() }

        // デコーダー
        // デコードした映像の向き先は OpenGL ES のテクスチャ（SurfaceTexture）
        val akariGraphicsSurfaceTexture = akariGraphicsProcessor.genTextureId { texId -> AkariGraphicsSurfaceTexture(texId) }
        val akariVideoDecoder = AkariVideoDecoder().apply {
            prepare(
                input = inputUri.toAkariCoreInputOutputData(context),
                outputSurface = akariGraphicsSurfaceTexture.surface
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
                        val isSuccessDecodeFrame = akariVideoDecoder.seekTo(currentPositionMs)
                        drawSurfaceTexture(akariGraphicsSurfaceTexture)
                        onProgressCurrentPositionMs(akariVideoDecoder.videoDurationMs, currentPositionMs)

                        // 次フレームがあるかとループ続行か
                        loopContinueData.currentFrameNanoSeconds = currentPositionMs * AkariGraphicsProcessor.LoopContinueData.MILLI_SECONDS_TO_NANO_SECONDS
                        loopContinueData.isRequestNextFrame = isSuccessDecodeFrame

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