package io.github.takusan23.himaridroid.data

/** エンコーダーに渡す設定 */
data class EncoderParams(
    val videoWidth: Int,
    val videoHeight: Int,
    val bitRate: Int,
    val frameRate: Int,
    val codecContainerType: CodecContainerType
) {

    /** コーデックとコンテナの種類 */
    enum class CodecContainerType {
        /** AVC / AAC / mp4 */
        AVC_AAC_MPEG4,

        /** HEVC / AAC / mp4 */
        HEVC_AAC_MPEG4,

        /** AV1 / AAC / mp4 */
        AV1_AAC_MPEG4,

        /** VP9 / Opus / WebM */
        VP9_OPUS_WEBM,

        /**
         * AV1 / Opus / WebM
         * WebM コンテナに AV1 を入れるのは仕様にないみたいなのですが、ブラウザ系がやってるので実質対応みたいになっているらしい？
         */
        AV1_OPUS_WEBM;

        companion object {

            private val MPEG_4_CONTAINER = listOf(AVC_AAC_MPEG4, HEVC_AAC_MPEG4, AV1_AAC_MPEG4)
            private val WEBM_CONTAINER = listOf(VP9_OPUS_WEBM, AV1_OPUS_WEBM)

            /** 音声の再エンコードが必要かどうか */
            fun isAudioReEncode(a: CodecContainerType, b: CodecContainerType): Boolean {
                return (MPEG_4_CONTAINER.contains(a) && MPEG_4_CONTAINER.contains(b)) || (WEBM_CONTAINER.contains(a) && WEBM_CONTAINER.contains(b))
            }
        }
    }

}