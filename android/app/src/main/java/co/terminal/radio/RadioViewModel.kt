package co.terminal.radio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RadioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RadioControlRepository(application.applicationContext)

    val uiState: StateFlow<PlaybackUiState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PlaybackUiState(),
    )

    fun startAutoPlay() = repository.startAutoPlay()
    fun play() = repository.play()
    fun pause() = repository.pause()
    fun stop() = repository.stop()
    fun reconnect() = repository.reconnect()
}
