package co.terminal.radio

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

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
    MaterialTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = Color(0xFF071018),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF071018), Color(0xFF102B35), Color(0xFF071018)),
                        ),
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { Header() }
                item { NowPlayingCard(state = state) }
                item {
                    ControlPanel(
                        status = state.status,
                        onPrevious = onPrevious,
                        onPlay = onPlay,
                        onPause = onPause,
                        onNext = onNext,
                        onStop = onStop,
                        onReconnect = onReconnect,
                    )
                }
                item { LibraryActions(onImportM3u = onImportM3u, onRestoreBuiltIn = onRestoreBuiltIn) }
                item { PlaylistHeader(state = state) }
                items(state.stations, key = { it.url }) { station ->
                    StationRow(
                        station = station,
                        status = state.status,
                        selected = station.url == state.selectedStationUrl,
                        onClick = { onSelectStation(station.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Terminal Radio",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "CNR live stream dashboard",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9FB5BD),
        )
    }
}

@Composable
private fun NowPlayingCard(state: PlaybackUiState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF10242D)),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "正在播放",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF77D7FF),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.stationName.ifBlank { "未选择电台" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.currentUrl.ifBlank { "等待加载播放地址" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA7BBC2),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                PlaybackVisualizer(status = state.status)
            }
            StatusChipGrid(state = state)
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A1E22))
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFC7CB),
                )
            }
        }
    }
}

@Composable
private fun PlaybackVisualizer(status: PlaybackStatus) {
    val transition = rememberInfiniteTransition(label = "playback-visualizer")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (status == PlaybackStatus.Buffering) 720 else 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1400)),
        label = "wave",
    )
    val accent = when (status) {
        PlaybackStatus.Playing -> Color(0xFF7CFFCB)
        PlaybackStatus.Buffering -> Color(0xFFFFD166)
        PlaybackStatus.Paused -> Color(0xFFB8C4CC)
        PlaybackStatus.Stopped, PlaybackStatus.Idle -> Color(0xFF8CA3AD)
        PlaybackStatus.Error -> Color(0xFFFF6B6B)
    }
    val active = status == PlaybackStatus.Playing || status == PlaybackStatus.Buffering

    Box(
        modifier = Modifier
            .size(108.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xFF071820)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = accent.copy(alpha = if (active) 0.14f * pulse else 0.09f), radius = size.minDimension * 0.43f)
            drawArc(
                color = accent.copy(alpha = 0.72f),
                startAngle = -90f + (if (active) wave * 360f else 0f),
                sweepAngle = if (active) 250f else 96f,
                useCenter = false,
                topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                size = Size(size.width * 0.64f, size.height * 0.64f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )
            val barCount = 5
            val maxHeight = size.height * 0.38f
            repeat(barCount) { index ->
                val phase = ((wave + index * 0.18f) % 1f)
                val heightFactor = if (active) 0.35f + phase * 0.65f else 0.32f
                val x = center.x - 24.dp.toPx() + index * 12.dp.toPx()
                val barHeight = maxHeight * heightFactor
                drawLine(
                    color = accent,
                    start = Offset(x, center.y + barHeight / 2f),
                    end = Offset(x, center.y - barHeight / 2f),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(
            text = status.iconText(),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusChipGrid(state: PlaybackUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusChip(modifier = Modifier.weight(1f), label = "状态", value = state.status.displayName())
            StatusChip(
                modifier = Modifier.weight(1f),
                label = "网络",
                value = if (state.isNetworkAvailable) "在线" else "离线",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusChip(modifier = Modifier.weight(1f), label = "时长", value = state.elapsedMs.formatDuration())
            StatusChip(modifier = Modifier.weight(1f), label = "来源", value = state.sourceName)
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF88A1AA))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ControlPanel(
    status: PlaybackStatus,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onReconnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1D25)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onPrevious) { Text("上一台") }
                Button(modifier = Modifier.weight(1.15f), onClick = onPlay) { Text(if (status == PlaybackStatus.Paused) "继续" else "播放") }
                Button(modifier = Modifier.weight(1f), onClick = onPause) { Text("暂停") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onNext) { Text("下一台") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onStop) { Text("停止") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onReconnect) { Text("重新连接") }
            }
        }
    }
}

@Composable
private fun LibraryActions(
    onImportM3u: () -> Unit,
    onRestoreBuiltIn: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(modifier = Modifier.weight(1f), onClick = onImportM3u) { Text("导入 m3u") }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = onRestoreBuiltIn) { Text("恢复内置") }
    }
}

@Composable
private fun PlaylistHeader(state: PlaybackUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "播放列表",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${state.sourceName} · ${state.stations.size} 个电台",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF93AAB3),
            )
        }
        Text(
            text = state.status.displayName(),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF7CFFCB),
        )
    }
}

@Composable
private fun StationRow(
    station: Station,
    status: PlaybackStatus,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                Color(0xFF163C45)
            } else {
                Color(0xFF0E1C23)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StationIndicator(selected = selected, status = status)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = station.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8FA7B0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text(
                    text = "当前",
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF7CFFCB).copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF7CFFCB),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StationIndicator(selected: Boolean, status: PlaybackStatus) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) Color(0xFF7CFFCB).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            PlaybackMiniBars(active = status == PlaybackStatus.Playing || status == PlaybackStatus.Buffering)
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF60737B)),
            )
        }
    }
}

@Composable
private fun PlaybackMiniBars(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "station-bars")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900)),
        label = "station-wave",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val factor = if (active) 0.35f + ((wave + index * 0.25f) % 1f) * 0.65f else 0.35f
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((8 + factor * 16).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF7CFFCB)),
            )
        }
    }
}

private fun PlaybackStatus.displayName(): String = when (this) {
    PlaybackStatus.Idle -> "初始化"
    PlaybackStatus.Buffering -> "缓冲中"
    PlaybackStatus.Playing -> "播放中"
    PlaybackStatus.Paused -> "已暂停"
    PlaybackStatus.Stopped -> "已停止"
    PlaybackStatus.Error -> "播放失败"
}

private fun PlaybackStatus.iconText(): String = when (this) {
    PlaybackStatus.Playing -> "ON"
    PlaybackStatus.Buffering -> "···"
    PlaybackStatus.Paused -> "II"
    PlaybackStatus.Stopped -> "■"
    PlaybackStatus.Idle -> "--"
    PlaybackStatus.Error -> "!"
}

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
