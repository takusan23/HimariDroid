package io.github.takusan23.himaridroid.ui.tool

object NumberFormat {

    /** 単位の調整 */
    fun formatBit(byte: Int): String = when {
        byte >= 1_000_000 -> "${byte / 1_000_000} Mbps"
        byte >= 1_000 -> "${byte / 1_000} Kbps"
        else -> "$byte bps"
    }

}