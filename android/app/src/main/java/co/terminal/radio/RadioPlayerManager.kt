package co.terminal.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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

class RadioPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
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
    private var station: Station? = null
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
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause(userInitiated = false)
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
        networkMonitor.start()
        registerNoisyReceiver()
        startWatchdog()
        startElapsedTicker()
        observeNetwork()
    }

    fun autoPlay() {
        userPaused = false
        userStopped = false
        val defaultStation = station ?: loadDefaultStation().also { station = it }
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
        val currentStation = station ?: loadDefaultStation().also { station = it }
        playStation(currentStation)
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
            errorMessage = null,
        )
    }

    private fun loadDefaultStation(): Station {
        val content = context.assets.open("cnr.m3u").use { input ->
            input.bufferedReader().readText()
        }
        val stations = M3uParser.parse(content)
        return stations.firstOrNull { it.name == "音乐之声" }
            ?: stations.firstOrNull()
            ?: Station("音乐之声", "")
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
                    player.prepare()
                    player.play()
                    publishPlayerState()
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
                    reconnect()
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
                    player.prepare()
                    player.play()
                    break
                }
            }
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
        context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
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
            errorMessage = if (status == PlaybackStatus.Error) _state.value.errorMessage else null,
        )
    }
}
