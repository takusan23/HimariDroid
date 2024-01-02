package io.github.takusan23.himariwebm

/**
 * EBMLの要素を表すデータクラス
 *
 * @param matroskaId [MatroskaId]
 * @param data データ
 */
internal data class MatroskaElement(
    val matroskaId: MatroskaId,
    val data: ByteArray
)