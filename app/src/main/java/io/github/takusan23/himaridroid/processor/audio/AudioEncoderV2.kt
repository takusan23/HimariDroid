package io.github.takusan23.himaridroid.processor.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import io.github.takusan23.akaricore.audio.AkariCoreAudioProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

// TODO akaricore:5.0.0-alpha02 でこれを入れる
class AudioEncoderV2 {

    /** MediaCodec エンコーダー */
    private var mediaCodec: MediaCodec? = null

    /**
     * エンコーダーを初期化する
     *
     * @param codec コーデック。[MediaFormat.MIMETYPE_AUDIO_AAC]など
     * @param sampleRate サンプリングレート
     * @param channelCount チャンネル数
     * @param bitRate ビットレート
     * @param
     */
    fun prepareEncoder(
        codec: String = MediaFormat.MIMETYPE_AUDIO_AAC,
        sampleRate: Int = AkariCoreAudioProperties.SAMPLING_RATE,
        channelCount: Int = 2,
        bitRate: Int = 192_000,
    ) {
        val audioEncodeFormat = MediaFormat.createAudioFormat(codec, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        }
        // エンコーダー用意
        mediaCodec = MediaCodec.createEncoderByType(codec).apply {
            configure(audioEncodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    /**
     * エンコーダーを開始する。同期モードを使うのでコルーチンを使います（スレッドでも良いけど）
     *
     * @param onRecordInput ByteArrayを渡すので、音声データを入れて、サイズを返してください
     * @param onOutputBufferAvailable エンコードされたデータが流れてきます
     * @param onOutputFormatAvailable エンコード後のMediaFormatが入手できる
     */
    suspend fun startAudioEncode(
        onRecordInput: suspend (ByteArray) -> Int,
        onOutputBufferAvailable: suspend (ByteBuffer, MediaCodec.BufferInfo) -> Unit,
        onOutputFormatAvailable: suspend (MediaFormat) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val bufferInfo = MediaCodec.BufferInfo()
        // TODO エンコードも最後途切れている可能性がある。fun test_モノラル音声をステレオ音声にできる() 参照。MediaCodec 非同期にしたい。
        var isRunning = isActive
        mediaCodec!!.start()

        try {
            while (isRunning) {
                // もし -1 が返ってくれば configure() が間違ってる
                val inputBufferId = mediaCodec!!.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferId >= 0) {
                    // AudioRecodeのデータをこの中に入れる
                    val inputBuffer = mediaCodec!!.getInputBuffer(inputBufferId)!!
                    val capacity = inputBuffer.capacity()
                    // サイズに合わせて作成
                    val byteArray = ByteArray(capacity)
                    // byteArrayへデータを入れてもらう
                    val readByteSize = onRecordInput(byteArray)
                    if (readByteSize > 0) {
                        // 書き込む。書き込んだデータは[onOutputBufferAvailable]で受け取れる
                        inputBuffer.put(byteArray, 0, readByteSize)
                        mediaCodec!!.queueInputBuffer(inputBufferId, 0, readByteSize, System.nanoTime() / 1000, 0)
                    } else {
                        // もうない！
                        isRunning = false
                    }
                }
                // 出力
                val outputBufferId = mediaCodec!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferId >= 0) {
                    val outputBuffer = mediaCodec!!.getOutputBuffer(outputBufferId)!!
                    // size > 0 とか BUFFER_FLAG_CODEC_CONFIG を消すと、MediaMuxer 周りで時間がおかしくなる
                    if (bufferInfo.size > 0) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            // ファイルに書き込む...
                            onOutputBufferAvailable(outputBuffer, bufferInfo)
                        }
                    }
                    // 返却
                    mediaCodec!!.releaseOutputBuffer(outputBufferId, false)
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // MediaFormat、MediaMuxerに入れるときに使うやつ
                    // たぶんこっちのほうが先に呼ばれる
                    onOutputFormatAvailable(mediaCodec!!.outputFormat)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // リソースを開放する
            try {
                mediaCodec?.stop()
                mediaCodec?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        /** MediaCodec タイムアウト */
        private const val TIMEOUT_US = 10_000L
    }
}