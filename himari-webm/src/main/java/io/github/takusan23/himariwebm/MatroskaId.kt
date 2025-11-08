package io.github.takusan23.himariwebm

/**
 * Matroska のタグ
 *
 * @param byteArray ID のバイト配列
 * @param isParent 子要素を持つ親かどうか
 * @param parentTag 親要素の [MatroskaId]
 */
enum class MatroskaId(
    val byteArray: ByteArray,
    val isParent: Boolean,
    val parentTag: MatroskaId?
) {
    EBML(byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte()), true, null),
    EBMLVersion(byteArrayOf(0x42.toByte(), 0x86.toByte()), false, EBML),
    EBMLReadVersion(byteArrayOf(0x42.toByte(), 0xF7.toByte()), false, EBML),
    EBMLMaxIDLength(byteArrayOf(0x42.toByte(), 0xF2.toByte()), false, EBML),
    EBMLMaxSizeLength(byteArrayOf(0x42.toByte(), 0xF3.toByte()), false, EBML),
    DocType(byteArrayOf(0x42.toByte(), 0x82.toByte()), false, EBML),
    DocTypeVersion(byteArrayOf(0x42.toByte(), 0x87.toByte()), false, EBML),
    DocTypeReadVersion(byteArrayOf(0x42.toByte(), 0x85.toByte()), false, EBML),

    Segment(byteArrayOf(0x18.toByte(), 0x53.toByte(), 0x80.toByte(), 0x67.toByte()), true, null),

    SeekHead(byteArrayOf(0x11.toByte(), 0x4D.toByte(), 0x9B.toByte(), 0x74.toByte()), true, Segment),
    Seek(byteArrayOf(0x4D.toByte(), 0xBB.toByte()), true, SeekHead),
    SeekID(byteArrayOf(0x53.toByte(), 0xAB.toByte()), false, Seek),
    SeekPosition(byteArrayOf(0x53.toByte(), 0xAC.toByte()), false, Seek),

    Info(byteArrayOf(0x15.toByte(), 0x49.toByte(), 0xA9.toByte(), 0x66.toByte()), true, Segment),
    Duration(byteArrayOf(0x44.toByte(), 0x89.toByte()), false, Info),
    SegmentUUID(byteArrayOf(0x73.toByte(), 0xA4.toByte()), false, Info),
    TimestampScale(byteArrayOf(0x2A.toByte(), 0xD7.toByte(), 0xB1.toByte()), false, Info),
    MuxingApp(byteArrayOf(0x4D.toByte(), 0x80.toByte()), false, Info),
    WritingApp(byteArrayOf(0x57.toByte(), 0x41.toByte()), false, Info),

    Tracks(byteArrayOf(0x16.toByte(), 0x54.toByte(), 0xAE.toByte(), 0x6B.toByte()), true, Segment),
    TrackEntry(byteArrayOf(0xAE.toByte()), true, Tracks),
    TrackNumber(byteArrayOf(0xD7.toByte()), false, TrackEntry),
    TrackUID(byteArrayOf(0x73.toByte(), 0xC5.toByte()), false, TrackEntry),
    TrackType(byteArrayOf(0x83.toByte()), false, TrackEntry),
    Language(byteArrayOf(0x22.toByte(), 0xB5.toByte(), 0x9C.toByte()), false, TrackEntry),
    CodecID(byteArrayOf(0x86.toByte()), false, TrackEntry),
    CodecPrivate(byteArrayOf(0x63.toByte(), 0xA2.toByte()), false, TrackEntry),
    CodecName(byteArrayOf(0x25.toByte(), 0x86.toByte(), 0x88.toByte()), false, TrackEntry),
    FlagLacing(byteArrayOf(0x9C.toByte()), false, TrackEntry),
    DefaultDuration(byteArrayOf(0x23.toByte(), 0xE3.toByte(), 0x83.toByte()), false, TrackEntry),
    TrackTimecodeScale(byteArrayOf(0x23.toByte(), 0x31.toByte(), 0x4F.toByte()), false, TrackEntry),
    VideoTrack(byteArrayOf(0xE0.toByte()), false, TrackEntry),
    PixelWidth(byteArrayOf(0xB0.toByte()), false, VideoTrack),
    PixelHeight(byteArrayOf(0xBA.toByte()), false, VideoTrack),
    FrameRate(byteArrayOf(0x23.toByte(), 0x83.toByte(), 0xE3.toByte()), false, VideoTrack),
    MaxBlockAdditionID(byteArrayOf(0x55.toByte(), 0xEE.toByte()), false, VideoTrack),
    AudioTrack(byteArrayOf(0xE1.toByte()), true, TrackEntry),
    SamplingFrequency(byteArrayOf(0xB5.toByte()), false, AudioTrack),
    Channels(byteArrayOf(0x9F.toByte()), false, AudioTrack),
    BitDepth(byteArrayOf(0x62.toByte(), 0x64.toByte()), false, AudioTrack),
    Colour(byteArrayOf(0x55.toByte(), 0xB0.toByte()), true, VideoTrack),
    MatrixCoefficients(byteArrayOf(0x55.toByte(), 0xB1.toByte()), false, Colour),
    TransferCharacteristics(byteArrayOf(0x55, 0xBA.toByte()), false, Colour),
    Primaries(byteArrayOf(0x55.toByte(), 0xBB.toByte()), false, Colour),

    Cues(byteArrayOf(0x1C.toByte(), 0x53.toByte(), 0xBB.toByte(), 0x6B.toByte()), true, Segment),
    CuePoint(byteArrayOf(0xBB.toByte()), true, Cues),
    CueTime(byteArrayOf(0xB3.toByte()), false, CuePoint),
    CueTrackPositions(byteArrayOf(0xB7.toByte()), true, CuePoint),
    CueTrack(byteArrayOf(0xF7.toByte()), false, CueTrackPositions),
    CueClusterPosition(byteArrayOf(0xF1.toByte()), false, CueTrackPositions),
    CueRelativePosition(byteArrayOf(0xF0.toByte()), false, CueTrackPositions),

    Cluster(byteArrayOf(0x1F.toByte(), 0x43.toByte(), 0xB6.toByte(), 0x75.toByte()), true, Segment),
    Timestamp(byteArrayOf(0xE7.toByte()), false, Cluster),
    SimpleBlock(byteArrayOf(0xA3.toByte()), false, Cluster)
}