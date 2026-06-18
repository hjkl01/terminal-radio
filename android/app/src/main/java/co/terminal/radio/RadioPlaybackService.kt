package co.terminal.radio

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RadioPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playerManager: RadioPlayerManager
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        playerManager = RadioPlayerManager(applicationContext, serviceScope)
        playerManager.start()
        mediaSession = MediaSession.Builder(this, playerManager.player).build()
        serviceScope.launch {
            playerManager.state.collect { RadioPlaybackStateHolder.state.value = it }
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
}
