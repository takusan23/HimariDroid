package io.github.takusan23.himaridroid.processor.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** 音声の操作をするユーティリティ関数たち */
object AudioProcessor {

    const val OPUS_SAMPLING_RATE = 48_000 // Opus は 44.1k 対応していないので、48k にアップサンプリングする

    /** aac 等を PCM にする。デコードする */
    suspend fun decodeAudio(
        mediaExtractor: MediaExtractor,
        mediaFormat: MediaFormat,
        outputFile: File,
    ) = withContext(Dispatchers.IO) {
        // デコーダーにメタデータを渡す
        val audioDecoder = AudioDecoder().apply {
            prepareDecoder(mediaFormat)
        }
        // ファイルに書き込む準備
        outputFile.outputStream().use { outputStream ->
            // デコードする
            audioDecoder.startAudioDecode(
                readSampleData = { byteBuffer ->
                    // データを進める
                    val size = mediaExtractor.readSampleData(byteBuffer, 0)
                    mediaExtractor.advance()
                    size to mediaExtractor.sampleTime
                },
                onOutputBufferAvailable = { bytes ->
                    // データを書き込む
                    outputStream.write(bytes)
                }
            )
        }
        mediaExtractor.release()
    }

    /** PCM データをエンコードする */
    suspend fun encodeAudio(
        rawFile: File,
        codec: String,
        sampleRate: Int,
        channelCount: Int = 2,
        bitRate: Int = 192_000,
        onOutputFormat: suspend (MediaFormat) -> Unit,
        onOutputData: suspend (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) = withContext(Dispatchers.Default) {
        // エンコーダーを初期化
        val audioEncoder = AudioEncoder().apply {
            prepareEncoder(
                codec = codec,
                sampleRate = sampleRate,
                channelCount = channelCount,
                bitRate = bitRate
            )
        }
        rawFile.inputStream().use { inputStream ->
            audioEncoder.startAudioEncode(
                onRecordInput = { bytes ->
                    // データをエンコーダーに渡す
                    inputStream.read(bytes)
                },
                onOutputBufferAvailable = onOutputData,
                onOutputFormatAvailable = onOutputFormat
            )
        }
    }

    /** Sonic ライブラリを使ってアップサンプリングする */
    suspend fun upsamplingBySonic(
        inFile: File,
        outFile: File,
        channelCount: Int,
        inSamplingRate: Int,
        outSamplingRate: Int
    ) = withContext(Dispatchers.Default) {
        val bufferSize = 8192
        val inByteArray = ByteArray(bufferSize)
        val outByteArray = ByteArray(bufferSize)
        var numRead: Int
        var numWritten: Int

        // Sonic を利用してアップサンプリングを行う
        val sonic = Sonic(inSamplingRate, channelCount)
        sonic.sampleRate = outSamplingRate
        sonic.speed = 1f
        sonic.pitch = 1f
        sonic.setRate(44_100f / 48_000f)
        sonic.volume = 1f
        sonic.chordPitch = false
        sonic.quality = 0

        inFile.inputStream().use { inputStream ->
            outFile.outputStream().use { outputStream ->

                do {
                    numRead = inputStream.read(inByteArray, 0, bufferSize)
                    if (numRead <= 0) {
                        sonic.flushStream()
                    } else {
                        sonic.writeBytesToStream(inByteArray, numRead)
                    }
                    do {
                        numWritten = sonic.readBytesFromStream(outByteArray, bufferSize)
                        if (numWritten > 0) {
                            outputStream.write(outByteArray, 0, numWritten)
                        }
                    } while (numWritten > 0)
                } while (numRead > 0)
            }
        }
    }

    /**
     * アップサンプリングする
     *
     * @param inFile 元の PCM ファイル
     * @param outFile アップサンプリングした PCM ファイル出力先
     * @param inSamplingRate 元のサンプリングレート
     * @param outSamplingRate アップサンプリングしたいサンプリングレート
     */
    suspend fun upsampling(
        inFile: File,
        outFile: File,
        inSamplingRate: Int,
        outSamplingRate: Int
    ) = withContext(Dispatchers.IO) {
        inFile.inputStream().use { inputStream ->
            outFile.outputStream().use { outputStream ->
                while (isActive) {
                    // データを取り出す
                    val pcmByteArray = ByteArray(8192)
                    val size = inputStream.read(pcmByteArray)
                    if (size == -1) {
                        break
                    }
                    // 水増しする
                    val upsamplingData = simpleUpsampling(
                        pcmByteArray = pcmByteArray,
                        inSamplingRate = inSamplingRate,
                        outSamplingRate = outSamplingRate
                    )
                    outputStream.write(upsamplingData)
                }
            }
        }
    }

    private suspend fun simpleUpsampling(
        pcmByteArray: ByteArray,
        inSamplingRate: Int,
        outSamplingRate: Int
    ) = withContext(Dispatchers.IO) {

        fun filterSingleChannel(pcmByteArray: ByteArray, startIndex: Int): ByteArray {
            // 元は 2 チャンネルだったので
            val singleChannelByteArray = ByteArray(pcmByteArray.size / 2)
            var readIndex = startIndex
            var writtenIndex = 0
            while (true) {
                singleChannelByteArray[writtenIndex++] = pcmByteArray[readIndex++]
                singleChannelByteArray[writtenIndex++] = pcmByteArray[readIndex++]
                // 次の 2 バイト分飛ばす
                // どういうことかと言うと 右左右左右左... で右だけほしい
                readIndex += 2
                // もうない場合
                if (pcmByteArray.size <= readIndex) {
                    break
                }
            }
            return singleChannelByteArray
        }

        fun upsampling(singleChannelPcm: ByteArray, inSamplingRate: Int, outSamplingRate: Int): ByteArray {
            // 返すデータ。元のサイズに倍率をかけて増やしておく
            val resultSingleChannelPcm = ByteArray((singleChannelPcm.size * (outSamplingRate / inSamplingRate.toFloat())).toInt())
            // 足りない分
            val diffSize = resultSingleChannelPcm.size - singleChannelPcm.size
            val addIndex = singleChannelPcm.size / diffSize
            // データを入れていく
            var writtenIndex = 0
            var readIndex = 0
            while (true) {
                // 1つのサンプルは 2byte で表現されているため、2バイトずつ扱う必要がある
                val byte1 = singleChannelPcm[readIndex++]
                val byte2 = singleChannelPcm[readIndex++]
                resultSingleChannelPcm[writtenIndex++] = byte1
                resultSingleChannelPcm[writtenIndex++] = byte2
                // 足りない分を入れる必要がある
                // ただ前回の値をもう一回使ってるだけ
                if (readIndex % addIndex == 0) {
                    resultSingleChannelPcm[writtenIndex++] = byte1
                    resultSingleChannelPcm[writtenIndex++] = byte2
                }
                if (singleChannelPcm.size <= readIndex) {
                    break
                }
                if (resultSingleChannelPcm.size <= writtenIndex) {
                    break
                }
            }
            return resultSingleChannelPcm
        }

        // 2 チャンネルしか想定していないので、右と左で予め分けておく
        // 右右左左右右左左... みたいに入ってて、それぞれ分ける必要がある
        val singleChannelPcm1 = filterSingleChannel(pcmByteArray, 0)
        val singleChannelPcm2 = filterSingleChannel(pcmByteArray, 2)

        // アップサンプリングにより、元のサンプリングレートにはなかった間を埋める
        val singleChannelUpscalingPcm1 = upsampling(singleChannelPcm1, inSamplingRate, outSamplingRate)
        val singleChannelUpscalingPcm2 = upsampling(singleChannelPcm2, inSamplingRate, outSamplingRate)

        // 右左、交互にデータを入れていく
        val resultPcm = ByteArray((pcmByteArray.size * (outSamplingRate / inSamplingRate.toFloat())).toInt())
        var writtenIndex = 0
        var readIndex1 = 0
        var readIndex2 = 0
        // 2 チャンネル、2 バイトずつ
        while (true) {
            resultPcm[writtenIndex++] = singleChannelUpscalingPcm1[readIndex1++]
            resultPcm[writtenIndex++] = singleChannelUpscalingPcm1[readIndex1++]
            resultPcm[writtenIndex++] = singleChannelUpscalingPcm2[readIndex2++]
            resultPcm[writtenIndex++] = singleChannelUpscalingPcm2[readIndex2++]
            // データがなくなったら return
            if (singleChannelUpscalingPcm1.size <= readIndex1) {
                break
            }
        }

        return@withContext resultPcm
    }

}