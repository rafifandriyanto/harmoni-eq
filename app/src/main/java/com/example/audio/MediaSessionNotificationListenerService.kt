package com.example.audio

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens for active media-session notifications (Spotify, YouTube Music, YouTube, etc.)
 * so Harmoni EQ knows when external playback starts and can keep its audio session attached.
 */
class MediaSessionNotificationListenerService : NotificationListenerService() {

    companion object {
        private val KNOWN_MEDIA_PACKAGES = setOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.youtube",
            "com.apple.android.music",
            "com.aspiro.tidal",
            "com.soundcloud.android",
            "com.maxmpz.audioplayer",
            "org.videolan.vlc",
            "com.amazon.mp3",
            "deezer.android.app",
            "com.pandora.android",
            "com.bandcamp.android"
        )

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _activeMediaPackages = MutableStateFlow<Set<String>>(emptySet())
        val activeMediaPackages: StateFlow<Set<String>> = _activeMediaPackages.asStateFlow()

        private val _activeMediaLabel = MutableStateFlow<String?>(null)
        val activeMediaLabel: StateFlow<String?> = _activeMediaLabel.asStateFlow()

        internal fun isMediaNotification(sbn: StatusBarNotification?): Boolean {
            if (sbn == null) return false
            val pkg = sbn.packageName ?: return false
            val notification = sbn.notification ?: return false
            val extras = notification.extras
            val hasMediaSession = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
            val isTransport = notification.category == Notification.CATEGORY_TRANSPORT
            return hasMediaSession || isTransport || pkg in KNOWN_MEDIA_PACKAGES
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        refreshActiveMediaSessions()
    }

    override fun onListenerDisconnected() {
        _isConnected.value = false
        _activeMediaPackages.value = emptySet()
        _activeMediaLabel.value = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        if (isMediaNotification(sbn)) {
            val pkg = sbn.packageName
            _activeMediaPackages.value = _activeMediaPackages.value + pkg
            val label = AudioForegroundService.formatAppLabel(pkg)
            _activeMediaLabel.value = label
            AudioForegroundService.onActiveMediaDetected(applicationContext, label)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        if (sbn.packageName in _activeMediaPackages.value) {
            refreshActiveMediaSessions()
        }
    }

    private fun refreshActiveMediaSessions() {
        val detected = runCatching {
            activeNotifications
                ?.filter { it.packageName != packageName && isMediaNotification(it) }
                ?.mapNotNull { it.packageName }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())

        _activeMediaPackages.value = detected
        val primaryLabel = detected.firstOrNull()?.let { AudioForegroundService.formatAppLabel(it) }
        _activeMediaLabel.value = primaryLabel
        if (primaryLabel != null && EqPowerBridge.eqEnabled.value) {
            AudioForegroundService.onActiveMediaDetected(applicationContext, primaryLabel)
        }
    }
}
