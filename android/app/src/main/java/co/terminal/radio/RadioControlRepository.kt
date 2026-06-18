package co.terminal.radio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow

class RadioControlRepository(private val context: Context) {
    val state: StateFlow<PlaybackUiState> = RadioPlaybackStateHolder.state

    fun startAutoPlay() = sendAction(RadioServiceActions.ACTION_START_AUTO_PLAY)


    fun play() = sendAction(RadioServiceActions.ACTION_PLAY)

    fun pause() = sendAction(RadioServiceActions.ACTION_PAUSE)
    fun stop() = sendAction(RadioServiceActions.ACTION_STOP)
    fun reconnect() = sendAction(RadioServiceActions.ACTION_RECONNECT)

    private fun sendAction(action: String) {
        val intent = Intent(context, RadioPlaybackService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, intent)
    }
}
