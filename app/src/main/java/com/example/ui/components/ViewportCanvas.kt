package com.example.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.domain.engine.EvaluatedClipState
import com.example.domain.engine.KeyframeEvaluator
import com.example.domain.model.MaskSpec
import com.example.domain.model.MaskType
import com.example.domain.model.ProjectTimeline
import com.example.domain.model.TextAnimationType
import com.example.domain.model.TextClip
import com.example.domain.model.TextFontStyle
import com.example.engine.CompositionBuilder
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HairlineOutline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun ViewportCanvas(
    timeline: ProjectTimeline,
    compositionVersion: Int,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedTextId: String?,
    isMaskOverlayActive: Boolean,
    evaluatedClipState: EvaluatedClipState?,
    onPlayheadTick: (Long) -> Unit,
    onPlaybackEnded: () -> Unit,
    onBeginGesture: () -> Unit,
    onTransformDelta: (dxNorm: Float, dyNorm: Float, scaleFactor: Float, dRotDeg: Float) -> Unit,
    onEndGesture: () -> Unit,
    onUpdateMaskLive: (MaskSpec) -> Unit,
    onSelectText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPlayhead by rememberUpdatedState(playheadMs)
    val currentIsPlaying by rememberUpdatedState(isPlaying)

    val compositionPlayer = remember(context) {
        CompositionPlayer.Builder(context).build()
    }

    var isGestureHudVisible by remember { mutableStateOf(false) }

    DisposableEffect(compositionPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && currentIsPlaying) {
                    onPlaybackEnded()
                }
            }
        }
        compositionPlayer.addListener(listener)
        onDispose {
            compositionPlayer.removeListener(listener)
            compositionPlayer.release()
        }
    }

    LaunchedEffect(compositionVersion, timeline.mainClips.size, timeline.audioClips.size, timeline.aspectRatio) {
        if (timeline.mainClips.isNotEmpty()) {
            val comp = CompositionBuilder.buildComposition(
                context = context,
                timeline = timeline,
                targetShortEdgePx = 720,
                includeTextOverlays = false
            )
            compositionPlayer.setComposition(comp)
            compositionPlayer.prepare()
            compositionPlayer.seekTo(currentPlayhead)
            compositionPlayer.playWhenReady = currentIsPlaying
        } else {
            compositionPlayer.stop()
        }
    }

    LaunchedEffect(isPlaying) {
        compositionPlayer.playWhenReady = isPlaying
        if (isPlaying) {
            if (compositionPlayer.currentPosition >= timeline.totalDurationMs - 40L && timeline.totalDurationMs > 0L) {
                compositionPlayer.seekTo(0L)
            }
            compositionPlayer.play()
        } else {
            compositionPlayer.pause()
        }
    }

    LaunchedEffect(isPlaying, timeline.totalDurationMs) {
        if (isPlaying) {
            while (isActive) {
                val pos = compositionPlayer.currentPosition.coerceIn(0L, timeline.totalDurationMs)
                onPlayheadTick(pos)
                delay(32L)
            }
        }
    }

    LaunchedEffect(playheadMs, isPlaying) {
        if (!isPlaying && timeline.mainClips.isNotEmpty()) {
            val diff = abs(compositionPlayer.currentPosition - playheadMs)
            if (diff > 25L) {
                compositionPlayer.seekTo(playheadMs)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val containerAspect = maxWidth / maxHeight.coerceAtLeast(1.dp)
        val targetAspect = timeline.aspectRatio.ratioFloat

        val frameModifier = if (containerAspect > targetAspect) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(targetAspect)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(targetAspect)
        }

        Box(
            modifier = frameModifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .border(1.dp, HairlineOutline, RoundedCornerShape(10.dp))
                .clipToBounds()
                .testTag("video_preview_viewport")
        ) {
            if (timeline.mainClips.isNotEmpty()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = compositionPlayer
                        }
                    },
                    update = { view ->
                        if (view.player !== compositionPlayer) {
                            view.player = compositionPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Pinch-to-Zoom / Pan / Rotate Direct Manipulation Layer
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isMaskOverlayActive, selectedTextId) {
                        if (!isMaskOverlayActive) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                isGestureHudVisible = true
                                onBeginGesture()
                                val dx = (pan.x / size.width.coerceAtLeast(1)) * 2f
                                val dy = -(pan.y / size.height.coerceAtLeast(1)) * 2f
                                onTransformDelta(dx, dy, zoom, rotation)
                            }
                        }
                    }
                    .pointerInput(isMaskOverlayActive) {
                        if (!isMaskOverlayActive) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.none { it.pressed } && isGestureHudVisible) {
                                        isGestureHudVisible = false
                                        onEndGesture()
                                    }
                                }
                            }
                        }
                    }
            ) {
                val vpW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val vpH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                // Live Text Overlays synced to playheadMs
                timeline.textClips.forEach { tc ->
                    if (playheadMs in tc.timelineStartMs..(tc.timelineStartMs + tc.durationMs)) {
                        LiveTextOverlayItem(
                            textClip = tc,
                            localMs = playheadMs - tc.timelineStartMs,
                            isSelected = tc.id == selectedTextId,
                            viewportW = vpW,
                            viewportH = vpH,
                            onSelect = { onSelectText(tc.id) }
                        )
                    }
                }

                // Snap-to-center guides when dragging
                val t = evaluatedClipState?.transform
                if (isGestureHudVisible && t != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (abs(t.posX) < 0.04f) {
                            drawLine(
                                color = ElectricCyan.copy(alpha = 0.85f),
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                        if (abs(t.posY) < 0.04f) {
                            drawLine(
                                color = ElectricCyan.copy(alpha = 0.85f),
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }
                }

                // Interactive Mask Vector Handles
                val evalMask = evaluatedClipState?.mask
                if (isMaskOverlayActive && evalMask != null && evalMask.type != MaskType.NONE) {
                    InteractiveMaskGuideOverlay(
                        mask = evalMask,
                        viewportW = vpW,
                        viewportH = vpH,
                        onBeginDrag = onBeginGesture,
                        onMaskChange = onUpdateMaskLive,
                        onEndDrag = onEndGesture
                    )
                }
            }

            // Live Transform Numerical HUD
            if (isGestureHudVisible && evaluatedClipState != null) {
                val tr = evaluatedClipState.transform
                Surface(
                    color = ObsidianBg.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Escala %.2fx • Rot %.0f° • X %.2f Y %.2f",
                            tr.scale,
                            tr.rotationDeg,
                            tr.posX,
                            tr.posY
                        ),
                        style = MonoTimecodeStyle,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTextOverlayItem(
    textClip: TextClip,
    localMs: Long,
    isSelected: Boolean,
    viewportW: Float,
    viewportH: Float,
    onSelect: () -> Unit
) {
    val evaluated = KeyframeEvaluator.evaluateTextFrame(textClip, localMs)
    val tr = evaluated.transform

    val animProgress = (localMs / 350f).coerceIn(0f, 1f)
    var animAlpha = 1f
    var animScale = 1f
    var animOffsetY = 0f

    when (textClip.animationIn) {
        TextAnimationType.NONE -> Unit
        TextAnimationType.FADE -> animAlpha = animProgress
        TextAnimationType.POP_SCALE -> {
            animAlpha = (animProgress * 1.4f).coerceIn(0f, 1f)
            animScale = 0.7f + 0.3f * animProgress
        }
        TextAnimationType.SLIDE_UP -> {
            animAlpha = animProgress
            animOffsetY = (1f - animProgress) * 40f
        }
        TextAnimationType.GLOW_PULSE -> {
            animAlpha = 0.85f + 0.15f * kotlin.math.sin(localMs / 240f)
        }
    }

    val fontFamily = when (textClip.fontStyle) {
        TextFontStyle.MODERN -> SpaceGroteskFontFamily
        TextFontStyle.CLEAN -> PlusJakartaSansFontFamily
        TextFontStyle.MONO -> JetBrainsMonoFontFamily
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (tr.posX * viewportW * 0.5f).roundToInt(),
                        y = (-tr.posY * viewportH * 0.5f + animOffsetY).roundToInt()
                    )
                }
                .graphicsLayer {
                    scaleX = tr.scale * animScale
                    scaleY = tr.scale * animScale
                    rotationZ = tr.rotationDeg
                    alpha = (tr.opacity * animAlpha).coerceIn(0f, 1f)
                }
                .then(
                    if (isSelected) {
                        Modifier
                            .border(1.5.dp, ElectricCyan, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    } else {
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    }
                )
                .background(
                    Color(textClip.backgroundArgb.toInt()),
                    RoundedCornerShape(8.dp)
                )
                .pointerInput(textClip.id) {
                    detectDragGestures(
                        onDragStart = { onSelect() },
                        onDrag = { _, _ -> }
                    )
                }
        ) {
            Text(
                text = textClip.text,
                color = Color(textClip.colorArgb.toInt()),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = textClip.fontSizeSp.sp,
                    shadow = Shadow(
                        color = Color(textClip.strokeArgb.toInt()),
                        blurRadius = 6f
                    )
                )
            )
        }
    }
}

@Composable
private fun InteractiveMaskGuideOverlay(
    mask: MaskSpec,
    viewportW: Float,
    viewportH: Float,
    onBeginDrag: () -> Unit,
    onMaskChange: (MaskSpec) -> Unit,
    onEndDrag: () -> Unit
) {
    val currentMask by rememberUpdatedState(mask)
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(mask.type) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    onBeginDrag()
                    val m = currentMask
                    val nextCx = (m.centerX + pan.x / viewportW).coerceIn(0.05f, 0.95f)
                    val nextCy = (m.centerY - pan.y / viewportH).coerceIn(0.05f, 0.95f)
                    val nextW = (m.width * zoom).coerceIn(0.08f, 1.4f)
                    val nextH = (m.height * zoom).coerceIn(0.08f, 1.4f)
                    onMaskChange(
                        m.copy(
                            centerX = nextCx,
                            centerY = nextCy,
                            width = nextW,
                            height = nextH,
                            rotationDeg = m.rotationDeg + rotation
                        )
                    )
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) {
                            onEndDrag()
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = mask.centerX * size.width
            val cy = (1f - mask.centerY) * size.height
            val w = mask.width * size.width
            val h = mask.height * size.height

            rotate(degrees = mask.rotationDeg, pivot = Offset(cx, cy)) {
                val stroke = Stroke(width = 2.dp.toPx(), pathEffect = dash)
                when (mask.type) {
                    MaskType.LINEAR -> {
                        drawLine(
                            color = CoralAmber,
                            start = Offset(cx - size.width, cy),
                            end = Offset(cx + size.width, cy),
                            strokeWidth = 2.5.dp.toPx()
                        )
                    }
                    MaskType.MIRROR -> {
                        drawLine(
                            color = CoralAmber,
                            start = Offset(0f, cy - h / 2f),
                            end = Offset(size.width, cy - h / 2f),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = dash
                        )
                        drawLine(
                            color = CoralAmber,
                            start = Offset(0f, cy + h / 2f),
                            end = Offset(size.width, cy + h / 2f),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = dash
                        )
                    }
                    MaskType.CIRCLE -> {
                        drawOval(
                            color = CoralAmber,
                            topLeft = Offset(cx - w / 2f, cy - h / 2f),
                            size = Size(w, h),
                            style = stroke
                        )
                    }
                    MaskType.RECTANGLE, MaskType.HEART -> {
                        drawRoundRect(
                            color = CoralAmber,
                            topLeft = Offset(cx - w / 2f, cy - h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(mask.cornerRadius * w, mask.cornerRadius * w),
                            style = stroke
                        )
                    }
                    MaskType.NONE -> Unit
                }
                // Center pivot handle
                drawCircle(color = CoralAmber, radius = 6.dp.toPx(), center = Offset(cx, cy))
                drawCircle(color = ObsidianBg, radius = 3.dp.toPx(), center = Offset(cx, cy))
            }
        }
    }
}
