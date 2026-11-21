package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.glass.GlassDialog
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalHarmoniPalette
import java.util.Locale

fun dbLabel(value: Float): String = String.format(Locale.US, "%+.1f dB", value)

/**
 * Kartu konten solid / near-solid (BUKAN glass).
 */
@Composable
fun ConsolePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalHarmoniPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassTokens.CornerRadius),
        color = palette.surface,
        border = BorderStroke(GlassTokens.BorderWidth, palette.borderSubtle)
    ) {
        Column(
            Modifier.padding(GlassTokens.Space16),
            verticalArrangement = Arrangement.spacedBy(GlassTokens.Space12),
            content = content
        )
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val palette = LocalHarmoniPalette.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GlassTokens.Space4)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary) }
    }
}

@Composable
fun ValueSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    tag: String,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
    onStart: () -> Unit = {},
    onFinish: () -> Unit = {},
    onReset: (() -> Unit)? = null
) {
    val palette = LocalHarmoniPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            Text(
                label,
                color = if (enabled) palette.accent else palette.textMuted,
                style = MaterialTheme.typography.labelMedium
            )
            if (onReset != null) TextButton(onClick = onReset, enabled = enabled) { Text("Reset") }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onStart(); onChange(it) },
            onValueChangeFinished = onFinish,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(tag)
                .semantics { contentDescription = title }
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    tag: String = title
) {
    val palette = LocalHarmoniPalette.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space12)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            modifier = Modifier
                .testTag(tag)
                .semantics { contentDescription = title }
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    GlassDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()); onDismiss() }, enabled = name.isNotBlank()) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text("Nama preset") },
            singleLine = true,
            supportingText = { Text("Menyimpan 20 band kiri/kanan dan semua pengaturan efek.") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
