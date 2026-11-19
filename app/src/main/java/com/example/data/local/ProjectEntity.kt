package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val aspectRatioName: String,
    val durationMs: Long,
    val resolutionLabel: String,
    val fps: Int,
    val thumbnailPath: String?,
    val timelineJson: String,
    val createdAt: Long,
    val updatedAt: Long
)
