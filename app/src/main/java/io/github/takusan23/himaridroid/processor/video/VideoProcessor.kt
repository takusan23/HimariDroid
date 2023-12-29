package io.github.takusan23.himaridroid.processor.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

object VideoProcessor {

    /** MediaCodec タイムアウト */
    private const val TIMEOUT_US = 10_000L

    /** 再エンコードする */
    suspend fun start(
        mediaExtractor: MediaExtractor,
        inputMediaFormat: MediaFormat,
        codec: String,
        bitRate: Int,
        keyframeInterval: Int,
        onOutputFormat: (MediaFormat) -> Unit,
        onOutputData: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) = withContext(Dispatchers.Default) {

        // 解析結果から各パラメータを取り出す
        // 動画の幅、高さは16の倍数である必要があります
        val width = inputMediaFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = inputMediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val frameRate = runCatching { inputMediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrNull() ?: 30

        // エンコーダーにセットするMediaFormat
        val videoMediaFormat = MediaFormat.createVideoFormat(codec, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeInterval)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }

        // エンコード用 MediaCodec
        val encodeMediaCodec = MediaCodec.createEncoderByType(codec).apply {
            configure(videoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        // エンコーダーの Surface を取得
        // デコーダーの出力 Surface にこれを指定して、エンコーダーに映像データが Surface 経由で行くようにする
        // なんだけど、直接 Surface を渡すだけではなくなんか OpenGL を利用しないと正しく描画できないみたい
        val codecInputSurface = InputSurface(encodeMediaCodec.createInputSurface(), TextureRenderer())
        codecInputSurface.makeCurrent()
        codecInputSurface.createRender()

        // デコード用 MediaCodec
        val decodeMediaCodec = MediaCodec.createDecoderByType(inputMediaFormat.getString(MediaFormat.KEY_MIME)!!).apply {
            // デコード時は MediaExtractor の MediaFormat で良さそう
            configure(inputMediaFormat, codecInputSurface.drawSurface, null, 0)
        }

        // 処理を始める
        encodeMediaCodec.start()
        decodeMediaCodec.start()
        val bufferInfo = MediaCodec.BufferInfo()
        var isOutputEol = false
        var isInputEol = false

        try {
            while (!isOutputEol) {

                // コルーチンキャンセル時は強制終了
                if (!isActive) break

                // デコーダーに渡す部分
                if (!isInputEol) {
                    val inputBufferId = decodeMediaCodec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer = decodeMediaCodec.getInputBuffer(inputBufferId)!!
                        val size = mediaExtractor.readSampleData(inputBuffer, 0)
                        if (size > 0) {
                            // デコーダーへ流す
                            decodeMediaCodec.queueInputBuffer(inputBufferId, 0, size, mediaExtractor.sampleTime, 0)
                            mediaExtractor.advance()
                        } else {
                            // もう無い
                            decodeMediaCodec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            // 終了
                            isInputEol = true
                        }
                    }
                }

                // エンコーダーから映像を受け取る部分
                // 二重 while になっているのは、デコーダーに渡したデータが一回の処理では全て受け取れないので、何回か繰り返す
                var decoderOutputAvailable = true
                while (decoderOutputAvailable) {
                    // Surface経由でデータを貰って保存する
                    val outputBufferId = encodeMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outputBufferId >= 0) {
                        val encodedData = encodeMediaCodec.getOutputBuffer(outputBufferId)!!
                        if (bufferInfo.size > 1) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                // MediaMuxer へ addTrack した後
                                onOutputData(encodedData, bufferInfo)
                            }
                        }
                        isOutputEol = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encodeMediaCodec.releaseOutputBuffer(outputBufferId, false)
                    } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // MediaMuxer へ映像トラックを追加するのはこのタイミングで行う
                        // このタイミングでやると固有のパラメーターがセットされた MediaFormat が手に入る(csd-0 とか)
                        // 映像がぶっ壊れている場合（緑で塗りつぶされてるとか）は多分このあたりが怪しい
                        val newFormat = encodeMediaCodec.outputFormat
                        onOutputFormat(newFormat)
                    }
                    if (outputBufferId != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        continue
                    }

                    // Surfaceへレンダリングする。そしてOpenGLでゴニョゴニョする
                    val inputBufferId = decodeMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (inputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        decoderOutputAvailable = false
                    } else if (inputBufferId >= 0) {
                        val doRender = bufferInfo.size != 0
                        decodeMediaCodec.releaseOutputBuffer(inputBufferId, doRender)
                        // OpenGL を経由しないとエンコーダーに映像が渡らないことがあった
                        if (doRender) {
                            var errorWait = false
                            try {
                                codecInputSurface.awaitNewImage()
                            } catch (e: Exception) {
                                errorWait = true
                            }
                            if (!errorWait) {
                                codecInputSurface.drawImage()
                                codecInputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                                codecInputSurface.swapBuffers()
                            }
                        }
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            decoderOutputAvailable = false
                            encodeMediaCodec.signalEndOfInputStream()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // リソース開放
            encodeMediaCodec.release()
            decodeMediaCodec.release()
            codecInputSurface.release()
            mediaExtractor.release()
        }
    }

}