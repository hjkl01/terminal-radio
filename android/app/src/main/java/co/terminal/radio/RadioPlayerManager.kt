package co.terminal.radio

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class RadioPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val CUSTOM_M3U_FILE = "custom.m3u"
        const val BUILT_IN_SOURCE = "内置列表"
        const val CUSTOM_SOURCE = "自定义列表"
        const val DEFAULT_STATION_NAME = "音乐之声"
    }

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            false,
        )
        setWakeMode(C.WAKE_MODE_NETWORK)
    }

    private val networkMonitor = NetworkMonitor(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()
    private var stations: List<Station> = emptyList()
    private var station: Station? = null
    private var sourceName = BUILT_IN_SOURCE
    private var userPaused = false
    private var userStopped = false
    private var wasPlayingBeforeFocusLoss = false
    private var reconnectJob: Job? = null
    private var focusRecoveryJob: Job? = null
    private var watchdogJob: Job? = null
    private var elapsedJob: Job? = null
    private var networkJob: Job? = null
    private var noisyReceiverRegistered = false

    private val audioFocusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(::handleAudioFocusChange)
            .build()
    } else {
        null
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                -> safetyPause()
            }
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                publishPlayerState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publishPlayerState()
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(
                    status = PlaybackStatus.Error,
                    errorMessage = error.localizedMessage ?: "播放失败",
                )
                scheduleReconnect(3_000L)
            }
        })
    }

    fun start() {
        ensureStationsLoaded()
        networkMonitor.start()
        registerNoisyReceiver()
        startWatchdog()
        startElapsedTicker()
        observeNetwork()
    }

    fun autoPlay() {
        userPaused = false
        userStopped = false
        ensureStationsLoaded()
        val defaultStation = station ?: selectDefaultStation().also { station = it }
        playStation(defaultStation)
    }

    fun play() {
        userPaused = false
        userStopped = false
        if (station == null || player.mediaItemCount == 0) {
            autoPlay()
            return
        }
        if (requestAudioFocus()) {
            player.prepare()
            player.play()
            publishPlayerState()
        }
    }

    fun pause(userInitiated: Boolean = true) {
        if (userInitiated) userPaused = true
        player.pause()
        publishPlayerState(PlaybackStatus.Paused)
    }

    fun stop() {
        userStopped = true
        userPaused = false
        reconnectJob?.cancel()
        player.stop()
        publishPlayerState(PlaybackStatus.Stopped)
    }

    fun reconnect() {
        userPaused = false
        userStopped = false
        reconnectJob?.cancel()
        ensureStationsLoaded()
        val currentStation = station ?: selectDefaultStation().also { station = it }
        playStation(currentStation)
    }

    fun selectStation(url: String) {
        ensureStationsLoaded()
        val selectedStation = stations.firstOrNull { it.url == url } ?: return
        userPaused = false
        userStopped = false
        reconnectJob?.cancel()
        playStation(selectedStation)
    }

    fun playPrevious() {
        playStationAtOffset(-1)
    }

    fun playNext() {
        playStationAtOffset(1)
    }

    fun importM3u(rawContent: String) {
        val parsedStations = M3uParser.parse(rawContent)
        if (parsedStations.isEmpty()) {
            _state.value = _state.value.copy(
                status = PlaybackStatus.Error,
                errorMessage = "导入的 m3u 没有可播放地址",
            )
            return
        }
        customM3uFile().writeText(rawContent)
        stations = parsedStations
        sourceName = CUSTOM_SOURCE
        userPaused = false
        userStopped = false
        val selectedStation = parsedStations.firstOrNull { it.name == DEFAULT_STATION_NAME } ?: parsedStations.first()
        playStation(selectedStation)
    }

    fun restoreBuiltInStations() {
        customM3uFile().delete()
        stations = loadBuiltInStations()
        sourceName = BUILT_IN_SOURCE
        userPaused = false
        userStopped = false
        val selectedStation = selectDefaultStation()
        playStation(selectedStation)
    }

    fun release() {
        reconnectJob?.cancel()
        focusRecoveryJob?.cancel()
        watchdogJob?.cancel()
        elapsedJob?.cancel()
        networkJob?.cancel()
        networkMonitor.stop()
        unregisterNoisyReceiver()
        abandonAudioFocus()
        player.release()
    }

    private fun playStation(station: Station) {
        this.station = station
        if (station.url.isBlank()) {
            _state.value = _state.value.copy(
                status = PlaybackStatus.Error,
                stationName = station.name,
                currentUrl = "",
                stations = stations,
                selectedStationUrl = station.url,
                sourceName = sourceName,
                errorMessage = "未找到可播放的电台地址",
            )
            return
        }
        if (!requestAudioFocus()) return
        val mediaItem = MediaItem.Builder()
            .setUri(station.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(station.name).build())
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        _state.value = _state.value.copy(
            status = PlaybackStatus.Buffering,
            stationName = station.name,
            currentUrl = station.url,
            stations = stations,
            selectedStationUrl = station.url,
            sourceName = sourceName,
            errorMessage = null,
        )
    }

    private fun ensureStationsLoaded() {
        if (stations.isNotEmpty()) return
        val customFile = customM3uFile()
        if (customFile.exists()) {
            val customStations = M3uParser.parse(customFile.readText())
            if (customStations.isNotEmpty()) {
                stations = customStations
                sourceName = CUSTOM_SOURCE
                station = selectDefaultStation()
                publishSourceState()
                return
            }
        }
        stations = loadBuiltInStations()
        sourceName = BUILT_IN_SOURCE
        station = selectDefaultStation()
        publishSourceState()
    }

    private fun loadBuiltInStations(): List<Station> {
        val content = context.assets.open("cnr.m3u").use { input ->
            input.bufferedReader().readText()
        }
        return M3uParser.parse(content)
    }

    private fun selectDefaultStation(): Station = stations.firstOrNull { it.name == DEFAULT_STATION_NAME }
        ?: stations.firstOrNull()
        ?: Station(DEFAULT_STATION_NAME, "")

    private fun playStationAtOffset(offset: Int) {
        ensureStationsLoaded()
        if (stations.isEmpty()) return
        val currentIndex = stations.indexOfFirst { it.url == station?.url }.takeIf { it >= 0 } ?: 0
        val nextIndex = Math.floorMod(currentIndex + offset, stations.size)
        userPaused = false
        userStopped = false
        reconnectJob?.cancel()
        playStation(stations[nextIndex])
    }

    private fun safetyPause() {
        pause(userInitiated = true)
    }

    private fun customM3uFile(): File = File(context.filesDir, CUSTOM_M3U_FILE)

    private fun publishSourceState() {
        val currentStation = station ?: selectDefaultStation()
        _state.value = _state.value.copy(
            stationName = currentStation.name,
            currentUrl = currentStation.url,
            stations = stations,
            selectedStationUrl = currentStation.url,
            sourceName = sourceName,
        )
    }

    private fun scheduleReconnect(delayMs: Long) {
        if (userPaused || userStopped) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!userPaused && !userStopped) reconnect()
        }
    }

    private fun startWatchdog() {
        if (watchdogJob != null) return
        watchdogJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                if (!player.isPlaying && !userPaused && !userStopped) {
                    resumeCurrentStation()
                }
            }
        }
    }

    private fun startElapsedTicker() {
        if (elapsedJob != null) return
        elapsedJob = scope.launch {
            while (isActive) {
                delay(1_000L)
        _state.value = _state.value.copy(elapsedMs = player.currentPosition.coerceAtLeast(0L))
            }
        }
    }

    private fun observeNetwork() {
        if (networkJob != null) return
        networkJob = scope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _state.value = _state.value.copy(isNetworkAvailable = isOnline)
                if (isOnline && !userPaused && !userStopped && station != null && !player.isPlaying) {
                    resumeCurrentStation()
                }
            }
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeFocusLoss = player.isPlaying
                pause(userInitiated = false)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeFocusLoss = player.isPlaying
                player.pause()
                startFocusRecovery()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.volume = 0.4f
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.volume = 1f
                focusRecoveryJob?.cancel()
                if (wasPlayingBeforeFocusLoss && !userPaused && !userStopped) play()
            }
        }
    }

    private fun startFocusRecovery() {
        focusRecoveryJob?.cancel()
        focusRecoveryJob = scope.launch {
            while (isActive && !userPaused && !userStopped) {
                delay(10_000L)
                if (!player.isPlaying && requestAudioFocus()) {
                    resumeCurrentStation()
                    break
                }
            }
        }
    }

    private fun resumeCurrentStation() {
        val currentStation = station
        if (player.mediaItemCount == 0) {
            if (currentStation != null) {
                playStation(currentStation)
            }
            return
        }
        runCatching {
            player.prepare()
            player.play()
            publishPlayerState()
        }.onFailure { error ->
            _state.value = _state.value.copy(
                status = PlaybackStatus.Error,
                errorMessage = error.localizedMessage ?: "恢复播放失败",
            )
            scheduleReconnect(3_000L)
        }
    }

    private fun requestAudioFocus(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioFocusRequest?.let { audioManager.requestAudioFocus(it) } == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else {
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            ::handleAudioFocusChange,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN,
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(::handleAudioFocusChange)
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        noisyReceiverRegistered = true
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        noisyReceiverRegistered = false
        runCatching { context.unregisterReceiver(noisyReceiver) }
    }

    private fun publishPlayerState(forcedStatus: PlaybackStatus? = null) {
        val status = forcedStatus ?: when {
            player.isPlaying -> PlaybackStatus.Playing
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.Buffering
            userStopped -> PlaybackStatus.Stopped
            userPaused -> PlaybackStatus.Paused
            player.playbackState == Player.STATE_IDLE -> PlaybackStatus.Idle
            else -> _state.value.status
        }
        _state.value = _state.value.copy(
            status = status,
            elapsedMs = player.currentPosition.coerceAtLeast(0L),
            stations = stations,
            selectedStationUrl = station?.url.orEmpty(),
            sourceName = sourceName,
            errorMessage = if (status == PlaybackStatus.Error) _state.value.errorMessage else null,
        )
    }
}
