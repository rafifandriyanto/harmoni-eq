package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import java.util.Locale

fun dbLabel(value: Float): String = String.format(Locale.US, "%+.1f dB", value)

@Composable
fun ConsolePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = GraphitePanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, SeamLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = BonePrimary)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = BoneSecondary) }
}

@Composable
fun ValueSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>,
    label: String, tag: String, enabled: Boolean = true, onChange: (Float) -> Unit,
    onStart: () -> Unit = {}, onFinish: () -> Unit = {}, onReset: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = BonePrimary)
            Text(label, color = if (enabled) SageSignal else BoneMuted, style = MaterialTheme.typography.labelLarge)
            if (onReset != null) TextButton(onClick = onReset, enabled = enabled) { Text("Reset") }
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = { onStart(); onChange(it) },
            onValueChangeFinished = onFinish, valueRange = range, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).semantics { contentDescription = title })
    }
}

@Composable
fun ToggleRow(title: String, description: String, checked: Boolean, onToggle: () -> Unit,
    enabled: Boolean = true, tag: String = title) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = BonePrimary)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = BoneMuted)
        }
        Switch(checked, { onToggle() }, enabled = enabled, modifier = Modifier.testTag(tag).semantics { contentDescription = title })
    }
}

@Composable
fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nama preset") }, singleLine = true,
            supportingText = { Text("Menyimpan 20 band kiri/kanan dan semua pengaturan efek.") })
    }, confirmButton = { TextButton(onClick = { onSave(name.trim()); onDismiss() }, enabled = name.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}
