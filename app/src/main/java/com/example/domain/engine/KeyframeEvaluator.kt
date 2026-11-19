package com.example.domain.engine

import com.example.domain.model.ClipTransform
import com.example.domain.model.EasingCurve
import com.example.domain.model.EasingType
import com.example.domain.model.KeyframeNode
import com.example.domain.model.KeyframeProperty
import com.example.domain.model.MaskSpec
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TextClip
import com.example.domain.model.VideoClip
import com.example.domain.model.AudioClip
import com.example.domain.model.OverlayClip
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

data class EvaluatedClipState(
    val transform: ClipTransform,
    val mask: MaskSpec,
    val volume: Float,
    val effectIntensity: Float,
    val speedMultiplier: Float
)

data class EvaluatedTextState(
    val transform: ClipTransform
)

object KeyframeEvaluator {

    /**
     * Evaluates a cubic Bézier curve defined by control points (0,0), (x1,y1), (x2,y2), (1,1)
     * at normalized time t in [0, 1]. Uses Newton-Raphson with binary-search fallback so
     * arbitrary user-drawn curves remain smooth and strictly monotonic in x.
     */
    fun evaluateEasing(curve: EasingCurve, rawT: Float): Float {
        val t = rawT.coerceIn(0f, 1f)
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        if (curve.type == EasingType.LINEAR) return t
        if (curve.type == EasingType.ELASTIC) {
            val c4 = (2.0 * PI) / 3.0
            return (2.0.pow(-10.0 * t) * sin((t * 10.0 - 0.75) * c4) + 1.0).toFloat()
        }

        val x1 = curve.x1.coerceIn(0.01f, 0.99f)
        val x2 = curve.x2.coerceIn(0.01f, 0.99f)

        val u = solveBezierX(t, x1, x2)
        return sampleBezier(u, curve.y1, curve.y2)
    }

    private fun sampleBezier(u: Float, p1: Float, p2: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * u * p1 + 3f * inv * u * u * p2 + u * u * u
    }

    private fun sampleBezierDerivative(u: Float, p1: Float, p2: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * p1 + 6f * inv * u * (p2 - p1) + 3f * u * u * (1f - p2)
    }

    private fun solveBezierX(targetX: Float, x1: Float, x2: Float): Float {
        var u = targetX
        for (i in 0 until 6) {
            val err = sampleBezier(u, x1, x2) - targetX
            if (abs(err) < 1e-5f) return u
            val d = sampleBezierDerivative(u, x1, x2)
            if (abs(d) < 1e-5f) break
            u = (u - err / d).coerceIn(0f, 1f)
        }
        var lo = 0f
        var hi = 1f
        var mid = targetX
        for (i in 0 until 14) {
            mid = 0.5f * (lo + hi)
            val x = sampleBezier(mid, x1, x2)
            if (abs(x - targetX) < 1e-5f) return mid
            if (x < targetX) lo = mid else hi = mid
        }
        return mid
    }

    fun evaluateProperty(
        nodes: List<KeyframeNode>?,
        baseValue: Float,
        localTimeMs: Long
    ): Float {
        if (nodes.isNullOrEmpty()) return baseValue
        val sorted = if (nodes.size > 1) nodes.sortedBy { it.timeOffsetMs } else nodes
        if (localTimeMs <= sorted.first().timeOffsetMs) return sorted.first().value
        if (localTimeMs >= sorted.last().timeOffsetMs) return sorted.last().value

        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (localTimeMs in a.timeOffsetMs..b.timeOffsetMs) {
                val span = (b.timeOffsetMs - a.timeOffsetMs).coerceAtLeast(1L)
                val linearT = (localTimeMs - a.timeOffsetMs).toFloat() / span.toFloat()
                val eased = evaluateEasing(a.easing, linearT)
                return a.value + (b.value - a.value) * eased
            }
        }
        return baseValue
    }

    fun evaluateSpeedMultiplier(preset: SpeedRampPreset, baseSpeed: Float, normalizedProgress: Float): Float {
        val p = normalizedProgress.coerceIn(0f, 1f)
        val factor = when (preset) {
            SpeedRampPreset.CONSTANT -> 1f
            SpeedRampPreset.MONTAGE -> when {
                p < 0.25f -> 0.55f
                p < 0.55f -> 2.1f
                else -> 0.75f
            }
            SpeedRampPreset.HERO_TIME -> when {
                p < 0.3f -> 1.8f
                p < 0.7f -> 0.4f
                else -> 1.9f
            }
            SpeedRampPreset.BULLET -> if (p in 0.35f..0.65f) 0.28f else 2.2f
            SpeedRampPreset.JUMP_CUT -> if (((p * 6).toInt() % 2) == 0) 1.5f else 0.7f
            SpeedRampPreset.FLASH_IN -> 2.4f - 1.4f * p
            SpeedRampPreset.FLASH_OUT -> 1.0f + 1.5f * p
        }
        return (baseSpeed * factor).coerceIn(0.1f, 10f)
    }

    fun evaluateClipFrame(clip: VideoClip, localTimeMs: Long): EvaluatedClipState {
        val kf = clip.keyframes
        val clampedTime = localTimeMs.coerceIn(0L, clip.effectiveDurationMs)
        val normProgress = if (clip.effectiveDurationMs > 0L) {
            clampedTime.toFloat() / clip.effectiveDurationMs.toFloat()
        } else 0f

        val baseVol = if (clip.isMuted) 0f else clip.volume
        var vol = evaluateProperty(kf[KeyframeProperty.VOLUME], baseVol, clampedTime)
        if (clip.fadeInMs > 0L && clampedTime < clip.fadeInMs) {
            vol *= clampedTime.toFloat() / clip.fadeInMs.toFloat()
        }
        val fromEnd = clip.effectiveDurationMs - clampedTime
        if (clip.fadeOutMs > 0L && fromEnd < clip.fadeOutMs) {
            vol *= (fromEnd.coerceAtLeast(0L).toFloat() / clip.fadeOutMs.toFloat())
        }

        return EvaluatedClipState(
            transform = ClipTransform(
                posX = evaluateProperty(kf[KeyframeProperty.POS_X], clip.transform.posX, clampedTime),
                posY = evaluateProperty(kf[KeyframeProperty.POS_Y], clip.transform.posY, clampedTime),
                scale = evaluateProperty(kf[KeyframeProperty.SCALE], clip.transform.scale, clampedTime),
                rotationDeg = evaluateProperty(kf[KeyframeProperty.ROTATION], clip.transform.rotationDeg, clampedTime),
                opacity = evaluateProperty(kf[KeyframeProperty.OPACITY], clip.transform.opacity, clampedTime).coerceIn(0f, 1f)
            ),
            mask = clip.mask.copy(
                centerX = evaluateProperty(kf[KeyframeProperty.MASK_X], clip.mask.centerX, clampedTime),
                centerY = evaluateProperty(kf[KeyframeProperty.MASK_Y], clip.mask.centerY, clampedTime),
                width = evaluateProperty(kf[KeyframeProperty.MASK_WIDTH], clip.mask.width, clampedTime).coerceAtLeast(0.02f),
                height = evaluateProperty(kf[KeyframeProperty.MASK_HEIGHT], clip.mask.height, clampedTime).coerceAtLeast(0.02f),
                rotationDeg = evaluateProperty(kf[KeyframeProperty.MASK_ROTATION], clip.mask.rotationDeg, clampedTime),
                feather = evaluateProperty(kf[KeyframeProperty.MASK_FEATHER], clip.mask.feather, clampedTime).coerceIn(0f, 0.5f)
            ),
            volume = vol.coerceIn(0f, 2f),
            effectIntensity = evaluateProperty(kf[KeyframeProperty.EFFECT_INTENSITY], clip.effectIntensity, clampedTime).coerceIn(0f, 1f),
            speedMultiplier = evaluateSpeedMultiplier(clip.speedPreset, clip.speed, normProgress)
        )
    }

    fun evaluateOverlayFrame(clip: OverlayClip, localTimeMs: Long): EvaluatedClipState {
        val kf = clip.keyframes
        val clampedTime = localTimeMs.coerceIn(0L, clip.durationMs)
        return EvaluatedClipState(
            transform = ClipTransform(
                posX = evaluateProperty(kf[KeyframeProperty.POS_X], clip.transform.posX, clampedTime),
                posY = evaluateProperty(kf[KeyframeProperty.POS_Y], clip.transform.posY, clampedTime),
                scale = evaluateProperty(kf[KeyframeProperty.SCALE], clip.transform.scale, clampedTime),
                rotationDeg = evaluateProperty(kf[KeyframeProperty.ROTATION], clip.transform.rotationDeg, clampedTime),
                opacity = evaluateProperty(kf[KeyframeProperty.OPACITY], clip.transform.opacity, clampedTime).coerceIn(0f, 1f)
            ),
            mask = clip.mask.copy(
                centerX = evaluateProperty(kf[KeyframeProperty.MASK_X], clip.mask.centerX, clampedTime),
                centerY = evaluateProperty(kf[KeyframeProperty.MASK_Y], clip.mask.centerY, clampedTime),
                width = evaluateProperty(kf[KeyframeProperty.MASK_WIDTH], clip.mask.width, clampedTime).coerceAtLeast(0.02f),
                height = evaluateProperty(kf[KeyframeProperty.MASK_HEIGHT], clip.mask.height, clampedTime).coerceAtLeast(0.02f),
                rotationDeg = evaluateProperty(kf[KeyframeProperty.MASK_ROTATION], clip.mask.rotationDeg, clampedTime),
                feather = evaluateProperty(kf[KeyframeProperty.MASK_FEATHER], clip.mask.feather, clampedTime).coerceIn(0f, 0.5f)
            ),
            volume = if (clip.isMuted) 0f else evaluateProperty(kf[KeyframeProperty.VOLUME], clip.volume, clampedTime),
            effectIntensity = 0f,
            speedMultiplier = 1f
        )
    }

    fun evaluateTextFrame(clip: TextClip, localTimeMs: Long): EvaluatedTextState {
        val kf = clip.keyframes
        val clampedTime = localTimeMs.coerceIn(0L, clip.durationMs)
        return EvaluatedTextState(
            transform = ClipTransform(
                posX = evaluateProperty(kf[KeyframeProperty.POS_X], clip.transform.posX, clampedTime),
                posY = evaluateProperty(kf[KeyframeProperty.POS_Y], clip.transform.posY, clampedTime),
                scale = evaluateProperty(kf[KeyframeProperty.SCALE], clip.transform.scale, clampedTime),
                rotationDeg = evaluateProperty(kf[KeyframeProperty.ROTATION], clip.transform.rotationDeg, clampedTime),
                opacity = evaluateProperty(kf[KeyframeProperty.OPACITY], clip.transform.opacity, clampedTime).coerceIn(0f, 1f)
            )
        )
    }

    fun evaluateAudioVolume(clip: AudioClip, localTimeMs: Long): Float {
        if (clip.isMuted) return 0f
        val clamped = localTimeMs.coerceIn(0L, clip.durationMs)
        var vol = evaluateProperty(clip.keyframes[KeyframeProperty.VOLUME], clip.volume, clamped)
        if (clip.fadeInMs > 0L && clamped < clip.fadeInMs) {
            vol *= clamped.toFloat() / clip.fadeInMs.toFloat()
        }
        val fromEnd = clip.durationMs - clamped
        if (clip.fadeOutMs > 0L && fromEnd < clip.fadeOutMs) {
            vol *= (fromEnd.coerceAtLeast(0L).toFloat() / clip.fadeOutMs.toFloat())
        }
        return vol.coerceIn(0f, 2f)
    }
}
