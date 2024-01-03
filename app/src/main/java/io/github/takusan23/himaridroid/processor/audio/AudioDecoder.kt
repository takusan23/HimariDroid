package io.github.takusan23.himaridroid.processor.audio

import android.media.MediaCodec
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 音声エンコーダー
 * MediaCodecを使いやすくしただけ
 *
 * 生（意味深）の音声（PCM）送られてくるので、 AAC / Opus にエンコードして圧縮する。
 */
class AudioDecoder {
    /** MediaCodec デコーダー */
    private var mediaCodec: MediaCodec? = null

    /**
     * 初期化する
     * デコーダーならパラメーター持ってるはず...
     *
     * @param mediaFormat [android.media.MediaExtractor]から出てきたMediaFormat
     */
    fun prepareDecoder(mediaFormat: MediaFormat) {
        val mimeType = mediaFormat.getString(MediaFormat.KEY_MIME)!!
        mediaCodec = MediaCodec.createDecoderByType(mimeType).apply {
            configure(mediaFormat, null, null, 0)
        }
    }

    /**
     * 音声のデコードをする
     *
     * @param onOutputFormat MediaFormat が確定したときに呼ばれる
     * @param readSampleData ByteArrayを渡すので、音声データを入れて、サイズと再生時間（マイクロ秒）を返してください
     * @param onOutputBufferAvailable デコードされたデータが流れてきます
     */
    suspend fun startAudioDecode(
        onOutputFormat: (MediaFormat) -> Unit,
        readSampleData: (ByteBuffer) -> Pair<Int, Long>,
        onOutputBufferAvailable: (ByteArray) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val bufferInfo = MediaCodec.BufferInfo()
        mediaCodec!!.start()

        try {
            while (isActive) {
                // もし -1 が返ってくれば configure() が間違ってる
                val inputBufferId = mediaCodec!!.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferId >= 0) {
                    // Extractorからデータを読みだす
                    val inputBuffer = mediaCodec!!.getInputBuffer(inputBufferId)!!
                    // 書き込む。書き込んだデータは[onOutputBufferAvailable]で受け取れる
                    val (size, presentationTime) = readSampleData(inputBuffer)
                    if (size > 0) {
                        mediaCodec!!.queueInputBuffer(inputBufferId, 0, size, presentationTime, 0)
                    } else {
                        // データなくなった場合は終了フラグを立てる
                        mediaCodec!!.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        // おわり
                        break
                    }
                }
                // 出力
                val outputBufferId = mediaCodec!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferId >= 0) {
                    // デコード結果をもらう
                    val outputBuffer = mediaCodec!!.getOutputBuffer(outputBufferId)!!
                    val outData = ByteArray(bufferInfo.size)
                    outputBuffer.get(outData)
                    onOutputBufferAvailable(outData)
                    // 返却
                    mediaCodec!!.releaseOutputBuffer(outputBufferId, false)
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // HE-AAC を MediaExtractor で解析すると、サンプリングレートが半分になる現象があった。
                    // 調べると、デコーダーが吐き出す MediaFormat を見る必要があった模様。
                    // ドキュメントに書いとけ
                    // https://stackoverflow.com/questions/33609775/
                    onOutputFormat(mediaCodec!!.outputFormat)
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