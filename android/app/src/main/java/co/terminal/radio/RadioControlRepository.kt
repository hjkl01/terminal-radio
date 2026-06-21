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
    fun previous() = sendAction(RadioServiceActions.ACTION_PREVIOUS)
    fun next() = sendAction(RadioServiceActions.ACTION_NEXT)
    fun restoreBuiltInStations() = sendAction(RadioServiceActions.ACTION_RESTORE_BUILT_IN)

    fun selectStation(url: String) = sendAction(
        action = RadioServiceActions.ACTION_SELECT_STATION,
        configure = { putExtra(RadioServiceActions.EXTRA_STATION_URL, url) },
    )

    fun importM3u(rawContent: String) = sendAction(
        action = RadioServiceActions.ACTION_IMPORT_M3U,
        configure = { putExtra(RadioServiceActions.EXTRA_M3U_CONTENT, rawContent) },
    )

    private fun sendAction(action: String, configure: Intent.() -> Unit = {}) {
        val intent = Intent(context, RadioPlaybackService::class.java).setAction(action).apply(configure)
        if (action.startsPlayback()) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    private fun String.startsPlayback(): Boolean = this == RadioServiceActions.ACTION_START_AUTO_PLAY ||
        this == RadioServiceActions.ACTION_PLAY ||
        this == RadioServiceActions.ACTION_SELECT_STATION ||
        this == RadioServiceActions.ACTION_PREVIOUS ||
        this == RadioServiceActions.ACTION_NEXT ||
        this == RadioServiceActions.ACTION_IMPORT_M3U ||
        this == RadioServiceActions.ACTION_RESTORE_BUILT_IN
}
