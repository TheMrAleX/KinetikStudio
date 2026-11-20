package com.example.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
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
    onTextDragDelta: (textId: String, dxNorm: Float, dyNorm: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPlayhead by rememberUpdatedState(playheadMs)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val latestTimeline by rememberUpdatedState(timeline)

    val playerViewHolder = remember { arrayOfNulls<PlayerView>(1) }
    var activePlayer by remember { mutableStateOf<CompositionPlayer?>(null) }
    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }
    var isGestureHudVisible by remember { mutableStateOf(false) }

    val hasClips = timeline.mainClips.isNotEmpty()
    val compositionKey = if (hasClips) compositionVersion else -1

    // Media3 CompositionPlayer enforces setComposition() only once per instance.
    // Dispose and recreate the player cleanly on each compositionKey bump.
    DisposableEffect(context, compositionKey) {
        if (compositionKey < 0) {
            runCatching { playerViewHolder[0]?.player = null }
            activePlayer = null
            playbackErrorMessage = null
            onDispose {}
        } else {
            var createdPlayer: CompositionPlayer? = null
            var playerListener: Player.Listener? = null
            runCatching {
                val tl = latestTimeline
                val comp = CompositionBuilder.buildComposition(
                    context = context,
                    timeline = tl,
                    targetShortEdgePx = 720,
                    includeTextOverlays = false
                )
                if (comp.sequences.isNotEmpty()) {
                    val newPlayer = CompositionPlayer.Builder(context).build()
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED && currentIsPlaying) {
                                onPlaybackEnded()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            playbackErrorMessage = error.localizedMessage ?: "Error en vista previa"
                            onPlaybackEnded()
                        }
                    }
                    newPlayer.addListener(listener)
                    newPlayer.setComposition(comp)
                    newPlayer.prepare()
                    val safeDuration = tl.totalDurationMs.coerceAtLeast(0L)
                    newPlayer.seekTo(currentPlayhead.coerceIn(0L, safeDuration))
                    newPlayer.playWhenReady = currentIsPlaying
                    runCatching { playerViewHolder[0]?.player = newPlayer }
                    activePlayer = newPlayer
                    createdPlayer = newPlayer
                    playerListener = listener
                    playbackErrorMessage = null
                } else {
                    runCatching { playerViewHolder[0]?.player = null }
                    activePlayer = null
                }
            }.onFailure { err ->
                playbackErrorMessage = err.localizedMessage ?: "No se pudo cargar la vista previa"
                createdPlayer?.let { p ->
                    playerListener?.let { p.removeListener(it) }
                    runCatching { p.release() }
                }
                runCatching { playerViewHolder[0]?.player = null }
                activePlayer = null
            }

            onDispose {
                val p = createdPlayer
                if (p != null) {
                    if (playerViewHolder[0]?.player === p) {
                        runCatching { playerViewHolder[0]?.player = null }
                    }
                    playerListener?.let { p.removeListener(it) }
                    runCatching { p.release() }
                }
                if (activePlayer === p) {
                    activePlayer = null
                }
            }
        }
    }

    LaunchedEffect(isPlaying, activePlayer) {
        val player = activePlayer ?: return@LaunchedEffect
        runCatching {
            player.playWhenReady = isPlaying
            if (isPlaying) {
                val total = latestTimeline.totalDurationMs.coerceAtLeast(0L)
                if (total > 0L && player.currentPosition >= total - 40L) {
                    player.seekTo(0L)
                }
                player.play()
            } else {
                player.pause()
            }
        }
    }

    LaunchedEffect(isPlaying, timeline.totalDurationMs, activePlayer) {
        val player = activePlayer ?: return@LaunchedEffect
        if (isPlaying) {
            val safeTotal = timeline.totalDurationMs.coerceAtLeast(0L)
            while (isActive) {
                val pos = runCatching { player.currentPosition }.getOrDefault(0L)
                    .coerceIn(0L, safeTotal)
                onPlayheadTick(pos)
                delay(32L)
            }
        }
    }

    LaunchedEffect(playheadMs, isPlaying, activePlayer) {
        val player = activePlayer ?: return@LaunchedEffect
        if (!isPlaying && timeline.mainClips.isNotEmpty()) {
            val safeTotal = timeline.totalDurationMs.coerceAtLeast(0L)
            val target = playheadMs.coerceIn(0L, safeTotal)
            val diff = abs(runCatching { player.currentPosition }.getOrDefault(target) - target)
            if (diff > 25L) {
                runCatching { player.seekTo(target) }
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
        val targetAspect by animateFloatAsState(
            targetValue = timeline.aspectRatio.ratioFloat,
            animationSpec = spring(stiffness = 500f),
            label = "viewport_aspect"
        )

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
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, HairlineOutline, RoundedCornerShape(12.dp))
                .clipToBounds()
                .testTag("video_preview_viewport")
        ) {
            if (hasClips) {
                AndroidView(
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(R.layout.studio_player_view, null, false) as PlayerView).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setKeepContentOnPlayerReset(true)
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            playerViewHolder[0] = this
                            runCatching { player = activePlayer }
                        }
                    },
                    update = { view ->
                        if (playerViewHolder[0] !== view) {
                            playerViewHolder[0] = view
                        }
                        if (view.player !== activePlayer) {
                            runCatching { view.player = activePlayer }
                                .onFailure { err ->
                                    playbackErrorMessage = err.localizedMessage ?: "Error al vincular vista previa"
                                }
                        }
                    },
                    onRelease = { view ->
                        if (playerViewHolder[0] === view) {
                            playerViewHolder[0] = null
                        }
                        runCatching { view.player = null }
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
                            onSelect = { onSelectText(tc.id) },
                            onBeginDrag = onBeginGesture,
                            onDragDelta = { dxNorm, dyNorm -> onTextDragDelta(tc.id, dxNorm, dyNorm) },
                            onEndDrag = onEndGesture
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

            // Interactive Mask Hint Pill
            if (isMaskOverlayActive && evaluatedClipState?.mask?.type != MaskType.NONE && !isGestureHudVisible) {
                Surface(
                    color = ObsidianBg.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "Arrastra o pellizca la máscara",
                        style = MonoTimecodeStyle,
                        color = CoralAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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

            playbackErrorMessage?.let { errMsg ->
                Surface(
                    color = ObsidianBg.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                ) {
                    Text(
                        text = errMsg,
                        style = MonoTimecodeStyle,
                        color = CoralAmber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
    onSelect: () -> Unit,
    onBeginDrag: () -> Unit,
    onDragDelta: (dxNorm: Float, dyNorm: Float) -> Unit,
    onEndDrag: () -> Unit
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
                        onDragStart = {
                            onSelect()
                            onBeginDrag()
                        },
                        onDragEnd = { onEndDrag() },
                        onDragCancel = { onEndDrag() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = (dragAmount.x / viewportW.coerceAtLeast(1f)) * 2f
                            val dy = -(dragAmount.y / viewportH.coerceAtLeast(1f)) * 2f
                            onDragDelta(dx, dy)
                        }
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
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(mask.type) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    isDragging = true
                    onBeginDrag()
                    val m = currentMask
                    val nextCx = (m.centerX + pan.x / viewportW.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f)
                    val nextCy = (m.centerY - pan.y / viewportH.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f)
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
                        if (event.changes.none { it.pressed } && isDragging) {
                            isDragging = false
                            onEndDrag()
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = mask.centerX * size.width
            val cy = (1f - mask.centerY) * size.height
            val w = (mask.width * size.width).coerceAtLeast(4f)
            val h = (mask.height * size.height).coerceAtLeast(4f)

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
                        val r = (mask.cornerRadius * w).coerceAtLeast(0f)
                        drawRoundRect(
                            color = CoralAmber,
                            topLeft = Offset(cx - w / 2f, cy - h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(r, r),
                            style = stroke
                        )
                    }
                    MaskType.NONE -> Unit
                }
                drawCircle(color = CoralAmber, radius = 6.dp.toPx(), center = Offset(cx, cy))
                drawCircle(color = ObsidianBg, radius = 3.dp.toPx(), center = Offset(cx, cy))
            }
        }
    }
}
