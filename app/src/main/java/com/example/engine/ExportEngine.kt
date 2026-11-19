package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.domain.model.ProjectTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportCodec(val label: String, val mimeType: String, val subtitle: String) {
    H264("H.264 (AVC)", MimeTypes.VIDEO_H264, "Máxima compatibilidad en redes y dispositivos"),
    H265("H.265 (HEVC)", MimeTypes.VIDEO_H265, "Compresión superior sin pérdida visual")
}

enum class ExportResolution(val label: String, val shortEdgePx: Int) {
    HD_720("720p HD", 720),
    FHD_1080("1080p Full HD", 1080),
    QHD_1440("1440p 2K", 1440),
    UHD_4K("2160p 4K", 2160)
}

enum class ExportQuality(val label: String, val multiplier: Float, val description: String) {
    VISUALLY_LOSSLESS("Sin pérdida visual", 1.0f, "Bitrate maestro para máxima nitidez"),
    ULTRA("Ultra", 0.72f, "Detalle cinematográfico para 4K y 60fps"),
    HIGH("Alta", 0.48f, "Equilibrio ideal para Reels, Shorts y TikTok"),
    CUSTOM("Personalizada", 0.6f, "Control manual exacto de Mbps")
}

data class ExportConfig(
    val codec: ExportCodec = ExportCodec.H265,
    val resolution: ExportResolution = ExportResolution.FHD_1080,
    val fps: Int = 30,
    val quality: ExportQuality = ExportQuality.VISUALLY_LOSSLESS,
    val customBitrateMbps: Int = 45
) {
    fun resolveTargetBitrateBps(): Int {
        if (quality == ExportQuality.CUSTOM) {
            return customBitrateMbps.coerceIn(5, 120) * 1_000_000
        }
        val base1080Mbps = if (codec == ExportCodec.H265) 34f else 48f
        val resScale = when (resolution) {
            ExportResolution.HD_720 -> 0.55f
            ExportResolution.FHD_1080 -> 1.0f
            ExportResolution.QHD_1440 -> 1.55f
            ExportResolution.UHD_4K -> 2.15f
        }
        val fpsScale = if (fps >= 50) 1.38f else 1.0f
        val mbps = (base1080Mbps * resScale * fpsScale * quality.multiplier).coerceIn(8f, 110f)
        return (mbps * 1_000_000f).toInt()
    }

    fun estimatedSizeMb(durationMs: Long): Double {
        val seconds = (durationMs / 1000.0).coerceAtLeast(1.0)
        val totalBits = (resolveTargetBitrateBps() + 256_000L) * seconds
        return totalBits / 8.0 / (1024.0 * 1024.0)
    }
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val progress: Float, val statusMessage: String) : ExportUiState
    data class Success(val outputUri: Uri, val fileSizeBytes: Long, val codecLabel: String) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

data class DeviceCodecCapabilities(
    val supportsH264: Boolean,
    val supportsH265: Boolean,
    val maxWidth: Int,
    val maxHeight: Int
)

@OptIn(UnstableApi::class)
class ExportEngine(private val context: Context) {

    private val _state = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private var activeTransformer: Transformer? = null
    private var progressJob: Job? = null

    fun queryCodecCapabilities(): DeviceCodecCapabilities {
        val codecs = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }
            .getOrDefault(emptyArray())
        var hasAvc = false
        var hasHevc = false
        var maxW = 1920
        var maxH = 1080

        for (info in codecs) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals(MimeTypes.VIDEO_H264, ignoreCase = true)) {
                    hasAvc = true
                    runCatching {
                        val caps = info.getCapabilitiesForType(type).videoCapabilities
                        if (caps != null) {
                            maxW = maxOf(maxW, caps.supportedWidths.upper)
                            maxH = maxOf(maxH, caps.supportedHeights.upper)
                        }
                    }
                } else if (type.equals(MimeTypes.VIDEO_H265, ignoreCase = true)) {
                    hasHevc = true
                    runCatching {
                        val caps = info.getCapabilitiesForType(type).videoCapabilities
                        if (caps != null) {
                            maxW = maxOf(maxW, caps.supportedWidths.upper)
                            maxH = maxOf(maxH, caps.supportedHeights.upper)
                        }
                    }
                }
            }
        }
        return DeviceCodecCapabilities(
            supportsH264 = hasAvc || !hasHevc,
            supportsH265 = hasHevc,
            maxWidth = maxW,
            maxHeight = maxH
        )
    }

    fun startExport(
        scope: CoroutineScope,
        projectTitle: String,
        timeline: ProjectTimeline,
        config: ExportConfig
    ) {
        if (timeline.mainClips.isEmpty()) {
            _state.value = ExportUiState.Error("Agrega al menos un clip antes de exportar.")
            return
        }

        scope.launch(Dispatchers.Main) {
            cancelExport()

            val tempOutFile = File(context.cacheDir, "kinetik_export_${System.currentTimeMillis()}.mp4")
            if (tempOutFile.exists()) tempOutFile.delete()

            val targetBitrate = config.resolveTargetBitrateBps()
            val codecCaps = queryCodecCapabilities()
            val chosenCodec = if (config.codec == ExportCodec.H265 && !codecCaps.supportsH265) {
                ExportCodec.H264
            } else {
                config.codec
            }

            try {
                val composition: Composition = CompositionBuilder.buildComposition(
                    context = context,
                    timeline = timeline.copy(fps = config.fps),
                    targetShortEdgePx = config.resolution.shortEdgePx,
                    includeTextOverlays = true
                )
                if (composition.sequences.isEmpty()) {
                    _state.value = ExportUiState.Error("No se encontraron archivos multimedia válidos para exportar.")
                    return@launch
                }

                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setEnableFallback(true)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(targetBitrate)
                            .build()
                    )
                    .build()

                _state.value = ExportUiState.Exporting(
                    progress = 0.02f,
                    statusMessage = "Inicializando codificador ${chosenCodec.label}..."
                )

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(chosenCodec.mimeType)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            progressJob?.cancel()
                            scope.launch(Dispatchers.Main) {
                                runCatching {
                                    val savedUri = publishToGallery(tempOutFile, projectTitle)
                                    val bytes = tempOutFile.length().coerceAtLeast(exportResult.fileSizeBytes)
                                    tempOutFile.delete()
                                    _state.value = ExportUiState.Success(
                                        outputUri = savedUri,
                                        fileSizeBytes = bytes,
                                        codecLabel = chosenCodec.label
                                    )
                                }.onFailure { err ->
                                    tempOutFile.delete()
                                    _state.value = ExportUiState.Error(
                                        err.localizedMessage ?: "Error al guardar el video exportado."
                                    )
                                }
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            progressJob?.cancel()
                            tempOutFile.delete()
                            _state.value = ExportUiState.Error(
                                exportException.localizedMessage
                                    ?: "Fallo en la codificación ${chosenCodec.label}."
                            )
                        }
                    })
                    .build()

                activeTransformer = transformer
                transformer.start(composition, tempOutFile.absolutePath)

                val holder = ProgressHolder()
                progressJob = scope.launch(Dispatchers.Main) {
                    while (isActive) {
                        delay(150L)
                        val stateCode = runCatching { transformer.getProgress(holder) }.getOrDefault(
                            Transformer.PROGRESS_STATE_UNAVAILABLE
                        )
                        if (stateCode == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val p = (holder.progress / 100f).coerceIn(0f, 0.99f)
                            _state.value = ExportUiState.Exporting(
                                progress = p,
                                statusMessage = "Renderizando ${chosenCodec.label} • ${(p * 100).toInt()}%"
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                progressJob?.cancel()
                tempOutFile.delete()
                activeTransformer = null
                _state.value = ExportUiState.Error(
                    t.localizedMessage ?: "No se pudo iniciar la exportación de video."
                )
            }
        }
    }

    fun cancelExport() {
        progressJob?.cancel()
        progressJob = null
        runCatching { activeTransformer?.cancel() }
        activeTransformer = null
        if (_state.value is ExportUiState.Exporting) {
            _state.value = ExportUiState.Idle
        }
    }

    fun resetState() {
        _state.value = ExportUiState.Idle
    }

    private suspend fun publishToGallery(sourceFile: File, projectTitle: String): Uri =
        withContext(Dispatchers.IO) {
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                throw IllegalStateException("El archivo exportado está vacío o no existe.")
            }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeTitle = projectTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Video" }.take(24)
            val fileName = "Kinetik_${safeTitle}_$stamp.mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Kinetik")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("No se pudo crear el destino en MediaStore")
                try {
                    val outStream = resolver.openOutputStream(uri)
                        ?: throw IllegalStateException("No se pudo abrir flujo de escritura en la galería")
                    outStream.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out, bufferSize = 64 * 1024)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri
                } catch (e: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw e
                }
            } else {
                val exportsDir = File(context.filesDir, "exports")
                if (!exportsDir.exists()) exportsDir.mkdirs()
                val dest = File(exportsDir, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    dest
                )
            }
        }
}
