package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground keep-alive service wrapping [StudioAudioEngine] so aggressive Android OEM
 * battery managers do not terminate DSP processing while music plays in the background.
 */
class AudioForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "harmoni_eq_keep_alive_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_STOP_SERVICE = "com.example.action.AUDIO_FGS_STOP"
        const val ACTION_PAUSE_RESUME_EQ = "com.example.action.AUDIO_FGS_PAUSE_RESUME"
        const val ACTION_REFRESH_STATUS = "com.example.action.AUDIO_FGS_REFRESH"
        const val ACTION_MEDIA_SESSION_ACTIVE = "com.example.action.AUDIO_FGS_MEDIA_ACTIVE"
        const val EXTRA_MEDIA_APP_LABEL = "extra_media_app_label"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _activeMediaApp = MutableStateFlow<String?>(null)
        val activeMediaApp: StateFlow<String?> = _activeMediaApp.asStateFlow()

        fun start(context: Context) {
            _error.value = null
            runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, AudioForegroundService::class.java)
                )
            }.onFailure { err ->
                _error.value = "Could not start audio keep-alive service: ${err.javaClass.simpleName}"
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, AudioForegroundService::class.java)
                )
            }
        }

        fun notifyStatusChanged(context: Context) {
            if (!_isRunning.value) return
            runCatching {
                context.applicationContext.startService(
                    Intent(context.applicationContext, AudioForegroundService::class.java).apply {
                        action = ACTION_REFRESH_STATUS
                    }
                )
            }
        }

        /**
         * Called by [MediaSessionNotificationListenerService] when another media app starts playing.
         * Keeps the audio session attached and updates the persistent notification subtitle.
         */
        fun onActiveMediaDetected(context: Context, appLabel: String?) {
            _activeMediaApp.value = appLabel
            if (!EqPowerBridge.eqEnabled.value) return
            val intent = Intent(context.applicationContext, AudioForegroundService::class.java).apply {
                action = ACTION_MEDIA_SESSION_ACTIVE
                putExtra(EXTRA_MEDIA_APP_LABEL, appLabel)
            }
            if (_isRunning.value) {
                runCatching { context.applicationContext.startService(intent) }
            } else {
                runCatching { ContextCompat.startForegroundService(context.applicationContext, intent) }
            }
        }

        internal fun formatAppLabel(packageName: String?): String? = when {
            packageName.isNullOrBlank() -> null
            packageName.contains("spotify", ignoreCase = true) -> "Spotify"
            packageName.contains("youtube.music", ignoreCase = true) ||
                packageName.contains("apps.youtube.music", ignoreCase = true) -> "YouTube Music"
            packageName.contains("youtube", ignoreCase = true) -> "YouTube"
            packageName.contains("apple.android.music", ignoreCase = true) -> "Apple Music"
            packageName.contains("tidal", ignoreCase = true) -> "TIDAL"
            packageName.contains("soundcloud", ignoreCase = true) -> "SoundCloud"
            packageName.contains("poweramp", ignoreCase = true) -> "Poweramp"
            packageName.contains("vlc", ignoreCase = true) -> "VLC"
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private var sessionReceiverRegistered = false
    private var lastDetectedApp: String? = null
    private var promotedToForeground = false

    private val audioSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    if (sessionId > 0) {
                        StudioAudioEngine.registerExternalSession(sessionId, packageName)
                        updateDetectedApp(formatAppLabel(packageName))
                    }
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    if (sessionId > 0) {
                        StudioAudioEngine.unregisterExternalSession(sessionId)
                        updateDetectedApp(null)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        StudioAudioEngine.bindContext(applicationContext)
        createNotificationChannel()
        registerSessionReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_RESUME_EQ -> {
                val nextEnabled = !EqPowerBridge.eqEnabled.value
                EqPowerBridge.dispatchExternalToggle(applicationContext, nextEnabled)
                ensureForegroundOrNotify()
                return START_STICKY
            }
            ACTION_REFRESH_STATUS -> {
                ensureForegroundOrNotify()
                return START_STICKY
            }
            ACTION_MEDIA_SESSION_ACTIVE -> {
                lastDetectedApp = intent.getStringExtra(EXTRA_MEDIA_APP_LABEL) ?: lastDetectedApp
                _activeMediaApp.value = lastDetectedApp
                promoteToForegroundAndAttach()
                return START_STICKY
            }
        }

        // Immediately invoke startForeground() well within Android's 5-second FGS timeout window.
        promoteToForegroundAndAttach()
        return START_STICKY
    }

    private fun promoteToForegroundAndAttach() {
        val notification = buildNotification(detectedApp = lastDetectedApp ?: _activeMediaApp.value)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onSuccess {
            promotedToForeground = true
            _isRunning.value = true
            _error.value = null
            EqualizerService.syncExternalRunningState(true)
            StudioAudioEngine.reattachCurrentEffects()
        }.onFailure { err ->
            promotedToForeground = false
            _isRunning.value = false
            EqualizerService.syncExternalRunningState(false)
            _error.value = "Android denied foreground audio service: ${err.javaClass.simpleName}. Open Harmoni EQ and try again."
            stopSelf()
        }
    }

    private fun ensureForegroundOrNotify() {
        if (!promotedToForeground) {
            promoteToForegroundAndAttach()
        } else {
            refreshNotificationOnly()
        }
    }

    private fun registerSessionReceiver() {
        if (sessionReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                audioSessionReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            sessionReceiverRegistered = true
        }
    }

    internal fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fgs_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun updateDetectedApp(appLabel: String?) {
        lastDetectedApp = appLabel
        _activeMediaApp.value = appLabel
        refreshNotificationOnly()
    }

    private fun refreshNotificationOnly() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(lastDetectedApp ?: _activeMediaApp.value))
    }

    internal fun buildNotification(detectedApp: String?): Notification {
        val eqActive = EqPowerBridge.eqEnabled.value
        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumeIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, AudioForegroundService::class.java).apply {
                action = ACTION_PAUSE_RESUME_EQ
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            12,
            Intent(this, AudioForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (eqActive) {
            getString(R.string.fgs_notification_title_running)
        } else {
            getString(R.string.fgs_notification_title_paused)
        }
        val subtitle = buildString {
            append(if (eqActive) "EQ Active" else "EQ Paused")
            if (!detectedApp.isNullOrBlank()) {
                append(" • ")
                append(detectedApp)
            } else {
                val preset = EqPowerBridge.activePresetName.value
                if (preset.isNotBlank()) {
                    append(" • ")
                    append(preset)
                }
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_eq)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent)
            .addAction(
                0,
                if (eqActive) getString(R.string.fgs_action_pause) else getString(R.string.fgs_action_resume),
                pauseResumeIntent
            )
            .addAction(0, getString(R.string.fgs_action_stop), stopIntent)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        promotedToForeground = false
        _isRunning.value = false
        _activeMediaApp.value = null
        EqualizerService.syncExternalRunningState(false)
        if (sessionReceiverRegistered) {
            runCatching { unregisterReceiver(audioSessionReceiver) }
            sessionReceiverRegistered = false
        }
        StudioAudioEngine.releaseGlobalAndExternal()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
