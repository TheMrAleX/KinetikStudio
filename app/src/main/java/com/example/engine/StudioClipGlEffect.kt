package com.example.engine

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.domain.engine.KeyframeEvaluator
import com.example.domain.model.ColorFilterPreset
import com.example.domain.model.MaskType
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.VisualEffectType
import kotlin.math.PI

@OptIn(UnstableApi::class)
class StudioClipGlEffect(
    private val clip: VideoClip,
    private val outputAspect: Float
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return StudioClipShaderProgram(context, useHdr, clip, outputAspect)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}

@OptIn(UnstableApi::class)
private class StudioClipShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val clip: VideoClip,
    private val outputAspect: Float
) : BaseGlShaderProgram(useHdr, 1) {

    private val glProgram: GlProgram
    private var outputSize = Size(1080, 1920)

    init {
        glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        outputSize = Size(inputWidth, inputHeight)
        return outputSize
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val localMs = (presentationTimeUs / 1000L).coerceIn(0L, clip.effectiveDurationMs)
        val evaluated = KeyframeEvaluator.evaluateClipFrame(clip, localMs)

        glProgram.use()
        glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)

        // Keyframed Transform uniforms
        glProgram.setFloatUniform("uPosX", evaluated.transform.posX)
        glProgram.setFloatUniform("uPosY", evaluated.transform.posY)
        glProgram.setFloatUniform("uScale", evaluated.transform.scale.coerceAtLeast(0.05f))
        glProgram.setFloatUniform(
            "uRotationRad",
            (evaluated.transform.rotationDeg * PI / 180.0).toFloat()
        )
        glProgram.setFloatUniform("uOpacity", evaluated.transform.opacity)
        glProgram.setFloatUniform("uAspect", outputAspect)
        glProgram.setFloatUniform("uTimeSec", localMs / 1000f)

        // Keyframed Mask uniforms
        glProgram.setIntUniform("uMaskType", evaluated.mask.type.ordinal)
        glProgram.setFloatsUniform(
            "uMaskCenter",
            floatArrayOf(evaluated.mask.centerX, evaluated.mask.centerY)
        )
        glProgram.setFloatsUniform(
            "uMaskSize",
            floatArrayOf(evaluated.mask.width, evaluated.mask.height)
        )
        glProgram.setFloatUniform(
            "uMaskRotRad",
            (evaluated.mask.rotationDeg * PI / 180.0).toFloat()
        )
        glProgram.setFloatUniform("uMaskFeather", evaluated.mask.feather.coerceAtLeast(0.002f))
        glProgram.setFloatUniform("uMaskCorner", evaluated.mask.cornerRadius)
        glProgram.setIntUniform("uMaskInvert", if (evaluated.mask.inverted) 1 else 0)

        // Color Grading uniforms
        val cg = clip.colorGrade
        glProgram.setFloatUniform("uExposure", cg.exposure)
        glProgram.setFloatUniform("uContrast", cg.contrast)
        glProgram.setFloatUniform("uSaturation", cg.saturation)
        glProgram.setFloatUniform("uTemp", cg.temperature)
        glProgram.setFloatUniform("uTint", cg.tint)
        glProgram.setFloatUniform("uHighlights", cg.highlights)
        glProgram.setFloatUniform("uShadows", cg.shadows)
        glProgram.setIntUniform("uPreset", cg.preset.ordinal)

        // Visual FX uniforms
        glProgram.setIntUniform("uFxType", clip.visualEffect.ordinal)
        glProgram.setFloatUniform("uFxIntensity", evaluated.effectIntensity)

        // Intro Transition uniforms
        val transType = clip.transitionIn.type
        val transDur = clip.transitionIn.durationMs.coerceAtLeast(1L)
        val transProgress = if (transType != TransitionType.NONE && localMs < transDur) {
            (localMs.toFloat() / transDur.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
        glProgram.setIntUniform("uTransType", transType.ordinal)
        glProgram.setFloatUniform("uTransProgress", transProgress)

        glProgram.bindAttributesAndUniforms()
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        glProgram.delete()
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(
                    aFramePosition.x * 0.5 + 0.5,
                    aFramePosition.y * 0.5 + 0.5
                );
            }
        """

        private val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            varying vec2 vTexSamplingCoord;

            uniform float uPosX;
            uniform float uPosY;
            uniform float uScale;
            uniform float uRotationRad;
            uniform float uOpacity;
            uniform float uAspect;
            uniform float uTimeSec;

            uniform int uMaskType;
            uniform vec2 uMaskCenter;
            uniform vec2 uMaskSize;
            uniform float uMaskRotRad;
            uniform float uMaskFeather;
            uniform float uMaskCorner;
            uniform int uMaskInvert;

            uniform float uExposure;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uTemp;
            uniform float uTint;
            uniform float uHighlights;
            uniform float uShadows;
            uniform int uPreset;

            uniform int uFxType;
            uniform float uFxIntensity;

            uniform int uTransType;
            uniform float uTransProgress;

            float rand(vec2 co) {
                return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
            }

            vec2 rotateAspect(vec2 p, float angle, float aspect) {
                vec2 scaled = vec2(p.x * aspect, p.y);
                float c = cos(angle);
                float s = sin(angle);
                vec2 r = vec2(scaled.x * c - scaled.y * s, scaled.x * s + scaled.y * c);
                return vec2(r.x / aspect, r.y);
            }

            float computeMaskAlpha(vec2 uv) {
                if (uMaskType == ${MaskType.NONE.ordinal}) return 1.0;

                vec2 p = uv - uMaskCenter;
                p.x *= uAspect;
                float c = cos(-uMaskRotRad);
                float s = sin(-uMaskRotRad);
                p = vec2(p.x * c - p.y * s, p.x * s + p.y * c);
                p.x /= uAspect;

                float dist = 0.0;
                if (uMaskType == ${MaskType.LINEAR.ordinal}) {
                    dist = -p.y;
                } else if (uMaskType == ${MaskType.MIRROR.ordinal}) {
                    dist = (uMaskSize.y * 0.5) - abs(p.y);
                } else if (uMaskType == ${MaskType.CIRCLE.ordinal}) {
                    vec2 r = max(uMaskSize * 0.5, vec2(0.01));
                    vec2 q = vec2(p.x * uAspect, p.y) / vec2(r.x * uAspect, r.y);
                    dist = (1.0 - length(q)) * min(r.x, r.y);
                } else if (uMaskType == ${MaskType.RECTANGLE.ordinal}) {
                    vec2 halfSize = max(uMaskSize * 0.5, vec2(0.01));
                    vec2 d = abs(p) - (halfSize - vec2(uMaskCorner));
                    float boxDist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - uMaskCorner;
                    dist = -boxDist;
                } else {
                    // Heart SDF approximation
                    vec2 hp = vec2(p.x * uAspect, -p.y) / max(min(uMaskSize.x, uMaskSize.y) * 0.55, 0.05);
                    hp.y -= 0.25;
                    float a = atan(hp.x, hp.y) / 3.14159;
                    float r = length(hp);
                    float h = abs(a);
                    float d = (13.0 * h - 22.0 * h * h + 10.0 * h * h * h) / (6.0 - 5.0 * h);
                    dist = (d - r) * 0.25;
                }

                float f = max(uMaskFeather, 0.002);
                float alpha = smoothstep(-f, f, dist);
                if (uMaskInvert == 1) alpha = 1.0 - alpha;
                return alpha;
            }

            vec3 applyColorGrade(vec3 col) {
                // Exposure
                col *= pow(2.0, uExposure * 1.4);
                // Contrast
                col = (col - 0.5) * (1.0 + uContrast * 0.9) + 0.5;
                // Temperature & Tint
                col.r += uTemp * 0.14;
                col.b -= uTemp * 0.14;
                col.g += uTint * 0.12;
                // Highlights & Shadows
                float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
                col += uShadows * (1.0 - smoothstep(0.0, 0.55, lum)) * 0.25;
                col += uHighlights * smoothstep(0.45, 1.0, lum) * 0.25;
                // Saturation
                float gray = dot(col, vec3(0.2126, 0.7152, 0.0722));
                col = mix(vec3(gray), col, 1.0 + uSaturation);

                // Preset LUT Looks
                if (uPreset == ${ColorFilterPreset.CINEMATIC.ordinal}) {
                    col = (col - 0.5) * 1.12 + 0.5;
                    col.r *= 1.04;
                    col.b *= 0.96;
                } else if (uPreset == ${ColorFilterPreset.TEAL_ORANGE.ordinal}) {
                    float l = dot(col, vec3(0.3, 0.59, 0.11));
                    vec3 shadowsTint = vec3(0.0, 0.18, 0.24);
                    vec3 highTint = vec3(0.26, 0.12, 0.0);
                    col = mix(col + shadowsTint * (1.0 - l), col + highTint * l, 0.65);
                } else if (uPreset == ${ColorFilterPreset.VINTAGE_90S.ordinal}) {
                    col = col * 0.92 + vec3(0.06, 0.04, 0.02);
                    col.g *= 0.97;
                } else if (uPreset == ${ColorFilterPreset.HIGH_CONTRAST.ordinal}) {
                    col = (col - 0.5) * 1.32 + 0.5;
                } else if (uPreset == ${ColorFilterPreset.PASTEL_SOFT.ordinal}) {
                    float g2 = dot(col, vec3(0.2126, 0.7152, 0.0722));
                    col = mix(vec3(g2), col, 0.78) * 0.95 + 0.06;
                }
                return clamp(col, 0.0, 1.0);
            }

            void main() {
                vec2 uv = vTexSamplingCoord;

                // Intro Transition geometric warp
                float tp = clamp(uTransProgress, 0.0, 1.0);
                float easedTp = tp * tp * (3.0 - 2.0 * tp);
                float transAlpha = 1.0;

                if (uTransType == ${TransitionType.CROSS_DISSOLVE.ordinal}) {
                    transAlpha = easedTp;
                } else if (uTransType == ${TransitionType.SLIDE_LEFT.ordinal}) {
                    uv.x += (1.0 - easedTp);
                } else if (uTransType == ${TransitionType.SLIDE_RIGHT.ordinal}) {
                    uv.x -= (1.0 - easedTp);
                } else if (uTransType == ${TransitionType.SLIDE_UP.ordinal}) {
                    uv.y -= (1.0 - easedTp);
                } else if (uTransType == ${TransitionType.ZOOM_IN.ordinal}) {
                    float z = mix(1.45, 1.0, easedTp);
                    uv = (uv - 0.5) / z + 0.5;
                    transAlpha = smoothstep(0.0, 0.35, tp);
                } else if (uTransType == ${TransitionType.CIRCLE_WIPE.ordinal}) {
                    float d = length(vec2((uv.x - 0.5) * uAspect, uv.y - 0.5));
                    transAlpha = 1.0 - smoothstep(easedTp * 1.1 - 0.03, easedTp * 1.1 + 0.03, d);
                }

                // Apply Clip 2D Keyframed Transform (inverse mapping)
                vec2 centered = uv - vec2(0.5 + uPosX * 0.5, 0.5 - uPosY * 0.5);
                centered = rotateAspect(centered, -uRotationRad, uAspect);
                vec2 sampleUv = centered / uScale + vec2(0.5);

                if (sampleUv.x < 0.0 || sampleUv.x > 1.0 || sampleUv.y < 0.0 || sampleUv.y > 1.0) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }

                vec4 texel = texture2D(uTexSampler, sampleUv);
                vec3 rgb = texel.rgb;

                // Visual FX Shaders
                float fx = clamp(uFxIntensity, 0.0, 1.0);
                if (uFxType == ${VisualEffectType.VHS_GLITCH.ordinal} && fx > 0.01) {
                    float jitter = (rand(vec2(floor(sampleUv.y * 45.0), floor(uTimeSec * 24.0))) - 0.5) * 0.035 * fx;
                    float r = texture2D(uTexSampler, clamp(sampleUv + vec2(jitter + 0.012 * fx, 0.0), 0.0, 1.0)).r;
                    float b = texture2D(uTexSampler, clamp(sampleUv + vec2(jitter - 0.012 * fx, 0.0), 0.0, 1.0)).b;
                    rgb = vec3(r, rgb.g, b);
                    rgb *= 0.94 + 0.06 * sin(sampleUv.y * 420.0);
                } else if (uFxType == ${VisualEffectType.CHROMATIC_ABERRATION.ordinal} && fx > 0.01) {
                    vec2 dir = (sampleUv - 0.5) * 0.025 * fx;
                    float r = texture2D(uTexSampler, clamp(sampleUv + dir, 0.0, 1.0)).r;
                    float b = texture2D(uTexSampler, clamp(sampleUv - dir, 0.0, 1.0)).b;
                    rgb = vec3(r, rgb.g, b);
                } else if (uFxType == ${VisualEffectType.RADIAL_BLUR.ordinal} && fx > 0.01) {
                    vec3 acc = vec3(0.0);
                    vec2 dir = (sampleUv - 0.5) * 0.04 * fx;
                    for (int i = 0; i < 6; i++) {
                        float k = float(i) / 5.0;
                        acc += texture2D(uTexSampler, clamp(sampleUv - dir * k, 0.0, 1.0)).rgb;
                    }
                    rgb = acc / 6.0;
                } else if (uFxType == ${VisualEffectType.NOIR_BW.ordinal}) {
                    float bw = dot(rgb, vec3(0.299, 0.587, 0.114));
                    bw = clamp((bw - 0.5) * (1.0 + 0.45 * fx) + 0.5, 0.0, 1.0);
                    rgb = mix(rgb, vec3(bw), fx);
                } else if (uFxType == ${VisualEffectType.HALATION_GLOW.ordinal} && fx > 0.01) {
                    vec3 blurCol = (
                        texture2D(uTexSampler, clamp(sampleUv + vec2(0.008, 0.008), 0.0, 1.0)).rgb +
                        texture2D(uTexSampler, clamp(sampleUv - vec2(0.008, 0.008), 0.0, 1.0)).rgb +
                        texture2D(uTexSampler, clamp(sampleUv + vec2(-0.008, 0.008), 0.0, 1.0)).rgb +
                        texture2D(uTexSampler, clamp(sampleUv + vec2(0.008, -0.008), 0.0, 1.0)).rgb
                    ) * 0.25;
                    vec3 bright = max(blurCol - 0.55, 0.0) * 1.8 * fx;
                    rgb = clamp(rgb + bright, 0.0, 1.0);
                }

                if (uTransType == ${TransitionType.GLITCH_FLASH.ordinal} && tp < 1.0) {
                    float flash = (1.0 - easedTp);
                    float split = 0.03 * flash;
                    float rr = texture2D(uTexSampler, clamp(sampleUv + vec2(split, 0.0), 0.0, 1.0)).r;
                    float bb = texture2D(uTexSampler, clamp(sampleUv - vec2(split, 0.0), 0.0, 1.0)).b;
                    rgb = mix(rgb, vec3(rr, rgb.g, bb) + vec3(0.45 * flash), flash);
                }

                rgb = applyColorGrade(rgb);

                if (uFxType == ${VisualEffectType.VIGNETTE.ordinal}) {
                    float d = length(vTexSamplingCoord - 0.5);
                    float vig = smoothstep(0.85, 0.25, d * (0.8 + 0.6 * fx));
                    rgb *= mix(1.0, vig, fx);
                } else if (uFxType == ${VisualEffectType.FILM_GRAIN.ordinal}) {
                    float n = (rand(sampleUv * 300.0 + uTimeSec) - 0.5) * 0.18 * fx;
                    rgb = clamp(rgb + n, 0.0, 1.0);
                }

                float maskAlpha = computeMaskAlpha(vTexSamplingCoord);
                float finalAlpha = clamp(uOpacity * maskAlpha * transAlpha, 0.0, 1.0);

                gl_FragColor = vec4(rgb * finalAlpha, 1.0);
            }
        """.trimIndent()
    }
}
