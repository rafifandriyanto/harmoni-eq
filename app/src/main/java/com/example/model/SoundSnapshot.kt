package com.example.model

/** Sound only: master bypass, transport, output and UI navigation are never preset data. */
data class SoundSnapshot(
    val left: List<Float> = List(20) { 0f },
    val right: List<Float> = left,
    val linked: Boolean = true,
    val bassEnabled: Boolean = false,
    val bass: Float = 0f,
    val surroundEnabled: Boolean = false,
    val surround: Float = 0f,
    val width: Float = 1f,
    val balance: Float = 0f,
    val preampEnabled: Boolean = true,
    val preamp: Float = 0f,
    val limiter: Boolean = true,
    val reverb: ReverbRoomPreset = ReverbRoomPreset.NONE,
    val autoHeadroom: Boolean = false
)

fun EqualizerUiState.soundSnapshot() = SoundSnapshot(
    left = bands.map { it.levelDb }, right = bands.map { it.rightLevelDb },
    linked = stereoLinked, bassEnabled = bassBoostEnabled, bass = bassBoostStrength,
    surroundEnabled = virtualizerEnabled, surround = virtualizerStrength,
    width = stereoWidth, balance = stereoBalance, preampEnabled = preampEnabled,
    preamp = preampGainDb, limiter = limiterEnabled, reverb = reverbPreset, autoHeadroom = autoHeadroom
)

fun EqualizerUiState.withSound(sound: SoundSnapshot): EqualizerUiState = copy(
    bands = bands.mapIndexed { i, b -> b.copy(levelDb = sound.left[i], rightLevelDb = sound.right[i]) },
    stereoLinked = sound.linked,
    activeChannelFocus = if (sound.linked) StereoChannelFocus.LINKED else StereoChannelFocus.LEFT,
    bassBoostEnabled = sound.bassEnabled, bassBoostStrength = sound.bass,
    virtualizerEnabled = sound.surroundEnabled, virtualizerStrength = sound.surround,
    stereoWidth = sound.width, stereoBalance = sound.balance,
    preampEnabled = sound.preampEnabled, preampGainDb = sound.preamp,
    limiterEnabled = sound.limiter, reverbPreset = sound.reverb, autoHeadroom = sound.autoHeadroom
)

fun EqPreset.soundSnapshot(): SoundSnapshot = sound ?: SoundSnapshot(
    left = bandGainsDb, right = bandGainsDb,
    bassEnabled = bassBoost > 0f, bass = bassBoost,
    surroundEnabled = virtualizer > 0f, surround = virtualizer, width = stereoWidth
)
