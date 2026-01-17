package io.github.takusan23.himaridroid.data

/** Snackbar の種類 */
sealed interface HomeScreenSnackbarType {

    /**
     * 解析に失敗した
     *
     * @param codec コーデック
     * @param container コンテナフォーマット
     */
    data class VideoFileParseError(
        val codec: String?,
        val container: String?
    ) : HomeScreenSnackbarType

}