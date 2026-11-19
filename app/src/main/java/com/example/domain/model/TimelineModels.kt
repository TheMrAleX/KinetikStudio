package com.example.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AspectRatio(
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int
) {
    RATIO_9_16("9:16", 9, 16),
    RATIO_16_9("16:9", 16, 9),
    RATIO_1_1("1:1", 1, 1),
    RATIO_4_5("4:5", 4, 5),
    RATIO_4_3("4:3", 4, 3);

    val ratioFloat: Float get() = widthRatio.toFloat() / heightRatio.toFloat()

    fun resolveDimensions(shortEdgePx: Int): Pair<Int, Int> {
        val evenShort = (shortEdgePx / 2) * 2
        return if (widthRatio <= heightRatio) {
            val w = evenShort
            val h = ((w.toFloat() * heightRatio / widthRatio).toInt() / 2) * 2
            w to h
        } else {
            val h = evenShort
            val w = ((h.toFloat() * widthRatio / heightRatio).toInt() / 2) * 2
            w to h
        }
    }
}

@Serializable
enum class EasingType(
    val label: String,
    val defaultX1: Float,
    val defaultY1: Float,
    val defaultX2: Float,
    val defaultY2: Float
) {
    LINEAR("Lineal", 0f, 0f, 1f, 1f),
    EASE_IN_OUT("Suave", 0.42f, 0f, 0.58f, 1f),
    EASE_IN("Acelerar", 0.42f, 0f, 1f, 1f),
    EASE_OUT("Desacelerar", 0f, 0f, 0.58f, 1f),
    OVERSHOOT("Rebote", 0.34f, 1.56f, 0.64f, 1f),
    ELASTIC("Elástico", 0.22f, 1.38f, 0.36f, 1f),
    CUSTOM_BEZIER("Curva", 0.25f, 0.1f, 0.25f, 1f)
}

@Serializable
data class EasingCurve(
    val type: EasingType = EasingType.EASE_IN_OUT,
    val x1: Float = type.defaultX1,
    val y1: Float = type.defaultY1,
    val x2: Float = type.defaultX2,
    val y2: Float = type.defaultY2
) {
    companion object {
        fun fromPreset(type: EasingType): EasingCurve =
            EasingCurve(type, type.defaultX1, type.defaultY1, type.defaultX2, type.defaultY2)
    }
}

@Serializable
enum class KeyframeProperty(
    val label: String,
    val defaultValue: Float,
    val minValue: Float,
    val maxValue: Float
) {
    POS_X("Posición X", 0f, -1.5f, 1.5f),
    POS_Y("Posición Y", 0f, -1.5f, 1.5f),
    SCALE("Escala", 1f, 0.1f, 4.0f),
    ROTATION("Rotación", 0f, -360f, 360f),
    OPACITY("Opacidad", 1f, 0f, 1f),
    MASK_X("Máscara X", 0.5f, 0f, 1f),
    MASK_Y("Máscara Y", 0.5f, 0f, 1f),
    MASK_WIDTH("Ancho Máscara", 0.55f, 0.05f, 1.5f),
    MASK_HEIGHT("Alto Máscara", 0.55f, 0.05f, 1.5f),
    MASK_ROTATION("Rotación Máscara", 0f, -180f, 180f),
    MASK_FEATHER("Difuminado", 0.04f, 0f, 0.45f),
    VOLUME("Volumen", 1f, 0f, 2f),
    EFFECT_INTENSITY("Intensidad FX", 0.7f, 0f, 1f)
}

@Serializable
data class KeyframeNode(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetMs: Long,
    val value: Float,
    val easing: EasingCurve = EasingCurve.fromPreset(EasingType.EASE_IN_OUT)
)

@Serializable
enum class MaskType(val label: String) {
    NONE("Ninguna"),
    LINEAR("Lineal"),
    MIRROR("Espejo"),
    CIRCLE("Círculo"),
    RECTANGLE("Rectángulo"),
    HEART("Corazón")
}

@Serializable
data class MaskSpec(
    val type: MaskType = MaskType.NONE,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.55f,
    val height: Float = 0.55f,
    val rotationDeg: Float = 0f,
    val feather: Float = 0.04f,
    val cornerRadius: Float = 0.08f,
    val inverted: Boolean = false
)

@Serializable
enum class TransitionType(val label: String) {
    NONE("Sin transición"),
    CROSS_DISSOLVE("Disolver"),
    SLIDE_LEFT("Deslizar Izq"),
    SLIDE_RIGHT("Deslizar Der"),
    SLIDE_UP("Deslizar Arriba"),
    ZOOM_IN("Zoom"),
    GLITCH_FLASH("Flash Glitch"),
    CIRCLE_WIPE("Círculo")
}

@Serializable
data class TransitionSpec(
    val type: TransitionType = TransitionType.NONE,
    val durationMs: Long = 500L
)

@Serializable
enum class VisualEffectType(val label: String) {
    NONE("Ninguno"),
    VHS_GLITCH("VHS Glitch"),
    CHROMATIC_ABERRATION("Aberración"),
    VIGNETTE("Viñeta"),
    FILM_GRAIN("Grano Cine"),
    RADIAL_BLUR("Desenfoque"),
    NOIR_BW("Noir B&N"),
    HALATION_GLOW("Resplandor")
}

@Serializable
enum class ColorFilterPreset(val label: String) {
    NONE("Original"),
    CINEMATIC("Cinemático"),
    TEAL_ORANGE("Teal & Orange"),
    VINTAGE_90S("Vintage 90s"),
    HIGH_CONTRAST("Alto Contraste"),
    PASTEL_SOFT("Pastel Suave")
}

@Serializable
data class ColorGradeSpec(
    val exposure: Float = 0f,       // -1f .. 1f
    val contrast: Float = 0f,       // -1f .. 1f
    val saturation: Float = 0f,     // -1f .. 1f
    val temperature: Float = 0f,    // -1f .. 1f
    val tint: Float = 0f,           // -1f .. 1f
    val highlights: Float = 0f,     // -1f .. 1f
    val shadows: Float = 0f,        // -1f .. 1f
    val sharpen: Float = 0f,        // 0f .. 1f
    val preset: ColorFilterPreset = ColorFilterPreset.NONE
) {
    fun isNeutral(): Boolean =
        exposure == 0f && contrast == 0f && saturation == 0f &&
            temperature == 0f && tint == 0f && highlights == 0f &&
            shadows == 0f && sharpen == 0f && preset == ColorFilterPreset.NONE
}

@Serializable
enum class SpeedRampPreset(val label: String) {
    CONSTANT("Normal"),
    MONTAGE("Montaje"),
    HERO_TIME("Heroico"),
    BULLET("Bala"),
    JUMP_CUT("Salto"),
    FLASH_IN("Flash Entrada"),
    FLASH_OUT("Flash Salida")
}

@Serializable
enum class TextAnimationType(val label: String) {
    NONE("Sin animación"),
    FADE("Aparecer"),
    POP_SCALE("Pop Escala"),
    SLIDE_UP("Subir"),
    GLOW_PULSE("Pulso Neón")
}

@Serializable
enum class TextFontStyle(val label: String) {
    MODERN("Space Grotesk"),
    CLEAN("Plus Jakarta"),
    MONO("JetBrains Mono")
}

@Serializable
data class ClipTransform(
    val posX: Float = 0f,         // Normalized -1..1
    val posY: Float = 0f,         // Normalized -1..1
    val scale: Float = 1f,        // 0.1..4.0
    val rotationDeg: Float = 0f,  // -360..360
    val opacity: Float = 1f       // 0..1
)

@Serializable
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val mediaPath: String,
    val isImage: Boolean = false,
    val sourceDurationMs: Long,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = sourceDurationMs,
    val speed: Float = 1f,
    val speedPreset: SpeedRampPreset = SpeedRampPreset.CONSTANT,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val isReversed: Boolean = false,
    val transform: ClipTransform = ClipTransform(),
    val mask: MaskSpec = MaskSpec(),
    val colorGrade: ColorGradeSpec = ColorGradeSpec(),
    val visualEffect: VisualEffectType = VisualEffectType.NONE,
    val effectIntensity: Float = 0.7f,
    val transitionIn: TransitionSpec = TransitionSpec(),
    val keyframes: Map<KeyframeProperty, List<KeyframeNode>> = emptyMap(),
    val thumbnailPaths: List<String> = emptyList(),
    val waveformPeaks: List<Float> = emptyList()
) {
    val effectiveDurationMs: Long
        get() {
            val raw = (sourceEndMs - sourceStartMs).coerceAtLeast(100L)
            return (raw / speed.coerceIn(0.1f, 10f)).toLong().coerceAtLeast(100L)
        }
}

@Serializable
data class OverlayClip(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val mediaPath: String,
    val isImage: Boolean = false,
    val timelineStartMs: Long = 0L,
    val sourceDurationMs: Long,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = sourceDurationMs,
    val volume: Float = 0.8f,
    val isMuted: Boolean = true,
    val transform: ClipTransform = ClipTransform(scale = 0.45f, posX = 0.25f, posY = -0.25f),
    val mask: MaskSpec = MaskSpec(),
    val colorGrade: ColorGradeSpec = ColorGradeSpec(),
    val keyframes: Map<KeyframeProperty, List<KeyframeNode>> = emptyMap()
) {
    val durationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(100L)
}

@Serializable
data class TextClip(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Título Studio",
    val timelineStartMs: Long = 0L,
    val durationMs: Long = 3000L,
    val fontStyle: TextFontStyle = TextFontStyle.MODERN,
    val fontSizeSp: Float = 28f,
    val colorArgb: Long = 0xFFFFFFFF,
    val backgroundArgb: Long = 0x00000000,
    val strokeArgb: Long = 0xFF0B0D12,
    val animationIn: TextAnimationType = TextAnimationType.POP_SCALE,
    val transform: ClipTransform = ClipTransform(),
    val keyframes: Map<KeyframeProperty, List<KeyframeNode>> = emptyMap()
)

@Serializable
data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val mediaPath: String,
    val timelineStartMs: Long = 0L,
    val sourceDurationMs: Long,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = sourceDurationMs,
    val volume: Float = 0.85f,
    val fadeInMs: Long = 200L,
    val fadeOutMs: Long = 300L,
    val isMuted: Boolean = false,
    val waveformPeaks: List<Float> = emptyList(),
    val keyframes: Map<KeyframeProperty, List<KeyframeNode>> = emptyMap()
) {
    val durationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(100L)
}

@Serializable
data class ProjectTimeline(
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val backgroundColorArgb: Long = 0xFF000000,
    val blurBackground: Boolean = false,
    val fps: Int = 30,
    val mainClips: List<VideoClip> = emptyList(),
    val overlayClips: List<OverlayClip> = emptyList(),
    val textClips: List<TextClip> = emptyList(),
    val audioClips: List<AudioClip> = emptyList()
) {
    val mainTrackDurationMs: Long
        get() = mainClips.sumOf { it.effectiveDurationMs }

    val totalDurationMs: Long
        get() {
            val mainEnd = mainTrackDurationMs
            val overlayEnd = overlayClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
            val textEnd = textClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
            val audioEnd = audioClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
            return maxOf(mainEnd, overlayEnd, textEnd, audioEnd)
        }

    fun mainClipStartMs(index: Int): Long {
        var acc = 0L
        for (i in 0 until index.coerceAtMost(mainClips.size)) {
            acc += mainClips[i].effectiveDurationMs
        }
        return acc
    }

    fun mainClipStartById(clipId: String): Long? {
        val idx = mainClips.indexOfFirst { it.id == clipId }
        return if (idx >= 0) mainClipStartMs(idx) else null
    }

    fun findMainClipAt(timelineMs: Long): Pair<VideoClip, Long>? {
        var cursor = 0L
        for (clip in mainClips) {
            val end = cursor + clip.effectiveDurationMs
            if (timelineMs in cursor..end || (timelineMs >= cursor && clip == mainClips.last())) {
                return clip to (timelineMs - cursor).coerceIn(0L, clip.effectiveDurationMs)
            }
            cursor = end
        }
        return null
    }
}
