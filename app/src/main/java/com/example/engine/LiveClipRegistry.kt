package com.example.engine

import com.example.domain.model.ProjectTimeline
import com.example.domain.model.VideoClip
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe live clip lookup shared between Compose ViewModel state and Media3's GL shader thread.
 * Allows real-time color grading, masks, effects, opacity, and transform updates during playback
 * without tearing down or rebuilding CompositionPlayer.
 */
object LiveClipRegistry {
    private val clipsById = ConcurrentHashMap<String, VideoClip>()

    fun syncTimeline(timeline: ProjectTimeline) {
        val activeIds = HashSet<String>(timeline.mainClips.size)
        for (clip in timeline.mainClips) {
            clipsById[clip.id] = clip
            activeIds.add(clip.id)
        }
        val iterator = clipsById.keys.iterator()
        while (iterator.hasNext()) {
            if (!activeIds.contains(iterator.next())) {
                iterator.remove()
            }
        }
    }

    fun getClip(clipId: String): VideoClip? = clipsById[clipId]
}
