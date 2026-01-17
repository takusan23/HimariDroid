package io.github.takusan23.himaridroid.processor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.contentValuesOf
import io.github.takusan23.akaricore.common.AkariCoreInputOutput
import io.github.takusan23.himaridroid.data.EncoderParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ファイル操作系
 * Koin が DI してくれるので、インスタンスはそっちから取得できます。
 *
 * @param context Koin により渡されます
 */
class MediaTool(private val context: Context) {

    enum class Track(val mimeTypePrefix: String) {
        VIDEO("video/"),
        AUDIO("audio/")
    }

    /**
     * [MediaExtractor]を作る
     *
     * @return [MediaExtractor]と選択したトラックの[MediaFormat]。選択したトラックがない場合は null
     */
    fun createMediaExtractor(
        uri: Uri,
        track: Track
    ): Pair<MediaExtractor, MediaFormat>? {
        val mediaExtractor = MediaExtractor().apply {
            // read で FileDescriptor を開く
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                setDataSource(it.fileDescriptor)
            }
        }

        // トラックがない場合は return
        val trackPairOrNull = mediaExtractor.getTrackMediaFormat(track)
        if (trackPairOrNull == null) {
            mediaExtractor.release()
            return null
        }

        val (index, mediaFormat) = trackPairOrNull
        mediaExtractor.selectTrack(index)
        // Extractor / MediaFormat を返す
        return mediaExtractor to mediaFormat
    }

    /**
     * [MediaMetadataRetriever]を作る。release() は呼び出し側で！
     *
     * @param uri 読み込むファイルの Uri
     */
    fun createMediaMetadataRetriever(uri: Uri): MediaMetadataRetriever {
        return MediaMetadataRetriever().apply {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { setDataSource(it.fileDescriptor) }
        }
    }

    /** 端末の動画フォルダに保存する */
    suspend fun saveToVideoFolder(
        file: File,
        containerType: EncoderParams.ContainerType
    ) = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = when (containerType) {
            EncoderParams.ContainerType.MPEG_4 -> "video/mp4"
            EncoderParams.ContainerType.WEBM -> "video/webm"
        }
        val contentValues = contentValuesOf(
            MediaStore.MediaColumns.DISPLAY_NAME to file.name,
            MediaStore.MediaColumns.RELATIVE_PATH to "${Environment.DIRECTORY_MOVIES}/HimariDroid",
            MediaStore.MediaColumns.MIME_TYPE to mimeType
        )
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext
        // コピーする
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    /** Uri のファイル名を取得する */
    suspend fun getFileName(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)!!.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
        }
    }

    /** [Context.getExternalFilesDir] の中にフォルダを作る TODO media...tool... の中にあるべきではないかも */
    fun createFolder(name: String): File {
        return context.getExternalFilesDir(null)!!.resolve(name).apply { mkdir() }
    }

    /** [Context.getExternalFilesDir] の中にファイルを作る TODO media...tool... の中にあるべきではないかも */
    fun createFile(name: String): File {
        return context.getExternalFilesDir(null)!!.resolve(name).apply { createNewFile() }
    }

    /**
     * [toAkariCoreInputOutputData]を呼び出す
     *
     * with(MediaTool) { uri.toAkariCoreInputOutputData() }
     */
    fun Uri.toAkariCoreInputOutputData() = AkariCoreInputOutput.AndroidUri(context, this)

    /**
     * [MediaExtractor]からトラック（映像 or 音声）の番号（インデックス）と[MediaFormat]を[Pair]で返す。
     *
     * @return トラック番号と[MediaFormat]の[Pair]。トラックがない場合は null。例えば音無しの動画で音声トラックを指定した場合は null
     */
    private fun MediaExtractor.getTrackMediaFormat(track: Track): Pair<Int, MediaFormat>? {
        // トラックを選択する（映像・音声どっち？）
        val trackIndex = (0 until this.trackCount)
            .map { this.getTrackFormat(it) }
            .indexOfFirst { it.getString(MediaFormat.KEY_MIME)?.startsWith(track.mimeTypePrefix) == true }

        if (trackIndex == -1) return null

        val mediaFormat = this.getTrackFormat(trackIndex)
        // 位置と MediaFormat
        return trackIndex to mediaFormat
    }

}