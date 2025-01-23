package io.github.takusan23.himaridroid.data

import android.media.MediaFormat
import io.github.takusan23.himaridroid.data.EncoderParams.TenBitHdrOption
import io.github.takusan23.himaridroid.data.EncoderParams.TenBitHdrOption.TenBitHdrMode

/**
 * エンコーダーに渡す設定
 *
 * @param tenBitHdrOptionOrNull 10-bit HDR 動画の場合は[TenBitHdrOption]。SDR 動画の場合は null
 */
data class EncoderParams(
    val fileNameWithoutExtension: String,
    val videoWidth: Int,
    val videoHeight: Int,
    val bitRate: Int,
    val frameRate: Int,
    val codecContainerType: CodecContainerType,
    val tenBitHdrOptionOrNull: TenBitHdrOption?
) {

    /** 拡張子付きのファイル名を返す */
    val fileNameAndExtension: String
        get() = "${fileNameWithoutExtension}.${codecContainerType.containerType.extension}"

    /** コーデックとコンテナの種類 */
    enum class CodecContainerType(val videoCodec: String, val audioCodec: String, val containerType: ContainerType) {
        /** AVC / AAC / mp4 */
        AVC_AAC_MPEG4(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_AUDIO_AAC, ContainerType.MPEG_4),

        /** HEVC / AAC / mp4 */
        HEVC_AAC_MPEG4(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_AUDIO_AAC, ContainerType.MPEG_4),

        /** AV1 / AAC / mp4 */
        AV1_AAC_MPEG4(MediaFormat.MIMETYPE_VIDEO_AV1, MediaFormat.MIMETYPE_AUDIO_AAC, ContainerType.MPEG_4),

        /** VP9 / Opus / WebM */
        VP9_OPUS_WEBM(MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_AUDIO_OPUS, ContainerType.WEBM),

        /**
         * AV1 / Opus / WebM
         * WebM コンテナに AV1 を入れるのは仕様にないみたいなのですが、ブラウザ系がやってるので実質対応みたいになっているらしい？
         */
        AV1_OPUS_WEBM(MediaFormat.MIMETYPE_VIDEO_AV1, MediaFormat.MIMETYPE_AUDIO_OPUS, ContainerType.WEBM);
    }

    /** コンテナ */
    enum class ContainerType(val extension: String) {
        MPEG_4("mp4"),
        WEBM("webm");
    }

    /**
     * 10-bit HDR 動画の情報
     *
     * @param mode [TenBitHdrMode]
     * @param tenBitHdrInfo 色域とガンマカーブ
     */
    data class TenBitHdrOption(
        val mode: TenBitHdrMode,
        val tenBitHdrInfo: VideoFormat.TenBitHdrInfo
    ) {

        enum class TenBitHdrMode {
            /** 10-bit HDR はそのまま HDR 動画として扱う。変換後も 10-bit HDR になります */
            KEEP,

            /** HDR から SDR へトーンマッピングします。端末にトーンマッピング機能があれば使うが、ない場合は白っぽくなる。 */
            TO_SDR
        }
    }
}