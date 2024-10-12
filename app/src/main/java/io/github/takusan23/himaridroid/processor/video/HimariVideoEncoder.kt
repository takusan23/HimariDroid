package io.github.takusan23.himaridroid.processor.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import kotlinx.coroutines.yield
import java.nio.ByteBuffer

class HimariVideoEncoder {

    private var encodeMediaCodec: MediaCodec? = null

    fun prepare(
        bitRate: Int = 1_000_000,
        frameRate: Int = 30,
        outputVideoWidth: Int = 1280,
        outputVideoHeight: Int = 720,
        codecName: String = MediaFormat.MIMETYPE_VIDEO_HEVC
    ) {
        // エンコーダーにセットするMediaFormat
        // コーデックが指定されていればそっちを使う
        val videoMediaFormat = MediaFormat.createVideoFormat(codecName, outputVideoWidth, outputVideoHeight).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }
        encodeMediaCodec = MediaCodec.createEncoderByType(codecName).apply {
            configure(videoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    fun getInputSurface(): Surface = encodeMediaCodec!!.createInputSurface()

    suspend fun start(
        onOutputFormat: suspend (MediaFormat) -> Unit,
        onOutputData: suspend (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val encodeMediaCodec = encodeMediaCodec ?: return

        val bufferInfo = MediaCodec.BufferInfo()
        var videoTrackIndex = -1

        encodeMediaCodec.start()

        try {
            while (true) {
                // yield() で 占有しないよう
                yield()

                // Surface経由でデータを貰って保存する
                val encoderStatus = encodeMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (0 <= encoderStatus) {
                    if (bufferInfo.size > 0) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val encodedData = encodeMediaCodec.getOutputBuffer(encoderStatus)!!
                            onOutputData(encodedData, bufferInfo)
                        }
                    }
                    encodeMediaCodec.releaseOutputBuffer(encoderStatus, false)
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // MediaMuxerへ映像トラックを追加するのはこのタイミングで行う
                    // このタイミングでやると固有のパラメーターがセットされたMediaFormatが手に入る(csd-0 とか)
                    // 映像がぶっ壊れている場合（緑で塗りつぶされてるとか）は多分このあたりが怪しい
                    val newFormat = encodeMediaCodec.outputFormat
                    onOutputFormat(newFormat)
                }
                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue
                }
            }
        } finally {
            // エンコーダー終了
            encodeMediaCodec.stop()
            encodeMediaCodec.release()
        }
    }

    companion object {
        /** タイムアウト */
        private const val TIMEOUT_US = 0L
    }
}