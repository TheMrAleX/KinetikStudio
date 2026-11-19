package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProjectRepository
import com.example.domain.engine.EvaluatedClipState
import com.example.domain.engine.KeyframeEvaluator
import com.example.domain.engine.UndoRedoManager
import com.example.domain.model.AspectRatio
import com.example.domain.model.AudioClip
import com.example.domain.model.ClipTransform
import com.example.domain.model.ColorGradeSpec
import com.example.domain.model.EasingCurve
import com.example.domain.model.KeyframeNode
import com.example.domain.model.KeyframeProperty
import com.example.domain.model.MaskSpec
import com.example.domain.model.MaskType
import com.example.domain.model.ProjectTimeline
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TextClip
import com.example.domain.model.TransitionSpec
import com.example.domain.model.VideoClip
import com.example.domain.model.VisualEffectType
import com.example.engine.ExportConfig
import com.example.engine.ExportEngine
import com.example.engine.LiveClipRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

enum class EditorToolTab(val label: String) {
    EDIT("Editar"),
    KEYFRAME("Keyframes"),
    MASK("Máscara"),
    EFFECTS("Efectos"),
    COLOR("Color"),
    TRANSITIONS("Transición"),
    TEXT("Texto"),
    AUDIO("Audio"),
    FORMAT("Formato")
}

data class CurveTarget(
    val clipId: String,
    val property: KeyframeProperty,
    val startNode: KeyframeNode,
    val endNode: KeyframeNode
)

data class EditorUiState(
    val isLoading: Boolean = true,
    val projectId: String = "",
    val projectTitle: String = "",
    val timeline: ProjectTimeline = ProjectTimeline(),
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val timelineZoomPxPerSec: Float = 90f,
    val selectedClipId: String? = null,
    val selectedTextId: String? = null,
    val selectedAudioId: String? = null,
    val activeToolTab: EditorToolTab = EditorToolTab.EDIT,
    val isInspectorExpanded: Boolean = true,
    val isMaskOverlayActive: Boolean = false,
    val isFullscreenPreview: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
    val isBusyImporting: Boolean = false,
    val curveTarget: CurveTarget? = null,
    val showExportSheet: Boolean = false,
    val exportConfig: ExportConfig = ExportConfig(),
    val statusBanner: String? = null,
    val compositionVersion: Int = 0
) {
    val selectedMainClip: VideoClip?
        get() = timeline.mainClips.firstOrNull { it.id == selectedClipId }
            ?: timeline.findMainClipAt(playheadMs)?.first

    val selectedTextClip: TextClip?
        get() = timeline.textClips.firstOrNull { it.id == selectedTextId }

    val selectedAudioClip: AudioClip?
        get() = timeline.audioClips.firstOrNull { it.id == selectedAudioId }

    fun evaluatedSelectedClipState(): EvaluatedClipState? {
        val clip = selectedMainClip ?: return null
        val start = timeline.mainClipStartById(clip.id) ?: 0L
        val localMs = (playheadMs - start).coerceIn(0L, clip.effectiveDurationMs)
        return KeyframeEvaluator.evaluateClipFrame(clip, localMs)
    }
}

class EditorViewModel(
    private val context: Context,
    private val projectId: String,
    private val repository: ProjectRepository
) : ViewModel() {

    private val undoRedoManager = UndoRedoManager()
    val exportEngine = ExportEngine(context.applicationContext)

    private val _uiState = MutableStateFlow(EditorUiState(projectId = projectId))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null
    private var rebuildJob: Job? = null
    private var liveGestureBaseline: ProjectTimeline? = null

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            runCatching { repository.getProject(projectId) }
                .onSuccess { project ->
                    if (project != null) {
                        LiveClipRegistry.syncTimeline(project.timeline)
                        val firstClipId = project.timeline.mainClips.firstOrNull()?.id
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                projectTitle = project.title,
                                timeline = project.timeline,
                                selectedClipId = firstClipId,
                                compositionVersion = it.compositionVersion + 1
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, statusBanner = "Proyecto no encontrado")
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusBanner = err.localizedMessage ?: "Error al cargar el proyecto"
                        )
                    }
                }
        }
    }

    private fun mutateTimeline(
        recordUndo: Boolean = true,
        rebuildComposition: Boolean = true,
        visualOnly: Boolean = false,
        debounceRebuildMs: Long = 0L,
        transform: (ProjectTimeline) -> ProjectTimeline
    ) {
        val current = _uiState.value
        val prevTimeline = current.timeline
        val nextTimeline = transform(prevTimeline)
        if (nextTimeline == prevTimeline) return

        LiveClipRegistry.syncTimeline(nextTimeline)

        if (recordUndo) {
            undoRedoManager.recordBeforeMutation(prevTimeline)
        }

        val maxPlayhead = nextTimeline.totalDurationMs.coerceAtLeast(0L)
        val clampedPlayhead = current.playheadMs.coerceIn(0L, maxPlayhead)
        val validSelectedClip = nextTimeline.mainClips.firstOrNull { it.id == current.selectedClipId }?.id
            ?: nextTimeline.mainClips.firstOrNull()?.id
        val validSelectedText = nextTimeline.textClips.firstOrNull { it.id == current.selectedTextId }?.id
        val validSelectedAudio = nextTimeline.audioClips.firstOrNull { it.id == current.selectedAudioId }?.id

        // During active playback, LiveClipRegistry feeds GL shader uniforms at 60fps with zero player reset.
        val shouldRebuildNow = rebuildComposition && !(visualOnly && current.isPlaying)
        val shouldDebounce = !shouldRebuildNow && debounceRebuildMs > 0L && !(visualOnly && current.isPlaying)

        if (shouldRebuildNow) {
            rebuildJob?.cancel()
        }

        _uiState.update {
            it.copy(
                timeline = nextTimeline,
                playheadMs = clampedPlayhead,
                selectedClipId = validSelectedClip,
                selectedTextId = validSelectedText,
                selectedAudioId = validSelectedAudio,
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo,
                compositionVersion = if (shouldRebuildNow) it.compositionVersion + 1 else it.compositionVersion
            )
        }

        if (shouldDebounce) {
            scheduleCompositionRebuild(debounceRebuildMs)
        }
        scheduleAutoSave(current.projectTitle, nextTimeline)
    }

    private fun scheduleCompositionRebuild(delayMs: Long = 180L) {
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            delay(delayMs)
            _uiState.update { it.copy(compositionVersion = it.compositionVersion + 1) }
        }
    }

    private fun scheduleAutoSave(title: String, timeline: ProjectTimeline) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            delay(350L)
            runCatching { repository.saveTimeline(projectId, title, timeline) }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    fun undo() {
        val current = _uiState.value
        val restored = undoRedoManager.undo(current.timeline) ?: return
        LiveClipRegistry.syncTimeline(restored)
        rebuildJob?.cancel()
        val maxPlayhead = restored.totalDurationMs.coerceAtLeast(0L)
        val validSelectedClip = restored.mainClips.firstOrNull { it.id == current.selectedClipId }?.id
            ?: restored.mainClips.firstOrNull()?.id
        val validSelectedText = restored.textClips.firstOrNull { it.id == current.selectedTextId }?.id
        val validSelectedAudio = restored.audioClips.firstOrNull { it.id == current.selectedAudioId }?.id

        _uiState.update {
            it.copy(
                timeline = restored,
                playheadMs = it.playheadMs.coerceIn(0L, maxPlayhead),
                selectedClipId = validSelectedClip,
                selectedTextId = validSelectedText,
                selectedAudioId = validSelectedAudio,
                curveTarget = null,
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo,
                compositionVersion = it.compositionVersion + 1
            )
        }
        scheduleAutoSave(current.projectTitle, restored)
    }

    fun redo() {
        val current = _uiState.value
        val restored = undoRedoManager.redo(current.timeline) ?: return
        LiveClipRegistry.syncTimeline(restored)
        rebuildJob?.cancel()
        val maxPlayhead = restored.totalDurationMs.coerceAtLeast(0L)
        val validSelectedClip = restored.mainClips.firstOrNull { it.id == current.selectedClipId }?.id
            ?: restored.mainClips.firstOrNull()?.id
        val validSelectedText = restored.textClips.firstOrNull { it.id == current.selectedTextId }?.id
        val validSelectedAudio = restored.audioClips.firstOrNull { it.id == current.selectedAudioId }?.id

        _uiState.update {
            it.copy(
                timeline = restored,
                playheadMs = it.playheadMs.coerceIn(0L, maxPlayhead),
                selectedClipId = validSelectedClip,
                selectedTextId = validSelectedText,
                selectedAudioId = validSelectedAudio,
                curveTarget = null,
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo,
                compositionVersion = it.compositionVersion + 1
            )
        }
        scheduleAutoSave(current.projectTitle, restored)
    }

    // --- Playback & Navigation ---
    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun togglePlayPause() {
        _uiState.update { state ->
            val total = state.timeline.totalDurationMs.coerceAtLeast(0L)
            val atEnd = state.playheadMs >= total && total > 0L
            state.copy(
                isPlaying = !state.isPlaying,
                playheadMs = if (!state.isPlaying && atEnd) 0L else state.playheadMs
            )
        }
    }

    fun seekTo(ms: Long) {
        val maxMs = _uiState.value.timeline.totalDurationMs.coerceAtLeast(0L)
        val clamped = ms.coerceIn(0L, maxMs)
        _uiState.update { it.copy(playheadMs = clamped) }
    }

    fun stepFrame(deltaFrames: Int) {
        val fps = _uiState.value.timeline.fps.coerceAtLeast(24)
        val frameMs = (1000f / fps).toLong().coerceAtLeast(16L)
        seekTo(_uiState.value.playheadMs + deltaFrames * frameMs)
    }

    fun setTimelineZoom(pxPerSec: Float) {
        _uiState.update {
            it.copy(timelineZoomPxPerSec = pxPerSec.coerceIn(22f, 360f))
        }
    }

    fun selectMainClip(clipId: String) {
        _uiState.update {
            it.copy(
                selectedClipId = clipId,
                selectedTextId = null,
                selectedAudioId = null
            )
        }
    }

    fun selectTextClip(textId: String) {
        _uiState.update {
            it.copy(
                selectedTextId = textId,
                activeToolTab = EditorToolTab.TEXT,
                isInspectorExpanded = true,
                isMaskOverlayActive = false
            )
        }
    }

    fun selectAudioClip(audioId: String) {
        _uiState.update {
            it.copy(
                selectedAudioId = audioId,
                activeToolTab = EditorToolTab.AUDIO,
                isInspectorExpanded = true,
                isMaskOverlayActive = false
            )
        }
    }

    fun setActiveToolTab(tab: EditorToolTab, toggleIfSame: Boolean = false) {
        _uiState.update { current ->
            val nextExpanded = if (toggleIfSame && current.activeToolTab == tab) {
                !current.isInspectorExpanded
            } else {
                true
            }
            current.copy(
                activeToolTab = tab,
                isInspectorExpanded = nextExpanded,
                isMaskOverlayActive = tab == EditorToolTab.MASK && nextExpanded
            )
        }
    }

    fun setInspectorExpanded(expanded: Boolean) {
        _uiState.update {
            it.copy(
                isInspectorExpanded = expanded,
                isMaskOverlayActive = expanded && it.activeToolTab == EditorToolTab.MASK
            )
        }
    }

    fun toggleMaskOverlayMode() {
        _uiState.update { it.copy(isMaskOverlayActive = !it.isMaskOverlayActive) }
    }

    fun toggleFullscreenPreview() {
        _uiState.update { it.copy(isFullscreenPreview = !it.isFullscreenPreview) }
    }

    fun reportStatusBanner(message: String) {
        _uiState.update { it.copy(statusBanner = message) }
    }

    fun dismissStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    // --- Media Import & Clip Operations ---
    fun appendVisualMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusyImporting = true) }
            runCatching {
                val newClips = uris.map { uri ->
                    val imported = repository.mediaVault.importVisualMedia(projectId, uri)
                    VideoClip(
                        displayName = imported.displayName,
                        mediaPath = imported.localPath,
                        isImage = imported.isImage,
                        sourceDurationMs = imported.durationMs,
                        thumbnailPaths = imported.thumbnailPaths,
                        waveformPeaks = imported.waveformPeaks
                    )
                }
                mutateTimeline { tl ->
                    tl.copy(mainClips = tl.mainClips + newClips)
                }
                newClips.firstOrNull()?.let { first ->
                    _uiState.update { it.copy(selectedClipId = first.id) }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(statusBanner = err.localizedMessage ?: "Error al añadir clip") }
            }
            _uiState.update { it.copy(isBusyImporting = false) }
        }
    }

    fun splitSelectedClipAtPlayhead() {
        val state = _uiState.value
        val clip = state.selectedMainClip ?: return
        val startMs = state.timeline.mainClipStartById(clip.id) ?: return
        val localMs = state.playheadMs - startMs
        val sourceSpan = clip.sourceEndMs - clip.sourceStartMs
        if (sourceSpan <= 240L || localMs <= 120L || localMs >= clip.effectiveDurationMs - 120L) {
            _uiState.update { it.copy(statusBanner = "Coloca el cabezal dentro del clip para dividir.") }
            return
        }

        val minSplitSource = clip.sourceStartMs + 100L
        val maxSplitSource = clip.sourceEndMs - 100L
        val sourceDelta = (localMs * clip.speed.coerceIn(0.1f, 8f)).toLong()
        val splitSourceMs = (clip.sourceStartMs + sourceDelta).coerceIn(minSplitSource, maxSplitSource)

        val leftKeyframes = clip.keyframes.mapValues { (_, nodes) ->
            nodes.filter { it.timeOffsetMs <= localMs }
        }.filterValues { it.isNotEmpty() }

        val rightKeyframes = clip.keyframes.mapValues { (_, nodes) ->
            nodes.filter { it.timeOffsetMs >= localMs }.map {
                it.copy(id = UUID.randomUUID().toString(), timeOffsetMs = (it.timeOffsetMs - localMs).coerceAtLeast(0L))
            }
        }.filterValues { it.isNotEmpty() }

        val leftClip = clip.copy(
            sourceEndMs = splitSourceMs,
            keyframes = leftKeyframes
        )
        val rightClip = clip.copy(
            id = UUID.randomUUID().toString(),
            sourceStartMs = splitSourceMs,
            transitionIn = TransitionSpec(),
            keyframes = rightKeyframes
        )

        mutateTimeline { tl ->
            val idx = tl.mainClips.indexOfFirst { it.id == clip.id }
            if (idx < 0) tl else {
                val updated = tl.mainClips.toMutableList()
                updated[idx] = leftClip
                updated.add(idx + 1, rightClip)
                tl.copy(mainClips = updated)
            }
        }
    }

    fun trimMainClip(clipId: String, newSourceStartMs: Long, newSourceEndMs: Long) {
        mutateTimeline(recordUndo = true, rebuildComposition = true) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { clip ->
                    if (clip.id != clipId) clip
                    else {
                        val safeDur = clip.sourceDurationMs.coerceAtLeast(100L)
                        val minSpan = minOf(200L, safeDur)
                        val maxStart = (safeDur - minSpan).coerceAtLeast(0L)
                        val clampedStart = newSourceStartMs.coerceIn(0L, maxStart)
                        val minEnd = (clampedStart + minSpan).coerceAtMost(safeDur)
                        val clampedEnd = newSourceEndMs.coerceIn(minEnd, safeDur)
                        clip.copy(sourceStartMs = clampedStart, sourceEndMs = clampedEnd)
                    }
                }
            )
        }
    }

    fun duplicateSelectedClip() {
        val clip = _uiState.value.selectedMainClip ?: return
        val copyClip = clip.copy(id = UUID.randomUUID().toString())
        mutateTimeline { tl ->
            val idx = tl.mainClips.indexOfFirst { it.id == clip.id }
            if (idx < 0) tl else {
                val mutable = tl.mainClips.toMutableList()
                mutable.add(idx + 1, copyClip)
                tl.copy(mainClips = mutable)
            }
        }
        _uiState.update { it.copy(selectedClipId = copyClip.id) }
    }

    fun deleteSelectedClip() {
        val state = _uiState.value
        val clip = state.selectedMainClip ?: return
        if (state.timeline.mainClips.size <= 1) {
            _uiState.update { it.copy(statusBanner = "El proyecto debe tener al menos un clip principal.") }
            return
        }
        mutateTimeline { tl ->
            tl.copy(mainClips = tl.mainClips.filterNot { it.id == clip.id })
        }
    }

    fun moveSelectedClip(direction: Int) {
        val clip = _uiState.value.selectedMainClip ?: return
        mutateTimeline { tl ->
            val idx = tl.mainClips.indexOfFirst { it.id == clip.id }
            val targetIdx = idx + direction
            if (idx < 0 || targetIdx !in tl.mainClips.indices) tl else {
                val list = tl.mainClips.toMutableList()
                val item = list.removeAt(idx)
                list.add(targetIdx, item)
                tl.copy(mainClips = list)
            }
        }
    }

    // --- Direct Canvas Manipulation & Continuous Slider Grouping ---
    fun beginLiveGesture() {
        if (liveGestureBaseline == null) {
            liveGestureBaseline = _uiState.value.timeline
        }
    }

    fun updateTransformFromCanvasGesture(
        deltaNormX: Float,
        deltaNormY: Float,
        scaleFactor: Float,
        deltaRotationDeg: Float
    ) {
        val state = _uiState.value
        val selectedText = state.selectedTextClip
        if (selectedText != null && state.activeToolTab == EditorToolTab.TEXT) {
            val maxTextDur = selectedText.durationMs.coerceAtLeast(0L)
            val localMs = (state.playheadMs - selectedText.timelineStartMs).coerceIn(0L, maxTextDur)
            val eval = KeyframeEvaluator.evaluateTextFrame(selectedText, localMs).transform
            val nextX = (eval.posX + deltaNormX).coerceIn(-1.4f, 1.4f)
            val nextY = (eval.posY + deltaNormY).coerceIn(-1.4f, 1.4f)
            val nextScale = (eval.scale * scaleFactor).coerceIn(0.25f, 4f)
            val nextRot = eval.rotationDeg + deltaRotationDeg

            mutateTimeline(recordUndo = false, rebuildComposition = false) { tl ->
                tl.copy(
                    textClips = tl.textClips.map { tc ->
                        if (tc.id != selectedText.id) tc
                        else tc.copy(
                            transform = tc.transform.copy(
                                posX = nextX,
                                posY = nextY,
                                scale = nextScale,
                                rotationDeg = nextRot
                            )
                        )
                    }
                )
            }
            return
        }

        val clip = state.selectedMainClip ?: return
        val clipStart = state.timeline.mainClipStartById(clip.id) ?: 0L
        val localMs = (state.playheadMs - clipStart).coerceIn(0L, clip.effectiveDurationMs.coerceAtLeast(0L))
        val eval = KeyframeEvaluator.evaluateClipFrame(clip, localMs).transform

        val nextX = (eval.posX + deltaNormX).coerceIn(-1.5f, 1.5f)
        val nextY = (eval.posY + deltaNormY).coerceIn(-1.5f, 1.5f)
        val nextScale = (eval.scale * scaleFactor).coerceIn(0.15f, 4.5f)
        val nextRot = eval.rotationDeg + deltaRotationDeg

        mutateTimeline(recordUndo = false, rebuildComposition = false, visualOnly = true, debounceRebuildMs = 200L) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c
                    else {
                        var updated = c.copy(
                            transform = c.transform.copy(
                                posX = nextX,
                                posY = nextY,
                                scale = nextScale,
                                rotationDeg = nextRot
                            )
                        )
                        if (updated.keyframes.containsKey(KeyframeProperty.POS_X)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.POS_X, localMs, nextX)
                        }
                        if (updated.keyframes.containsKey(KeyframeProperty.POS_Y)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.POS_Y, localMs, nextY)
                        }
                        if (updated.keyframes.containsKey(KeyframeProperty.SCALE)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.SCALE, localMs, nextScale)
                        }
                        if (updated.keyframes.containsKey(KeyframeProperty.ROTATION)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.ROTATION, localMs, nextRot)
                        }
                        updated
                    }
                }
            )
        }
    }

    fun updateTextPositionDelta(textId: String, deltaNormX: Float, deltaNormY: Float) {
        mutateTimeline(recordUndo = false, rebuildComposition = false, visualOnly = true) { tl ->
            tl.copy(
                textClips = tl.textClips.map { tc ->
                    if (tc.id != textId) tc
                    else tc.copy(
                        transform = tc.transform.copy(
                            posX = (tc.transform.posX + deltaNormX).coerceIn(-1.4f, 1.4f),
                            posY = (tc.transform.posY + deltaNormY).coerceIn(-1.4f, 1.4f)
                        )
                    )
                }
            )
        }
    }

    fun endLiveGesture(rebuildImmediately: Boolean = true) {
        val baseline = liveGestureBaseline
        liveGestureBaseline = null
        val state = _uiState.value
        if (baseline != null && baseline != state.timeline) {
            undoRedoManager.recordBeforeMutation(baseline)
            val shouldRebuild = rebuildImmediately && !state.isPlaying
            if (shouldRebuild) {
                rebuildJob?.cancel()
            }
            _uiState.update {
                it.copy(
                    canUndo = undoRedoManager.canUndo,
                    canRedo = undoRedoManager.canRedo,
                    compositionVersion = if (shouldRebuild) it.compositionVersion + 1 else it.compositionVersion
                )
            }
            scheduleAutoSave(state.projectTitle, state.timeline)
        }
    }

    fun resetSelectedTransform() {
        val clip = _uiState.value.selectedMainClip ?: return
        mutateTimeline { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c
                    else c.copy(
                        transform = ClipTransform(),
                        keyframes = c.keyframes - KeyframeProperty.POS_X - KeyframeProperty.POS_Y - KeyframeProperty.SCALE - KeyframeProperty.ROTATION
                    )
                }
            )
        }
    }

    // --- Keyframes & Animation Curves ---
    fun toggleKeyframe(property: KeyframeProperty) {
        val state = _uiState.value
        val clip = state.selectedMainClip ?: return
        val clipStart = state.timeline.mainClipStartById(clip.id) ?: return
        val localMs = (state.playheadMs - clipStart).coerceIn(0L, clip.effectiveDurationMs.coerceAtLeast(0L))

        val existingNodes = clip.keyframes[property].orEmpty()
        val nearby = existingNodes.firstOrNull { abs(it.timeOffsetMs - localMs) <= 45L }

        mutateTimeline { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c
                    else if (nearby != null) {
                        val remaining = existingNodes.filterNot { it.id == nearby.id }
                        val newMap = if (remaining.isEmpty()) c.keyframes - property else c.keyframes + (property to remaining)
                        c.copy(keyframes = newMap)
                    } else {
                        val currentValue = evaluateCurrentPropertyValue(c, property, localMs)
                        upsertKeyframeOnClip(c, property, localMs, currentValue)
                    }
                }
            )
        }
    }

    fun hasKeyframeAtPlayhead(property: KeyframeProperty): Boolean {
        val state = _uiState.value
        val clip = state.selectedMainClip ?: return false
        val clipStart = state.timeline.mainClipStartById(clip.id) ?: return false
        val localMs = (state.playheadMs - clipStart).coerceIn(0L, clip.effectiveDurationMs.coerceAtLeast(0L))
        return clip.keyframes[property].orEmpty().any { abs(it.timeOffsetMs - localMs) <= 45L }
    }

    fun openCurveEditor(property: KeyframeProperty) {
        val state = _uiState.value
        val clip = state.selectedMainClip ?: return
        val nodes = clip.keyframes[property].orEmpty().sortedBy { it.timeOffsetMs }
        if (nodes.size < 2) {
            _uiState.update {
                it.copy(statusBanner = "Agrega al menos 2 fotogramas clave en ${property.label} para editar su curva.")
            }
            return
        }
        val clipStart = state.timeline.mainClipStartById(clip.id) ?: 0L
        val localMs = (state.playheadMs - clipStart).coerceIn(0L, clip.effectiveDurationMs.coerceAtLeast(0L))

        var segmentIndex = 0
        for (i in 0 until nodes.lastIndex) {
            if (localMs in nodes[i].timeOffsetMs..nodes[i + 1].timeOffsetMs) {
                segmentIndex = i
                break
            }
            if (localMs > nodes[i + 1].timeOffsetMs) {
                segmentIndex = i
            }
        }
        _uiState.update {
            it.copy(
                curveTarget = CurveTarget(
                    clipId = clip.id,
                    property = property,
                    startNode = nodes[segmentIndex],
                    endNode = nodes[segmentIndex + 1]
                )
            )
        }
    }

    fun closeCurveEditor() {
        _uiState.update { it.copy(curveTarget = null) }
    }

    fun updateCurveForSegment(curve: EasingCurve) {
        val target = _uiState.value.curveTarget ?: return
        mutateTimeline(recordUndo = true, rebuildComposition = false, debounceRebuildMs = 180L) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != target.clipId) c
                    else {
                        val updatedNodes = c.keyframes[target.property].orEmpty().map { node ->
                            if (node.id == target.startNode.id) node.copy(easing = curve) else node
                        }
                        c.copy(keyframes = c.keyframes + (target.property to updatedNodes))
                    }
                }
            )
        }
        _uiState.update { state ->
            state.curveTarget?.let { ct ->
                state.copy(curveTarget = ct.copy(startNode = ct.startNode.copy(easing = curve)))
            } ?: state
        }
    }

    fun jumpToAdjacentKeyframe(next: Boolean) {
        val state = _uiState.value
        val clip = state.selectedMainClip ?: return
        val clipStart = state.timeline.mainClipStartById(clip.id) ?: return
        val localMs = state.playheadMs - clipStart
        val allTimes = clip.keyframes.values.flatten().map { it.timeOffsetMs }.distinct().sorted()
        val targetLocal = if (next) {
            allTimes.firstOrNull { it > localMs + 30L }
        } else {
            allTimes.lastOrNull { it < localMs - 30L }
        } ?: return
        seekTo(clipStart + targetLocal)
    }

    private fun evaluateCurrentPropertyValue(
        clip: VideoClip,
        property: KeyframeProperty,
        localMs: Long
    ): Float {
        val base = when (property) {
            KeyframeProperty.POS_X -> clip.transform.posX
            KeyframeProperty.POS_Y -> clip.transform.posY
            KeyframeProperty.SCALE -> clip.transform.scale
            KeyframeProperty.ROTATION -> clip.transform.rotationDeg
            KeyframeProperty.OPACITY -> clip.transform.opacity
            KeyframeProperty.MASK_X -> clip.mask.centerX
            KeyframeProperty.MASK_Y -> clip.mask.centerY
            KeyframeProperty.MASK_WIDTH -> clip.mask.width
            KeyframeProperty.MASK_HEIGHT -> clip.mask.height
            KeyframeProperty.MASK_ROTATION -> clip.mask.rotationDeg
            KeyframeProperty.MASK_FEATHER -> clip.mask.feather
            KeyframeProperty.VOLUME -> clip.volume
            KeyframeProperty.EFFECT_INTENSITY -> clip.effectIntensity
        }
        return KeyframeEvaluator.evaluateProperty(clip.keyframes[property], base, localMs)
    }

    private fun upsertKeyframeOnClip(
        clip: VideoClip,
        property: KeyframeProperty,
        localMs: Long,
        value: Float
    ): VideoClip {
        val list = clip.keyframes[property].orEmpty().toMutableList()
        val existingIdx = list.indexOfFirst { abs(it.timeOffsetMs - localMs) <= 45L }
        if (existingIdx >= 0) {
            list[existingIdx] = list[existingIdx].copy(value = value)
        } else {
            list.add(KeyframeNode(timeOffsetMs = localMs, value = value))
        }
        list.sortBy { it.timeOffsetMs }
        return clip.copy(keyframes = clip.keyframes + (property to list))
    }

    // --- Masks ---
    fun setMaskType(maskType: MaskType) {
        val clip = _uiState.value.selectedMainClip ?: return
        mutateTimeline(visualOnly = true) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c
                    else c.copy(mask = c.mask.copy(type = maskType))
                }
            )
        }
    }

    fun updateMask(mask: MaskSpec, recordUndo: Boolean = true, livePreviewOnly: Boolean = false) {
        val state = _uiState.value
        val clip = state.selectedMainClip ?: return
        val clipStart = state.timeline.mainClipStartById(clip.id) ?: 0L
        val localMs = (state.playheadMs - clipStart).coerceIn(0L, clip.effectiveDurationMs.coerceAtLeast(0L))

        if (livePreviewOnly) beginLiveGesture()

        mutateTimeline(
            recordUndo = recordUndo && !livePreviewOnly,
            rebuildComposition = !livePreviewOnly,
            visualOnly = true,
            debounceRebuildMs = if (livePreviewOnly) 200L else 0L
        ) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c
                    else {
                        var updated = c.copy(mask = mask)
                        if (updated.keyframes.containsKey(KeyframeProperty.MASK_X)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.MASK_X, localMs, mask.centerX)
                        }
                        if (updated.keyframes.containsKey(KeyframeProperty.MASK_Y)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.MASK_Y, localMs, mask.centerY)
                        }
                        if (updated.keyframes.containsKey(KeyframeProperty.MASK_WIDTH)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.MASK_WIDTH, localMs, mask.width)
                        }
                        if (updated.keyframes.containsKey(KeyframeProperty.MASK_HEIGHT)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.MASK_HEIGHT, localMs, mask.height)
                        }
                        if (updated.keyframes.containsKey(KeyframeProperty.MASK_ROTATION)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.MASK_ROTATION, localMs, mask.rotationDeg)
                        }
                        if (updated.keyframes.containsKey(KeyframeProperty.MASK_FEATHER)) {
                            updated = upsertKeyframeOnClip(updated, KeyframeProperty.MASK_FEATHER, localMs, mask.feather)
                        }
                        updated
                    }
                }
            )
        }
    }

    fun keyframeAllMaskProperties() {
        val state = _uiState.value
        val clip = state.selectedMainClip ?: return
        val clipStart = state.timeline.mainClipStartById(clip.id) ?: return
        val localMs = (state.playheadMs - clipStart).coerceIn(0L, clip.effectiveDurationMs.coerceAtLeast(0L))
        val evalMask = KeyframeEvaluator.evaluateClipFrame(clip, localMs).mask

        mutateTimeline(visualOnly = true) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c
                    else {
                        var u = upsertKeyframeOnClip(c, KeyframeProperty.MASK_X, localMs, evalMask.centerX)
                        u = upsertKeyframeOnClip(u, KeyframeProperty.MASK_Y, localMs, evalMask.centerY)
                        u = upsertKeyframeOnClip(u, KeyframeProperty.MASK_WIDTH, localMs, evalMask.width)
                        u = upsertKeyframeOnClip(u, KeyframeProperty.MASK_HEIGHT, localMs, evalMask.height)
                        u = upsertKeyframeOnClip(u, KeyframeProperty.MASK_ROTATION, localMs, evalMask.rotationDeg)
                        u = upsertKeyframeOnClip(u, KeyframeProperty.MASK_FEATHER, localMs, evalMask.feather)
                        u
                    }
                }
            )
        }
    }

    // --- Effects, Color, Transitions, Speed, Opacity ---
    fun setVisualEffect(effect: VisualEffectType, intensity: Float, livePreviewOnly: Boolean = false) {
        val clip = _uiState.value.selectedMainClip ?: return
        if (livePreviewOnly) beginLiveGesture()
        mutateTimeline(
            recordUndo = !livePreviewOnly,
            rebuildComposition = !livePreviewOnly,
            visualOnly = true,
            debounceRebuildMs = if (livePreviewOnly) 200L else 0L
        ) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c
                    else c.copy(visualEffect = effect, effectIntensity = intensity.coerceIn(0f, 1f))
                }
            )
        }
    }

    fun updateColorGrade(grade: ColorGradeSpec, livePreviewOnly: Boolean = false) {
        val clip = _uiState.value.selectedMainClip ?: return
        if (livePreviewOnly) beginLiveGesture()
        mutateTimeline(
            recordUndo = !livePreviewOnly,
            rebuildComposition = !livePreviewOnly,
            visualOnly = true,
            debounceRebuildMs = if (livePreviewOnly) 200L else 0L
        ) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c else c.copy(colorGrade = grade)
                }
            )
        }
    }

    fun setTransitionIn(transition: TransitionSpec, livePreviewOnly: Boolean = false) {
        val clip = _uiState.value.selectedMainClip ?: return
        if (livePreviewOnly) beginLiveGesture()
        mutateTimeline(
            recordUndo = !livePreviewOnly,
            rebuildComposition = !livePreviewOnly,
            visualOnly = true,
            debounceRebuildMs = if (livePreviewOnly) 200L else 0L
        ) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c else c.copy(transitionIn = transition)
                }
            )
        }
    }

    fun setClipSpeed(speed: Float, preset: SpeedRampPreset, livePreviewOnly: Boolean = false) {
        val clip = _uiState.value.selectedMainClip ?: return
        if (livePreviewOnly) beginLiveGesture()
        mutateTimeline(
            recordUndo = !livePreviewOnly,
            rebuildComposition = !livePreviewOnly,
            debounceRebuildMs = if (livePreviewOnly) 220L else 0L
        ) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c else c.copy(
                        speed = speed.coerceIn(0.1f, 8f),
                        speedPreset = preset
                    )
                }
            )
        }
    }

    fun setClipOpacity(opacity: Float, livePreviewOnly: Boolean = false) {
        val state = _uiState.value
        val clip = state.selectedMainClip ?: return
        val clipStart = state.timeline.mainClipStartById(clip.id) ?: 0L
        val localMs = (state.playheadMs - clipStart).coerceIn(0L, clip.effectiveDurationMs.coerceAtLeast(0L))

        if (livePreviewOnly) beginLiveGesture()
        mutateTimeline(
            recordUndo = !livePreviewOnly,
            rebuildComposition = !livePreviewOnly,
            visualOnly = true,
            debounceRebuildMs = if (livePreviewOnly) 200L else 0L
        ) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c
                    else {
                        val safeOpacity = opacity.coerceIn(0f, 1f)
                        var u = c.copy(transform = c.transform.copy(opacity = safeOpacity))
                        if (u.keyframes.containsKey(KeyframeProperty.OPACITY)) {
                            u = upsertKeyframeOnClip(u, KeyframeProperty.OPACITY, localMs, safeOpacity)
                        }
                        u
                    }
                }
            )
        }
    }

    fun setClipAudio(volume: Float, muted: Boolean, fadeInMs: Long, fadeOutMs: Long, livePreviewOnly: Boolean = false) {
        val clip = _uiState.value.selectedMainClip ?: return
        if (livePreviewOnly) beginLiveGesture()
        mutateTimeline(
            recordUndo = !livePreviewOnly,
            rebuildComposition = !livePreviewOnly,
            debounceRebuildMs = if (livePreviewOnly) 180L else 0L
        ) { tl ->
            tl.copy(
                mainClips = tl.mainClips.map { c ->
                    if (c.id != clip.id) c
                    else c.copy(
                        volume = volume.coerceIn(0f, 2f),
                        isMuted = muted,
                        fadeInMs = fadeInMs.coerceAtLeast(0L),
                        fadeOutMs = fadeOutMs.coerceAtLeast(0L)
                    )
                }
            )
        }
    }

    // --- Audio Tracks ---
    fun addAudioTrack(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusyImporting = true) }
            runCatching {
                val imported = repository.mediaVault.importAudioMedia(projectId, uri)
                val newAudio = AudioClip(
                    title = imported.displayName,
                    mediaPath = imported.localPath,
                    timelineStartMs = _uiState.value.playheadMs,
                    sourceDurationMs = imported.durationMs,
                    waveformPeaks = imported.waveformPeaks
                )
                mutateTimeline { tl ->
                    tl.copy(audioClips = tl.audioClips + newAudio)
                }
                _uiState.update { it.copy(selectedAudioId = newAudio.id) }
            }.onFailure { err ->
                _uiState.update { it.copy(statusBanner = err.localizedMessage ?: "Error al importar audio") }
            }
            _uiState.update { it.copy(isBusyImporting = false) }
        }
    }

    fun updateAudioClip(updated: AudioClip, livePreviewOnly: Boolean = false) {
        if (livePreviewOnly) beginLiveGesture()
        mutateTimeline(
            recordUndo = !livePreviewOnly,
            rebuildComposition = !livePreviewOnly,
            debounceRebuildMs = if (livePreviewOnly) 180L else 0L
        ) { tl ->
            tl.copy(audioClips = tl.audioClips.map { if (it.id == updated.id) updated else it })
        }
    }

    fun deleteAudioClip(id: String) {
        mutateTimeline { tl ->
            tl.copy(audioClips = tl.audioClips.filterNot { it.id == id })
        }
    }

    // --- Text Overlays ---
    fun addTextClip(initialText: String = "Nuevo Título") {
        val state = _uiState.value
        val newText = TextClip(
            text = initialText,
            timelineStartMs = state.playheadMs,
            durationMs = 3000L
        )
        mutateTimeline(rebuildComposition = false) { tl ->
            tl.copy(textClips = tl.textClips + newText)
        }
        _uiState.update {
            it.copy(
                selectedTextId = newText.id,
                activeToolTab = EditorToolTab.TEXT
            )
        }
    }

    fun updateTextClip(updated: TextClip) {
        mutateTimeline(rebuildComposition = false) { tl ->
            tl.copy(textClips = tl.textClips.map { if (it.id == updated.id) updated else it })
        }
    }

    fun deleteTextClip(id: String) {
        mutateTimeline(rebuildComposition = false) { tl ->
            tl.copy(textClips = tl.textClips.filterNot { it.id == id })
        }
    }

    // --- Aspect Ratio & FPS ---
    fun setAspectRatio(ratio: AspectRatio) {
        mutateTimeline { tl -> tl.copy(aspectRatio = ratio) }
    }

    fun setProjectFps(fps: Int) {
        mutateTimeline { tl -> tl.copy(fps = fps.coerceIn(24, 60)) }
    }

    // --- Export Studio ---
    fun openExportSheet() {
        _uiState.update { it.copy(showExportSheet = true, isPlaying = false) }
    }

    fun closeExportSheet() {
        _uiState.update { it.copy(showExportSheet = false) }
    }

    fun updateExportConfig(config: ExportConfig) {
        _uiState.update { it.copy(exportConfig = config) }
    }

    fun startExport() {
        val state = _uiState.value
        exportEngine.startExport(
            scope = viewModelScope,
            projectTitle = state.projectTitle,
            timeline = state.timeline,
            config = state.exportConfig
        )
    }

    override fun onCleared() {
        super.onCleared()
        rebuildJob?.cancel()
        saveJob?.cancel()
        exportEngine.cancelExport()
    }

    class Factory(
        private val context: Context,
        private val projectId: String,
        private val repository: ProjectRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(context, projectId, repository) as T
        }
    }
}
