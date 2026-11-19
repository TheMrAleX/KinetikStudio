package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.example.R
import com.example.domain.engine.KeyframeEvaluator
import com.example.domain.model.ProjectTimeline
import com.example.domain.model.TextClip
import com.example.domain.model.TextFontStyle
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
object CompositionBuilder {

    fun buildComposition(
        context: Context,
        timeline: ProjectTimeline,
        targetShortEdgePx: Int = 1080,
        includeTextOverlays: Boolean = false
    ): Composition {
        val (rawW, rawH) = timeline.aspectRatio.resolveDimensions(targetShortEdgePx.coerceAtLeast(16))
        val outW = rawW.coerceAtLeast(16)
        val outH = rawH.coerceAtLeast(16)
        val aspectFloat = timeline.aspectRatio.ratioFloat

        val mainEditedItems = timeline.mainClips.mapIndexedNotNull { index, clip ->
            val mediaFile = File(clip.mediaPath)
            if (!mediaFile.exists()) return@mapIndexedNotNull null

            val mediaUri = Uri.fromFile(mediaFile)
            val mediaItemBuilder = MediaItem.Builder().setUri(mediaUri)

            val durationUs: Long
            if (clip.isImage) {
                val imgMs = clip.effectiveDurationMs.coerceAtLeast(100L)
                mediaItemBuilder.setImageDurationMs(imgMs)
                durationUs = imgMs * 1000L
            } else {
                val safeSourceMs = clip.sourceDurationMs.coerceAtLeast(250L)
                val safeStartMs = clip.sourceStartMs.coerceIn(0L, safeSourceMs - 100L)
                val safeEndMs = clip.sourceEndMs.coerceIn(safeStartMs + 100L, safeSourceMs)
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(safeStartMs)
                        .setEndPositionMs(safeEndMs)
                        .build()
                )
                durationUs = safeSourceMs * 1000L
            }

            val videoEffects = mutableListOf<Effect>(
                Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT),
                StudioClipGlEffect(clip, aspectFloat, useLiveRegistry = !includeTextOverlays)
            )

            val hasSpeedChange = !clip.isImage && abs(clip.speed - 1f) > 0.01f
            val clampedSpeed = clip.speed.coerceIn(0.1f, 8f)
            if (hasSpeedChange) {
                videoEffects.add(SpeedChangeEffect(clampedSpeed))
            }

            if (includeTextOverlays && timeline.textClips.isNotEmpty()) {
                val clipStartMs = timeline.mainClipStartMs(index)
                val clipEndMs = clipStartMs + clip.effectiveDurationMs
                val overlappingTexts = timeline.textClips.filter { tc ->
                    val tcEnd = tc.timelineStartMs + tc.durationMs
                    tcEnd > clipStartMs && tc.timelineStartMs < clipEndMs
                }
                if (overlappingTexts.isNotEmpty()) {
                    val overlays = overlappingTexts.map { tc ->
                        createTextBitmapOverlay(context, tc, clipStartMs)
                    }
                    videoEffects.add(OverlayEffect(overlays))
                }
            }

            val audioProcessors = mutableListOf<AudioProcessor>()
            if (!clip.isImage && !clip.isMuted && hasSpeedChange) {
                val sonic = SonicAudioProcessor().apply {
                    setSpeed(clampedSpeed)
                }
                audioProcessors.add(sonic)
            }
            if (!clip.isImage && !clip.isMuted && abs(clip.volume - 1f) > 0.01f) {
                audioProcessors.add(GainAudioProcessor(clip.volume.coerceIn(0f, 2f)))
            }

            val itemBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setDurationUs(durationUs)
                .setRemoveAudio(clip.isImage || clip.isMuted)
                .setEffects(Effects(audioProcessors, videoEffects))

            if (clip.isImage) {
                itemBuilder.setFrameRate(timeline.fps.coerceIn(24, 60))
            }
            itemBuilder.build()
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        if (mainEditedItems.isNotEmpty()) {
            sequences.add(EditedMediaItemSequence.Builder(mainEditedItems).build())
        }

        // Additional Audio Tracks
        timeline.audioClips.filter { !it.isMuted }.forEach { audioClip ->
            val audioFile = File(audioClip.mediaPath)
            if (!audioFile.exists()) return@forEach

            val safeAudioSourceMs = audioClip.sourceDurationMs.coerceAtLeast(250L)
            val safeAudioStartMs = audioClip.sourceStartMs.coerceIn(0L, safeAudioSourceMs - 100L)
            val safeAudioEndMs = audioClip.sourceEndMs.coerceIn(safeAudioStartMs + 100L, safeAudioSourceMs)

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(audioFile))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(safeAudioStartMs)
                        .setEndPositionMs(safeAudioEndMs)
                        .build()
                )
                .build()

            val audioProcessors = mutableListOf<AudioProcessor>()
            if (abs(audioClip.volume - 1f) > 0.01f) {
                audioProcessors.add(GainAudioProcessor(audioClip.volume.coerceIn(0f, 2f)))
            }

            val editedAudio = EditedMediaItem.Builder(mediaItem)
                .setDurationUs(safeAudioSourceMs * 1000L)
                .setRemoveVideo(true)
                .setEffects(Effects(audioProcessors, emptyList()))
                .build()

            val audioSeqBuilder = EditedMediaItemSequence.Builder()
            // CompositionPlayer forbids gaps (!composition.hasGaps()), while Transformer supports them.
            if (includeTextOverlays && audioClip.timelineStartMs > 0L) {
                audioSeqBuilder.addGap(audioClip.timelineStartMs * 1000L)
            }
            audioSeqBuilder.addItem(editedAudio)
            sequences.add(audioSeqBuilder.build())
        }

        return Composition.Builder(sequences)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
    }

    private fun createTextBitmapOverlay(
        context: Context,
        textClip: TextClip,
        clipTimelineStartMs: Long
    ): BitmapOverlay {
        val bitmap = renderTextBitmap(context, textClip)
        return object : BitmapOverlay() {
            override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                val globalMs = clipTimelineStartMs + (presentationTimeUs / 1000L)
                val localMs = globalMs - textClip.timelineStartMs
                if (localMs < 0L || localMs > textClip.durationMs) {
                    return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
                }
                val evaluated = KeyframeEvaluator.evaluateTextFrame(textClip, localMs)
                return StaticOverlaySettings.Builder()
                    .setAlphaScale(evaluated.transform.opacity.coerceIn(0f, 1f))
                    .setScale(
                        evaluated.transform.scale.coerceIn(0.1f, 5f),
                        evaluated.transform.scale.coerceIn(0.1f, 5f)
                    )
                    .setRotationDegrees(-evaluated.transform.rotationDeg)
                    .setBackgroundFrameAnchor(
                        evaluated.transform.posX.coerceIn(-2f, 2f),
                        -evaluated.transform.posY.coerceIn(-2f, 2f)
                    )
                    .setOverlayFrameAnchor(0f, 0f)
                    .build()
            }
        }
    }

    private fun renderTextBitmap(context: Context, textClip: TextClip): Bitmap {
        val typeface = when (textClip.fontStyle) {
            TextFontStyle.MODERN -> ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT_BOLD
            TextFontStyle.CLEAN -> ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.SANS_SERIF
            TextFontStyle.MONO -> ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        }

        val textSizePx = (textClip.fontSizeSp * 2.6f).coerceIn(32f, 180f)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textClip.colorArgb.toInt()
            textSize = textSizePx
            this.typeface = typeface
        }

        val safeText = textClip.text.ifBlank { " " }
        val maxWidth = 900
        val layout = StaticLayout.Builder.obtain(
            safeText,
            0,
            safeText.length,
            textPaint,
            maxWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()

        var textW = 0f
        for (i in 0 until layout.lineCount) {
            textW = maxOf(textW, layout.getLineWidth(i))
        }
        val padX = 36
        val padY = 22
        val bmpW = (textW.roundToInt() + padX * 2).coerceIn(64, 1024)
        val bmpH = (layout.height + padY * 2).coerceIn(48, 768)

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgAlpha = (textClip.backgroundArgb ushr 24) and 0xFF
        if (bgAlpha > 0) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textClip.backgroundArgb.toInt()
            }
            canvas.drawRoundRect(
                RectF(0f, 0f, bmpW.toFloat(), bmpH.toFloat()),
                28f,
                28f,
                bgPaint
            )
        }

        canvas.save()
        canvas.translate((bmpW - maxWidth) / 2f, padY.toFloat())
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }
}

/**
 * Channel-count-agnostic PCM volume processor supporting 16-bit and 32-bit float audio
 * across mono, stereo, and surround streams without failing on >2 channels.
 */
@OptIn(UnstableApi::class)
private class GainAudioProcessor(private val gain: Float) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (abs(gain - 1f) < 0.01f) {
            return AudioFormat.NOT_SET
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val out = replaceOutputBuffer(remaining)
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.remaining() >= 2) {
                    val sample = inputBuffer.getShort()
                    val scaled = (sample * gain).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    out.putShort(scaled.toShort())
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.remaining() >= 4) {
                    val sample = inputBuffer.getFloat()
                    val scaled = (sample * gain).coerceIn(-1f, 1f)
                    out.putFloat(scaled)
                }
            }
        }
        if (inputBuffer.hasRemaining()) {
            out.put(inputBuffer)
        }
        out.flip()
    }
}
