package io.github.takusan23.himaridroid.data

data class VideoFormat(
    val codecContainerType: EncoderParams.CodecContainerType,
    val videoWidth: Int,
    val videoHeight: Int,
    val bitRate: Int,
    val frameRate: Int
)