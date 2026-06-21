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
    val stations: List<Station> = emptyList(),
    val selectedStationUrl: String = "",
    val sourceName: String = "内置列表",
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
    const val ACTION_SELECT_STATION = "co.terminal.radio.action.SELECT_STATION"
    const val ACTION_PREVIOUS = "co.terminal.radio.action.PREVIOUS"
    const val ACTION_NEXT = "co.terminal.radio.action.NEXT"
    const val ACTION_IMPORT_M3U = "co.terminal.radio.action.IMPORT_M3U"
    const val ACTION_RESTORE_BUILT_IN = "co.terminal.radio.action.RESTORE_BUILT_IN"

    const val EXTRA_STATION_URL = "co.terminal.radio.extra.STATION_URL"
    const val EXTRA_M3U_CONTENT = "co.terminal.radio.extra.M3U_CONTENT"
}

object RadioPlaybackStateHolder {
    val state = kotlinx.coroutines.flow.MutableStateFlow(PlaybackUiState())
}
