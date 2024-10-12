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

    /** コンテナ */
    enum class ContainerType(val extension: String) {
        MPEG_4("mp4"),
        WEBM("webm");
    }

    /** 出力形式。コーデックとコンテナフォーマットのデータ */
    sealed interface CodecContainerType {
        val videoCodec: String
        val audioCodec: String
        val containerType: ContainerType

        companion object {

            /** AVC / AAC / mp4 */
            object AVC_AAC_MPEG4 : CodecContainerType {
                override val videoCodec: String = MediaFormat.MIMETYPE_VIDEO_AVC
                override val audioCodec: String = MediaFormat.MIMETYPE_AUDIO_AAC
                override val containerType: ContainerType = ContainerType.MPEG_4
            }

            /** HEVC / AAC / mp4 */
            data class HEVC_AAC_MPEG4(val isEnableTenBitHdr: Boolean) : CodecContainerType {
                override val videoCodec: String = MediaFormat.MIMETYPE_VIDEO_HEVC
                override val audioCodec: String = MediaFormat.MIMETYPE_AUDIO_AAC
                override val containerType: ContainerType = ContainerType.MPEG_4
            }

            /** AV1 / AAC / mp4 */
            data class AV1_AAC_MPEG4(val isEnableTenBitHdr: Boolean) : CodecContainerType {
                override val videoCodec: String = MediaFormat.MIMETYPE_VIDEO_HEVC
                override val audioCodec: String = MediaFormat.MIMETYPE_AUDIO_AAC
                override val containerType: ContainerType = ContainerType.MPEG_4
            }

            /** VP9 / Opus / WebM */
            data object VP9_OPUS_WEBM : CodecContainerType {
                override val videoCodec: String = MediaFormat.MIMETYPE_VIDEO_AV1
                override val audioCodec: String = MediaFormat.MIMETYPE_AUDIO_OPUS
                override val containerType: ContainerType = ContainerType.WEBM
            }

            /**
             * AV1 / Opus / WebM
             * WebM コンテナに AV1 を入れるのは仕様にないみたいなのですが、ブラウザ系がやってるので実質対応みたいになっているらしい？
             */
            data object AV1_OPUS_WEBM : CodecContainerType {
                override val videoCodec: String = MediaFormat.MIMETYPE_VIDEO_AV1
                override val audioCodec: String = MediaFormat.MIMETYPE_AUDIO_OPUS
                override val containerType: ContainerType = ContainerType.WEBM
            }
        }
    }
}