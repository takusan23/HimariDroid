package io.github.takusan23.himaridroid.data

import io.github.takusan23.himaridroid.data.VideoFormat.TenBitHdrInfo

/**
 * 入力した動画のフォーマットとかの情報
 *
 * @param codecContainerType コーデックとコンテナ
 * @param fileName ファイル明
 * @param videoWidth 幅
 * @param videoHeight 高さ
 * @param bitRate ビットレート
 * @param frameRate フレームレート
 * @param tenBitHdrInfo 10Bit HDR の動画の場合は[TenBitHdrInfo]。SDR 動画の場合は null。
 */
data class VideoFormat(
    val codecContainerType: EncoderParams.CodecContainerType,
    val fileName: String,
    val videoWidth: Int,
    val videoHeight: Int,
    val bitRate: Int,
    val frameRate: Int,
    val tenBitHdrInfo: TenBitHdrInfo?
) {

    /**
     * 10Bit HDR の詳細
     *
     * @param colorStandard 色域
     * @param colorTransfer ガンマカーブ
     *
     */
    data class TenBitHdrInfo(
        val colorStandard: Int,
        val colorTransfer: Int
    )

}