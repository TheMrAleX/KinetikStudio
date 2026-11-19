package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.AudioClip
import com.example.domain.model.ProjectTimeline
import com.example.domain.model.TextClip
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CardHighlight
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.FxViolet
import com.example.ui.theme.HairlineOutline
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.util.formatDurationShort
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MultiTrackTimeline(
    timeline: ProjectTimeline,
    playheadMs: Long,
    isPlaying: Boolean,
    pxPerSec: Float,
    selectedClipId: String?,
    selectedTextId: String?,
    selectedAudioId: String?,
    onSeekMs: (Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onSelectMainClip: (String) -> Unit,
    onTrimMainClip: (clipId: String, newStartMs: Long, newEndMs: Long) -> Unit,
    onOpenTransitionForClip: (String) -> Unit,
    onSelectTextClip: (String) -> Unit,
    onSelectAudioClip: (String) -> Unit,
    onAddClipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var programmaticScroll by remember { mutableStateOf(false) }

    val pxPerMs = pxPerSec / 1000f
    val currentPxPerSec by rememberUpdatedState(pxPerSec)
    val totalMs = timeline.totalDurationMs.coerceAtLeast(1000L)

    val currentPlayheadSeek by rememberUpdatedState(onSeekMs)
    val currentTotalDurationMs by rememberUpdatedState(timeline.totalDurationMs)

    // Sync scroll position to playhead while playing or seeking externally (never fight active user drag)
    LaunchedEffect(playheadMs, isPlaying, pxPerSec) {
        if (!isPlaying && scrollState.isScrollInProgress) return@LaunchedEffect
        val targetPx = (playheadMs * pxPerMs * density.density).roundToInt().coerceAtLeast(0)
        if (isPlaying || abs(scrollState.value - targetPx) > 14) {
            programmaticScroll = true
            try {
                scrollState.scrollTo(targetPx)
            } finally {
                programmaticScroll = false
            }
        }
    }

    // Update playhead smoothly when user manually scrubs timeline
    LaunchedEffect(scrollState, pxPerMs, density.density) {
        val scale = (pxPerMs * density.density).coerceAtLeast(0.001f)
        snapshotFlow { scrollState.value }
            .collect { offsetPx ->
                if (!programmaticScroll && !isPlaying && scrollState.isScrollInProgress) {
                    val ms = (offsetPx / scale).toLong()
                        .coerceIn(0L, currentTotalDurationMs.coerceAtLeast(0L))
                    currentPlayheadSeek(ms)
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (abs(zoom - 1f) > 0.01f) {
                        onZoomChange((currentPxPerSec * zoom).coerceIn(22f, 360f))
                    }
                }
            }
            .testTag("multitrack_timeline")
    ) {
        val halfViewportDp = maxWidth / 2
        val contentWidthDp = ((totalMs / 1000f) * pxPerSec).dp + 120.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(horizontal = halfViewportDp)
        ) {
            // Timecode Ruler
            TimeRulerRow(
                totalDurationMs = totalMs,
                pxPerSec = pxPerSec,
                widthDp = contentWidthDp
            )

            Spacer(Modifier.height(6.dp))

            // Main Video Track
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(64.dp)
            ) {
                timeline.mainClips.forEachIndexed { index, clip ->
                    if (index > 0) {
                        val hasTrans = clip.transitionIn.type != TransitionType.NONE
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (hasTrans) FxViolet else ElevatedSurface)
                                .border(
                                    1.dp,
                                    if (hasTrans) FxViolet else HairlineOutline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onOpenTransitionForClip(clip.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (hasTrans) "✦" else "⇄",
                                color = if (hasTrans) Color.White else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    MainVideoClipItem(
                        clip = clip,
                        isSelected = clip.id == selectedClipId,
                        pxPerMs = pxPerMs,
                        onSelect = { onSelectMainClip(clip.id) },
                        onTrimCommit = { newStart, newEnd ->
                            onTrimMainClip(clip.id, newStart, newEnd)
                        }
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Add more clips inline button
                Surface(
                    onClick = onAddClipClick,
                    shape = RoundedCornerShape(10.dp),
                    color = ElevatedSurface,
                    border = BorderStroke(1.dp, HairlineOutline),
                    modifier = Modifier
                        .size(width = 48.dp, height = 56.dp)
                        .testTag("timeline_add_clip_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir clip",
                            tint = ElectricCyan
                        )
                    }
                }
            }

            // Text Overlays Track
            if (timeline.textClips.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(contentWidthDp)
                        .height(30.dp)
                ) {
                    timeline.textClips.forEach { tc ->
                        TextTrackBar(
                            textClip = tc,
                            isSelected = tc.id == selectedTextId,
                            pxPerMs = pxPerMs,
                            onSelect = { onSelectTextClip(tc.id) }
                        )
                    }
                }
            }

            // Audio Waveforms Track
            if (timeline.audioClips.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(contentWidthDp)
                        .height(38.dp)
                ) {
                    timeline.audioClips.forEach { ac ->
                        AudioTrackBar(
                            audioClip = ac,
                            isSelected = ac.id == selectedAudioId,
                            pxPerMs = pxPerMs,
                            onSelect = { onSelectAudioClip(ac.id) }
                        )
                    }
                }
            }
        }

        // Fixed Center Playhead Needle
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(14.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val headPath = Path().apply {
                    moveTo(cx, 0f)
                    lineTo(cx + 6.dp.toPx(), 6.dp.toPx())
                    lineTo(cx, 12.dp.toPx())
                    lineTo(cx - 6.dp.toPx(), 6.dp.toPx())
                    close()
                }
                drawPath(headPath, color = ElectricCyan)
                drawLine(
                    color = ElectricCyan,
                    start = Offset(cx, 10.dp.toPx()),
                    end = Offset(cx, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun TimeRulerRow(
    totalDurationMs: Long,
    pxPerSec: Float,
    widthDp: Dp
) {
    val stepSec = when {
        pxPerSec >= 180f -> 0.5f
        pxPerSec >= 75f -> 1f
        pxPerSec >= 38f -> 2f
        else -> 5f
    }
    val stepMs = (stepSec * 1000f).toLong().coerceAtLeast(500L)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember { MonoTimecodeStyle.copy(fontSize = 10.sp, color = TextMuted) }

    Canvas(
        modifier = Modifier
            .width(widthDp)
            .height(24.dp)
    ) {
        var t = 0L
        while (t <= totalDurationMs + stepMs) {
            val x = (t / 1000f) * pxPerSec * density
            if (x <= size.width) {
                drawLine(
                    color = HairlineOutline,
                    start = Offset(x, size.height - 8.dp.toPx()),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                if (t <= totalDurationMs) {
                    val label = formatDurationShort(t)
                    val result = textMeasurer.measure(label, style = labelStyle)
                    if (x + result.size.width <= size.width) {
                        drawText(
                            textLayoutResult = result,
                            topLeft = Offset(x + 3.dp.toPx(), 1.dp.toPx())
                        )
                    }
                }
                val halfX = x + (stepSec * 0.5f) * pxPerSec * density
                if (halfX <= size.width) {
                    drawLine(
                        color = HairlineOutline.copy(alpha = 0.45f),
                        start = Offset(halfX, size.height - 4.dp.toPx()),
                        end = Offset(halfX, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            t += stepMs
        }
    }
}

@Composable
private fun MainVideoClipItem(
    clip: VideoClip,
    isSelected: Boolean,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onTrimCommit: (Long, Long) -> Unit
) {
    var dragLeftMs by remember(clip.id, clip.sourceStartMs) { mutableFloatStateOf(0f) }
    var dragRightMs by remember(clip.id, clip.sourceEndMs) { mutableFloatStateOf(0f) }
    var isTrimming by remember(clip.id) { mutableStateOf(false) }

    val safeDur = clip.sourceDurationMs.coerceAtLeast(100L)
    val previewStartMs = if (dragLeftMs != 0f) {
        val maxStart = (clip.sourceEndMs - 250L).coerceAtLeast(0L)
        (clip.sourceStartMs + (dragLeftMs * clip.speed).toLong()).coerceIn(0L, maxStart)
    } else {
        clip.sourceStartMs
    }
    val previewEndMs = if (dragRightMs != 0f) {
        val minEnd = (clip.sourceStartMs + 250L).coerceAtMost(safeDur)
        (clip.sourceEndMs + (dragRightMs * clip.speed).toLong()).coerceIn(minEnd, safeDur)
    } else {
        clip.sourceEndMs
    }
    val previewEffectiveDurationMs = (((previewEndMs - previewStartMs).coerceAtLeast(100L)) / clip.speed.coerceIn(0.1f, 10f))
        .toLong()
        .coerceAtLeast(100L)

    val clipWidthDp = (previewEffectiveDurationMs * pxPerMs).dp.coerceAtLeast(52.dp)

    val allKeyframeOffsets = remember(clip.keyframes) {
        clip.keyframes.values.flatten().map { it.timeOffsetMs }.distinct().sorted()
    }

    Box(
        modifier = Modifier
            .width(clipWidthDp)
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StudioSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ElectricCyan else HairlineOutline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onSelect)
            .testTag("timeline_clip_${clip.id}")
    ) {
        // Filmstrip thumbnails
        Row(modifier = Modifier.fillMaxSize()) {
            val thumbs = clip.thumbnailPaths
            if (thumbs.isNotEmpty()) {
                val slots = (clipWidthDp / 42.dp).toInt().coerceIn(1, 12)
                for (i in 0 until slots) {
                    val path = thumbs[(i * thumbs.size) / slots]
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ElevatedSurface)
                )
            }
        }

        // Subtle dark scrim for metadata readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg.copy(alpha = 0.25f))
        )

        // Speed or FX badge
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = if (isSelected) 20.dp else 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = if (isTrimming) ElectricCyan else ObsidianBg.copy(alpha = 0.78f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = formatDurationShort(previewEffectiveDurationMs),
                    style = MonoTimecodeStyle.copy(fontSize = 9.sp, fontWeight = if (isTrimming) FontWeight.Bold else FontWeight.Normal),
                    color = if (isTrimming) Color(0xFF002026) else TextPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            if (clip.speed != 1f) {
                Surface(
                    color = CoralAmber.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", clip.speed)}x",
                        style = MonoTimecodeStyle.copy(fontSize = 9.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Keyframe diamonds plotted along bottom of clip
        if (allKeyframeOffsets.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                val dur = clip.effectiveDurationMs.coerceAtLeast(1L).toFloat()
                allKeyframeOffsets.forEach { offsetMs ->
                    val frac = (offsetMs / dur).coerceIn(0f, 1f)
                    val cx = frac * size.width
                    val cy = size.height / 2f
                    val r = 4.dp.toPx()
                    val diamond = Path().apply {
                        moveTo(cx, cy - r)
                        lineTo(cx + r, cy)
                        lineTo(cx, cy + r)
                        lineTo(cx - r, cy)
                        close()
                    }
                    drawPath(diamond, color = ElectricCyan)
                }
            }
        }

        // Left & Right Trim Handles when selected
        if (isSelected) {
            val currentClip by rememberUpdatedState(clip)
            val currentOnTrimCommit by rememberUpdatedState(onTrimCommit)

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(18.dp)
                    .fillMaxHeight()
                    .background(ElectricCyan)
                    .pointerInput(clip.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragLeftMs = 0f
                                isTrimming = true
                            },
                            onDragCancel = {
                                dragLeftMs = 0f
                                isTrimming = false
                            },
                            onDragEnd = {
                                val c = currentClip
                                val maxStart = (c.sourceEndMs - 250L).coerceAtLeast(0L)
                                val nextStart = (c.sourceStartMs + (dragLeftMs * c.speed).toLong())
                                    .coerceIn(0L, maxStart)
                                currentOnTrimCommit(nextStart, c.sourceEndMs)
                                dragLeftMs = 0f
                                isTrimming = false
                            }
                        ) { _, dragAmount ->
                            val scale = (pxPerMs * density).coerceAtLeast(0.001f)
                            dragLeftMs += dragAmount / scale
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(22.dp)
                        .background(ObsidianBg, RoundedCornerShape(2.dp))
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(18.dp)
                    .fillMaxHeight()
                    .background(ElectricCyan)
                    .pointerInput(clip.id, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragRightMs = 0f
                                isTrimming = true
                            },
                            onDragCancel = {
                                dragRightMs = 0f
                                isTrimming = false
                            },
                            onDragEnd = {
                                val c = currentClip
                                val safeDur = c.sourceDurationMs.coerceAtLeast(100L)
                                val minEnd = (c.sourceStartMs + 250L).coerceAtMost(safeDur)
                                val nextEnd = (c.sourceEndMs + (dragRightMs * c.speed).toLong())
                                    .coerceIn(minEnd, safeDur)
                                currentOnTrimCommit(c.sourceStartMs, nextEnd)
                                dragRightMs = 0f
                                isTrimming = false
                            }
                        ) { _, dragAmount ->
                            val scale = (pxPerMs * density).coerceAtLeast(0.001f)
                            dragRightMs += dragAmount / scale
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(22.dp)
                        .background(ObsidianBg, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun TextTrackBar(
    textClip: TextClip,
    isSelected: Boolean,
    pxPerMs: Float,
    onSelect: () -> Unit
) {
    val startDp = (textClip.timelineStartMs * pxPerMs).dp
    val widthDp = (textClip.durationMs * pxPerMs).dp.coerceAtLeast(44.dp)
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(8.dp),
        color = TextGold.copy(alpha = 0.22f),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, TextGold),
        modifier = Modifier
            .offset(x = startDp)
            .width(widthDp)
            .fillMaxHeight()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Title, null, tint = TextGold, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = textClip.text,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AudioTrackBar(
    audioClip: AudioClip,
    isSelected: Boolean,
    pxPerMs: Float,
    onSelect: () -> Unit
) {
    val startDp = (audioClip.timelineStartMs * pxPerMs).dp
    val widthDp = (audioClip.durationMs * pxPerMs).dp.coerceAtLeast(52.dp)
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(8.dp),
        color = AudioEmerald.copy(alpha = 0.16f),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, AudioEmerald),
        modifier = Modifier
            .offset(x = startDp)
            .width(widthDp)
            .fillMaxHeight()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Real PCM Waveform Visualization
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                val peaks = audioClip.waveformPeaks
                if (peaks.isNotEmpty()) {
                    val barCount = (size.width / 4.dp.toPx()).toInt().coerceIn(8, peaks.size)
                    val step = size.width / barCount
                    val midY = size.height / 2f
                    for (i in 0 until barCount) {
                        val amp = peaks[(i * peaks.size) / barCount].coerceIn(0.08f, 1f)
                        val barH = amp * size.height * 0.85f
                        val x = i * step + step * 0.35f
                        drawLine(
                            color = AudioEmerald.copy(alpha = 0.72f),
                            start = Offset(x, midY - barH / 2f),
                            end = Offset(x, midY + barH / 2f),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, null, tint = AudioEmerald, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    text = audioClip.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
