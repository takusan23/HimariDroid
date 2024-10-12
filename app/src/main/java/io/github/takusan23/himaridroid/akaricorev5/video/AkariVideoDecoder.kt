package io.github.takusan23.himaridroid.akaricorev5.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.view.Surface
import kotlinx.coroutines.yield

// todo ドキュメント書く
class AkariVideoDecoder {

    private var decodeMediaCodec: MediaCodec? = null
    private var mediaExtractor: MediaExtractor? = null

    /** 最後の[seekTo]で取得したフレームの位置 */
    private var latestDecodePositionMs = 0L

    /** 前回のシーク位置 */
    private var prevSeekToMs = -1L

    fun prepare(
        context: Context,
        inputUri: Uri, // TODO AKariInputOutputData を取る
        outputSurface: Surface
    ) {
        val mediaExtractor = MediaExtractor().apply {
            context.contentResolver.openFileDescriptor(inputUri, "r")?.use {
                setDataSource(it.fileDescriptor)
            }
        }
        this.mediaExtractor = mediaExtractor

        val videoTrackIndex = (0 until mediaExtractor.trackCount)
            .map { mediaExtractor.getTrackFormat(it) }
            .withIndex()
            .first { it.value.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            .index

        mediaExtractor.selectTrack(videoTrackIndex)
        val mediaFormat = mediaExtractor.getTrackFormat(videoTrackIndex)
        val codecName = mediaFormat.getString(MediaFormat.KEY_MIME)!!

        decodeMediaCodec = MediaCodec.createDecoderByType(codecName).apply {
            configure(mediaFormat, outputSurface, null, 0)
        }
        decodeMediaCodec?.start()
    }

    suspend fun seekTo(seekToMs: Long): Boolean {
        val isSuccessDecodeFrame = when {
            // 現在の再生位置よりも戻る方向に（巻き戻し）した場合
            seekToMs < prevSeekToMs -> {
                latestDecodePositionMs = prevSeekTo(seekToMs)
                true
            }

            // シーク不要
            // 例えば 30fps なら 33ms 毎なら新しい Bitmap を返す必要があるが、 16ms 毎に要求されたら Bitmap 変化しないので
            // つまり映像のフレームレートよりも高頻度で Bitmap が要求されたら、前回取得した Bitmap がそのまま使い回せる
            seekToMs < latestDecodePositionMs -> {
                // do nothing
                true
            }

            else -> {
                // 巻き戻しでも無く、フレームを取り出す必要がある
                val framePositionMsOrNull = nextSeekTo(seekToMs)
                if (framePositionMsOrNull != null) {
                    latestDecodePositionMs = framePositionMsOrNull
                }
                framePositionMsOrNull != null
            }
        }
        prevSeekToMs = seekToMs
        return isSuccessDecodeFrame
    }

    /** @return 次のフレームがない場合は null。そうじゃない場合は動画フレームの時間 */
    suspend fun nextSeekTo(seekToMs: Long): Long? {
        val decodeMediaCodec = decodeMediaCodec!!
        val mediaExtractor = mediaExtractor!!

        // advance() で false を返したことがある場合、もうデータがない。getSampleTime も -1 になる。
        if (mediaExtractor.sampleTime == -1L) {
            return null
        }

        var isRunning = true
        val bufferInfo = MediaCodec.BufferInfo()
        var returnValue: Long? = null
        while (isRunning) {

            // キャンセル時
            yield()

            // コンテナフォーマットからサンプルを取り出し、デコーダーに渡す
            // シークしないことで、連続してフレームを取得する場合にキーフレームまで戻る必要がなくなり、早くなる
            val inputBufferIndex = decodeMediaCodec.dequeueInputBuffer(TIMEOUT_US)
            if (0 <= inputBufferIndex) {
                // デコーダーへ流す
                val inputBuffer = decodeMediaCodec.getInputBuffer(inputBufferIndex)!!
                val size = mediaExtractor.readSampleData(inputBuffer, 0)
                decodeMediaCodec.queueInputBuffer(inputBufferIndex, 0, size, mediaExtractor.sampleTime, 0)
            }

            // デコーダーから映像を受け取る部分
            var isDecoderOutputAvailable = true
            while (isDecoderOutputAvailable) {

                // キャンセル時
                yield()

                // デコード結果が来ているか
                val outputBufferIndex = decodeMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // リトライが必要
                        isDecoderOutputAvailable = false
                    }

                    0 <= outputBufferIndex -> {
                        // ImageReader ( Surface ) に描画する
                        val doRender = bufferInfo.size != 0
                        decodeMediaCodec.releaseOutputBuffer(outputBufferIndex, doRender)
                        if (doRender) {
                            // 欲しいフレームの時間に到達した場合、ループを抜ける
                            val presentationTimeMs = bufferInfo.presentationTimeUs / 1000
                            if (seekToMs <= presentationTimeMs) {
                                isRunning = false
                                isDecoderOutputAvailable = false
                                returnValue = presentationTimeMs
                            }
                        }
                    }
                }
            }

            // 次に進める。デコーダーにデータを入れた事を確認してから。
            // advance() が false の場合はもうデータがないので、break
            if (0 <= inputBufferIndex) {
                val isEndOfFile = !mediaExtractor.advance()
                if (isEndOfFile) {
                    // return で false（フレームが取得できない旨）を返す
                    returnValue = null
                    break
                }
            }

            // 同様に
            if (0 <= inputBufferIndex) {
                // 欲しいフレームが前回の呼び出しと連続していないときの処理。
                // Android 10 以前はここでシークの判断をします。Android 11 以降は MediaParserKeyFrameTimeDetector でシークの判断をします。
                // 例えば、前回の取得位置よりもさらに数秒以上先にシークした場合、指定位置になるまで待ってたら遅くなるので、数秒先にあるキーフレームまでシークする
                // で、このシークが必要かどうかの判定がこれ。数秒先をリクエストした結果、欲しいフレームが来るよりも先にキーフレームが来てしまった
                // この場合は一気にシーク位置に一番近いキーフレームまで進める
                // ただし、キーフレームが来ているサンプルの時間を比べて、欲しいフレームの位置の方が大きくなっていることを確認してから。
                // デコーダーの時間 presentationTimeUs と、MediaExtractor の sampleTime は同じじゃない？らしく、sampleTime の方がデコーダーの時間より早くなるので注意
                val isKeyFrame = mediaExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                val currentSampleTimeMs = mediaExtractor.sampleTime / 1000
                if (isKeyFrame && currentSampleTimeMs < seekToMs) {
                    mediaExtractor.seekTo(seekToMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    decodeMediaCodec.flush()
                }
            }
        }

        return returnValue
    }

    private suspend fun prevSeekTo(seekToMs: Long): Long {
        val decodeMediaCodec = decodeMediaCodec!!
        val mediaExtractor = mediaExtractor!!

        // シークする。SEEK_TO_PREVIOUS_SYNC なので、シーク位置にキーフレームがない場合はキーフレームがある場所まで戻る
        // エンコードサれたデータを順番通りに送るわけではない（隣接したデータじゃない）ので flush する
        mediaExtractor.seekTo(seekToMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        decodeMediaCodec.flush()

        // デコーダーに渡す
        var isRunning = true
        val bufferInfo = MediaCodec.BufferInfo()
        var returnValue = 0L
        while (isRunning) {
            // キャンセル時
            yield()

            // コンテナフォーマットからサンプルを取り出し、デコーダーに渡す
            // while で繰り返しているのは、シーク位置がキーフレームのため戻った場合に、狙った時間のフレームが表示されるまで繰り返しデコーダーに渡すため
            val inputBufferIndex = decodeMediaCodec.dequeueInputBuffer(TIMEOUT_US)
            if (0 <= inputBufferIndex) {
                val inputBuffer = decodeMediaCodec.getInputBuffer(inputBufferIndex)!!
                // デコーダーへ流す
                val size = mediaExtractor.readSampleData(inputBuffer, 0)
                decodeMediaCodec.queueInputBuffer(inputBufferIndex, 0, size, mediaExtractor.sampleTime, 0)
                // 狙ったフレームになるまでデータを進める
                mediaExtractor.advance()
            }

            // デコーダーから映像を受け取る部分
            var isDecoderOutputAvailable = true
            while (isDecoderOutputAvailable) {
                // デコード結果が来ているか
                val outputBufferIndex = decodeMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // リトライが必要
                        isDecoderOutputAvailable = false
                    }

                    0 <= outputBufferIndex -> {
                        // Surface へ描画
                        val doRender = bufferInfo.size != 0
                        decodeMediaCodec.releaseOutputBuffer(outputBufferIndex, doRender)
                        // 欲しいフレームの時間に到達した場合、ループを抜ける
                        val presentationTimeMs = bufferInfo.presentationTimeUs / 1000
                        if (doRender) {
                            if (seekToMs <= presentationTimeMs) {
                                isRunning = false
                                isDecoderOutputAvailable = false
                                returnValue = presentationTimeMs
                            }
                        }
                    }
                }
            }

            // もうない場合
            if (mediaExtractor.sampleTime == -1L) break
        }

        return returnValue
    }

    fun destroy() {
        decodeMediaCodec?.stop()
        decodeMediaCodec?.release()
        mediaExtractor?.release()
    }

    companion object {
        /** MediaCodec タイムアウト */
        private const val TIMEOUT_US = 0L
    }

}