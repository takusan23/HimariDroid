package io.github.takusan23.himariwebm

/**
 * エンコードしたデータは一旦別のファイルに書き込む。
 * 何故かというと、Cluster に音声と映像を書き込む際に時間をあわせて保存していく必要がある。
 * 今回は音声のエンコードのほうが先に終わってしまうため、映像のエンコードを待って、時間順に並び替えて、Cluster に入れる。
 *
 * @param trackIndex [audioTrackIndex] [videoTrackIndex]
 * @param time 時間
 * @param isKeyFrame キーフレームかどうか
 * @param encodeDataSize エンコードしたデータのサイズ。注意：SimpleBlock として使うためには先頭 4 バイトを追加する必要があります。
 * @param encodedDataSaveFilePath 保存先。エンコードしたデータをメモリに載せておくと OOM になるのでファイルに吐き出す。
 */
internal data class EncodeData(
    val trackIndex: Int,
    val time: Long,
    val isKeyFrame: Boolean,
    val encodeDataSize: Int,
    val encodedDataSaveFilePath: String
)