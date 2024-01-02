package io.github.takusan23.himariwebm

import java.io.File
import kotlin.random.Random

/** Kotlin/JVM で動く WebM に書き込むやつ */
class HimariWebm(
    tempFolder: File,
    private val outputFile: File
) {
    // Cluster 一時保存先。エンコードしたデータをずっと持っていると OOM になるので
    private var encodeDataList = mutableListOf<EncodeData>()

    // 複数のインスタンスで同じ名前にならないように工夫する必要あり
    private val tempEncodeFileFolder = tempFolder.resolve("temp_${Random.nextInt()}").apply { mkdir() }
    private var tempEncodeDataIndex = 0

    private var audioTrack: MatroskaElement? = null
    private var videoTrack: MatroskaElement? = null

    private var trackCount = 1
    private var audioTrackIndex = -1
    private var videoTrackIndex = -1

    fun setAudioTrack(
        audioCodec: String,
        audioSamplingRate: Float,
        audioChannelCount: Int
    ) {
        audioTrackIndex = trackCount++
        audioTrack = MatroskaElement(
            MatroskaId.TrackEntry,
            byteArrayOf(
                *MatroskaElement(MatroskaId.TrackNumber, audioTrackIndex.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.TrackUID, audioTrackIndex.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.TrackType, AUDIO_TRACK_TYPE.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.CodecID, audioCodec.toAscii()).toEbmlByteArray(),
                // Opus Codec Private
                // https://wiki.xiph.org/OggOpus#ID_Header
                *MatroskaElement(
                    MatroskaId.CodecPrivate,
                    byteArrayOf(
                        *"OpusHead".toAscii(),
                        1.toByte(),
                        audioChannelCount.toByte(),
                        0x00.toByte(),
                        0x00.toByte(),
                        // リトルエンディアンなので逆に
                        *audioSamplingRate.toInt().toByteArray().reversed().toByteArray(),
                        0x00.toByte(),
                        0x00.toByte(),
                        0x00.toByte(),
                        0x00.toByte(),
                        0x00.toByte()
                    )
                ).toEbmlByteArray(),
                *MatroskaElement(
                    MatroskaId.AudioTrack,
                    byteArrayOf(
                        *MatroskaElement(MatroskaId.SamplingFrequency, audioSamplingRate.toBits().toByteArray()).toEbmlByteArray(),
                        *MatroskaElement(MatroskaId.Channels, audioChannelCount.toByteArray()).toEbmlByteArray()
                    )
                ).toEbmlByteArray()
            )
        )
    }

    fun setVideoTrack(
        videoCodec: String,
        videoWidth: Int,
        videoHeight: Int
    ) {
        videoTrackIndex = trackCount++
        videoTrack = MatroskaElement(
            MatroskaId.TrackEntry,
            byteArrayOf(
                *MatroskaElement(MatroskaId.TrackNumber, videoTrackIndex.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.TrackUID, videoTrackIndex.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.TrackType, VIDEO_TRACK_TYPE.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.CodecID, videoCodec.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(
                    MatroskaId.VideoTrack,
                    byteArrayOf(
                        *MatroskaElement(MatroskaId.PixelWidth, videoWidth.toByteArray()).toEbmlByteArray(),
                        *MatroskaElement(MatroskaId.PixelHeight, videoHeight.toByteArray()).toEbmlByteArray()
                    )
                ).toEbmlByteArray()
            )
        )
    }

    fun writeAudio(
        byteArray: ByteArray,
        durationMs: Long,
        isKeyFrame: Boolean = true,
    ) = writeSample(audioTrackIndex, byteArray, durationMs, isKeyFrame)

    fun writeVideo(
        byteArray: ByteArray,
        durationMs: Long,
        isKeyFrame: Boolean
    ) = writeSample(videoTrackIndex, byteArray, durationMs, isKeyFrame)

    fun stop() {
        // stop するまで時間がわからない。ようやく分かる
        val duration = encodeDataList.maxOf { it.time }

        // EBMLヘッダー を書き込む
        val ebmlByteArray = MatroskaElement(
            MatroskaId.EBML,
            byteArrayOf(
                *MatroskaElement(MatroskaId.EBMLVersion, 1.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.EBMLReadVersion, 1.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.EBMLMaxIDLength, 4.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.EBMLMaxSizeLength, 8.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.DocType, "webm".toAscii()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.DocTypeVersion, 4.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.DocTypeReadVersion, 2.toByteArray()).toEbmlByteArray()
            )
        ).toEbmlByteArray()

        // すでに確定している要素は ByteArray にする
        val infoByteArray = MatroskaElement(
            MatroskaId.Info,
            byteArrayOf(
                *MatroskaElement(MatroskaId.TimestampScale, 1_000_000.toByteArray()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.MuxingApp, "himariwebm".toAscii()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.WritingApp, "himariwebm".toAscii()).toEbmlByteArray(),
                *MatroskaElement(MatroskaId.Duration, duration.toFloat().toBits().toByteArray()).toEbmlByteArray()
            )
        ).toEbmlByteArray()
        val tracksByteArray = MatroskaElement(
            MatroskaId.Tracks,
            byteArrayOf(
                *(audioTrack?.toEbmlByteArray() ?: byteArrayOf()),
                *(videoTrack?.toEbmlByteArray() ?: byteArrayOf())
            )
        ).toEbmlByteArray()

        // Cluster をつくる
        // 時系列順に並び替える。音声と映像の並びが追加順ではなく、追加時間順になるように
        encodeDataList.sortBy { it.time }

        // Cluster に入れる SimpleBlock も、わかりやすいように二重の配列にする
        // 最初の配列が 0から4秒まで。2つ目が 4から8 までみたいな
        // [ [Data, Data ...], [Data, Data ...] ]
        // つまりこの配列の数だけ Cluster を作り、二重になってる List の中身を Cluster に入れれば良い
        // 0 から動画の時間まで 4000 ごとに生成
        val clusterSimpleBlockChildrenList = (0 until encodeDataList.last().time step CLUSTER_INTERVAL_MS.toLong())
            .map { time -> time until (time + CLUSTER_INTERVAL_MS) }
            .map { range -> encodeDataList.filter { it.time in range } }

        // Cluster を入れる前に、Cluster 全体の大きさと、Cue を生成する
        // Cluster 全体の大きさは Cluster + Timestamp + SimpleBlock ... の全部を足さないといけない
        val allClusterByteSize = clusterSimpleBlockChildrenList.sumOf { children -> children.toClusterSize() }

        // SeekHead を作る
        // SeekHead は自分自身の要素の長さを加味した Info / Tracks の位置を出す必要が有るため大変
        // Cue は一番最後って約束で
        val seekHead = reclusiveCreateSeekHead(
            infoSize = infoByteArray.size,
            tracksSize = tracksByteArray.size,
            clusterSize = allClusterByteSize
        ).toEbmlByteArray()

        // Cue を作る。プレイヤーがシークする際に読み出す位置を教えてあげる
        // これがないとプレイヤーはシークする際に Cluster を上から舐める（かランダムアクセス）が必要になる。
        // プレイヤーによっては Info の Duration があればランダムアクセスでシークできる実装もあるが、Cue に頼っているプレイヤーもある。
        // 初回 Cluster の追加位置
        var prevInsertClusterPosition = seekHead.size + infoByteArray.size + tracksByteArray.size
        val cuesByteArray = MatroskaElement(
            MatroskaId.Cues, clusterSimpleBlockChildrenList.map { children ->
                val encodeData = children.first()
                val seekPosition = encodeData.time.toInt()

                val cuePointByteArray = MatroskaElement(
                    MatroskaId.CuePoint,
                    byteArrayOf(
                        *MatroskaElement(MatroskaId.CueTime, seekPosition.toByteArray()).toEbmlByteArray(),
                        *MatroskaElement(
                            MatroskaId.CueTrackPositions,
                            byteArrayOf(
                                *MatroskaElement(MatroskaId.CueTrack, videoTrackIndex.toByteArray()).toEbmlByteArray(),
                                *MatroskaElement(MatroskaId.CueClusterPosition, prevInsertClusterPosition.toByteArray()).toEbmlByteArray()
                            )
                        ).toEbmlByteArray()
                    )
                ).toEbmlByteArray()

                // 次の Cluster 追加位置を計算する
                prevInsertClusterPosition += children.toClusterSize()

                cuePointByteArray
            }.concatByteArray()
        ).toEbmlByteArray()

        // 書き込む
        outputFile.outputStream().use { outputStream ->

            // EBML ヘッダー
            outputStream.write(ebmlByteArray)

            // Segment を書き込む。まずは Id と DataSize
            val segmentIdAndDataSize = byteArrayOf(
                // Segment ID
                *MatroskaId.Segment.byteArray,
                // DataSize
                *(seekHead.size + infoByteArray.size + tracksByteArray.size + allClusterByteSize + cuesByteArray.size).toDataSize()
            )
            outputStream.write(segmentIdAndDataSize)

            // SeekHead Info Tracks を書き込む
            outputStream.write(seekHead)
            outputStream.write(infoByteArray)
            outputStream.write(tracksByteArray)

            // Cluster は別ファイルなので、ファイルから読み出す
            clusterSimpleBlockChildrenList.forEach { children ->

                // 時間
                val timestamp = children.first().time

                // Cluster を作る
                // ここも OOM なりそうでちょい怖いけど 4 秒ごとにエンコード済みデータをメモリに乗せるだけだし大丈夫やろ
                val cluster = MatroskaElement(
                    MatroskaId.Cluster,
                    byteArrayOf(
                        // Cluster 最初の要素は Timestamp
                        *MatroskaElement(MatroskaId.Timestamp, timestamp.toInt().toByteArray()).toEbmlByteArray(),

                        // そしたらあとは SimpleBlock で埋める
                        *children.map { encodeData ->
                            // データをファイルから読み出す
                            val encodeByteArray = File(encodeData.encodedDataSaveFilePath).readBytes()
                            // 先頭にトラック番号、Timestamp からの相対時間（2バイト）、キーフレームかどうかを入れる
                            val trackIndexByteArray = encodeData.trackIndex.toDataSize()
                            val relativeTimeByteArray = (encodeData.time - timestamp).toInt().toByteArray().padding(2)
                            val keyFrameByteArray = (if (encodeData.isKeyFrame) SIMPLE_BLOCK_FLAGS_KEYFRAME else SIMPLE_BLOCK_FLAGS_NONE).toByteArray()

                            MatroskaElement(
                                MatroskaId.SimpleBlock,
                                byteArrayOf(
                                    *trackIndexByteArray,
                                    *relativeTimeByteArray,
                                    *keyFrameByteArray,
                                    *encodeByteArray
                                )
                            ).toEbmlByteArray()
                        }.concatByteArray()
                    )
                ).toEbmlByteArray()

                // 書き込む
                outputStream.write(cluster)
            }

            // Cue は最後
            outputStream.write(cuesByteArray)
        }

        // 要らないファイルを消す
        tempEncodeFileFolder.deleteRecursively()
    }

    private fun writeSample(
        trackIndex: Int,
        byteArray: ByteArray,
        durationMs: Long,
        isKeyFrame: Boolean
    ) {
        // メモリにエンコードしたデータまでは載せれないので、今は外に出す（意味深）
        val tempClusterFile = tempEncodeFileFolder.resolve("encoded_data_${tempEncodeDataIndex++}")
        tempClusterFile.writeBytes(byteArray)
        encodeDataList += EncodeData(
            trackIndex = trackIndex,
            time = durationMs,
            isKeyFrame = isKeyFrame,
            encodeDataSize = byteArray.size,
            encodedDataSaveFilePath = tempClusterFile.path
        )
    }

    /** SeekHead を組み立てる */
    private fun reclusiveCreateSeekHead(
        infoSize: Int,
        tracksSize: Int,
        clusterSize: Int
    ): MatroskaElement {
        // SeekHead を作る・・・・がこれがとても複雑で、こんな感じに書き込みたいとして
        // +---------+----------+------+ ... +-----+
        // | Segment | SeekHead | Info | ... | Cue |
        // +---------+----------+------+ ... +-----+
        //          ↑ Segment の終わりから各要素の位置を SeekHead に書く必要がある。
        //
        // で、これの問題なんですが、
        // SeekHead を書き込む際に各要素の位置を書き込むのですが（Info Tracks など）、
        // この位置計算が、Segment から後なので、当然 SeekHead が書き込まれることを想定したサイズにする必要があります。
        // +---------+------------ ... -+------+
        // | Segment | SeekHead         | Info |
        // +---------+------------ ... -+------+
        //                             ↑ SeekHead 書き込みで Info の位置を知りたいけど、SeekHead 自体もまだ決まっていないので難しい
        // SeekHead + Info + Tracks + Cluster... + Cue の順番で書き込む予定
        // でその SeekHead のサイズが分からないというわけ
        fun createSeekHead(seekHeadSize: Int): MatroskaElement {
            val infoPosition = seekHeadSize
            val tracksPosition = infoPosition + infoSize
            val clusterPosition = tracksPosition + tracksSize
            // Cue は最後
            val cuePosition = clusterPosition + clusterSize
            // トップレベル要素、この子たちの位置を入れる
            val topLevelElementList = listOf(
                MatroskaId.Info to infoPosition,
                MatroskaId.Tracks to tracksPosition,
                MatroskaId.Cluster to clusterPosition,
                MatroskaId.Cues to cuePosition
            ).map { (tag, position) ->
                MatroskaElement(
                    MatroskaId.Seek,
                    byteArrayOf(
                        *MatroskaElement(MatroskaId.SeekID, tag.byteArray).toEbmlByteArray(),
                        *MatroskaElement(MatroskaId.SeekPosition, position.toByteArray()).toEbmlByteArray()
                    )
                )
            }
            val seekHead = MatroskaElement(MatroskaId.SeekHead, topLevelElementList.map { it.toEbmlByteArray() }.concatByteArray())
            return seekHead
        }

        // まず一回 SeekHead 自身のサイズを含めない SeekHead を作る。
        // もちろん SeekHead 自身のサイズを含めた計算をしないとずれるが、
        // それを修正するために再帰的に呼び出し修正する
        // （再帰的に呼び出しが必要な理由ですが、位置の更新で DataSize も拡張が必要になると、後続の位置が全部ずれるので）
        var prevSeekHeadSize = createSeekHead(0).toEbmlByteArray().size
        var seekHead: MatroskaElement
        while (true) {
            seekHead = createSeekHead(prevSeekHeadSize)
            val seekHeadSize = seekHead.toEbmlByteArray().size
            // サイズが同じになるまで SeekHead を作り直す
            if (prevSeekHeadSize == seekHeadSize) {
                break
            } else {
                prevSeekHeadSize = seekHeadSize
            }
        }
        return seekHead
    }

    companion object {
        private const val CLUSTER_INTERVAL_MS = 4_000 // よく分からんけど 4 秒ごとに Cluster を作り直す
    }

}