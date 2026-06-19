package co.terminal.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RadioPlaybackService : MediaSessionService() {
    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "radio_playback"
        const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playerManager: RadioPlayerManager
    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        playerManager = RadioPlayerManager(applicationContext, serviceScope)
        playerManager.start()
        mediaSession = MediaSession.Builder(this, playerManager.player).build()
        startInForeground(PlaybackUiState())
        serviceScope.launch {
            playerManager.state.collect { state ->
                RadioPlaybackStateHolder.state.value = state
                notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action ?: RadioServiceActions.ACTION_START_AUTO_PLAY) {
            RadioServiceActions.ACTION_START_AUTO_PLAY -> playerManager.autoPlay()
            RadioServiceActions.ACTION_PLAY -> playerManager.play()
            RadioServiceActions.ACTION_PAUSE -> playerManager.pause()
            RadioServiceActions.ACTION_STOP -> {
                playerManager.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            RadioServiceActions.ACTION_RECONNECT -> playerManager.reconnect()
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        mediaSession.release()
        playerManager.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(state: PlaybackUiState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(state),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(state), 0)
        }
    }

    private fun buildNotification(state: PlaybackUiState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val playPauseAction = if (state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering) {
            NotificationCompat.Action(
                R.drawable.ic_notification_pause,
                "暂停",
                servicePendingIntent(RadioServiceActions.ACTION_PAUSE, 1),
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_notification_play,
                "播放",
                servicePendingIntent(RadioServiceActions.ACTION_PLAY, 2),
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentTitle(state.stationName.ifBlank { "Terminal Radio" })
            .setContentText(state.status.displayText())
            .setContentIntent(openAppIntent)
            .setOngoing(state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_skip_next,
                    "重连",
                    servicePendingIntent(RadioServiceActions.ACTION_RECONNECT, 3),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "停止",
                    servicePendingIntent(RadioServiceActions.ACTION_STOP, 4),
                ),
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, RadioPlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "电台播放",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Terminal Radio 后台播放控制"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun PlaybackStatus.displayText(): String = when (this) {
        PlaybackStatus.Idle -> "准备播放"
        PlaybackStatus.Buffering -> "缓冲中"
        PlaybackStatus.Playing -> "播放中"
        PlaybackStatus.Paused -> "已暂停"
        PlaybackStatus.Stopped -> "已停止"
        PlaybackStatus.Error -> "播放失败"
    }
}
