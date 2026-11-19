package com.example.ui.util

import java.util.Locale
import kotlin.math.roundToInt

fun formatTimecode(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val centiseconds = ((safe % 1000L) / 10L).toInt()
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds)
}

fun formatDurationShort(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = (safe / 1000.0).roundToInt()
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
