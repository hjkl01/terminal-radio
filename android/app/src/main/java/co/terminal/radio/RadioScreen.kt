package co.terminal.radio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

private val NightTop = Color(0xFF070B13)
private val NightMid = Color(0xFF0D1524)
private val NightBottom = Color(0xFF080D16)
private val CardSurface = Color(0xFF152030)
private val Accent = Color(0xFF5CE1D0)
private val AccentDeep = Color(0xFF2AA79B)
private val Warm = Color(0xFFFFC078)
private val Danger = Color(0xFFFF8A8A)
private val TextPrimary = Color(0xFFF2F6FC)
private val TextMuted = Color(0xFF93A2B8)

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
    var stationPickerVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightTop, NightMid, NightBottom))),
    ) {
        Box(
            Modifier
                .size(320.dp)
                .offset(x = 140.dp, y = (-160).dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.18f), Color.Transparent))),
        )
        Box(
            Modifier
                .size(260.dp)
                .offset(x = (-120).dp, y = 320.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Warm.copy(alpha = 0.12f), Color.Transparent))),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { ScreenHeader(state.isBluetoothConnected) }
            item { NowPlayingCard(state) }
            item {
                TransportControls(
                    enabled = state.isBluetoothConnected,
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
                StationListEntry(
                    state = state,
                    onOpen = { stationPickerVisible = true },
                )
            }
            item { ScreenFooter(state) }
        }
    }

    if (stationPickerVisible) {
        StationPickerDialog(
            state = state,
            onDismiss = { stationPickerVisible = false },
            onSelect = { url ->
                onSelectStation(url)
                stationPickerVisible = false
            },
            onImportM3u = {
                stationPickerVisible = false
                onImportM3u()
            },
            onRestoreBuiltIn = {
                stationPickerVisible = false
                onRestoreBuiltIn()
            },
        )
    }
}

@Composable
private fun ScreenHeader(bluetoothConnected: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Accent, AccentDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Text("TR", style = MaterialTheme.typography.labelLarge, color = Color(0xFF04121A), fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Terminal Radio", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
            Text("车载电台 · 蓝牙优先", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        StatusPill(
            text = if (bluetoothConnected) "蓝牙已连接" else "等待蓝牙",
            color = if (bluetoothConnected) Accent else Warm,
        )
    }
}

@Composable
private fun NowPlayingCard(state: PlaybackUiState) {
    val active = state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1C2C42), Color(0xFF121B2A))))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(30.dp)),
    ) {
        Box(
            Modifier
                .size(190.dp)
                .offset(x = 150.dp, y = (-90).dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.22f), Color.Transparent))),
        )
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.8.sp,
                )
                EqualizerBars(
                    active = active,
                    color = Accent,
                    modifier = Modifier.width(34.dp).height(16.dp),
                )
                Box(Modifier.weight(1f))
                StatusPill(text = state.status.displayName(), color = state.status.tint())
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = state.stationName.ifBlank { "未选择电台" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.currentUrl.ifBlank { "尚未加载播放地址" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                InfoTile(Modifier.weight(1f), "来源", state.sourceName)
                InfoTile(Modifier.weight(1f), "网络", if (state.isNetworkAvailable) "在线" else "离线", if (state.isNetworkAvailable) Accent else Danger)
                InfoTile(Modifier.weight(1f), "时长", state.elapsedMs.formatDuration())
            }

            state.errorMessage?.let { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Warm.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Warm))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = Warm)
                }
            }
        }
    }
}

@Composable
private fun InfoTile(
    modifier: Modifier,
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EqualizerBars(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    pattern: List<Float> = listOf(0.45f, 0.85f, 0.6f, 1f, 0.5f),
) {
    if (!active) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            pattern.forEach { value ->
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight(value * 0.4f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = 0.35f)),
                )
            }
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "equalizer")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        pattern.forEachIndexed { index, value ->
            val height by transition.animateFloat(
                initialValue = value * 0.3f,
                targetValue = value,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 380 + index * 90, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(height.coerceAtLeast(0.08f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun TransportControls(
    enabled: Boolean,
    status: PlaybackStatus,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onReconnect: () -> Unit,
) {
    val playing = status == PlaybackStatus.Playing || status == PlaybackStatus.Buffering

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CardSurface)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(onClick = onPrevious, label = "上一台") {
                SkipGlyph(forward = false, color = TextPrimary, modifier = Modifier.size(22.dp))
            }
            PrimaryPlayButton(
                playing = playing,
                enabled = enabled,
                onClick = { if (playing) onPause() else onPlay() },
            )
            CircleIconButton(onClick = onNext, label = "下一台") {
                SkipGlyph(forward = true, color = TextPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            ) { Text("停止", style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = onReconnect,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                border = BorderStroke(1.dp, Accent.copy(alpha = 0.35f)),
            ) { Text("重新连接", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun PrimaryPlayButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(if (enabled) Brush.linearGradient(listOf(Accent.copy(alpha = 0.28f), Color.Transparent)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
        )
        Box(
            Modifier
                .size(70.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Accent, AccentDeep)))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseGlyph(
                playing = playing,
                color = Color(0xFF04121A),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    label: String,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun StationListEntry(state: PlaybackUiState, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardSurface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { index ->
                    Box(Modifier.width((13 - index * 3).dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Accent))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("电台列表", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "${state.sourceName} · 共 ${state.stations.size} 个电台",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "切换",
            style = MaterialTheme.typography.labelLarge,
            color = Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Accent.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ScreenFooter(state: PlaybackUiState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (state.isBluetoothConnected) Accent else Warm),
        )
        Text(
            text = if (state.isBluetoothConnected) "蓝牙音频已连接，正在车机播放就绪状态" else "未连接蓝牙音频时不会通过手机扬声器播放",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StationPickerDialog(
    state: PlaybackUiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onImportM3u: () -> Unit,
    onRestoreBuiltIn: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF101A28),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 20.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("选择电台", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "${state.sourceName} · ${state.stations.size} 个电台",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        CloseGlyph(color = TextMuted, modifier = Modifier.size(13.dp))
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

                if (state.stations.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("暂无电台", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("可以导入 m3u 播放列表", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.stations, key = { it.url }) { station ->
                            StationPickerRow(
                                station = station,
                                selected = station.url == state.selectedStationUrl,
                                playing = state.status == PlaybackStatus.Playing && station.url == state.selectedStationUrl,
                                onClick = { onSelect(station.url) },
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onImportM3u, modifier = Modifier.weight(1f)) {
                        Text("导入 M3U", style = MaterialTheme.typography.labelLarge, color = Accent, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.1f)).align(Alignment.CenterVertically))
                    TextButton(onClick = onRestoreBuiltIn, modifier = Modifier.weight(1f)) {
                        Text("恢复内置", style = MaterialTheme.typography.labelLarge, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StationPickerRow(
    station: Station,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (selected) Accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (playing) {
                EqualizerBars(
                    active = true,
                    color = Accent,
                    modifier = Modifier.width(16.dp).height(12.dp),
                    pattern = listOf(0.5f, 1f, 0.65f),
                )
            } else {
                PlayPauseGlyph(
                    playing = false,
                    color = if (selected) Accent else TextMuted,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                station.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) Accent else TextPrimary,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                station.url,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Text(
                if (playing) "播放中" else "当前",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayPauseGlyph(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (playing) {
            val barWidth = size.width * 0.24f
            val gap = size.width * 0.18f
            val left = size.width / 2f - gap / 2f - barWidth
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(left + barWidth + gap, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.16f, 0f)
                lineTo(size.width * 0.96f, size.height / 2f)
                lineTo(size.width * 0.16f, size.height)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun SkipGlyph(forward: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val barWidth = size.width * 0.14f
        val triangleWidth = size.width * 0.62f
        val gap = size.width * 0.08f

        if (forward) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(triangleWidth, size.height / 2f)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, color)
            drawRoundRect(
                color = color,
                topLeft = Offset(triangleWidth + gap, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        } else {
            val path = Path().apply {
                moveTo(size.width, 0f)
                lineTo(size.width - triangleWidth, size.height / 2f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, color)
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun CloseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.width * 0.16f
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun PlaybackStatus.tint(): Color = when (this) {
    PlaybackStatus.Playing -> Accent
    PlaybackStatus.Buffering -> Warm
    PlaybackStatus.Error -> Danger
    PlaybackStatus.Idle, PlaybackStatus.Paused, PlaybackStatus.Stopped -> TextMuted
}

private fun PlaybackStatus.displayName(): String = when (this) {
    PlaybackStatus.Idle -> "待机"
    PlaybackStatus.Buffering -> "缓冲中"
    PlaybackStatus.Playing -> "播放中"
    PlaybackStatus.Paused -> "已暂停"
    PlaybackStatus.Stopped -> "已停止"
    PlaybackStatus.Error -> "播放失败"
}

private fun Long.formatDuration(): String {
    val total = this / 1_000L
    val h = total / 3_600L
    val m = (total % 3_600L) / 60L
    val s = total % 60L
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}