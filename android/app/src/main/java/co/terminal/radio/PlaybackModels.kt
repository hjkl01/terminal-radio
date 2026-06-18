package co.terminal.radio

data class Station(
    val name: String,
    val url: String,
)

enum class PlaybackStatus {
    Idle,
    Buffering,
    Playing,
    Paused,
    Stopped,
    Error,
}

data class PlaybackUiState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val stationName: String = "音乐之声",
    val currentUrl: String = "",
    val isNetworkAvailable: Boolean = true,
    val elapsedMs: Long = 0L,
    val errorMessage: String? = null,
)

object RadioServiceActions {
    const val ACTION_START_AUTO_PLAY = "co.terminal.radio.action.START_AUTO_PLAY"
    const val ACTION_PLAY = "co.terminal.radio.action.PLAY"
    const val ACTION_PAUSE = "co.terminal.radio.action.PAUSE"
    const val ACTION_STOP = "co.terminal.radio.action.STOP"
    const val ACTION_RECONNECT = "co.terminal.radio.action.RECONNECT"
}

object RadioPlaybackStateHolder {
    val state = kotlinx.coroutines.flow.MutableStateFlow(PlaybackUiState())
}
