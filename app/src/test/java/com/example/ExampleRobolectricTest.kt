package com.example

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KinetikDatabase
import com.example.data.media.MediaVault
import com.example.data.repository.ProjectRepository
import com.example.domain.engine.KeyframeEvaluator
import com.example.domain.engine.UndoRedoManager
import com.example.domain.model.AspectRatio
import com.example.domain.model.AudioClip
import com.example.domain.model.ColorFilterPreset
import com.example.domain.model.ColorGradeSpec
import com.example.domain.model.EasingCurve
import com.example.domain.model.EasingType
import com.example.domain.model.KeyframeNode
import com.example.domain.model.KeyframeProperty
import com.example.domain.model.MaskType
import com.example.domain.model.ProjectTimeline
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TextClip
import com.example.domain.model.TransitionSpec
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.VisualEffectType
import com.example.engine.CompositionBuilder
import com.example.engine.ExportCodec
import com.example.engine.ExportConfig
import com.example.engine.ExportQuality
import com.example.engine.ExportResolution
import com.example.engine.LiveClipRegistry
import com.example.ui.viewmodel.EditorToolTab
import com.example.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: KinetikDatabase
    private lateinit var repository: ProjectRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KinetikDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProjectRepository(db.projectDao(), MediaVault(context))
    }

    @After
    fun tearDown() {
        db.close()
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
        Dispatchers.resetMain()
    }

    private fun createTempMedia(prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix, context.cacheDir).also { tempFiles.add(it) }
    }

    private fun createLoadedEditorViewModel(
        projectId: String = "proj-1",
        timeline: ProjectTimeline
    ): EditorViewModel {
        return runBlocking {
            repository.saveTimeline(projectId, "Proyecto Test", timeline)
            val vm = EditorViewModel(context, projectId, repository)
            withTimeout(3000L) {
                vm.uiState.first { !it.isLoading }
            }
            vm
        }
    }

    @Test
    fun appName_matchesKinetikStudio() {
        assertEquals("Kinetik Studio", context.getString(R.string.app_name))
    }

    @Test
    fun keyframeEvaluator_interpolatesLinearAndBezierCurvesAccurately() {
        val linearNodes = listOf(
            KeyframeNode(timeOffsetMs = 0L, value = 1.0f, easing = EasingCurve.fromPreset(EasingType.LINEAR)),
            KeyframeNode(timeOffsetMs = 1000L, value = 3.0f)
        )
        assertEquals(2.0f, KeyframeEvaluator.evaluateProperty(linearNodes, 1f, 500L), 0.02f)

        val easeNodes = listOf(
            KeyframeNode(timeOffsetMs = 0L, value = 0f, easing = EasingCurve.fromPreset(EasingType.OVERSHOOT)),
            KeyframeNode(timeOffsetMs = 1000L, value = 100f)
        )
        val overshootSample = KeyframeEvaluator.evaluateProperty(easeNodes, 0f, 750L)
        assertTrue(
            "Expected overshoot curve to exceed 90, got $overshootSample",
            overshootSample > 90f
        )
    }

    @Test
    fun undoRedoManager_restoresSnapshotsInLifoOrder() {
        val manager = UndoRedoManager(maxHistory = 50)
        val clip1 = VideoClip(displayName = "a.mp4", mediaPath = "/tmp/a.mp4", sourceDurationMs = 3000L)
        val t0 = ProjectTimeline(mainClips = listOf(clip1))
        val t1 = t0.copy(mainClips = listOf(clip1.copy(speed = 2f)))

        manager.recordBeforeMutation(t0)
        assertTrue(manager.canUndo)
        val undone = manager.undo(t1)
        assertNotNull(undone)
        assertEquals(1f, undone!!.mainClips.first().speed, 0.001f)
        assertTrue(manager.canRedo)

        val redone = manager.redo(undone)
        assertNotNull(redone)
        assertEquals(2f, redone!!.mainClips.first().speed, 0.001f)
        assertFalse(manager.canRedo)
    }

    @Test
    fun exportConfig_calculatesHighBitrateForVisuallyLosslessH264AndH265() {
        val h265Config = ExportConfig(
            codec = ExportCodec.H265,
            resolution = ExportResolution.FHD_1080,
            fps = 60,
            quality = ExportQuality.VISUALLY_LOSSLESS
        )
        val h264Config = h265Config.copy(codec = ExportCodec.H264)
        assertTrue(h265Config.resolveTargetBitrateBps() >= 40_000_000)
        assertTrue(h264Config.resolveTargetBitrateBps() > h265Config.resolveTargetBitrateBps())
    }

    @Test
    fun editorViewModel_splitKeyframeMaskEffectsAndUndoWorkWithoutCrashing() {
        val clip = VideoClip(
            id = "clip-1",
            displayName = "demo.mp4",
            mediaPath = "/tmp/demo.mp4",
            sourceDurationMs = 4000L
        )
        val vm = createLoadedEditorViewModel(
            timeline = ProjectTimeline(mainClips = listOf(clip))
        )

        val initialState = vm.uiState.value
        assertFalse(initialState.isLoading)
        assertEquals("clip-1", initialState.selectedClipId)
        val initialVersion = initialState.compositionVersion

        // 1. Seek inside clip and split
        vm.seekTo(1500L)
        vm.splitSelectedClipAtPlayhead()
        val afterSplit = vm.uiState.value
        assertEquals(2, afterSplit.timeline.mainClips.size)
        assertEquals(1500L, afterSplit.timeline.mainClips[0].sourceEndMs)
        assertEquals(1500L, afterSplit.timeline.mainClips[1].sourceStartMs)
        assertTrue(afterSplit.compositionVersion > initialVersion)

        // 2. Toggle keyframes on Scale at 0ms and 1000ms, then open curve editor
        vm.seekTo(0L)
        vm.toggleKeyframe(KeyframeProperty.SCALE)
        vm.seekTo(1000L)
        vm.toggleKeyframe(KeyframeProperty.SCALE)
        vm.openCurveEditor(KeyframeProperty.SCALE)
        val curveTarget = vm.uiState.value.curveTarget
        assertNotNull(curveTarget)
        assertEquals(KeyframeProperty.SCALE, curveTarget!!.property)
        vm.updateCurveForSegment(EasingCurve.fromPreset(EasingType.OVERSHOOT))
        vm.closeCurveEditor()
        assertNull(vm.uiState.value.curveTarget)

        // 3. Apply linear mask, visual effect, color grade, transition, speed, aspect ratio
        vm.setMaskType(MaskType.LINEAR)
        assertEquals(MaskType.LINEAR, vm.uiState.value.selectedMainClip?.mask?.type)

        vm.setVisualEffect(VisualEffectType.VHS_GLITCH, 0.8f)
        assertEquals(VisualEffectType.VHS_GLITCH, vm.uiState.value.selectedMainClip?.visualEffect)

        vm.updateColorGrade(ColorGradeSpec(exposure = 0.25f, preset = ColorFilterPreset.CINEMATIC))
        assertEquals(ColorFilterPreset.CINEMATIC, vm.uiState.value.selectedMainClip?.colorGrade?.preset)

        vm.setTransitionIn(TransitionSpec(TransitionType.CROSS_DISSOLVE, 600L))
        assertEquals(TransitionType.CROSS_DISSOLVE, vm.uiState.value.selectedMainClip?.transitionIn?.type)

        vm.setClipSpeed(2f, SpeedRampPreset.HERO_TIME)
        assertEquals(2f, vm.uiState.value.selectedMainClip?.speed ?: 0f, 0.01f)

        vm.setAspectRatio(AspectRatio.RATIO_16_9)
        assertEquals(AspectRatio.RATIO_16_9, vm.uiState.value.timeline.aspectRatio)

        // 4. Add text clip and undo
        vm.addTextClip("Título Pro")
        assertEquals(1, vm.uiState.value.timeline.textClips.size)
        vm.undo()
        assertEquals(0, vm.uiState.value.timeline.textClips.size)
        vm.redo()
        assertEquals(1, vm.uiState.value.timeline.textClips.size)
    }

    @Test
    fun editorViewModel_handlesShortClipSplitAndTrimSafelyWithoutIllegalArgument() {
        val tinyClip = VideoClip(
            id = "tiny-1",
            displayName = "tiny.mp4",
            mediaPath = "/tmp/tiny.mp4",
            sourceDurationMs = 150L
        )
        val vm = createLoadedEditorViewModel(
            timeline = ProjectTimeline(mainClips = listOf(tinyClip))
        )

        // Attempting to split a 150ms clip should show statusBanner instead of throwing coerceIn exception
        vm.seekTo(75L)
        vm.splitSelectedClipAtPlayhead()
        assertEquals(1, vm.uiState.value.timeline.mainClips.size)
        assertNotNull(vm.uiState.value.statusBanner)

        // Attempting to trim a short clip beyond bounds must clamp safely without crashing
        vm.trimMainClip("tiny-1", newSourceStartMs = 140L, newSourceEndMs = 145L)
        val trimmed = vm.uiState.value.timeline.mainClips.first()
        assertTrue(trimmed.sourceStartMs >= 0L)
        assertTrue(trimmed.sourceEndMs <= 150L)
        assertTrue(trimmed.sourceEndMs > trimmed.sourceStartMs)
    }

    @OptIn(UnstableApi::class)
    @Test
    fun compositionBuilder_buildsPreviewWithoutGapsAndExportWithBlankTextSafely() {
        val videoFile = createTempMedia("video", ".mp4")
        val audioFile = createTempMedia("audio", ".m4a")

        val timeline = ProjectTimeline(
            aspectRatio = AspectRatio.RATIO_9_16,
            mainClips = listOf(
                VideoClip(
                    displayName = "v.mp4",
                    mediaPath = videoFile.absolutePath,
                    sourceDurationMs = 3000L,
                    speed = 1.5f,
                    volume = 1.2f,
                    visualEffect = VisualEffectType.CHROMATIC_ABERRATION
                )
            ),
            audioClips = listOf(
                AudioClip(
                    title = "music.m4a",
                    mediaPath = audioFile.absolutePath,
                    timelineStartMs = 1000L,
                    sourceDurationMs = 4000L,
                    volume = 0.8f
                )
            ),
            textClips = listOf(
                TextClip(
                    text = "   ",
                    timelineStartMs = 0L,
                    durationMs = 2000L
                )
            )
        )

        val previewComp = CompositionBuilder.buildComposition(
            context = context,
            timeline = timeline,
            targetShortEdgePx = 720,
            includeTextOverlays = false
        )
        assertEquals(2, previewComp.sequences.size)
        // Preview must not have gaps because CompositionPlayer forbids gaps
        assertFalse(previewComp.sequences.any { seq -> seq.editedMediaItems.isEmpty() })

        val exportComp = CompositionBuilder.buildComposition(
            context = context,
            timeline = timeline,
            targetShortEdgePx = 1080,
            includeTextOverlays = true
        )
        assertEquals(2, exportComp.sequences.size)
    }

    @Test
    fun editorViewModel_collapsibleInspectorAndLivePlaybackRegistryWorkSmoothly() {
        val clip = VideoClip(
            id = "clip-live",
            displayName = "live.mp4",
            mediaPath = "/tmp/live.mp4",
            sourceDurationMs = 5000L
        )
        val vm = createLoadedEditorViewModel(
            projectId = "proj-live",
            timeline = ProjectTimeline(mainClips = listOf(clip))
        )

        // Inspector starts expanded; tapping same tab collapses it, tapping another opens it
        assertTrue(vm.uiState.value.isInspectorExpanded)
        vm.setActiveToolTab(EditorToolTab.EDIT, toggleIfSame = true)
        assertFalse(vm.uiState.value.isInspectorExpanded)
        vm.setActiveToolTab(EditorToolTab.COLOR, toggleIfSame = true)
        assertTrue(vm.uiState.value.isInspectorExpanded)
        assertEquals(EditorToolTab.COLOR, vm.uiState.value.activeToolTab)

        // During playback, visual updates sync LiveClipRegistry at 60fps without rebuilding player
        vm.setPlaying(true)
        val versionBeforePlaybackEdits = vm.uiState.value.compositionVersion
        vm.updateColorGrade(ColorGradeSpec(exposure = 0.4f, preset = ColorFilterPreset.TEAL_ORANGE))
        vm.setMaskType(MaskType.CIRCLE)
        vm.setVisualEffect(VisualEffectType.FILM_GRAIN, 0.5f)

        assertEquals(versionBeforePlaybackEdits, vm.uiState.value.compositionVersion)
        val liveClip = LiveClipRegistry.getClip("clip-live")
        assertNotNull(liveClip)
        assertEquals(ColorFilterPreset.TEAL_ORANGE, liveClip!!.colorGrade.preset)
        assertEquals(MaskType.CIRCLE, liveClip.mask.type)
        assertEquals(VisualEffectType.FILM_GRAIN, liveClip.visualEffect)
    }
}
