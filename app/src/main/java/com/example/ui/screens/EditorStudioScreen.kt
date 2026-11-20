package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.KeyframeProperty
import com.example.ui.components.CurveEditorSheet
import com.example.ui.components.EditorToolInspectorPanel
import com.example.ui.components.ExportStudioSheet
import com.example.ui.components.MultiTrackTimeline
import com.example.ui.components.ViewportCanvas
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineOutline
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.formatTimecode
import com.example.ui.viewmodel.EditorToolTab
import com.example.ui.viewmodel.EditorUiState
import com.example.ui.viewmodel.EditorViewModel

@Composable
fun EditorStudioScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportEngine.state.collectAsStateWithLifecycle()
    val codecCaps = remember { viewModel.exportEngine.queryCodecCapabilities() }
    val snackbarHostState = remember { SnackbarHostState() }

    val addClipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.appendVisualMedia(uris)
        }
    }

    BackHandler(enabled = uiState.isFullscreenPreview) {
        viewModel.toggleFullscreenPreview()
    }

    LaunchedEffect(uiState.statusBanner) {
        uiState.statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissStatusBanner()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElectricCyan)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Top Studio Header
            EditorTopBar(
                uiState = uiState,
                onBack = onNavigateBack,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onAspectClick = { viewModel.setActiveToolTab(EditorToolTab.FORMAT, toggleIfSame = false) },
                onExportClick = viewModel::openExportSheet
            )

            HorizontalDivider(color = HairlineOutline, thickness = 1.dp)

            val evaluatedClip = uiState.evaluatedSelectedClipState()
            val hasKeyAtPlayhead = viewModel.hasKeyframeAtPlayhead(KeyframeProperty.SCALE)

            if (uiState.isFullscreenPreview) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    ViewportCanvas(
                        timeline = uiState.timeline,
                        compositionVersion = uiState.compositionVersion,
                        playheadMs = uiState.playheadMs,
                        isPlaying = uiState.isPlaying,
                        selectedTextId = uiState.selectedTextId,
                        isMaskOverlayActive = uiState.isMaskOverlayActive,
                        evaluatedClipState = evaluatedClip,
                        onPlayheadTick = viewModel::seekTo,
                        onPlaybackEnded = { viewModel.setPlaying(false) },
                        onBeginGesture = viewModel::beginLiveGesture,
                        onTransformDelta = viewModel::updateTransformFromCanvasGesture,
                        onEndGesture = { viewModel.endLiveGesture(rebuildImmediately = true) },
                        onUpdateMaskLive = { viewModel.updateMask(it, recordUndo = false, livePreviewOnly = true) },
                        onSelectText = viewModel::selectTextClip,
                        onTextDragDelta = viewModel::updateTextPositionDelta,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 74.dp)
                    )

                    // Mobile Fullscreen Transport Scrubber Bar
                    Surface(
                        color = StudioSurface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, HairlineOutline),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        val totalMs = uiState.timeline.totalDurationMs.coerceAtLeast(1L)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = viewModel::togglePlayPause,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                                    tint = ElectricCyan
                                )
                            }
                            Slider(
                                value = uiState.playheadMs.toFloat().coerceIn(0f, totalMs.toFloat()),
                                onValueChange = { viewModel.seekTo(it.toLong()) },
                                valueRange = 0f..totalMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = ElectricCyan,
                                    activeTrackColor = ElectricCyan,
                                    inactiveTrackColor = HairlineOutline
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatTimecode(uiState.playheadMs),
                                style = MonoTimecodeStyle,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = viewModel::toggleFullscreenPreview,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.FullscreenExit, "Salir de pantalla completa", tint = TextPrimary)
                            }
                        }
                    }
                }
            } else {
                val launchAddClip = {
                    runCatching {
                        addClipLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }.onFailure {
                        viewModel.reportStatusBanner("No se pudo abrir el selector multimedia.")
                    }
                    Unit
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isWideScreen = maxWidth >= 700.dp

                    if (isWideScreen) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .weight(0.56f)
                                    .fillMaxWidth()
                            ) {
                                ViewportCanvas(
                                    timeline = uiState.timeline,
                                    compositionVersion = uiState.compositionVersion,
                                    playheadMs = uiState.playheadMs,
                                    isPlaying = uiState.isPlaying,
                                    selectedTextId = uiState.selectedTextId,
                                    isMaskOverlayActive = uiState.isMaskOverlayActive,
                                    evaluatedClipState = evaluatedClip,
                                    onPlayheadTick = viewModel::seekTo,
                                    onPlaybackEnded = { viewModel.setPlaying(false) },
                                    onBeginGesture = viewModel::beginLiveGesture,
                                    onTransformDelta = viewModel::updateTransformFromCanvasGesture,
                                    onEndGesture = { viewModel.endLiveGesture(rebuildImmediately = true) },
                                    onUpdateMaskLive = { viewModel.updateMask(it, recordUndo = false, livePreviewOnly = true) },
                                    onSelectText = viewModel::selectTextClip,
                                    onTextDragDelta = viewModel::updateTextPositionDelta,
                                    modifier = Modifier
                                        .weight(0.58f)
                                        .fillMaxHeight()
                                )

                                EditorToolInspectorPanel(
                                    uiState = uiState,
                                    onCollapseInspector = { viewModel.setInspectorExpanded(false) },
                                    onSplitClip = viewModel::splitSelectedClipAtPlayhead,
                                    onDuplicateClip = viewModel::duplicateSelectedClip,
                                    onDeleteClip = viewModel::deleteSelectedClip,
                                    onMoveClip = viewModel::moveSelectedClip,
                                    onResetTransform = viewModel::resetSelectedTransform,
                                    onSetSpeed = viewModel::setClipSpeed,
                                    onSetOpacity = viewModel::setClipOpacity,
                                    onSliderDragEnd = { viewModel.endLiveGesture(rebuildImmediately = true) },
                                    onToggleKeyframe = viewModel::toggleKeyframe,
                                    onHasKeyframeAtPlayhead = viewModel::hasKeyframeAtPlayhead,
                                    onOpenCurveEditor = viewModel::openCurveEditor,
                                    onJumpKeyframe = viewModel::jumpToAdjacentKeyframe,
                                    onSetMaskType = viewModel::setMaskType,
                                    onUpdateMask = { mask, liveOnly ->
                                        viewModel.updateMask(mask, recordUndo = !liveOnly, livePreviewOnly = liveOnly)
                                    },
                                    onKeyframeMaskAll = viewModel::keyframeAllMaskProperties,
                                    onSetVisualEffect = viewModel::setVisualEffect,
                                    onUpdateColorGrade = viewModel::updateColorGrade,
                                    onSetTransition = viewModel::setTransitionIn,
                                    onAddText = { viewModel.addTextClip() },
                                    onUpdateText = viewModel::updateTextClip,
                                    onDeleteText = viewModel::deleteTextClip,
                                    onSetClipAudio = viewModel::setClipAudio,
                                    onAddAudioUri = viewModel::addAudioTrack,
                                    onUpdateAudioClip = viewModel::updateAudioClip,
                                    onDeleteAudioClip = viewModel::deleteAudioClip,
                                    onSetAspectRatio = viewModel::setAspectRatio,
                                    onSetFps = viewModel::setProjectFps,
                                    onStatusMessage = viewModel::reportStatusBanner,
                                    showCollapseButton = false,
                                    modifier = Modifier
                                        .weight(0.42f)
                                        .fillMaxHeight()
                                )
                            }

                            TransportControlBar(
                                uiState = uiState,
                                hasKeyAtPlayhead = hasKeyAtPlayhead,
                                onTogglePlay = viewModel::togglePlayPause,
                                onStepFrame = viewModel::stepFrame,
                                onQuickKeyframe = { viewModel.toggleKeyframe(KeyframeProperty.SCALE) },
                                onToggleFullscreen = viewModel::toggleFullscreenPreview
                            )

                            MultiTrackTimeline(
                                timeline = uiState.timeline,
                                playheadMs = uiState.playheadMs,
                                isPlaying = uiState.isPlaying,
                                pxPerSec = uiState.timelineZoomPxPerSec,
                                selectedClipId = uiState.selectedMainClip?.id,
                                selectedTextId = uiState.selectedTextId,
                                selectedAudioId = uiState.selectedAudioId,
                                onSeekMs = viewModel::seekTo,
                                onZoomChange = viewModel::setTimelineZoom,
                                onSelectMainClip = viewModel::selectMainClip,
                                onTrimMainClip = viewModel::trimMainClip,
                                onOpenTransitionForClip = { clipId ->
                                    viewModel.selectMainClip(clipId)
                                    viewModel.setActiveToolTab(EditorToolTab.TRANSITIONS, toggleIfSame = false)
                                },
                                onSelectTextClip = viewModel::selectTextClip,
                                onSelectAudioClip = viewModel::selectAudioClip,
                                onAddClipClick = launchAddClip,
                                modifier = Modifier.weight(0.44f)
                            )
                        }
                    } else {
                        // Portrait Mobile-First NLE Layout with collapsible inspector
                        val viewportWeight by animateFloatAsState(
                            targetValue = if (uiState.isInspectorExpanded) 0.36f else 0.56f,
                            animationSpec = spring(stiffness = 420f),
                            label = "viewport_weight"
                        )
                        val timelineWeight by animateFloatAsState(
                            targetValue = if (uiState.isInspectorExpanded) 0.27f else 0.44f,
                            animationSpec = spring(stiffness = 420f),
                            label = "timeline_weight"
                        )
                        val inspectorWeight by animateFloatAsState(
                            targetValue = if (uiState.isInspectorExpanded) 0.37f else 0f,
                            animationSpec = spring(stiffness = 420f),
                            label = "inspector_weight"
                        )

                        Column(modifier = Modifier.fillMaxSize()) {
                            ViewportCanvas(
                                timeline = uiState.timeline,
                                compositionVersion = uiState.compositionVersion,
                                playheadMs = uiState.playheadMs,
                                isPlaying = uiState.isPlaying,
                                selectedTextId = uiState.selectedTextId,
                                isMaskOverlayActive = uiState.isMaskOverlayActive,
                                evaluatedClipState = evaluatedClip,
                                onPlayheadTick = viewModel::seekTo,
                                onPlaybackEnded = { viewModel.setPlaying(false) },
                                onBeginGesture = viewModel::beginLiveGesture,
                                onTransformDelta = viewModel::updateTransformFromCanvasGesture,
                                onEndGesture = { viewModel.endLiveGesture(rebuildImmediately = true) },
                                onUpdateMaskLive = { viewModel.updateMask(it, recordUndo = false, livePreviewOnly = true) },
                                onSelectText = viewModel::selectTextClip,
                                onTextDragDelta = viewModel::updateTextPositionDelta,
                                modifier = Modifier.weight(viewportWeight)
                            )

                            TransportControlBar(
                                uiState = uiState,
                                hasKeyAtPlayhead = hasKeyAtPlayhead,
                                onTogglePlay = viewModel::togglePlayPause,
                                onStepFrame = viewModel::stepFrame,
                                onQuickKeyframe = { viewModel.toggleKeyframe(KeyframeProperty.SCALE) },
                                onToggleFullscreen = viewModel::toggleFullscreenPreview
                            )

                            MultiTrackTimeline(
                                timeline = uiState.timeline,
                                playheadMs = uiState.playheadMs,
                                isPlaying = uiState.isPlaying,
                                pxPerSec = uiState.timelineZoomPxPerSec,
                                selectedClipId = uiState.selectedMainClip?.id,
                                selectedTextId = uiState.selectedTextId,
                                selectedAudioId = uiState.selectedAudioId,
                                onSeekMs = viewModel::seekTo,
                                onZoomChange = viewModel::setTimelineZoom,
                                onSelectMainClip = viewModel::selectMainClip,
                                onTrimMainClip = viewModel::trimMainClip,
                                onOpenTransitionForClip = { clipId ->
                                    viewModel.selectMainClip(clipId)
                                    viewModel.setActiveToolTab(EditorToolTab.TRANSITIONS, toggleIfSame = false)
                                },
                                onSelectTextClip = viewModel::selectTextClip,
                                onSelectAudioClip = viewModel::selectAudioClip,
                                onAddClipClick = launchAddClip,
                                modifier = Modifier.weight(timelineWeight)
                            )

                            if (inspectorWeight > 0.01f) {
                                Column(
                                    modifier = Modifier
                                        .weight(inspectorWeight)
                                        .fillMaxWidth()
                                ) {
                                    HorizontalDivider(color = HairlineOutline, thickness = 1.dp)
                                    EditorToolInspectorPanel(
                                        uiState = uiState,
                                        onCollapseInspector = { viewModel.setInspectorExpanded(false) },
                                        onSplitClip = viewModel::splitSelectedClipAtPlayhead,
                                        onDuplicateClip = viewModel::duplicateSelectedClip,
                                        onDeleteClip = viewModel::deleteSelectedClip,
                                        onMoveClip = viewModel::moveSelectedClip,
                                        onResetTransform = viewModel::resetSelectedTransform,
                                        onSetSpeed = viewModel::setClipSpeed,
                                        onSetOpacity = viewModel::setClipOpacity,
                                        onSliderDragEnd = { viewModel.endLiveGesture(rebuildImmediately = true) },
                                        onToggleKeyframe = viewModel::toggleKeyframe,
                                        onHasKeyframeAtPlayhead = viewModel::hasKeyframeAtPlayhead,
                                        onOpenCurveEditor = viewModel::openCurveEditor,
                                        onJumpKeyframe = viewModel::jumpToAdjacentKeyframe,
                                        onSetMaskType = viewModel::setMaskType,
                                        onUpdateMask = { mask, liveOnly ->
                                            viewModel.updateMask(mask, recordUndo = !liveOnly, livePreviewOnly = liveOnly)
                                        },
                                        onKeyframeMaskAll = viewModel::keyframeAllMaskProperties,
                                        onSetVisualEffect = viewModel::setVisualEffect,
                                        onUpdateColorGrade = viewModel::updateColorGrade,
                                        onSetTransition = viewModel::setTransitionIn,
                                        onAddText = { viewModel.addTextClip() },
                                        onUpdateText = viewModel::updateTextClip,
                                        onDeleteText = viewModel::deleteTextClip,
                                        onSetClipAudio = viewModel::setClipAudio,
                                        onAddAudioUri = viewModel::addAudioTrack,
                                        onUpdateAudioClip = viewModel::updateAudioClip,
                                        onDeleteAudioClip = viewModel::deleteAudioClip,
                                        onSetAspectRatio = viewModel::setAspectRatio,
                                        onSetFps = viewModel::setProjectFps,
                                        onStatusMessage = viewModel::reportStatusBanner,
                                        showCollapseButton = true,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Tool Dock with mandatory navigationBarsPadding()
                EditorToolDock(
                    activeTab = uiState.activeToolTab,
                    isExpanded = uiState.isInspectorExpanded,
                    onSelectTab = { tab -> viewModel.setActiveToolTab(tab, toggleIfSame = true) }
                )
            }
        }
    }

    uiState.curveTarget?.let { target ->
        CurveEditorSheet(
            target = target,
            onCurveChange = viewModel::updateCurveForSegment,
            onDismiss = viewModel::closeCurveEditor
        )
    }

    if (uiState.showExportSheet) {
        ExportStudioSheet(
            config = uiState.exportConfig,
            exportState = exportState,
            capabilities = codecCaps,
            projectDurationMs = uiState.timeline.totalDurationMs,
            onConfigChange = viewModel::updateExportConfig,
            onStartExport = viewModel::startExport,
            onCancelExport = { viewModel.exportEngine.cancelExport() },
            onDismiss = {
                viewModel.exportEngine.resetState()
                viewModel.closeExportSheet()
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    uiState: EditorUiState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAspectClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("editor_back_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAspectClick)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = uiState.projectTitle.ifBlank { "Estudio" },
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (uiState.isSaving) "Guardando..." else "${uiState.timeline.aspectRatio.label} • ${uiState.timeline.fps}fps",
                style = MonoTimecodeStyle.copy(fontSize = 10.sp),
                color = if (uiState.isSaving) ElectricCyan else TextSecondary
            )
        }

        IconButton(
            onClick = onUndo,
            enabled = uiState.canUndo,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("undo_button")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                "Deshacer",
                tint = if (uiState.canUndo) TextPrimary else TextMuted
            )
        }

        IconButton(
            onClick = onRedo,
            enabled = uiState.canRedo,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("redo_button")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                "Rehacer",
                tint = if (uiState.canRedo) TextPrimary else TextMuted
            )
        }

        Button(
            onClick = onExportClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = Color(0xFF002026)
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier
                .height(40.dp)
                .testTag("open_export_button")
        ) {
            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Exportar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TransportControlBar(
    uiState: EditorUiState,
    hasKeyAtPlayhead: Boolean,
    onTogglePlay: () -> Unit,
    onStepFrame: (Int) -> Unit,
    onQuickKeyframe: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${formatTimecode(uiState.playheadMs)} / ${formatTimecode(uiState.timeline.totalDurationMs)}",
            style = MonoTimecodeStyle,
            color = TextPrimary
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onStepFrame(-1) },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.SkipPrevious, "Cuadro anterior", tint = TextSecondary)
            }

            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ElevatedSurface)
                    .testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                    tint = ElectricCyan
                )
            }

            IconButton(
                onClick = { onStepFrame(1) },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.SkipNext, "Siguiente cuadro", tint = TextSecondary)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onQuickKeyframe,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quick_keyframe_button")
            ) {
                Icon(
                    Icons.Default.Diamond,
                    "Keyframe rápido",
                    tint = if (hasKeyAtPlayhead) ElectricCyan else CoralAmber
                )
            }

            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.Fullscreen, "Pantalla completa", tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun EditorToolDock(
    activeTab: EditorToolTab,
    isExpanded: Boolean,
    onSelectTab: (EditorToolTab) -> Unit
) {
    Surface(
        color = ObsidianBg,
        border = BorderStroke(1.dp, HairlineOutline),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("editor_tool_dock"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(EditorToolTab.entries) { tab ->
                val selected = tab == activeTab && isExpanded
                val icon = toolTabIcon(tab)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) ElectricCyan.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("tool_tab_${tab.name}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (selected) ElectricCyan else TextSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ElectricCyan else TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun toolTabIcon(tab: EditorToolTab): ImageVector = when (tab) {
    EditorToolTab.EDIT -> Icons.Default.ContentCut
    EditorToolTab.KEYFRAME -> Icons.Default.Diamond
    EditorToolTab.MASK -> Icons.Default.Layers
    EditorToolTab.EFFECTS -> Icons.Default.AutoAwesome
    EditorToolTab.COLOR -> Icons.Default.ColorLens
    EditorToolTab.TRANSITIONS -> Icons.Default.SwapHoriz
    EditorToolTab.TEXT -> Icons.Default.Title
    EditorToolTab.AUDIO -> Icons.Default.MusicNote
    EditorToolTab.FORMAT -> Icons.Default.AspectRatio
}
