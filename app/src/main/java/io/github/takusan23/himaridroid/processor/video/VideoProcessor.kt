package io.github.takusan23.himaridroid.processor.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import io.github.takusan23.himaridroid.akaricorev5.AkariGraphicsProcessor
import io.github.takusan23.himaridroid.akaricorev5.gl.AkariGraphicsSurfaceTexture
import io.github.takusan23.himaridroid.akaricorev5.video.AkariVideoDecoder
import io.github.takusan23.himaridroid.data.EncoderParams
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

object VideoProcessor {

    /** 再エンコードする */
    suspend fun start(
        context: Context,
        inputUri: Uri,
        encoderParams: EncoderParams,
        onProgressCurrentPositionMs: (Long) -> Unit,
        onOutputFormat: suspend (MediaFormat) -> Unit,
        onOutputData: suspend (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) = coroutineScope {

        // エンコーダーをつくる
        // MediaMuxer / HimariWebM に対応するため、onOutputFormat / onOutputData を取る
        val akariVideoEncoder = HimariVideoEncoder().apply {
            prepare(
                bitRate = encoderParams.bitRate,
                frameRate = encoderParams.frameRate,
                outputVideoWidth = encoderParams.videoWidth,
                outputVideoHeight = encoderParams.videoHeight,
                codecName = encoderParams.codecContainerType.videoCodec
            )
        }

        // OpenGL ES の用意をする
        val akariGraphicsProcessor = AkariGraphicsProcessor(
            outputSurface = akariVideoEncoder.getInputSurface(),
            isEnableTenBitHdr = true,
            width = encoderParams.videoWidth,
            height = encoderParams.videoHeight
        ).apply { prepare() }

        // 動画フレームを OpenGL ES のテクスチャとして使う SurfaceTexture を、Processor で使う
        val akariSurfaceTexture = akariGraphicsProcessor.genTextureId { texId -> AkariGraphicsSurfaceTexture(texId) }
        // 動画デコーダー
        val akariVideoDecoder = AkariVideoDecoder().apply {
            prepare(
                context = context,
                inputUri = inputUri,
                outputSurface = akariSurfaceTexture.surface
            )
        }

        // エンコーダーを起動する
        val encoderJob = launch {
            akariVideoEncoder.start(onOutputFormat, onOutputData)
        }

        // 描画ループを回す
        val graphicsJob = launch {
            val oneFrameMs = 1_000 / encoderParams.frameRate
            var currentPositionMs = 0L
            akariGraphicsProcessor.drawLoop {
                // シークして動画フレームを描画する
                val isSuccessDecodeFrame = akariVideoDecoder.seekTo(currentPositionMs)
                drawSurfaceTexture(akariSurfaceTexture)
                onProgressCurrentPositionMs(currentPositionMs)
                // 返り値
                val info = AkariGraphicsProcessor.DrawInfo(
                    isRunning = isSuccessDecodeFrame,
                    currentFrameMs = currentPositionMs
                )
                // 動画時間を進める
                currentPositionMs += oneFrameMs
                info
            }
        }

        // graphicsJob が終わったらキャンセルする
        try {
            graphicsJob.join()
            encoderJob.cancelAndJoin()
        } finally {
            akariGraphicsProcessor.destroy()
            akariVideoDecoder.destroy()
            akariSurfaceTexture.destroy()
        }
    }

}