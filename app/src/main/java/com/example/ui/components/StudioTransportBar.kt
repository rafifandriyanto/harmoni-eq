package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AudioSourceMode
import com.example.model.TestTrackStyle
import com.example.ui.theme.BrassCaution
import com.example.ui.theme.SeamLine
import com.example.ui.theme.GraphitePanel
import com.example.ui.theme.GraphiteWell
import com.example.ui.theme.MadderAlert
import com.example.ui.theme.MadderWell
import com.example.ui.theme.ClayChannel
import com.example.ui.theme.BrassWell
import com.example.ui.theme.SteelWell
import com.example.ui.theme.SageWell
import com.example.ui.theme.SteelChannel
import com.example.ui.theme.SageSignal
import com.example.ui.theme.GraphiteBase
import com.example.ui.theme.GridLine
import com.example.ui.theme.BoneMuted
import com.example.ui.theme.BonePrimary
import com.example.ui.theme.BoneSecondary

@Composable
fun StudioTransportBar(
    audioSourceMode: AudioSourceMode,
    isTestAudioPlaying: Boolean,
    testTrackStyle: TestTrackStyle,
    vuLevels: Pair<Float, Float>,
    stereoScopePoints: FloatArray,
    stereoWidth: Float,
    eqEnabled: Boolean,
    backgroundServiceActive: Boolean,
    detectedOutputDevice: String,
    detectedSessions: List<String>,
    onSwitchMode: (AudioSourceMode) -> Unit,
    onTogglePlay: () -> Unit,
    onCycleTrackStyle: () -> Unit,
    onToggleBackgroundService: () -> Unit,
    onLaunchYouTube: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GraphitePanel)
            .border(1.dp, SeamLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Mode switcher + Mini Stereo Scope + Dual L/R VU Meter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GraphiteWell)
                    .border(1.dp, SeamLine, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                AudioSourceMode.entries.forEach { mode ->
                    val selected = mode == audioSourceMode
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) SageWell else Color.Transparent)
                            .clickable { onSwitchMode(mode) }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .testTag("mode_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (mode == AudioSourceMode.STUDIO_TEST) Icons.Rounded.GraphicEq else Icons.Rounded.Speaker,
                            contentDescription = mode.label,
                            tint = if (selected) SageSignal else BoneMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (mode == AudioSourceMode.GLOBAL_SYSTEM) "YouTube / All" else "Uji Audio",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) SageSignal else BoneMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GraphiteWell)
                        .border(1.dp, SeamLine, RoundedCornerShape(8.dp))
                        .padding(3.dp)
                ) {
                    StereoGoniometer(
                        scopePoints = stereoScopePoints,
                        stereoWidth = stereoWidth,
                        eqEnabled = eqEnabled,
                        modifier = Modifier.size(26.dp)
                    )
                }

                StereoVuMeter(
                    leftLevel = vuLevels.first,
                    rightLevel = vuLevels.second,
                    modifier = Modifier.width(80.dp)
                )
            }
        }

        if (audioSourceMode == AudioSourceMode.GLOBAL_SYSTEM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GraphiteWell)
                    .border(1.dp, SeamLine, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (backgroundServiceActive) SageSignal else BrassCaution)
                        )
                        Text(
                            text = if (backgroundServiceActive) "Layanan Latar Aktif" else "Aktifkan Layanan Latar",
                            style = MaterialTheme.typography.titleSmall,
                            color = BonePrimary,
                            maxLines = 1
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = "Perangkat Output",
                            tint = SteelChannel,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (detectedSessions.isNotEmpty()) {
                                "$detectedOutputDevice | Sesi: ${detectedSessions.joinToString(", ")}"
                            } else {
                                "Output: $detectedOutputDevice"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = BoneSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (backgroundServiceActive) SageWell else GraphitePanel)
                            .border(1.dp, SeamLine, RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleBackgroundService)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("toggle_background_service_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (backgroundServiceActive) "Latar ON" else "Aktifkan",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (backgroundServiceActive) SageSignal else BoneMuted
                        )
                    }

                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MadderWell)
                            .border(1.dp, SeamLine, RoundedCornerShape(10.dp))
                            .clickable(onClick = onLaunchYouTube)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("launch_youtube_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = "Buka YouTube",
                            tint = MadderAlert,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "YouTube",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MadderAlert
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GraphiteWell)
                    .border(1.dp, SeamLine, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isTestAudioPlaying) SageSignal else SteelWell)
                            .border(1.dp, SeamLine, CircleShape)
                            .clickable(onClick = onTogglePlay)
                            .testTag("play_test_audio_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTestAudioPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isTestAudioPlaying) "Jeda audio uji" else "Putar audio uji",
                            tint = if (isTestAudioPlaying) GraphiteBase else SteelChannel,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isTestAudioPlaying) "Memutar 20-Band Stereo" else "Putar Audio Uji Stereo",
                            style = MaterialTheme.typography.titleSmall,
                            color = BonePrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Biquad L/R + Stereo Width ($detectedOutputDevice)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BoneSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrassWell)
                        .border(1.dp, SeamLine, RoundedCornerShape(10.dp))
                        .clickable(onClick = onCycleTrackStyle)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("cycle_track_style_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Ganti gaya audio uji",
                        tint = BrassCaution,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = testTrackStyle.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = BrassCaution,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun StereoVuMeter(
    leftLevel: Float,
    rightLevel: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VuChannelRow(channel = "L", level = leftLevel, baseColor = SteelChannel)
        VuChannelRow(channel = "R", level = rightLevel, baseColor = ClayChannel)
    }
}

@Composable
private fun VuChannelRow(
    channel: String,
    level: Float,
    baseColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = channel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = baseColor
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
        ) {
            val segments = 12
            val gap = 1.8.dp.toPx()
            val segW = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(2f)
            val litCount = (level.coerceIn(0f, 1f) * segments).toInt()
            for (i in 0 until segments) {
                val x = i * (segW + gap)
                val lit = i < litCount
                val segColor = when {
                    !lit -> GridLine
                    i >= 10 -> MadderAlert
                    i >= 7 -> BrassCaution
                    else -> SageSignal
                }
                drawRoundRect(
                    color = segColor,
                    topLeft = Offset(x, 0f),
                    size = Size(segW, size.height),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}
