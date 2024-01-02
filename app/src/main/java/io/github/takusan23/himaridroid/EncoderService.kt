package io.github.takusan23.himaridroid

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.lang.ref.WeakReference

/** エンコードするためのフォアグラウンドサービス。時間がかかるのでフォアグラウンドサービスでやる。 */
class EncoderService : Service() {

    private val scope = MainScope()
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    /** バインドする */
    private val localBinder = LocalBinder(this)

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onDestroy() {
        super.onDestroy()
        forceStop()
    }

    /** エンコードを開始する */
    fun startEncode() {
        // フォアグラウンドサービスに昇格する
        setForegroundNotification()
    }

    /** エンコードを強制終了する */
    fun forceStop() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun setForegroundNotification() {
        // 通知ちゃんねる無ければ作る
        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationCompat.PRIORITY_LOW).apply {
                setName("エンコーダーサービス実行中通知")
            }.build()
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
            setContentTitle("再エンコード中です")
            setContentText("進捗")
            setSmallIcon(R.drawable.ic_launcher_foreground)
        }.build()
        // 一応 compat で
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

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