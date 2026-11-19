package com.example.data.repository

import android.net.Uri
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.media.MediaVault
import com.example.domain.model.ProjectTimeline
import com.example.domain.model.VideoClip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

data class StudioProject(
    val id: String,
    val title: String,
    val timeline: ProjectTimeline,
    val thumbnailPath: String?,
    val resolutionLabel: String,
    val createdAt: Long,
    val updatedAt: Long
)

class ProjectRepository(
    private val projectDao: ProjectDao,
    val mediaVault: MediaVault
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeProjects(): Flow<List<StudioProject>> =
        projectDao.observeAllProjects().map { list ->
            list.mapNotNull { entity -> entityToStudioProject(entity) }
        }

    suspend fun getProject(projectId: String): StudioProject? {
        val entity = projectDao.getProjectById(projectId) ?: return null
        return entityToStudioProject(entity)
    }

    suspend fun createProjectFromMedia(title: String, uris: List<Uri>): StudioProject {
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val clips = mutableListOf<VideoClip>()
        var resolutionLabel = "1080p"

        for ((idx, uri) in uris.withIndex()) {
            val imported = mediaVault.importVisualMedia(projectId, uri)
            if (idx == 0 && imported.width > 0 && imported.height > 0) {
                val shortSide = minOf(imported.width, imported.height)
                resolutionLabel = when {
                    shortSide >= 2160 -> "4K"
                    shortSide >= 1440 -> "1440p"
                    shortSide >= 1080 -> "1080p"
                    else -> "720p"
                }
            }
            clips.add(
                VideoClip(
                    displayName = imported.displayName,
                    mediaPath = imported.localPath,
                    isImage = imported.isImage,
                    sourceDurationMs = imported.durationMs,
                    thumbnailPaths = imported.thumbnailPaths,
                    waveformPeaks = imported.waveformPeaks
                )
            )
        }

        val firstAspect = if (uris.isNotEmpty()) {
            val firstMedia = clips.firstOrNull()
            // Default to 9:16 mobile studio format; creator can switch aspect anytime
            com.example.domain.model.AspectRatio.RATIO_9_16
        } else {
            com.example.domain.model.AspectRatio.RATIO_9_16
        }

        val timeline = ProjectTimeline(
            aspectRatio = firstAspect,
            mainClips = clips
        )
        val thumb = clips.firstOrNull()?.thumbnailPaths?.firstOrNull()

        val project = StudioProject(
            id = projectId,
            title = title.ifBlank { "Proyecto Sin Título" },
            timeline = timeline,
            thumbnailPath = thumb,
            resolutionLabel = resolutionLabel,
            createdAt = now,
            updatedAt = now
        )
        saveProject(project)
        return project
    }

    suspend fun saveTimeline(projectId: String, title: String, timeline: ProjectTimeline) {
        val existing = projectDao.getProjectById(projectId)
        val now = System.currentTimeMillis()
        val thumb = timeline.mainClips.firstOrNull()?.thumbnailPaths?.firstOrNull()
            ?: existing?.thumbnailPath
        val entity = ProjectEntity(
            id = projectId,
            title = title,
            aspectRatioName = timeline.aspectRatio.name,
            durationMs = timeline.totalDurationMs,
            resolutionLabel = existing?.resolutionLabel ?: "1080p",
            fps = timeline.fps,
            thumbnailPath = thumb,
            timelineJson = json.encodeToString(ProjectTimeline.serializer(), timeline),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        projectDao.upsertProject(entity)
    }

    private suspend fun saveProject(project: StudioProject) {
        val entity = ProjectEntity(
            id = project.id,
            title = project.title,
            aspectRatioName = project.timeline.aspectRatio.name,
            durationMs = project.timeline.totalDurationMs,
            resolutionLabel = project.resolutionLabel,
            fps = project.timeline.fps,
            thumbnailPath = project.thumbnailPath,
            timelineJson = json.encodeToString(ProjectTimeline.serializer(), project.timeline),
            createdAt = project.createdAt,
            updatedAt = project.updatedAt
        )
        projectDao.upsertProject(entity)
    }

    suspend fun renameProject(projectId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            projectDao.renameProject(projectId, trimmed, System.currentTimeMillis())
        }
    }

    suspend fun duplicateProject(projectId: String): StudioProject? {
        val source = getProject(projectId) ?: return null
        val newId = UUID.randomUUID().toString()
        mediaVault.cloneProjectMedia(projectId, newId)
        val remappedTimeline = remapTimelinePaths(source.timeline, projectId, newId)
        val now = System.currentTimeMillis()
        val clone = source.copy(
            id = newId,
            title = "${source.title} (Copia)",
            timeline = remappedTimeline,
            thumbnailPath = source.thumbnailPath?.replace("/projects/$projectId/", "/projects/$newId/"),
            createdAt = now,
            updatedAt = now
        )
        saveProject(clone)
        return clone
    }

    suspend fun deleteProject(projectId: String) {
        projectDao.deleteProjectById(projectId)
        mediaVault.deleteProjectFiles(projectId)
    }

    private fun remapTimelinePaths(
        timeline: ProjectTimeline,
        oldId: String,
        newId: String
    ): ProjectTimeline {
        val oldSeg = "/projects/$oldId/"
        val newSeg = "/projects/$newId/"
        return timeline.copy(
            mainClips = timeline.mainClips.map { clip ->
                clip.copy(
                    id = UUID.randomUUID().toString(),
                    mediaPath = clip.mediaPath.replace(oldSeg, newSeg),
                    thumbnailPaths = clip.thumbnailPaths.map { it.replace(oldSeg, newSeg) }
                )
            },
            overlayClips = timeline.overlayClips.map { ov ->
                ov.copy(
                    id = UUID.randomUUID().toString(),
                    mediaPath = ov.mediaPath.replace(oldSeg, newSeg)
                )
            },
            audioClips = timeline.audioClips.map { au ->
                au.copy(
                    id = UUID.randomUUID().toString(),
                    mediaPath = au.mediaPath.replace(oldSeg, newSeg)
                )
            }
        )
    }

    private fun entityToStudioProject(entity: ProjectEntity): StudioProject? {
        return try {
            val timeline = json.decodeFromString(ProjectTimeline.serializer(), entity.timelineJson)
            StudioProject(
                id = entity.id,
                title = entity.title,
                timeline = timeline,
                thumbnailPath = entity.thumbnailPath,
                resolutionLabel = entity.resolutionLabel,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        } catch (_: Exception) {
            null
        }
    }
}
