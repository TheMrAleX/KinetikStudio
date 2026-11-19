package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AspectRatio
import com.example.domain.model.AudioClip
import com.example.domain.model.ColorFilterPreset
import com.example.domain.model.ColorGradeSpec
import com.example.domain.model.KeyframeProperty
import com.example.domain.model.MaskSpec
import com.example.domain.model.MaskType
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TextAnimationType
import com.example.domain.model.TextClip
import com.example.domain.model.TextFontStyle
import com.example.domain.model.TransitionSpec
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.VisualEffectType
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.FxViolet
import com.example.ui.theme.HairlineOutline
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.EditorToolTab
import com.example.ui.viewmodel.EditorUiState
import java.util.Locale

@Composable
fun EditorToolInspectorPanel(
    uiState: EditorUiState,
    onSplitClip: () -> Unit,
    onDuplicateClip: () -> Unit,
    onDeleteClip: () -> Unit,
    onMoveClip: (Int) -> Unit,
    onResetTransform: () -> Unit,
    onSetSpeed: (Float, SpeedRampPreset) -> Unit,
    onSetOpacity: (Float) -> Unit,
    onToggleKeyframe: (KeyframeProperty) -> Unit,
    onHasKeyframeAtPlayhead: (KeyframeProperty) -> Boolean,
    onOpenCurveEditor: (KeyframeProperty) -> Unit,
    onJumpKeyframe: (Boolean) -> Unit,
    onSetMaskType: (MaskType) -> Unit,
    onUpdateMask: (MaskSpec) -> Unit,
    onKeyframeMaskAll: () -> Unit,
    onSetVisualEffect: (VisualEffectType, Float) -> Unit,
    onUpdateColorGrade: (ColorGradeSpec) -> Unit,
    onSetTransition: (TransitionSpec) -> Unit,
    onAddText: () -> Unit,
    onUpdateText: (TextClip) -> Unit,
    onDeleteText: (String) -> Unit,
    onSetClipAudio: (Float, Boolean, Long, Long) -> Unit,
    onAddAudioUri: (Uri) -> Unit,
    onUpdateAudioClip: (AudioClip) -> Unit,
    onDeleteAudioClip: (String) -> Unit,
    onSetAspectRatio: (AspectRatio) -> Unit,
    onSetFps: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedClip = uiState.selectedMainClip
    val eval = uiState.evaluatedSelectedClipState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        when (uiState.activeToolTab) {
            EditorToolTab.EDIT -> {
                if (selectedClip != null) {
                    EditToolContent(
                        clip = selectedClip,
                        opacity = eval?.transform?.opacity ?: selectedClip.transform.opacity,
                        onSplit = onSplitClip,
                        onDuplicate = onDuplicateClip,
                        onDelete = onDeleteClip,
                        onMove = onMoveClip,
                        onResetTransform = onResetTransform,
                        onSetSpeed = onSetSpeed,
                        onSetOpacity = onSetOpacity
                    )
                }
            }

            EditorToolTab.KEYFRAME -> {
                if (selectedClip != null) {
                    KeyframeToolContent(
                        clip = selectedClip,
                        hasKeyframeAtPlayhead = onHasKeyframeAtPlayhead,
                        onToggleKeyframe = onToggleKeyframe,
                        onOpenCurveEditor = onOpenCurveEditor,
                        onJumpKeyframe = onJumpKeyframe
                    )
                }
            }

            EditorToolTab.MASK -> {
                if (selectedClip != null) {
                    MaskToolContent(
                        mask = eval?.mask ?: selectedClip.mask,
                        onSetMaskType = onSetMaskType,
                        onUpdateMask = onUpdateMask,
                        onKeyframeMaskAll = onKeyframeMaskAll
                    )
                }
            }

            EditorToolTab.EFFECTS -> {
                if (selectedClip != null) {
                    EffectsToolContent(
                        activeEffect = selectedClip.visualEffect,
                        intensity = eval?.effectIntensity ?: selectedClip.effectIntensity,
                        onSelectEffect = onSetVisualEffect
                    )
                }
            }

            EditorToolTab.COLOR -> {
                if (selectedClip != null) {
                    ColorToolContent(
                        grade = selectedClip.colorGrade,
                        onUpdate = onUpdateColorGrade
                    )
                }
            }

            EditorToolTab.TRANSITIONS -> {
                if (selectedClip != null) {
                    TransitionsToolContent(
                        spec = selectedClip.transitionIn,
                        onUpdate = onSetTransition
                    )
                }
            }

            EditorToolTab.TEXT -> {
                TextToolContent(
                    textClips = uiState.timeline.textClips,
                    selectedText = uiState.selectedTextClip,
                    onAddText = onAddText,
                    onUpdateText = onUpdateText,
                    onDeleteText = onDeleteText
                )
            }

            EditorToolTab.AUDIO -> {
                AudioToolContent(
                    selectedClip = selectedClip,
                    audioClips = uiState.timeline.audioClips,
                    onSetClipAudio = onSetClipAudio,
                    onAddAudioUri = onAddAudioUri,
                    onUpdateAudioClip = onUpdateAudioClip,
                    onDeleteAudioClip = onDeleteAudioClip
                )
            }

            EditorToolTab.FORMAT -> {
                FormatToolContent(
                    currentAspect = uiState.timeline.aspectRatio,
                    currentFps = uiState.timeline.fps,
                    onSelectAspect = onSetAspectRatio,
                    onSelectFps = onSetFps
                )
            }
        }
    }
}

@Composable
private fun EditToolContent(
    clip: VideoClip,
    opacity: Float,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onResetTransform: () -> Unit,
    onSetSpeed: (Float, SpeedRampPreset) -> Unit,
    onSetOpacity: (Float) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ActionPillButton("Dividir", Icons.Default.ContentCut, ElectricCyan, "split_clip_button", onSplit)
        }
        item {
            ActionPillButton("Duplicar", Icons.Default.ContentCopy, TextPrimary, "duplicate_clip_button", onDuplicate)
        }
        item {
            ActionPillButton("Mover Izq", Icons.AutoMirrored.Filled.ArrowBack, TextPrimary, "move_left_button") {
                onMove(-1)
            }
        }
        item {
            ActionPillButton("Mover Der", Icons.AutoMirrored.Filled.ArrowForward, TextPrimary, "move_right_button") {
                onMove(1)
            }
        }
        item {
            ActionPillButton("Restablecer", Icons.Default.RestartAlt, TextSecondary, "reset_transform_button", onResetTransform)
        }
        item {
            ActionPillButton("Eliminar", Icons.Default.DeleteOutline, CoralAmber, "delete_clip_button", onDelete)
        }
    }

    Spacer(Modifier.height(12.dp))

    StudioSliderRow(
        label = "Velocidad (${String.format(Locale.US, "%.2fx", clip.speed)})",
        value = clip.speed,
        range = 0.25f..4.0f,
        onValueChange = { onSetSpeed(it, clip.speedPreset) }
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(SpeedRampPreset.entries) { preset ->
            val selected = clip.speedPreset == preset
            FilterChip(
                selected = selected,
                onClick = { onSetSpeed(clip.speed, preset) },
                label = { Text(preset.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.16f),
                    selectedLabelColor = ElectricCyan,
                    containerColor = ElevatedSurface,
                    labelColor = TextSecondary
                )
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    StudioSliderRow(
        label = "Opacidad (${(opacity * 100).toInt()}%)",
        value = opacity,
        range = 0f..1f,
        onValueChange = onSetOpacity
    )
}

@Composable
private fun KeyframeToolContent(
    clip: VideoClip,
    hasKeyframeAtPlayhead: (KeyframeProperty) -> Boolean,
    onToggleKeyframe: (KeyframeProperty) -> Unit,
    onOpenCurveEditor: (KeyframeProperty) -> Unit,
    onJumpKeyframe: (Boolean) -> Unit
) {
    var selectedProp by remember { mutableStateOf(KeyframeProperty.SCALE) }
    val propList = remember {
        listOf(
            KeyframeProperty.POS_X,
            KeyframeProperty.POS_Y,
            KeyframeProperty.SCALE,
            KeyframeProperty.ROTATION,
            KeyframeProperty.OPACITY,
            KeyframeProperty.EFFECT_INTENSITY,
            KeyframeProperty.VOLUME
        )
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(propList) { prop ->
            val count = clip.keyframes[prop]?.size ?: 0
            val selected = selectedProp == prop
            FilterChip(
                selected = selected,
                onClick = { selectedProp = prop },
                label = {
                    Text(if (count > 0) "${prop.label} ($count)" else prop.label)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                    selectedLabelColor = ElectricCyan,
                    containerColor = ElevatedSurface,
                    labelColor = TextSecondary
                )
            )
        }
    }

    Spacer(Modifier.height(10.dp))

    val isKeyAtCursor = hasKeyframeAtPlayhead(selectedProp)
    val nodeCount = clip.keyframes[selectedProp]?.size ?: 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onJumpKeyframe(false) },
            border = BorderStroke(1.dp, HairlineOutline),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Anterior keyframe", tint = TextPrimary)
        }

        Button(
            onClick = { onToggleKeyframe(selectedProp) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isKeyAtCursor) CoralAmber else ElectricCyan,
                contentColor = Color(0xFF002026)
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("toggle_keyframe_button")
        ) {
            Icon(Icons.Default.Diamond, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isKeyAtCursor) "Quitar ◆" else "Añadir ◆")
        }

        Button(
            onClick = { onOpenCurveEditor(selectedProp) },
            enabled = nodeCount >= 2,
            colors = ButtonDefaults.buttonColors(
                containerColor = FxViolet,
                contentColor = Color.White,
                disabledContainerColor = ElevatedSurface,
                disabledContentColor = TextSecondary
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("open_curve_editor_button")
        ) {
            Icon(Icons.Default.ShowChart, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Curva")
        }

        OutlinedButton(
            onClick = { onJumpKeyframe(true) },
            border = BorderStroke(1.dp, HairlineOutline),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Siguiente keyframe", tint = TextPrimary)
        }
    }
}

@Composable
private fun MaskToolContent(
    mask: MaskSpec,
    onSetMaskType: (MaskType) -> Unit,
    onUpdateMask: (MaskSpec) -> Unit,
    onKeyframeMaskAll: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MaskType.entries) { type ->
            val selected = mask.type == type
            FilterChip(
                selected = selected,
                onClick = { onSetMaskType(type) },
                label = { Text(type.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoralAmber.copy(alpha = 0.2f),
                    selectedLabelColor = CoralAmber,
                    containerColor = ElevatedSurface,
                    labelColor = TextSecondary
                )
            )
        }
    }

    if (mask.type != MaskType.NONE) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Invertir máscara", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = mask.inverted,
                    onCheckedChange = { onUpdateMask(mask.copy(inverted = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = CoralAmber)
                )
            }

            OutlinedButton(
                onClick = onKeyframeMaskAll,
                border = BorderStroke(1.dp, ElectricCyan)
            ) {
                Icon(Icons.Default.Diamond, null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Animar ◆", color = ElectricCyan)
            }
        }

        StudioSliderRow("Difuminado", mask.feather, 0f..0.4f) {
            onUpdateMask(mask.copy(feather = it))
        }
        StudioSliderRow("Tamaño", mask.width, 0.08f..1.3f) {
            onUpdateMask(mask.copy(width = it, height = it))
        }
        StudioSliderRow("Rotación (${mask.rotationDeg.toInt()}°)", mask.rotationDeg, -180f..180f) {
            onUpdateMask(mask.copy(rotationDeg = it))
        }
    }
}

@Composable
private fun EffectsToolContent(
    activeEffect: VisualEffectType,
    intensity: Float,
    onSelectEffect: (VisualEffectType, Float) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(VisualEffectType.entries) { fx ->
            val selected = activeEffect == fx
            FilterChip(
                selected = selected,
                onClick = { onSelectEffect(fx, intensity) },
                label = { Text(fx.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FxViolet.copy(alpha = 0.24f),
                    selectedLabelColor = FxViolet,
                    containerColor = ElevatedSurface,
                    labelColor = TextSecondary
                )
            )
        }
    }
    if (activeEffect != VisualEffectType.NONE) {
        Spacer(Modifier.height(10.dp))
        StudioSliderRow("Intensidad FX (${(intensity * 100).toInt()}%)", intensity, 0f..1f) {
            onSelectEffect(activeEffect, it)
        }
    }
}

@Composable
private fun ColorToolContent(
    grade: ColorGradeSpec,
    onUpdate: (ColorGradeSpec) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ColorFilterPreset.entries) { preset ->
            val selected = grade.preset == preset
            FilterChip(
                selected = selected,
                onClick = { onUpdate(grade.copy(preset = preset)) },
                label = { Text(preset.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                    selectedLabelColor = ElectricCyan,
                    containerColor = ElevatedSurface,
                    labelColor = TextSecondary
                )
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    StudioSliderRow("Exposición", grade.exposure, -1f..1f) { onUpdate(grade.copy(exposure = it)) }
    StudioSliderRow("Contraste", grade.contrast, -1f..1f) { onUpdate(grade.copy(contrast = it)) }
    StudioSliderRow("Saturación", grade.saturation, -1f..1f) { onUpdate(grade.copy(saturation = it)) }
    StudioSliderRow("Temperatura", grade.temperature, -1f..1f) { onUpdate(grade.copy(temperature = it)) }
    StudioSliderRow("Luces", grade.highlights, -1f..1f) { onUpdate(grade.copy(highlights = it)) }
    StudioSliderRow("Sombras", grade.shadows, -1f..1f) { onUpdate(grade.copy(shadows = it)) }
}

@Composable
private fun TransitionsToolContent(
    spec: TransitionSpec,
    onUpdate: (TransitionSpec) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TransitionType.entries) { type ->
            val selected = spec.type == type
            FilterChip(
                selected = selected,
                onClick = { onUpdate(spec.copy(type = type)) },
                label = { Text(type.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FxViolet.copy(alpha = 0.24f),
                    selectedLabelColor = FxViolet,
                    containerColor = ElevatedSurface,
                    labelColor = TextSecondary
                )
            )
        }
    }
    if (spec.type != TransitionType.NONE) {
        Spacer(Modifier.height(10.dp))
        StudioSliderRow(
            label = "Duración (${String.format(Locale.US, "%.2fs", spec.durationMs / 1000f)})",
            value = spec.durationMs.toFloat(),
            range = 200f..2000f,
            onValueChange = { onUpdate(spec.copy(durationMs = it.toLong())) }
        )
    }
}

@Composable
private fun TextToolContent(
    textClips: List<TextClip>,
    selectedText: TextClip?,
    onAddText: () -> Unit,
    onUpdateText: (TextClip) -> Unit,
    onDeleteText: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onAddText,
            colors = ButtonDefaults.buttonColors(
                containerColor = TextGold,
                contentColor = Color(0xFF281D00)
            ),
            modifier = Modifier.testTag("add_text_button")
        ) {
            Icon(Icons.Default.Title, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Añadir Texto")
        }

        if (selectedText != null) {
            IconButton(onClick = { onDeleteText(selectedText.id) }) {
                Icon(Icons.Default.DeleteOutline, "Eliminar texto", tint = CoralAmber)
            }
        }
    }

    if (selectedText != null) {
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = selectedText.text,
            onValueChange = { onUpdateText(selectedText.copy(text = it)) },
            label = { Text("Texto en pantalla") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TextGold,
                unfocusedBorderColor = HairlineOutline,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TextFontStyle.entries) { font ->
                FilterChip(
                    selected = selectedText.fontStyle == font,
                    onClick = { onUpdateText(selectedText.copy(fontStyle = font)) },
                    label = { Text(font.label) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TextAnimationType.entries) { anim ->
                FilterChip(
                    selected = selectedText.animationIn == anim,
                    onClick = { onUpdateText(selectedText.copy(animationIn = anim)) },
                    label = { Text(anim.label) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val palette = listOf(0xFFFFFFFF, 0xFF00E5FF, 0xFFFF6B4A, 0xFFFFC857, 0xFFA87BFF, 0xFF2CE59B)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            palette.forEach { argb ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(argb.toInt()))
                        .clickable { onUpdateText(selectedText.copy(colorArgb = argb)) }
                )
            }
        }
        StudioSliderRow("Tamaño (${selectedText.fontSizeSp.toInt()}sp)", selectedText.fontSizeSp, 14f..58f) {
            onUpdateText(selectedText.copy(fontSizeSp = it))
        }
        StudioSliderRow(
            "Duración (${String.format(Locale.US, "%.1fs", selectedText.durationMs / 1000f)})",
            selectedText.durationMs.toFloat(),
            500f..12000f
        ) {
            onUpdateText(selectedText.copy(durationMs = it.toLong()))
        }
    } else if (textClips.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Agrega títulos animados que se exportan incrustados en el video final.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun AudioToolContent(
    selectedClip: VideoClip?,
    audioClips: List<AudioClip>,
    onSetClipAudio: (Float, Boolean, Long, Long) -> Unit,
    onAddAudioUri: (Uri) -> Unit,
    onUpdateAudioClip: (AudioClip) -> Unit,
    onDeleteAudioClip: (String) -> Unit
) {
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onAddAudioUri(uri)
    }

    Button(
        onClick = { audioPicker.launch(arrayOf("audio/*")) },
        colors = ButtonDefaults.buttonColors(
            containerColor = AudioEmerald,
            contentColor = Color(0xFF062617)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("import_audio_button")
    ) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.width(6.dp))
        Text("Importar Música o Efecto de Sonido")
    }

    if (selectedClip != null) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Audio del clip seleccionado", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            IconButton(
                onClick = {
                    onSetClipAudio(selectedClip.volume, !selectedClip.isMuted, selectedClip.fadeInMs, selectedClip.fadeOutMs)
                }
            ) {
                Icon(
                    imageVector = if (selectedClip.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Silenciar",
                    tint = if (selectedClip.isMuted) CoralAmber else AudioEmerald
                )
            }
        }
        StudioSliderRow(
            label = "Volumen Clip (${(selectedClip.volume * 100).toInt()}%)",
            value = selectedClip.volume,
            range = 0f..2f,
            onValueChange = {
                onSetClipAudio(it, selectedClip.isMuted, selectedClip.fadeInMs, selectedClip.fadeOutMs)
            }
        )
    }

    audioClips.forEach { ac ->
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ElevatedSurface,
            border = BorderStroke(1.dp, HairlineOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.MusicNote, null, tint = AudioEmerald, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(ac.title, style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 1)
                    }
                    IconButton(onClick = { onDeleteAudioClip(ac.id) }) {
                        Icon(Icons.Default.DeleteOutline, "Eliminar audio", tint = CoralAmber)
                    }
                }
                StudioSliderRow("Volumen (${(ac.volume * 100).toInt()}%)", ac.volume, 0f..2f) {
                    onUpdateAudioClip(ac.copy(volume = it))
                }
            }
        }
    }
}

@Composable
private fun FormatToolContent(
    currentAspect: AspectRatio,
    currentFps: Int,
    onSelectAspect: (AspectRatio) -> Unit,
    onSelectFps: (Int) -> Unit
) {
    Text("Relación de Aspecto", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AspectRatio.entries) { ratio ->
            val selected = currentAspect == ratio
            FilterChip(
                selected = selected,
                onClick = { onSelectAspect(ratio) },
                label = { Text(ratio.label) },
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
    Text("Velocidad de Cuadros (FPS)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(24, 30, 60).forEach { fps ->
            val selected = currentFps == fps
            FilterChip(
                selected = selected,
                onClick = { onSelectFps(fps) },
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
}

@Composable
private fun ActionPillButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = ElevatedSurface,
        border = BorderStroke(1.dp, HairlineOutline),
        modifier = Modifier.testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
        }
    }
}

@Composable
private fun StudioSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MonoTimecodeStyle,
            color = TextSecondary,
            modifier = Modifier.width(136.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = HairlineOutline
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
