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
        AV1_OPUS_WEBM
    }

}