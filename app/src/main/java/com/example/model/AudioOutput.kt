package com.example.model

data class OutputDevice(val key: String, val label: String, val kind: String, val stableIdentity: Boolean)
data class OutputState(
    val available: List<OutputDevice> = emptyList(),
    val route: OutputDevice? = null,
    val routeDescription: String = "Rute belum diketahui; pilih profil manual.",
    val musicActive: Boolean = false
) {
    val autoProfileKey: String? get() = route?.takeIf { it.stableIdentity }?.key
}

data class OutputProfile(val deviceKey: String, val deviceLabel: String, val presetId: String)

fun SoundSnapshot.estimatedCompensationDb(): Float {
    val peak = (left + right).maxOrNull()?.coerceAtLeast(0f) ?: 0f
    return -(peak + if (bassEnabled) bass * 6f else 0f).coerceIn(0f, 12f)
}
