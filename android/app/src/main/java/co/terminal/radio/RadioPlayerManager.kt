package co.terminal.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioDeviceInfo
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
    private var bluetoothRouteJob: Job? = null
    private var noisyReceiverRegistered = false
    private var bluetoothAudioConnected: Boolean? = null

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
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                syncBluetoothAudioRoute()
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
        startBluetoothRouteMonitor()
        startWatchdog()
        startElapsedTicker()
        observeNetwork()
    }

    fun autoPlay() {
        userPaused = false
        userStopped = false
        ensureStationsLoaded()
        if (!hasBluetoothAudioOutput()) {
            pause(userInitiated = false)
            return
        }
        val defaultStation = station ?: selectDefaultStation().also { station = it }
        playStation(defaultStation)
    }

    fun play() {
        userPaused = false
        userStopped = false
        if (!hasBluetoothAudioOutput()) {
            pause(userInitiated = false)
            return
        }
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
        if (!hasBluetoothAudioOutput()) {
            pause(userInitiated = false)
            return
        }
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
        bluetoothRouteJob?.cancel()
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
        if (!hasBluetoothAudioOutput()) {
            pause(userInitiated = false)
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
        if (userPaused || userStopped || !hasBluetoothAudioOutput()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!userPaused && !userStopped && hasBluetoothAudioOutput()) reconnect()
        }
    }

    private fun startWatchdog() {
        if (watchdogJob != null) return
        watchdogJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                if (hasBluetoothAudioOutput() && !player.isPlaying && !userPaused && !userStopped) {
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
                if (isOnline && hasBluetoothAudioOutput() && !userPaused && !userStopped && station != null && !player.isPlaying) {
                    resumeCurrentStation()
                }
            }
        }
    }

    private fun startBluetoothRouteMonitor() {
        if (bluetoothRouteJob != null) return
        bluetoothRouteJob = scope.launch {
            syncBluetoothAudioRoute()
            while (isActive) {
                delay(1_000L)
                syncBluetoothAudioRoute()
            }
        }
    }

    private fun syncBluetoothAudioRoute() {
        val connected = hasBluetoothAudioOutput()
        val previous = bluetoothAudioConnected
        bluetoothAudioConnected = connected

        if (previous == connected) return

        if (connected) {
            // 蓝牙音频设备刚连接：自动开始播放当前电台。
            if (!userStopped) {
                userPaused = false
                autoPlay()
            }
        } else {
            // 蓝牙音频断开：立即暂停，避免声音从手机扬声器继续播放。
            reconnectJob?.cancel()
            player.pause()
            publishPlayerState(PlaybackStatus.Paused)
        }
    }

    private fun hasBluetoothAudioOutput(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLE_BROADCAST,
                -> true
                else -> false
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
                // 手机上的其他播放器获得长期音频焦点时，立即暂停电台。
                // 不做定时恢复，避免与手机本机音频争抢播放。
                wasPlayingBeforeFocusLoss = player.isPlaying
                focusRecoveryJob?.cancel()
                player.pause()
                publishPlayerState(PlaybackStatus.Paused)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.volume = 0.4f
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.volume = 1f
                focusRecoveryJob?.cancel()
                // 只有短暂失焦时才自动恢复；长期失焦意味着手机本机正在播放。
                if (wasPlayingBeforeFocusLoss && !userPaused && !userStopped && hasBluetoothAudioOutput()) play()
            }
        }
    }

    private fun resumeCurrentStation() {
        if (!hasBluetoothAudioOutput()) {
            pause(userInitiated = false)
            return
        }
        val currentStation = station
        if (player.mediaItemCount == 0) {
            if (currentStation != null) {
                playStation(currentStation)
            }
            return
        }
        runCatching {
            if (!requestAudioFocus()) return@runCatching
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
