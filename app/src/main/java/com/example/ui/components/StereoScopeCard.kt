package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ClayChannel
import com.example.ui.theme.SeamLine
import com.example.ui.theme.GraphitePanel
import com.example.ui.theme.GraphiteWell
import com.example.ui.theme.SageWell
import com.example.ui.theme.SteelChannel
import com.example.ui.theme.SageSignal
import com.example.ui.theme.GridLine
import com.example.ui.theme.BoneMuted
import com.example.ui.theme.BoneSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun StereoGoniometer(
    scopePoints: FloatArray,
    stereoWidth: Float,
    eqEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) * 0.45f).coerceAtLeast(8f)

        drawCircle(
            color = SeamLine,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = GridLine,
            radius = radius * 0.55f,
            center = Offset(cx, cy),
            style = Stroke(width = 0.75.dp.toPx())
        )

        val diag = radius * 0.707f
        drawLine(
            color = SeamLine,
            start = Offset(cx - diag, cy - diag),
            end = Offset(cx + diag, cy + diag),
            strokeWidth = 0.9.dp.toPx()
        )
        drawLine(
            color = SeamLine,
            start = Offset(cx + diag, cy - diag),
            end = Offset(cx - diag, cy + diag),
            strokeWidth = 0.9.dp.toPx()
        )

        val count = scopePoints.size / 2
        if (count > 1) {
            val path = Path()
            for (i in 0 until count) {
                val sx = scopePoints[i * 2].coerceIn(-1f, 1f)
                val sy = scopePoints[i * 2 + 1].coerceIn(-1f, 1f)
                val px = cx + sx * radius * 0.92f
                val py = cy - sy * radius * 0.92f
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                drawCircle(
                    color = if (eqEnabled) {
                        if (sx < 0f) SteelChannel else ClayChannel
                    } else {
                        BoneMuted.copy(alpha = 0.6f)
                    },
                    radius = 1.8.dp.toPx(),
                    center = Offset(px, py)
                )
            }
            drawPath(
                path = path,
                color = if (eqEnabled) SageSignal else BoneMuted,
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
                alpha = if (eqEnabled) 0.9f else 0.45f
            )
        }
    }
}

@Composable
fun StereoScopeCard(
    scopePoints: FloatArray,
    stereoWidth: Float,
    eqEnabled: Boolean,
    onUpdateStereoWidth: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GraphitePanel)
            .border(1.dp, SeamLine, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STEREO FIELD & GONIOMETER",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SageSignal
                )
                Text(
                    text = "Visualisasi fase Mid-Side L/R real-time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoneSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Mono" to 0f, "100%" to 1f, "Wide" to 1.65f).forEach { (label, valWidth) ->
                    val selected = abs(stereoWidth - valWidth) < 0.1f
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) SageWell else GraphiteWell)
                            .border(1.dp, SeamLine, RoundedCornerShape(10.dp))
                            .clickable { onUpdateStereoWidth(valWidth) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("stereo_width_preset_${label.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) SageSignal else BoneSecondary
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(142.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GraphiteWell)
                    .border(1.dp, SeamLine, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                StereoGoniometer(
                    scopePoints = scopePoints,
                    stereoWidth = stereoWidth,
                    eqEnabled = eqEnabled,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "L",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SteelChannel,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                Text(
                    text = "R",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ClayChannel,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            RotaryKnob(
                title = "Stereo Width",
                subtitle = "Matriks Mid-Side",
                valueNormalized = (stereoWidth / 2f).coerceIn(0f, 1f),
                displayValue = "${(stereoWidth * 100).roundToInt()}%",
                enabled = true,
                masterEnabled = eqEnabled,
                accentColor = SageSignal,
                testTagPrefix = "stereo_width",
                onValueChange = { norm -> onUpdateStereoWidth(norm * 2f) },
                onToggleEnabled = {
                    onUpdateStereoWidth(if (stereoWidth > 0.05f) 0f else 1.15f)
                },
                onReset = { onUpdateStereoWidth(1.0f) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
