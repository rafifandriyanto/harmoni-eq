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
import com.example.model.AudioSourceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EqualizerService : Service() {

    companion object {
        const val CHANNEL_ID = "harmoni_eq_dsp_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_EQ_SERVICE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        fun start(context: Context) {
            _error.value = null
            runCatching { ContextCompat.startForegroundService(context, Intent(context, EqualizerService::class.java)) }
                .onFailure { _error.value = "Layanan gagal dimulai. Buka aplikasi lalu coba lagi: ${it.javaClass.simpleName}" }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, EqualizerService::class.java))
            }
        }
    }

    private var sessionReceiverRegistered = false

    private val audioSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    if (sessionId > 0) {
                        StudioAudioEngine.registerExternalSession(sessionId, packageName)
                        updateNotification(packageName)
                    }
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    if (sessionId > 0) {
                        StudioAudioEngine.unregisterExternalSession(sessionId)
                        updateNotification(null)
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
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(detectedApp = null)
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
            _isRunning.value = true
            StudioAudioEngine.reattachCurrentEffects()
        }.onFailure {
            _isRunning.value = false
            _error.value = "Android menolak layanan audio: ${it.javaClass.simpleName}. Buka aplikasi lalu coba lagi."
            stopSelf()
        }
        // A cold sticky restart must not apply unsaved/default sound settings.
        return START_NOT_STICKY
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Harmoni EQ Latar Belakang",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Memantau sesi audio yang kompatibel; pemrosesan bergantung perangkat dan pemutar"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(detectedPackage: String?) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val appLabel = when {
            detectedPackage?.contains("youtube", ignoreCase = true) == true -> "YouTube"
            detectedPackage?.contains("spotify", ignoreCase = true) == true -> "Spotify"
            !detectedPackage.isNullOrBlank() -> detectedPackage.substringAfterLast('.')
            else -> null
        }
        nm.notify(NOTIFICATION_ID, buildNotification(appLabel))
    }

    private fun buildNotification(detectedApp: String?): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EqualizerService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (detectedApp != null) {
            "Sesi $detectedApp terdeteksi • cek efek di konsol"
        } else {
            "Pemantau sesi audio berjalan • kompatibilitas bergantung pemutar"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Layanan Harmoni EQ")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Buka Konsol", openAppIntent)
            .addAction(0, "Nonaktifkan", stopIntent)
            .build()
    }

    override fun onDestroy() {
        _isRunning.value = false
        if (sessionReceiverRegistered) {
            runCatching { unregisterReceiver(audioSessionReceiver) }
            sessionReceiverRegistered = false
        }
        StudioAudioEngine.releaseGlobalAndExternal()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
