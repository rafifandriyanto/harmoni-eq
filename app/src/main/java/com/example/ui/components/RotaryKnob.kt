package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SeamLine
import com.example.ui.theme.GraphitePanel
import com.example.ui.theme.GraphiteWell
import com.example.ui.theme.GraphiteRaised
import com.example.ui.theme.GraphiteBase
import com.example.ui.theme.GridLine
import com.example.ui.theme.BoneMuted
import com.example.ui.theme.BonePrimary
import com.example.ui.theme.BoneSecondary
import com.example.ui.theme.SageSignal
import com.example.ui.theme.SageWell
import com.example.ui.theme.SteelChannel
import com.example.ui.theme.SteelWell
import com.example.ui.theme.ClayChannel
import com.example.ui.theme.ClayWell
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotaryKnob(
    title: String,
    subtitle: String,
    valueNormalized: Float,
    displayValue: String,
    enabled: Boolean,
    masterEnabled: Boolean,
    accentColor: Color,
    testTagPrefix: String,
    onValueChange: (Float) -> Unit,
    onToggleEnabled: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = enabled && masterEnabled
    val currentVal by rememberUpdatedState(valueNormalized)
    val onChange by rememberUpdatedState(onValueChange)
    val onDoubleTapReset by rememberUpdatedState(onReset)

    val animatedVal by animateFloatAsState(
        targetValue = valueNormalized.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "knob_$testTagPrefix"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GraphitePanel)
            .border(1.dp, SeamLine, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = BonePrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoneSecondary
                )
            }

            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) when (accentColor) {
                        SageSignal -> SageWell
                        SteelChannel -> SteelWell
                        ClayChannel -> ClayWell
                        else -> GraphiteWell
                    } else GraphiteWell)
                    .border(1.dp, SeamLine, RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleEnabled)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("${testTagPrefix}_toggle"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (active) accentColor else BoneMuted)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (active) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (active) accentColor else BoneMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .testTag("${testTagPrefix}_knob")
                .semantics {
                    contentDescription = "Knob putar $title nilai saat ini $displayValue"
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDoubleTapReset() })
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount / 240f
                        onChange((currentVal + delta).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val diameter = size.minDimension
                val center = Offset(size.width / 2f, size.height / 2f)
                val arcRadius = diameter * 0.42f
                val startAngle = 135f
                val sweepMax = 270f

                val ticks = 15
                for (i in 0..ticks) {
                    val angleDeg = startAngle + (sweepMax * i / ticks)
                    val angleRad = angleDeg * (PI / 180.0)
                    val innerR = arcRadius + 4.dp.toPx()
                    val outerR = arcRadius + if (i % 5 == 0) 8.dp.toPx() else 5.dp.toPx()
                    val tickActive = (i.toFloat() / ticks) <= animatedVal && active
                    drawLine(
                        color = if (tickActive) accentColor else GridLine,
                        start = Offset(
                            center.x + (cos(angleRad) * innerR).toFloat(),
                            center.y + (sin(angleRad) * innerR).toFloat()
                        ),
                        end = Offset(
                            center.x + (cos(angleRad) * outerR).toFloat(),
                            center.y + (sin(angleRad) * outerR).toFloat()
                        ),
                        strokeWidth = if (i % 5 == 0) 2.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                drawArc(
                    color = GraphiteWell,
                    startAngle = startAngle,
                    sweepAngle = sweepMax,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )

                val activeSweep = sweepMax * animatedVal
                if (activeSweep > 1f) {
                    drawArc(
                        color = if (active) accentColor else BoneMuted,
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Machined Dial Body
                val knobRadius = diameter * 0.30f
                drawCircle(
                    color = GraphiteRaised,
                    radius = knobRadius,
                    center = center
                )
                drawCircle(
                    color = GraphiteBase,
                    radius = knobRadius * 0.84f,
                    center = center
                )
                drawCircle(
                    color = SeamLine,
                    radius = knobRadius,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                val pointerAngleRad = (startAngle + activeSweep) * (PI / 180.0)
                val notchStart = knobRadius * 0.44f
                val notchEnd = knobRadius * 0.82f
                drawLine(
                    color = if (active) accentColor else BoneMuted,
                    start = Offset(
                        center.x + (cos(pointerAngleRad) * notchStart).toFloat(),
                        center.y + (sin(pointerAngleRad) * notchStart).toFloat()
                    ),
                    end = Offset(
                        center.x + (cos(pointerAngleRad) * notchEnd).toFloat(),
                        center.y + (sin(pointerAngleRad) * notchEnd).toFloat()
                    ),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (active) accentColor else BoneMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Seret atas/bawah | Ketuk 2x reset",
            style = MaterialTheme.typography.labelSmall,
            color = BoneMuted
        )
    }
}
