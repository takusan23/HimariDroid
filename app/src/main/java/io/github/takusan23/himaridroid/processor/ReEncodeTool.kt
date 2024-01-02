package io.github.takusan23.himaridroid.processor

import android.content.Context
import android.media.MediaMuxer
import android.net.Uri
import io.github.takusan23.himaridroid.processor.video.VideoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ReEncodeTool {

    suspend fun start(
        context: Context,
        inputUri: Uri,
        codecName:String,
        videoBitrate: Int
    ) = withContext(Dispatchers.Default) {
        // 再エンコードをする
        // とりあえず音声は AAC が入ってくることを期待して映像のみ再エンコードする
        val videoOnlyFile = context.getExternalFilesDir(null)!!.resolve("temp_video_only_${System.currentTimeMillis()}.mp4")
        startVideoProcess(
            resultFile = videoOnlyFile,
            context = context,
            inputUri = inputUri,
            videoBitrate = videoBitrate,
            codecName = codecName
        )

        // これだと映像だけなので、音声トラックを追加する。これで AV1 でエンコードした動画ができる。
        // このままだと端末の動画フォルダにコピーされないので、後でその対応をします
        val resultFile = context.getExternalFilesDir(null)!!.resolve("${codecName.replace("/","")}_${videoBitrate}_${System.currentTimeMillis()}.mp4")
        MediaTool.mixAvTrack(
            audioPair = MediaTool.createMediaExtractor(context, inputUri, MediaTool.Track.AUDIO),
            videoPair = MediaTool.createMediaExtractor(videoOnlyFile, MediaTool.Track.VIDEO),
            resultFile = resultFile,
        )

        // 端末の動画フォルダにコピーする
        MediaTool.saveToVideoFolder(context, resultFile)
        // 余計なファイルを消す
        videoOnlyFile.delete()
        resultFile.delete()
    }

    private suspend fun startVideoProcess(
        resultFile: File,
        context: Context,
        inputUri: Uri,
        videoBitrate: Int,
        codecName: String
    ) {
        // MediaExtractor
        val (videoExtractor, inputVideoFormat) = MediaTool.createMediaExtractor(context, inputUri, MediaTool.Track.VIDEO)

        // コンテナに書き込むやつ
        var trackIndex = -1
        val mediaMuxer = MediaMuxer(resultFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // 再エンコードをする
        VideoProcessor.start(
            mediaExtractor = videoExtractor,
            inputMediaFormat = inputVideoFormat,
            codec = codecName,
            bitRate = videoBitrate,
            keyframeInterval = 1,
            onOutputFormat = { format ->
                // onOutputData より先に呼ばれるはずです
                trackIndex = mediaMuxer.addTrack(format)
                mediaMuxer.start()
            },
            onOutputData = { byteBuffer, bufferInfo ->
                mediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo)
            }
        )

        // 終わり
        mediaMuxer.stop()
    }

}