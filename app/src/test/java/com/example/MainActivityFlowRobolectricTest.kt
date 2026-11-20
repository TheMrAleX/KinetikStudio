package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KinetikDatabase
import com.example.data.media.MediaVault
import com.example.data.repository.ProjectRepository
import com.example.ui.viewmodel.EditorToolTab
import com.example.ui.viewmodel.ProjectsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityFlowRobolectricTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
    }

    private fun createValidJpegFile(): File {
        val file = File.createTempFile("sample_photo", ".jpg", ApplicationProvider.getApplicationContext<Context>().cacheDir)
        tempFiles.add(file)
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.CYAN)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        return file
    }

    @Test
    fun mainActivity_createProjectFromImageAndEnterExistingProjectThroughNavHost_noCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = KinetikDatabase.getInstance(context)
        val repo = ProjectRepository(db.projectDao(), MediaVault(context))

        val imgFile = createValidJpegFile()
        val created = runBlocking {
            repo.createProjectFromMedia(
                title = "Proyecto Imagen E2E",
                uris = listOf(Uri.fromFile(imgFile))
            )
        }
        assertNotNull(created.id)
        assertTrue(created.timeline.mainClips.first().isImage)

        // Also test ProjectsViewModel.createProject callback
        val vm = ProjectsViewModel(repo)
        var createdViaVmId: String? = null
        activityRule.runOnUiThread {
            vm.createProject("Proyecto VM E2E", listOf(Uri.fromFile(imgFile))) { id ->
                createdViaVmId = id
            }
        }
        val deadline = System.currentTimeMillis() + 5_000L
        while (createdViaVmId == null && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertNotNull(createdViaVmId)

        activityRule.waitForIdle()

        // Verify newly created project card is shown on ProjectsRoute and click to enter EditorRoute
        activityRule.onNodeWithTag("project_card_${created.id}").assertIsDisplayed()
        activityRule.onNodeWithTag("project_card_${created.id}").performClick()
        activityRule.waitForIdle()

        // Verify EditorStudioScreen loaded with ViewportCanvas & MultiTrackTimeline
        activityRule.onNodeWithTag("video_preview_viewport").assertIsDisplayed()
        activityRule.onNodeWithTag("multitrack_timeline").assertIsDisplayed()

        // Exercise Mask (Linear), Keyframe, Effects, Color, Transitions, Text, Format
        activityRule.onNodeWithTag("editor_tool_dock").performScrollToIndex(EditorToolTab.MASK.ordinal)
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("tool_tab_${EditorToolTab.MASK.name}").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Lineal").performClick()
        activityRule.waitForIdle()

        activityRule.onNodeWithTag("editor_tool_dock").performScrollToIndex(EditorToolTab.KEYFRAME.ordinal)
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("tool_tab_${EditorToolTab.KEYFRAME.name}").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("toggle_keyframe_button").performClick()
        activityRule.waitForIdle()

        activityRule.onNodeWithTag("editor_tool_dock").performScrollToIndex(EditorToolTab.EFFECTS.ordinal)
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("tool_tab_${EditorToolTab.EFFECTS.name}").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("VHS Glitch").performClick()
        activityRule.waitForIdle()

        activityRule.onNodeWithTag("editor_tool_dock").performScrollToIndex(EditorToolTab.COLOR.ordinal)
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("tool_tab_${EditorToolTab.COLOR.name}").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Cinemático").performClick()
        activityRule.waitForIdle()

        activityRule.onNodeWithTag("editor_tool_dock").performScrollToIndex(EditorToolTab.TEXT.ordinal)
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("tool_tab_${EditorToolTab.TEXT.name}").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("add_text_button").performClick()
        activityRule.waitForIdle()

        // Navigate back to ProjectHubScreen
        activityRule.onNodeWithTag("editor_back_button").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("project_card_${created.id}").assertIsDisplayed()
        assertEquals(created.title, runBlocking { repo.getProject(created.id)?.title })
    }
}
