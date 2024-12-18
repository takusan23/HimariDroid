package io.github.takusan23.himaridroid

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.github.takusan23.himaridroid.data.EncoderParams
import io.github.takusan23.himaridroid.processor.ReEncodeTool
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** エンコードするためのフォアグラウンドサービス。時間がかかるのでフォアグラウンドサービスでやる。 */
class EncoderService : Service() {

    private val scope = MainScope()
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val localBinder = LocalBinder(this)

    private val _isEncoding = MutableStateFlow(false)
    private val _progressCurrentPositionMs = MutableStateFlow<ReEncodeProgressData?>(null)

    /** エンコード中かどうか */
    val isEncoding = _isEncoding.asStateFlow()

    /** 再エンコードの進捗 */
    val progressCurrentPositionMs = _progressCurrentPositionMs.asStateFlow()

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onDestroy() {
        super.onDestroy()
        stopEncode()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // エンコード中でなければタスクキル時に終了
        if (!isEncoding.value) {
            stopSelf()
        }
    }

    // MEDIA_PROCESSING は 6 時間までしか動かせないらしい。アプリを表示させるとリセットされる模様。
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        stopSelf()
    }

    /** エンコードを開始する */
    fun startEncode(
        inputUri: Uri,
        encoderParams: EncoderParams
    ) {
        scope.launch {
            // フォアグラウンドサービスに昇格する
            setForegroundNotification()

            // Flow を購読する
            launch {
                // 秒が変化したら
                // TODO 動画時間を受け取ってプログレスバーが出せるようにする
                var prevCurrentPositionSec = 0
                progressCurrentPositionMs.collect {
                    it ?: return@collect
                    val second = (it.currentPositionMs / 1_000).toInt()
                    if (prevCurrentPositionSec != second) {
                        prevCurrentPositionSec = second
                        setForegroundNotification(second)
                    }
                }
            }

            // エンコードを開始する
            try {
                _isEncoding.value = true

                ReEncodeTool.encoder(
                    context = this@EncoderService,
                    inputUri = inputUri,
                    encoderParams = encoderParams,
                    onProgressCurrentPositionMs = { videoDurationMs, currentPositionMs ->
                        _progressCurrentPositionMs.value = ReEncodeProgressData(videoDurationMs, currentPositionMs)
                    }
                )
                Toast.makeText(this@EncoderService, getString(R.string.encoder_service_finish), Toast.LENGTH_SHORT).show()
            } finally {
                // コルーチンキャンセル時・エンコーダー終了時
                _isEncoding.value = false
                _progressCurrentPositionMs.value = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /** 終了する */
    fun stopEncode() {
        scope.coroutineContext.cancelChildren()
    }

    private fun setForegroundNotification(currentPositionSec: Int = 0) {
        // 通知ちゃんねる無ければ作る
        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW).apply {
                setName(getString(R.string.encoder_service_notification_channel_title))
            }.build()
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
            setContentTitle(getString(R.string.encoder_service_notification_title))
            setContentText("${getString(R.string.encoder_service_notification_description)} $currentPositionSec ${getString(R.string.seconds)}")
            setSmallIcon(R.drawable.android_himari_droid)
        }.build()

        // 一応 compat で
        // MEDIA_PROCESSING は Android 15 で追加されたため、14 の場合は SPECIAL_USE を使う。
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }

        // TODO ServiceCompat.startForeground が targetSdk=35 に対応したら Compat だけ使う
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
        }
    }

    /**
     * 再エンコードの進捗
     *
     * @param videoDurationMs 動画の時間
     * @param currentPositionMs エンコード済みの時間
     */
    data class ReEncodeProgressData(
        val videoDurationMs: Long,
        val currentPositionMs: Long
    )

    private class LocalBinder(service: EncoderService) : Binder() {
        val serviceRef = WeakReference(service)
        val service: EncoderService
            get() = serviceRef.get()!!
    }

    companion object {
        private const val NOTIFICATION_ID = 4545
        private const val NOTIFICATION_CHANNEL_ID = "running_foreground_service"

        fun bindService(
            context: Context,
            lifecycle: Lifecycle
        ) = callbackFlow {
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val encoderService = (service as LocalBinder).service
                    trySend(encoderService)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    trySend(null)
                }
            }
            // ライフサイクルを監視してバインド、バインド解除する
            val lifecycleObserver = object : DefaultLifecycleObserver {
                val intent = Intent(context, EncoderService::class.java)
                override fun onStart(owner: LifecycleOwner) {
                    super.onStart(owner)
                    context.startService(intent)
                    context.bindService(intent, serviceConnection, BIND_AUTO_CREATE)
                }

                override fun onStop(owner: LifecycleOwner) {
                    super.onStop(owner)
                    context.unbindService(serviceConnection)
                }
            }
            lifecycle.addObserver(lifecycleObserver)
            awaitClose { lifecycle.removeObserver(lifecycleObserver) }
        }
    }
}