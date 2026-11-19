package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class ImportedMediaResult(
    val displayName: String,
    val localPath: String,
    val isImage: Boolean,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumbnailPaths: List<String>,
    val waveformPeaks: List<Float>
)

class MediaVault(private val context: Context) {

    private fun projectDir(projectId: String): File {
        val dir = File(context.filesDir, "projects/$projectId/media")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun thumbsDir(projectId: String): File {
        val dir = File(context.filesDir, "projects/$projectId/thumbs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun importVisualMedia(projectId: String, uri: Uri): ImportedMediaResult =
        withContext(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val isImage = mimeType.startsWith("image/")
            val displayName = queryDisplayName(uri) ?: if (isImage) "Imagen.jpg" else "Clip.mp4"
            val ext = resolveExtension(displayName, mimeType, if (isImage) "jpg" else "mp4")
            val destFile = File(projectDir(projectId), "${UUID.randomUUID()}.$ext")

            copyUriToFile(uri, destFile)

            if (isImage) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destFile.absolutePath, opts)
                val thumbFile = File(thumbsDir(projectId), "${UUID.randomUUID()}_thumb.jpg")
                createImageThumbnail(destFile, thumbFile)
                ImportedMediaResult(
                    displayName = displayName,
                    localPath = destFile.absolutePath,
                    isImage = true,
                    durationMs = 4000L,
                    width = opts.outWidth.coerceAtLeast(1080),
                    height = opts.outHeight.coerceAtLeast(1920),
                    thumbnailPaths = listOf(thumbFile.absolutePath),
                    waveformPeaks = emptyList()
                )
            } else {
                val retriever = MediaMetadataRetriever()
                var durationMs = 3000L
                var width = 1080
                var height = 1920
                val thumbs = mutableListOf<String>()
                try {
                    retriever.setDataSource(destFile.absolutePath)
                    durationMs = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull()?.coerceAtLeast(500L) ?: 3000L
                    width = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )?.toIntOrNull() ?: 1080
                    height = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )?.toIntOrNull() ?: 1920

                    val thumbCount = (durationMs / 1500L).toInt().coerceIn(3, 10)
                    for (i in 0 until thumbCount) {
                        val us = ((durationMs * 1000L) * i) / thumbCount.coerceAtLeast(1)
                        val frame = retriever.getFrameAtTime(
                            us,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (frame != null) {
                            val scaled = scaleBitmapForStrip(frame, 144)
                            if (scaled !== frame) frame.recycle()
                            val thumbFile = File(thumbsDir(projectId), "${UUID.randomUUID()}_$i.jpg")
                            FileOutputStream(thumbFile).use { out ->
                                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                            }
                            scaled.recycle()
                            thumbs.add(thumbFile.absolutePath)
                        }
                    }
                } catch (_: Exception) {
                    // Keep safe metadata defaults if device codec cannot decode frame thumbnail
                } finally {
                    runCatching { retriever.release() }
                }

                val peaks = extractWaveformPeaks(destFile.absolutePath, targetBins = 72)
                ImportedMediaResult(
                    displayName = displayName,
                    localPath = destFile.absolutePath,
                    isImage = false,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    thumbnailPaths = thumbs,
                    waveformPeaks = peaks
                )
            }
        }

    suspend fun importAudioMedia(projectId: String, uri: Uri): ImportedMediaResult =
        withContext(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(uri) ?: "audio/mp4"
            val displayName = queryDisplayName(uri) ?: "Audio.m4a"
            val ext = resolveExtension(displayName, mimeType, "m4a")
            val destFile = File(projectDir(projectId), "${UUID.randomUUID()}.$ext")
            copyUriToFile(uri, destFile)

            val retriever = MediaMetadataRetriever()
            var durationMs = 10_000L
            try {
                retriever.setDataSource(destFile.absolutePath)
                durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()?.coerceAtLeast(500L) ?: 10_000L
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }

            val peaks = extractWaveformPeaks(destFile.absolutePath, targetBins = 120)
            ImportedMediaResult(
                displayName = displayName,
                localPath = destFile.absolutePath,
                isImage = false,
                durationMs = durationMs,
                width = 0,
                height = 0,
                thumbnailPaths = emptyList(),
                waveformPeaks = peaks
            )
        }

    suspend fun cloneProjectMedia(sourceProjectId: String, targetProjectId: String) =
        withContext(Dispatchers.IO) {
            val srcRoot = File(context.filesDir, "projects/$sourceProjectId")
            val dstRoot = File(context.filesDir, "projects/$targetProjectId")
            if (srcRoot.exists()) {
                srcRoot.copyRecursively(dstRoot, overwrite = true)
            }
        }

    suspend fun deleteProjectFiles(projectId: String) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "projects/$projectId")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun copyUriToFile(uri: Uri, destFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: throw IllegalStateException("No se pudo leer el archivo multimedia seleccionado")
    }

    private fun createImageThumbnail(src: File, dst: File) {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return
        val scaled = scaleBitmapForStrip(bmp, 220)
        if (scaled !== bmp) bmp.recycle()
        FileOutputStream(dst).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        scaled.recycle()
    }

    private fun scaleBitmapForStrip(src: Bitmap, targetHeight: Int): Bitmap {
        if (src.height <= targetHeight) return src
        val ratio = targetHeight.toFloat() / src.height.toFloat()
        val targetW = (src.width * ratio).toInt().coerceAtLeast(32)
        return Bitmap.createScaledBitmap(src, targetW, targetHeight, true)
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }

    private fun resolveExtension(name: String, mimeType: String, fallback: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
        if (fromName.length in 2..5 && fromName.all { it.isLetterOrDigit() }) return fromName
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: fallback
    }

    private fun extractWaveformPeaks(path: String, targetBins: Int): List<Float> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = f
                    break
                }
            }
            if (audioTrack < 0 || format == null) return emptyList()
            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else 10_000_000L

            val bins = FloatArray(targetBins)
            val counts = IntArray(targetBins)
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var stepSamples = 0

            while (!sawOutputEos && stepSamples < 450) {
                stepSamples++
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        val sampleSize = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                            extractor.advance()
                            // Fast-seek across long clips for quick UI waveform rendering
                            val jumpUs = durationUs / (targetBins * 2L).coerceAtLeast(1L)
                            if (jumpUs > 40_000L) {
                                extractor.seekTo(pts + jumpUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 5_000)
                if (outIdx >= 0) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.order(ByteOrder.LITTLE_ENDIAN)
                        val shortBuf = outBuf.asShortBuffer()
                        var sumSq = 0.0
                        var count = 0
                        while (shortBuf.hasRemaining()) {
                            val s = shortBuf.get().toDouble() / 32768.0
                            sumSq += s * s
                            count++
                        }
                        val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
                        val bin = ((info.presentationTimeUs.toDouble() / durationUs.toDouble()) * targetBins)
                            .toInt().coerceIn(0, targetBins - 1)
                        bins[bin] = max(bins[bin], abs(rms))
                        counts[bin]++
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                }
            }

            val peakMax = bins.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
            bins.map { (it / peakMax).coerceIn(0.08f, 1f) }
        } catch (_: Exception) {
            List(targetBins) { 0.25f }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
