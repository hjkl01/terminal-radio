package co.terminal.radio

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

private val Bg = Color(0xFFF5F7FA)
private val Ink = Color(0xFF17212B)
private val Muted = Color(0xFF71808C)
private val Accent = Color(0xFF087F8C)
private val AccentSoft = Color(0xFFE1F4F3)

@Composable
fun RadioScreen(
    state: PlaybackUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onReconnect: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectStation: (String) -> Unit,
    onImportM3u: () -> Unit,
    onRestoreBuiltIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = Bg) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Terminal Radio", style = MaterialTheme.typography.headlineMedium, color = Ink, fontWeight = FontWeight.ExtraBold)
                        Text("随时收听 · 蓝牙优先", style = MaterialTheme.typography.bodyMedium, color = Muted)
                    }
                    BluetoothDot(state.isBluetoothConnected)
                }
            }
            item { BluetoothBanner(state.isBluetoothConnected) }
            item { NowPlaying(state) }
            item {
                Controls(
                    bluetoothConnected = state.isBluetoothConnected,
                    status = state.status,
                    onPrevious = onPrevious,
                    onPlay = onPlay,
                    onPause = onPause,
                    onNext = onNext,
                    onStop = onStop,
                    onReconnect = onReconnect,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                    Button(Modifier.weight(1f), onClick = onImportM3u, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("＋ 导入 M3U") }
                    OutlinedButton(Modifier.weight(1f), onClick = onRestoreBuiltIn, shape = RoundedCornerShape(14.dp)) { Text("恢复内置") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("电台列表", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.ExtraBold)
                        Text("${state.stations.size} 个", style = MaterialTheme.typography.labelMedium, color = Muted)
                    }
                    Text("未连接蓝牙时不会从手机扬声器主动播放", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            items(state.stations, key = { it.url }) { station ->
                StationRow(
                    station = station,
                    selected = station.url == state.selectedStationUrl,
                    playing = state.status == PlaybackStatus.Playing && station.url == state.selectedStationUrl,
                    onClick = { onSelectStation(station.url) },
                )
            }
        }
    }
}

@Composable
private fun BluetoothDot(connected: Boolean) {
    Box(
        modifier = Modifier.size(42.dp).clip(CircleShape).background(if (connected) AccentSoft else Color(0xFFE8EDF0)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(if (connected) Accent else Color(0xFF9AA7AF)))
    }
}

@Composable
private fun BluetoothBanner(connected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (connected) AccentSoft else Color(0xFFFFF3E0)),
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), Alignment.CenterVertically, Arrangement.spacedBy(12.dp)) {
            Text(if (connected) "♫" else "⌁", style = MaterialTheme.typography.titleLarge, color = if (connected) Accent else Color(0xFFB76E00))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (connected) "蓝牙音频已连接" else "等待蓝牙音频设备", style = MaterialTheme.typography.titleSmall, color = Ink, fontWeight = FontWeight.Bold)
                Text(if (connected) "电台可以播放" else "连接后才会自动播放", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Text(if (connected) "已连接" else "未连接", style = MaterialTheme.typography.labelMedium, color = if (connected) Accent else Color(0xFFB76E00), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NowPlaying(state: PlaybackUiState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = Accent, fontWeight = FontWeight.ExtraBold)
                    Text(state.stationName.ifBlank { "未选择电台" }, style = MaterialTheme.typography.headlineSmall, color = Ink, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(state.sourceName, style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                Spacer(Modifier.width(14.dp))
                PlaybackOrb(state.status)
            }
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(9.dp)) {
                InfoTile(Modifier.weight(1f), "状态", state.status.displayName())
                InfoTile(Modifier.weight(1f), "网络", if (state.isNetworkAvailable) "在线" else "离线")
                InfoTile(Modifier.weight(1f), "时长", state.elapsedMs.formatDuration())
            }
            state.errorMessage?.let { message ->
                Text(message, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFE9E9)).padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB3261E))
            }
        }
    }
}

@Composable
private fun PlaybackOrb(status: PlaybackStatus) {
    val active = status == PlaybackStatus.Playing || status == PlaybackStatus.Buffering
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (status == PlaybackStatus.Buffering) 650 else 1000), RepeatMode.Reverse),
        label = "pulse",
    )
    val text = when (status) {
        PlaybackStatus.Playing -> "▶"
        PlaybackStatus.Buffering -> "…"
        PlaybackStatus.Paused -> "Ⅱ"
        PlaybackStatus.Stopped -> "■"
        PlaybackStatus.Idle -> "—"
        PlaybackStatus.Error -> "!"
    }
    Box(Modifier.size(82.dp).clip(RoundedCornerShape(26.dp)).background(if (status == PlaybackStatus.Playing) AccentSoft else Color(0xFFE8EDF0)), Alignment.Center) {
        Box(Modifier.size(if (active) (48 * pulse).dp else 42.dp).clip(CircleShape).background(Color.White), Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = Accent, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun InfoTile(modifier: Modifier, label: String, value: String) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Color(0xFFF4F6F7)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        Text(value, style = MaterialTheme.typography.labelLarge, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Controls(
    bluetoothConnected: Boolean,
    status: PlaybackStatus,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onReconnect: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                OutlinedButton(Modifier.weight(1f).height(48.dp), onClick = onPrevious, shape = RoundedCornerShape(16.dp)) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                Button(Modifier.weight(1.45f).height(48.dp), onClick = onPlay, enabled = bluetoothConnected, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text(if (status == PlaybackStatus.Paused) "继续播放" else "播放", fontWeight = FontWeight.Bold) }
                OutlinedButton(Modifier.weight(1f).height(48.dp), onClick = onPause, shape = RoundedCornerShape(16.dp)) { Text("Ⅱ") }
                OutlinedButton(Modifier.weight(1f).height(48.dp), onClick = onNext, shape = RoundedCornerShape(16.dp)) { Text("›", style = MaterialTheme.typography.titleLarge) }
            }
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                OutlinedButton(Modifier.weight(1f), onClick = onStop, shape = RoundedCornerShape(14.dp)) { Text("停止") }
                OutlinedButton(Modifier.weight(1f), onClick = onReconnect, shape = RoundedCornerShape(14.dp)) { Text("重新连接") }
            }
        }
    }
}

@Composable
private fun StationRow(station: Station, selected: Boolean, playing: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (selected) AccentSoft else Color.White)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), Alignment.CenterVertically, Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) Color.White else Color(0xFFF0F3F5)), Alignment.Center) { Text(if (playing) "▶" else "♪", color = if (selected) Accent else Muted, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(station.name, style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(station.url, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Text("当前", style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun PlaybackStatus.displayName(): String = when (this) {
    PlaybackStatus.Idle -> "待机"
    PlaybackStatus.Buffering -> "缓冲"
    PlaybackStatus.Playing -> "播放中"
    PlaybackStatus.Paused -> "已暂停"
    PlaybackStatus.Stopped -> "已停止"
    PlaybackStatus.Error -> "失败"
}

private fun Long.formatDuration(): String {
    val total = this / 1_000L
    val h = total / 3_600L
    val m = (total % 3_600L) / 60L
    val s = total % 60L
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
