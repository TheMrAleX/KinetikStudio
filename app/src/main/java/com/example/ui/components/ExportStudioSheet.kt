package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.DeviceCodecCapabilities
import com.example.engine.ExportCodec
import com.example.engine.ExportConfig
import com.example.engine.ExportQuality
import com.example.engine.ExportResolution
import com.example.engine.ExportUiState
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineOutline
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.formatFileSize
import java.util.Locale

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportStudioSheet(
    config: ExportConfig,
    exportState: ExportUiState,
    capabilities: DeviceCodecCapabilities,
    projectDurationMs: Long,
    onConfigChange: (ExportConfig) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (exportState !is ExportUiState.Exporting) onDismiss()
        },
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HighQuality, null, tint = ElectricCyan)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Estudio de Exportación",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ElevatedSurface,
                    border = BorderStroke(1.dp, HairlineOutline)
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "~%.1f MB • %d Mbps",
                            config.estimatedSizeMb(projectDurationMs),
                            config.resolveTargetBitrateBps() / 1_000_000
                        ),
                        style = MonoTimecodeStyle,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (exportState) {
                is ExportUiState.Exporting -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianBg,
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = exportState.statusMessage,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { exportState.progress },
                                color = ElectricCyan,
                                trackColor = ElevatedSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onCancelExport,
                                border = BorderStroke(1.dp, CoralAmber)
                            ) {
                                Icon(Icons.Default.Close, null, tint = CoralAmber)
                                Spacer(Modifier.width(6.dp))
                                Text("Cancelar", color = CoralAmber)
                            }
                        }
                    }
                }

                is ExportUiState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianBg,
                        border = BorderStroke(1.dp, AudioEmerald.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AudioEmerald,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Exportación completada",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${exportState.codecLabel} • ${formatFileSize(exportState.fileSizeBytes)}",
                                style = MonoTimecodeStyle,
                                color = AudioEmerald
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { openUri(context, exportState.outputUri, Intent.ACTION_VIEW) },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, ElectricCyan)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = ElectricCyan)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reproducir", color = ElectricCyan)
                                }
                                Button(
                                    onClick = { shareUri(context, exportState.outputUri) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = Color(0xFF002026)
                                    )
                                ) {
                                    Icon(Icons.Default.Share, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Compartir")
                                }
                            }
                        }
                    }
                }

                is ExportUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CoralAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, CoralAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoralAmber,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ExportOptionsForm(
                        config = config,
                        capabilities = capabilities,
                        onConfigChange = onConfigChange,
                        onStartExport = onStartExport
                    )
                }

                ExportUiState.Idle -> {
                    ExportOptionsForm(
                        config = config,
                        capabilities = capabilities,
                        onConfigChange = onConfigChange,
                        onStartExport = onStartExport
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportOptionsForm(
    config: ExportConfig,
    capabilities: DeviceCodecCapabilities,
    onConfigChange: (ExportConfig) -> Unit,
    onStartExport: () -> Unit
) {
    Text("Códec de Video", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExportCodec.entries.forEach { codec ->
            val supported = if (codec == ExportCodec.H265) capabilities.supportsH265 else capabilities.supportsH264
            val selected = config.codec == codec
            Surface(
                onClick = {
                    if (supported) onConfigChange(config.copy(codec = codec))
                },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) ElectricCyan.copy(alpha = 0.14f) else ElevatedSurface,
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) ElectricCyan else HairlineOutline
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("codec_option_${codec.name}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = codec.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (supported) TextPrimary else TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (supported) codec.subtitle else "No soportado por hardware (usará H.264)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("Calidad Visual", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ExportQuality.entries.forEach { q ->
            val selected = config.quality == q
            FilterChip(
                selected = selected,
                onClick = { onConfigChange(config.copy(quality = q)) },
                label = { Text(q.label, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                    selectedLabelColor = ElectricCyan,
                    containerColor = ElevatedSurface,
                    labelColor = TextSecondary
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
    Text(
        text = config.quality.description,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary
    )

    if (config.quality == ExportQuality.CUSTOM) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Bitrate Manual", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text("${config.customBitrateMbps} Mbps", style = MonoTimecodeStyle, color = ElectricCyan)
        }
        Slider(
            value = config.customBitrateMbps.toFloat(),
            onValueChange = { onConfigChange(config.copy(customBitrateMbps = it.toInt())) },
            valueRange = 5f..100f,
            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
        )
    }

    Spacer(Modifier.height(14.dp))
    Text("Resolución", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExportResolution.entries.forEach { res ->
            val selected = config.resolution == res
            FilterChip(
                selected = selected,
                onClick = { onConfigChange(config.copy(resolution = res)) },
                label = { Text(res.label.substringBefore(" ")) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                    selectedLabelColor = ElectricCyan,
                    containerColor = ElevatedSurface,
                    labelColor = TextSecondary
                )
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Fotogramas por segundo (FPS)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(24, 30, 60).forEach { fps ->
            val selected = config.fps == fps
            FilterChip(
                selected = selected,
                onClick = { onConfigChange(config.copy(fps = fps)) },
                label = { Text("$fps fps") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                    selectedLabelColor = ElectricCyan,
                    containerColor = ElevatedSurface,
                    labelColor = TextSecondary
                )
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onStartExport,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("start_export_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricCyan,
            contentColor = Color(0xFF002026)
        )
    ) {
        Text(
            text = "Exportar en ${config.codec.label}",
            fontWeight = FontWeight.Bold
        )
    }
}

private fun openUri(context: android.content.Context, uri: Uri, action: String) {
    val intent = Intent(action).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareUri(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Compartir video exportado"))
    }
}
