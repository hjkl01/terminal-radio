package co.terminal.radio

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class RadioApplication : Application() {

    lateinit var player: ExoPlayer

    companion object {
        const val CHANNEL_ID = "radio_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupPlayer()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Terminal Radio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Radio playback controls"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            prepare()
            playWhenReady = false
        }
    }

    fun playStation(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        updateNotification(isPlaying = true)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            updateNotification(isPlaying = false)
        } else {
            player.play()
            updateNotification(isPlaying = true)
        }
    }

    fun playNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNext()
            player.play()
        }
    }

    fun playPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
            player.play()
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun getCurrentStation(): Station? {
        val uri = player.currentMediaItem?.localConfiguration?.uri
        return uri?.toString()?.let { url ->
            // Try to get name from current media item meta
            Station(player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "", url)
        }
    }

    private fun updateNotification(isPlaying: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, RadioReceiver::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            this, 1, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = Intent(this, RadioReceiver::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            this, 2, nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = Intent(this, RadioReceiver::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            this, 3, prevIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentTitle("Terminal Radio")
            .setContentText(isPlaying.takeIf { it }?.let { "Playing" } ?: "Paused")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
                if (isPlaying) "Pause" else "Play",
                pausePendingIntent
            )
            .addAction(R.drawable.ic_skip_next, "Next", nextPendingIntent)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
