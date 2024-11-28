package io.github.takusan23.himaridroid.processor.muxer

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.takusan23.akaricore.common.AkariCoreInputOutput
import io.github.takusan23.akaricore.common.MediaMuxerTool
import java.nio.ByteBuffer

// TODO akaricore:5.0.0-alpha02 でこれを入れる
class AndroidMediaMuxer(
    output: AkariCoreInputOutput.Output,
    containerFormat: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
) : VideoEncoderMuxerInterface {

    /** コンテナフォーマットのマルチプレクサ */
    private val mediaMuxer = MediaMuxerTool.createMediaMuxer(output, containerFormat)

    /** トラックのインデックス */
    private var videoTrackIndex = -1

    override suspend fun onOutputFormat(mediaFormat: MediaFormat) {
        videoTrackIndex = mediaMuxer.addTrack(mediaFormat)
        mediaMuxer.start()
    }

    override suspend fun onOutputData(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        mediaMuxer.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
    }

    override suspend fun stop() {
        mediaMuxer.stop()
        mediaMuxer.release()
    }
}