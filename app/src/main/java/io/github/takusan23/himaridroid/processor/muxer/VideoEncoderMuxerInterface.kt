package io.github.takusan23.himaridroid.processor.muxer

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

// TODO akaricore:5.0.0-alpha02 でこれを入れる
interface VideoEncoderMuxerInterface {
    suspend fun onOutputFormat(mediaFormat: MediaFormat)
    suspend fun onOutputData(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
    suspend fun stop()
}