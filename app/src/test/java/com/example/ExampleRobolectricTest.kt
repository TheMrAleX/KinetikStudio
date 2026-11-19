package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.engine.KeyframeEvaluator
import com.example.domain.engine.UndoRedoManager
import com.example.domain.model.EasingCurve
import com.example.domain.model.EasingType
import com.example.domain.model.KeyframeNode
import com.example.domain.model.KeyframeProperty
import com.example.domain.model.ProjectTimeline
import com.example.domain.model.VideoClip
import com.example.engine.ExportCodec
import com.example.engine.ExportConfig
import com.example.engine.ExportQuality
import com.example.engine.ExportResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_matchesKinetikStudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        assertTrue("Expected overshoot curve to exceed 100 briefly or accelerate smoothly, got $overshootSample", overshootSample > 90f)
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
}
