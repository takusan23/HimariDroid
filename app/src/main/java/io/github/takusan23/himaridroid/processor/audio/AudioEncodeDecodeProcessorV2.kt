package io.github.takusan23.himaridroid.processor.audio

import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.takusan23.akaricore.audio.AkariCoreAudioProperties
import io.github.takusan23.akaricore.common.AkariCoreInputOutput
import io.github.takusan23.himaridroid.processor.muxer.AndroidMediaMuxer
import io.github.takusan23.himaridroid.processor.muxer.VideoEncoderMuxerInterface

// TODO akaricore:5.0.0-alpha02 でこれを入れる
object AudioEncodeDecodeProcessorV2 {

    suspend fun encode(
        input: AkariCoreInputOutput.Input,
        muxerInterface: VideoEncoderMuxerInterface,
        codecName: String = MediaFormat.MIMETYPE_AUDIO_AAC,
        samplingRate: Int = AkariCoreAudioProperties.SAMPLING_RATE,
        bitRate: Int = 192_000,
        channelCount: Int = 2
    ) {
        // エンコードする
        // コンテナフォーマット
        // エンコーダー起動
        val audioEncoder = AudioEncoderV2().apply {
            prepareEncoder(
                codec = codecName,
                sampleRate = samplingRate,
                channelCount = channelCount,
                bitRate = bitRate
            )
        }
        input.inputStream().buffered().use { inputStream ->
            audioEncoder.startAudioEncode(
                onRecordInput = { byteArray -> inputStream.read(byteArray) },
                onOutputFormatAvailable = muxerInterface::onOutputFormat,
                onOutputBufferAvailable = muxerInterface::onOutputData
            )
        }
        muxerInterface.stop()
    }

    suspend fun encode(
        input: AkariCoreInputOutput.Input,
        output: AkariCoreInputOutput.Output,
        containerFormat: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        codecName: String = MediaFormat.MIMETYPE_AUDIO_AAC,
        samplingRate: Int = AkariCoreAudioProperties.SAMPLING_RATE,
        bitRate: Int = 192_000,
        channelCount: Int = 2
    ) = encode(
        input = input,
        muxerInterface = AndroidMediaMuxer(output, containerFormat),
        codecName = codecName,
        samplingRate = samplingRate,
        bitRate = bitRate,
        channelCount = channelCount
    )

}