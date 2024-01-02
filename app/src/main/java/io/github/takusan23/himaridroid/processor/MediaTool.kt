package io.github.takusan23.himaridroid.processor

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.contentValuesOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object MediaTool {

    private const val BUFFER_SIZE = 1024 * 4096

    enum class Track(val mimeTypePrefix: String) {
        VIDEO("video/"),
        AUDIO("audio/")
    }

    /**
     * [MediaExtractor]を作る
     *
     * @return [MediaExtractor]と選択したトラックの[MediaFormat]
     */
    fun createMediaExtractor(
        context: Context,
        uri: Uri,
        track: Track
    ): Pair<MediaExtractor, MediaFormat> {
        val mediaExtractor = MediaExtractor().apply {
            // read で FileDescriptor を開く
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                setDataSource(it.fileDescriptor)
            }
        }
        val (index, mediaFormat) = mediaExtractor.getTrackMediaFormat(track)
        mediaExtractor.selectTrack(index)
        // Extractor / MediaFormat を返す
        return mediaExtractor to mediaFormat
    }

    /**
     * [MediaExtractor]を作る
     *
     * @return [MediaExtractor]と選択したトラックの[MediaFormat]
     */
    fun createMediaExtractor(
        file: File,
        track: Track
    ): Pair<MediaExtractor, MediaFormat> {
        val mediaExtractor = MediaExtractor().apply {
            setDataSource(file.path)
        }
        val (index, mediaFormat) = mediaExtractor.getTrackMediaFormat(track)
        mediaExtractor.selectTrack(index)
        // Extractor / MediaFormat を返す
        return mediaExtractor to mediaFormat
    }

    /** 音声トラックと映像トラックを一つのファイルにする。 */
    @SuppressLint("WrongConstant")
    suspend fun mixAvTrack(
        audioPair: Pair<MediaExtractor, MediaFormat>,
        videoPair: Pair<MediaExtractor, MediaFormat>,
        resultFile: File
    ) = withContext(Dispatchers.IO) {
        // 各ファイルから MediaExtractor を作る
        val (audioMediaExtractor, audioFormat) = videoPair
        val (videoMediaExtractor, videoFormat) = audioPair

        // 新しくコンテナファイルを作って保存する
        // 音声と映像を追加
        val mediaMuxer = MediaMuxer(resultFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val audioTrackIndex = mediaMuxer.addTrack(audioFormat)
        val videoTrackIndex = mediaMuxer.addTrack(videoFormat)
        // MediaMuxerスタート。スタート後は addTrack が呼べない
        mediaMuxer.start()

        // 音声をコンテナに追加する
        audioMediaExtractor.apply {
            val byteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()
            // データが無くなるまで回す
            while (isActive) {
                // データを読み出す
                val offset = byteBuffer.arrayOffset()
                bufferInfo.size = readSampleData(byteBuffer, offset)
                // もう無い場合
                if (bufferInfo.size < 0) break
                // 書き込む
                bufferInfo.presentationTimeUs = sampleTime
                bufferInfo.flags = sampleFlags // Lintがキレるけど黙らせる
                mediaMuxer.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo)
                // 次のデータに進める
                advance()
            }
            // あとしまつ
            release()
        }

        // 映像をコンテナに追加する
        videoMediaExtractor.apply {
            val byteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()
            // データが無くなるまで回す
            while (isActive) {
                // データを読み出す
                val offset = byteBuffer.arrayOffset()
                bufferInfo.size = readSampleData(byteBuffer, offset)
                // もう無い場合
                if (bufferInfo.size < 0) break
                // 書き込む
                bufferInfo.presentationTimeUs = sampleTime
                bufferInfo.flags = sampleFlags // Lintがキレるけど黙らせる
                mediaMuxer.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
                // 次のデータに進める
                advance()
            }
            // あとしまつ
            release()
        }

        // 終わり
        mediaMuxer.stop()
        mediaMuxer.release()
    }

    /** 端末の動画フォルダに保存する */
    suspend fun saveToVideoFolder(
        context: Context,
        file: File
    ) = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val contentValues = contentValuesOf(
            MediaStore.MediaColumns.DISPLAY_NAME to file.name,
            MediaStore.MediaColumns.RELATIVE_PATH to "${Environment.DIRECTORY_MOVIES}/HimariDroid",
            MediaStore.MediaColumns.MIME_TYPE to "video/mp4"
        )
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext
        // コピーする
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    /** 単位の調整 */
    fun normalizeByte(byte: Int): String = when {
        byte >= 1_000_000 -> "${byte / 1_000_000} M"
        byte >= 1_000 -> "${byte / 1_000} K"
        else -> byte.toString() // 特に無ければ MB で
    }

    private fun MediaExtractor.getTrackMediaFormat(track: Track): Pair<Int, MediaFormat> {
        // トラックを選択する（映像・音声どっち？）
        val trackIndex = (0 until this.trackCount)
            .map { this.getTrackFormat(it) }
            .indexOfFirst { it.getString(MediaFormat.KEY_MIME)?.startsWith(track.mimeTypePrefix) == true }
        val mediaFormat = this.getTrackFormat(trackIndex)
        // 位置と MediaFormat
        return trackIndex to mediaFormat
    }

}