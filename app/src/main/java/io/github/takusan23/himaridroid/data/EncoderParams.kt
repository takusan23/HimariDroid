package io.github.takusan23.himaridroid.data

import android.media.MediaFormat

/** エンコーダーに渡す設定 */
data class EncoderParams(
    val fileNameWithoutExtension: String,
    val videoWidth: Int,
    val videoHeight: Int,
    val bitRate: Int,
    val frameRate: Int,
    val codecContainerType: CodecContainerType
) {

    /** 拡張子付きのファイル名を返す */
    val fileNameAndExtension: String
        get() = "${fileNameWithoutExtension}.${codecContainerType.containerType.extension}"

    /** コーデックとコンテナの種類 */
    enum class CodecContainerType(val codecName: String, val containerType: ContainerType) {
        /** AVC / AAC / mp4 */
        AVC_AAC_MPEG4(MediaFormat.MIMETYPE_VIDEO_AVC, ContainerType.MPEG_4),

        /** HEVC / AAC / mp4 */
        HEVC_AAC_MPEG4(MediaFormat.MIMETYPE_VIDEO_HEVC, ContainerType.MPEG_4),

        /** AV1 / AAC / mp4 */
        AV1_AAC_MPEG4(MediaFormat.MIMETYPE_VIDEO_AV1, ContainerType.MPEG_4),

        /** VP9 / Opus / WebM */
        VP9_OPUS_WEBM(MediaFormat.MIMETYPE_VIDEO_VP9, ContainerType.WEBM),

        /**
         * AV1 / Opus / WebM
         * WebM コンテナに AV1 を入れるのは仕様にないみたいなのですが、ブラウザ系がやってるので実質対応みたいになっているらしい？
         */
        AV1_OPUS_WEBM(MediaFormat.MIMETYPE_VIDEO_AV1, ContainerType.WEBM);
    }

    /** コンテナ */
    enum class ContainerType(val extension: String) {
        MPEG_4("mp4"),
        WEBM("webm");
    }

}