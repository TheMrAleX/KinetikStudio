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
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
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
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
object CompositionBuilder {

    fun buildComposition(
        context: Context,
        timeline: ProjectTimeline,
        targetShortEdgePx: Int = 1080,
        includeTextOverlays: Boolean = false
    ): Composition {
        val (outW, outH) = timeline.aspectRatio.resolveDimensions(targetShortEdgePx)
        val aspectFloat = timeline.aspectRatio.ratioFloat

        val mainEditedItems = timeline.mainClips.mapIndexed { index, clip ->
            val mediaUri = Uri.fromFile(File(clip.mediaPath))
            val mediaItemBuilder = MediaItem.Builder().setUri(mediaUri)
            if (clip.isImage) {
                mediaItemBuilder.setImageDurationMs(clip.effectiveDurationMs)
            } else {
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.sourceStartMs)
                        .setEndPositionMs(clip.sourceEndMs)
                        .build()
                )
            }

            val videoEffects = mutableListOf<Effect>(
                Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT),
                StudioClipGlEffect(clip, aspectFloat)
            )

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
            if (!clip.isImage && !clip.isMuted && clip.speed != 1f) {
                val sonic = SonicAudioProcessor().apply {
                    setSpeed(clip.speed.coerceIn(0.1f, 10f))
                }
                audioProcessors.add(sonic)
            }
            if (!clip.isImage && !clip.isMuted && clip.volume != 1f) {
                val mixer = ChannelMixingAudioProcessor()
                val v = clip.volume.coerceIn(0f, 2f)
                mixer.putChannelMixingMatrix(ChannelMixingMatrix(2, 2, floatArrayOf(v, 0f, 0f, v)))
                mixer.putChannelMixingMatrix(ChannelMixingMatrix(1, 2, floatArrayOf(v, v)))
                audioProcessors.add(mixer)
            }

            val itemBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setRemoveAudio(clip.isImage || clip.isMuted)
                .setEffects(Effects(audioProcessors, videoEffects))

            if (clip.isImage) {
                itemBuilder.setFrameRate(timeline.fps)
            }
            itemBuilder.build()
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        if (mainEditedItems.isNotEmpty()) {
            sequences.add(EditedMediaItemSequence.Builder(mainEditedItems).build())
        }

        // Additional Audio Tracks
        val totalDurationUs = timeline.mainTrackDurationMs * 1000L
        timeline.audioClips.filter { !it.isMuted }.forEach { audioClip ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(File(audioClip.mediaPath)))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(audioClip.sourceStartMs)
                        .setEndPositionMs(audioClip.sourceEndMs)
                        .build()
                )
                .build()

            val audioProcessors = mutableListOf<AudioProcessor>()
            if (audioClip.volume != 1f) {
                val mixer = ChannelMixingAudioProcessor()
                val v = audioClip.volume.coerceIn(0f, 2f)
                mixer.putChannelMixingMatrix(ChannelMixingMatrix(2, 2, floatArrayOf(v, 0f, 0f, v)))
                mixer.putChannelMixingMatrix(ChannelMixingMatrix(1, 2, floatArrayOf(v, v)))
                audioProcessors.add(mixer)
            }

            val editedAudio = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .setEffects(Effects(audioProcessors, emptyList()))
                .build()

            val audioSeqBuilder = EditedMediaItemSequence.Builder(listOf(editedAudio))
            if (audioClip.durationMs < timeline.mainTrackDurationMs && audioClip.timelineStartMs == 0L) {
                // Keep exact duration without unwanted looping
            }
            sequences.add(audioSeqBuilder.build())
        }

        return Composition.Builder(sequences).build()
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
                    .setAlphaScale(evaluated.transform.opacity)
                    .setScale(evaluated.transform.scale, evaluated.transform.scale)
                    .setRotationDegrees(-evaluated.transform.rotationDeg)
                    .setBackgroundFrameAnchor(evaluated.transform.posX, -evaluated.transform.posY)
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

        val maxWidth = 900
        val layout = StaticLayout.Builder.obtain(
            textClip.text.ifBlank { " " },
            0,
            textClip.text.length.coerceAtLeast(1),
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
