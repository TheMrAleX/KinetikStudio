package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.KeyframeEvaluator
import com.example.domain.model.EasingCurve
import com.example.domain.model.EasingType
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineOutline
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.formatTimecode
import com.example.ui.viewmodel.CurveTarget
import java.util.Locale
import kotlin.math.hypot

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurveEditorSheet(
    target: CurveTarget,
    onCurveChange: (EasingCurve) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val curve = target.startNode.easing
    val currentCurve by rememberUpdatedState(curve)

    val transition = rememberInfiniteTransition(label = "curve_preview")
    val animT by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bead_t"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Curva de Animación • ${target.property.label}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${formatTimecode(target.startNode.timeOffsetMs)} → ${formatTimecode(target.endNode.timeOffsetMs)}",
                        style = MonoTimecodeStyle,
                        color = TextSecondary
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
                            "%.2f, %.2f, %.2f, %.2f",
                            curve.x1,
                            curve.y1,
                            curve.x2,
                            curve.y2
                        ),
                        style = MonoTimecodeStyle,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Interactive Cubic Bezier Graph
            var activeHandle by remember { mutableStateOf(0) } // 1 = P1, 2 = P2
            val dash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianBg)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val padX = 28.dp.toPx()
                                val padY = 34.dp.toPx()
                                val gw = (size.width - padX * 2).coerceAtLeast(1f)
                                val gh = (size.height - padY * 2).coerceAtLeast(1f)
                                val p1 = Offset(
                                    padX + currentCurve.x1 * gw,
                                    padY + (1f - currentCurve.y1) * gh
                                )
                                val p2 = Offset(
                                    padX + currentCurve.x2 * gw,
                                    padY + (1f - currentCurve.y2) * gh
                                )
                                val d1 = hypot(offset.x - p1.x, offset.y - p1.y)
                                val d2 = hypot(offset.x - p2.x, offset.y - p2.y)
                                activeHandle = if (d1 <= d2) 1 else 2
                            },
                            onDragEnd = { activeHandle = 0 },
                            onDragCancel = { activeHandle = 0 }
                        ) { change, _ ->
                            change.consume()
                            val padX = 28.dp.toPx()
                            val padY = 34.dp.toPx()
                            val gw = (size.width - padX * 2).coerceAtLeast(1f)
                            val gh = (size.height - padY * 2).coerceAtLeast(1f)
                            val nx = ((change.position.x - padX) / gw).coerceIn(0.02f, 0.98f)
                            val ny = (1f - (change.position.y - padY) / gh).coerceIn(-0.35f, 1.45f)

                            val updated = if (activeHandle == 1) {
                                currentCurve.copy(type = EasingType.CUSTOM_BEZIER, x1 = nx, y1 = ny)
                            } else {
                                currentCurve.copy(type = EasingType.CUSTOM_BEZIER, x2 = nx, y2 = ny)
                            }
                            onCurveChange(updated)
                        }
                    }
                    .testTag("bezier_curve_canvas")
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    val padX = 28.dp.toPx()
                    val padY = 34.dp.toPx()
                    val gw = size.width - padX * 2
                    val gh = size.height - padY * 2

                    fun toScreen(nx: Float, ny: Float): Offset =
                        Offset(padX + nx * gw, padY + (1f - ny) * gh)

                    // Grid lines
                    for (i in 0..4) {
                        val f = i / 4f
                        val x = padX + f * gw
                        val y = padY + f * gh
                        drawLine(HairlineOutline.copy(alpha = 0.45f), Offset(x, padY), Offset(x, padY + gh), 1.dp.toPx())
                        drawLine(HairlineOutline.copy(alpha = 0.45f), Offset(padX, y), Offset(padX + gw, y), 1.dp.toPx())
                    }

                    // Unit box border [0,1]x[0,1]
                    val tl = toScreen(0f, 1f)
                    val br = toScreen(1f, 0f)
                    drawRect(
                        color = HairlineOutline,
                        topLeft = tl,
                        size = androidx.compose.ui.geometry.Size(br.x - tl.x, br.y - tl.y),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Linear reference diagonal
                    drawLine(
                        color = TextMuted.copy(alpha = 0.4f),
                        start = toScreen(0f, 0f),
                        end = toScreen(1f, 1f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash
                    )

                    // Evaluated curve polyline (supports overshoot & elastic)
                    val path = Path()
                    val steps = 60
                    for (s in 0..steps) {
                        val t = s / steps.toFloat()
                        val ey = KeyframeEvaluator.evaluateEasing(curve, t)
                        val pt = toScreen(t, ey)
                        if (s == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                    }
                    drawPath(
                        path = path,
                        color = ElectricCyan,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Control handle tangents & pins
                    if (curve.type != EasingType.ELASTIC) {
                        val p0 = toScreen(0f, 0f)
                        val p1 = toScreen(curve.x1, curve.y1)
                        val p2 = toScreen(curve.x2, curve.y2)
                        val p3 = toScreen(1f, 1f)

                        drawLine(CoralAmber.copy(alpha = 0.7f), p0, p1, 1.5.dp.toPx())
                        drawLine(CoralAmber.copy(alpha = 0.7f), p3, p2, 1.5.dp.toPx())

                        drawCircle(CoralAmber, radius = 9.dp.toPx(), center = p1)
                        drawCircle(ObsidianBg, radius = 4.dp.toPx(), center = p1)

                        drawCircle(CoralAmber, radius = 9.dp.toPx(), center = p2)
                        drawCircle(ObsidianBg, radius = 4.dp.toPx(), center = p2)
                    }

                    // Live animated preview bead along curve + side motion bar
                    val beadY = KeyframeEvaluator.evaluateEasing(curve, animT)
                    val beadPt = toScreen(animT, beadY)
                    drawCircle(Color.White, radius = 5.dp.toPx(), center = beadPt)
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Presets de Curva",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EasingType.entries.filter { it != EasingType.CUSTOM_BEZIER }) { preset ->
                    val selected = curve.type == preset
                    FilterChip(
                        selected = selected,
                        onClick = { onCurveChange(EasingCurve.fromPreset(preset)) },
                        label = { Text(preset.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                            selectedLabelColor = ElectricCyan,
                            containerColor = ElevatedSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = HairlineOutline,
                            selectedBorderColor = ElectricCyan
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color(0xFF002026)
                )
            ) {
                Text("Aplicar Curva")
            }
        }
    }
}
