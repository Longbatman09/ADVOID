package com.example.admute.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import com.example.admute.detection.AdKeywordRules
import com.example.admute.detection.WhitelistedApps
import com.example.admute.settings.AdLogEntry
import com.example.admute.settings.AdMuteSettings
import com.example.admute.settings.NowPlayingInfo
import com.example.admute.settings.NotificationSoundConfig
import com.example.admute.settings.NotificationSoundSource
import java.io.File
import java.io.FileOutputStream

class AdNotificationListenerService : NotificationListenerService() {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var trigger = false
    private var lastMediaApp: String? = null
    private var restoreVolumeTarget = 0
    private var cooldownEndsAtMs = 0L
    private val activeAdNotificationKeys = mutableSetOf<String>()
    private var currentFadeRunnable: Runnable? = null
    private var isMediaRefreshRunning = false
    private var lastPublishedMediaSignature: String? = null
    private var lastAlbumArtFingerprint: String? = null
    private var cachedAlbumArtPath: String? = null

    // Muting sequence state: 0=normal, 1=muted, 2=in sound, 3=out sound
    private var muteSequenceState = 0

    private val mediaRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isMediaRefreshRunning) return
            refreshNowPlayingState()
            if (!isMediaRefreshRunning) return
            mainHandler.postDelayed(this, MEDIA_REFRESH_INTERVAL_MS)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        if (!WhitelistedApps.contains(this, packageName)) return

        val notification = sbn.notification ?: return
        val notificationKey = buildNotificationKey(sbn)
        val content = extractNotificationText(notification)
        val isAd = content.isNotBlank() && AdKeywordRules.matches(content)

        when {
            isAd -> {
                if (isCooldownActive()) {
                    Log.i(TAG, "Cooldown active, skipping mute for $packageName")
                } else if (trigger && lastMediaApp == packageName) {
                    // Already in mute cycle from same app, advance to next state
                    startMuteCycle(packageName, content, notificationKey)
                } else if (!trigger) {
                    // Start new mute cycle
                    startMuteCycle(packageName, content, notificationKey)
                } else {
                    // Ad from different app while in cycle, ignore
                    Log.i(TAG, "Ad from different app, ignoring: $packageName")
                }
            }
            trigger && lastMediaApp == packageName -> {
                // Non-ad notification from same app while in cycle, check if we should end
                if (activeAdNotificationKeys.remove(notificationKey) && activeAdNotificationKeys.isEmpty()) {
                    endMuteCycle("ad notification content removed")
                }
            }
        }

        refreshNowPlayingState()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (trigger) {
            val packageName = sbn.packageName
            if (packageName == lastMediaApp) {
                val removedKey = buildNotificationKey(sbn)
                if (activeAdNotificationKeys.remove(removedKey) && activeAdNotificationKeys.isEmpty()) {
                    endMuteCycle("ad notification removed")
                }
            }
        }

        refreshNowPlayingState()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        startMediaMonitoring()
        refreshNowPlayingState()
    }

    override fun onListenerDisconnected() {
        stopMediaMonitoring()
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
    }

    override fun onDestroy() {
        stopMediaMonitoring()
        super.onDestroy()
    }

    private fun startMuteCycle(packageName: String, content: String, notificationKey: String) {
        when (muteSequenceState) {
            0 -> {
                // State 0 → State 1: Mute only, no sounds
                if (currentFadeRunnable == null) {
                    restoreVolumeTarget = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
                fadeVolume(0, durationMs = 400L)
                muteSequenceState = 1
                Log.i(TAG, "Muting sequence State 1: Muted only")
                showToast("State 1: Muted")
            }
            1 -> {
                // State 1 → State 2: Play in sound (stay muted)
                playConfiguredSound(isStart = true)
                muteSequenceState = 2
                Log.i(TAG, "Muting sequence State 2: Playing in sound")
                showToast("State 2: Playing in sound")
            }
            2 -> {
                // State 2 → State 3: Play out sound (stay muted)
                playConfiguredSound(isStart = false)
                muteSequenceState = 3
                Log.i(TAG, "Muting sequence State 3: Playing out sound")
                showToast("State 3: Playing out sound")
            }
            3 -> {
                // State 3 → State 0: Unmute
                val restoreVolume = resolveRestoreVolume()
                fadeVolume(restoreVolume, durationMs = 700L)
                muteSequenceState = 0
                activeAdNotificationKeys.clear()
                cooldownEndsAtMs = System.currentTimeMillis() + AdMuteSettings.getCooldownMs(this)
                Log.i(TAG, "Muting sequence State 0: Unmuted. Restored volume to $restoreVolume")
                showToast("State 4: Unmuted")
                trigger = false
                lastMediaApp = null
            }
        }

        // Only update tracking on first trigger
        if (muteSequenceState == 1) {
            trigger = true
            lastMediaApp = packageName
            activeAdNotificationKeys.clear()
            activeAdNotificationKeys.add(notificationKey)
            Log.i(TAG, "Ad detected from $packageName: $content")
            logAdDetection(packageName, content)
        }
    }

    private fun logAdDetection(packageName: String, content: String) {
        val appName = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)

        val lastLogs = AdMuteSettings.getAdLogs(this)
        val lastEntry = lastLogs.firstOrNull()
        
        // Avoid duplicate logs for same content within 2 seconds
        if (lastEntry != null && 
            lastEntry.packageName == packageName && 
            lastEntry.content == content && 
            System.currentTimeMillis() - lastEntry.timestamp < 2000) {
            return
        }

        AdMuteSettings.addAdLog(
            context = this,
            entry = AdLogEntry(
                appName = appName,
                packageName = packageName,
                timestamp = System.currentTimeMillis(),
                content = content
            )
        )
    }

    private fun endMuteCycle(reason: String) {
        val now = System.currentTimeMillis()
        AdMuteSettings.updateLastLogEndTime(this, now)

        val restoreVolume = resolveRestoreVolume()
        fadeVolume(restoreVolume, durationMs = 700L)

        val previousApp = lastMediaApp
        trigger = false
        lastMediaApp = null
        activeAdNotificationKeys.clear()
        muteSequenceState = 0  // Reset to initial state
        cooldownEndsAtMs = System.currentTimeMillis() + AdMuteSettings.getCooldownMs(this)

        Log.i(TAG, "Mute cycle ended for $previousApp ($reason). Restored volume to $restoreVolume")
        showToast("Ad ended. Volume restored")
    }

    private fun enforceMuteWhileActive() {
        if (currentFadeRunnable != null) return

        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVolume != 0) {
            // Capture the latest user intent before forcing mute back to zero.
            restoreVolumeTarget = currentVolume
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            showToast("Volume change saved. ADMUTE will restore latest level")
        }
    }

    private fun resolveRestoreVolume(): Int {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (currentVolume > 0) currentVolume else restoreVolumeTarget.coerceAtLeast(0)
    }

    private fun isCooldownActive(): Boolean = System.currentTimeMillis() < cooldownEndsAtMs

    private fun startMediaMonitoring() {
        if (isMediaRefreshRunning) return
        isMediaRefreshRunning = true
        mainHandler.removeCallbacks(mediaRefreshRunnable)
        mainHandler.post(mediaRefreshRunnable)
    }

    private fun stopMediaMonitoring() {
        isMediaRefreshRunning = false
        mainHandler.removeCallbacks(mediaRefreshRunnable)
    }

    private fun refreshNowPlayingState() {
        val snapshot = findCurrentPlayingSnapshot()
        if (snapshot == null) {
            if (AdMuteSettings.getNowPlayingInfo(this) != null) {
                AdMuteSettings.clearNowPlayingInfo(this)
            }
            clearCachedAlbumArt()
            lastPublishedMediaSignature = null
            return
        }

        if (snapshot.signature == lastPublishedMediaSignature) return

        AdMuteSettings.saveNowPlayingInfo(this, snapshot.info)
        lastPublishedMediaSignature = snapshot.signature
    }

    private fun findCurrentPlayingSnapshot(): MediaSnapshot? {
        val activeNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getActiveNotifications()
        } else {
            emptyArray()
        }

        return activeNotifications
            .asSequence()
            .filter { WhitelistedApps.contains(this, it.packageName) }
            .sortedByDescending { it.postTime }
            .mapNotNull { sbn -> buildMediaSnapshot(sbn) }
            .firstOrNull()
    }

    private fun buildMediaSnapshot(sbn: StatusBarNotification): MediaSnapshot? {
        val notification = sbn.notification ?: return null
        val controller = extractMediaController(notification) ?: return null
        if (!isActiveMediaController(controller, notification)) return null

        val info = buildNowPlayingInfo(sbn.packageName, notification, controller)
        val signature = buildMediaSignature(sbn, info, controller)
        return MediaSnapshot(info = info, signature = signature)
    }

    private fun buildMediaSignature(
        sbn: StatusBarNotification,
        info: NowPlayingInfo,
        controller: MediaController
    ): String {
        val playbackState = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        return buildString {
            append(sbn.packageName)
            append('|')
            append(sbn.postTime)
            append('|')
            append(playbackState)
            append('|')
            append(info.appName)
            append('|')
            append(info.title)
            append('|')
            append(info.text)
            append('|')
            append(info.subText)
            append('|')
            append(info.albumArtPath.orEmpty())
        }
    }

    private fun isActiveMediaController(controller: MediaController, notification: Notification): Boolean {
        val playbackState = controller.playbackState?.state
        if (playbackState != null) {
            return playbackState.isPlaybackActive()
        }

        return extractMediaSessionToken(notification) != null &&
            ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                !notification.extras?.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank() ||
                !notification.extras?.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank())
    }

    private fun Int.isPlaybackActive(): Boolean {
        return when (this) {
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_REWINDING -> true

            else -> false
        }
    }

    private fun extractMediaController(notification: Notification): MediaController? {
        val token = extractMediaSessionToken(notification) ?: return null
        return runCatching { MediaController(this, token) }.getOrNull()
    }

    private fun extractMediaSessionToken(notification: Notification): MediaSession.Token? {
        val extras = notification.extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
    }

    private fun fadeVolume(targetVolume: Int, durationMs: Long) {
        currentFadeRunnable?.let { mainHandler.removeCallbacks(it) }

        val startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (startVolume == targetVolume) {
            currentFadeRunnable = null
            return
        }

        val diff = targetVolume - startVolume
        val steps = abs(diff)
        val stepDelay = durationMs / steps.coerceAtLeast(1)

        val runnable = object : Runnable {
            var currentStep = 1
            override fun run() {
                val volumeToSet = startVolume + (diff * currentStep / steps)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0)

                if (currentStep < steps) {
                    currentStep++
                    mainHandler.postDelayed(this, stepDelay)
                } else {
                    currentFadeRunnable = null
                }
            }
        }
        currentFadeRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun playConfiguredSound(isStart: Boolean) {
        val settings = AdMuteSettings.getNotificationSoundSettings(this)
        val config = if (isStart) settings.inSound else settings.outSound

        when (config.source) {
            NotificationSoundSource.OFF -> Unit
            NotificationSoundSource.STOCK -> playRawSound(config)
            NotificationSoundSource.CUSTOM -> playCustomSound(config)
        }
    }

    private fun playRawSound(config: NotificationSoundConfig) {
        runCatching {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val player = MediaPlayer.create(this, config.stockSound.resId, attributes, 0) ?: return
            // Ensure notification stream volume is audible
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) / 2, 0)
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                true
            }
            player.start()
        }.onFailure {
            Log.w(TAG, "Failed to play stock sound", it)
            playToneFallback()
        }
    }

    private fun playCustomSound(config: NotificationSoundConfig) {
        val uriText = config.customUri ?: return
        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(this@AdNotificationListenerService, android.net.Uri.parse(uriText))
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                // Ensure notification stream volume is audible
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) / 2, 0)
                setOnCompletionListener { it.release() }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    true
                }
                prepare()
                start()
            }
            player
        }.onFailure {
            Log.w(TAG, "Failed to play custom sound", it)
            playToneFallback()
        }
    }

    private fun playToneFallback() {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
            mainHandler.postDelayed({ generator.release() }, 250L)
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractNotificationText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val parts = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        )

        return parts
            .filterNotNull()
            .joinToString(separator = " ")
            .trim()
    }

    private fun buildNotificationKey(sbn: StatusBarNotification): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            sbn.key
        } else {
            "${sbn.packageName}:${sbn.id}:${sbn.tag.orEmpty()}"
        }
    }

    private fun buildNowPlayingInfo(
        packageName: String,
        notification: Notification,
        controller: MediaController
    ): NowPlayingInfo {
        val extras = notification.extras
        val metadata = controller.metadata
        val title = listOf(
            metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.toString(),
            metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString(),
            extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        ).firstNotNullOfOrNull { candidate -> candidate?.takeIf { it.isNotBlank() } }.orEmpty()

        val text = listOf(
            metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString(),
            metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM)?.toString(),
            extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        ).filterNotNull().joinToString(separator = " • ").ifBlank { "Unknown track info" }

        val subText = listOf(
            metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.toString(),
            extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        ).firstNotNullOfOrNull { candidate -> candidate?.takeIf { it.isNotBlank() } }.orEmpty()

        val albumArtPath = extractAndCacheAlbumArt(notification, controller)

        val appName = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)

        return NowPlayingInfo(
            appName = appName,
            packageName = packageName,
            title = title.ifBlank { "Unknown title" },
            text = text,
            subText = subText,
            albumArtPath = albumArtPath,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun extractAndCacheAlbumArt(notification: Notification, controller: MediaController?): String? {
        val bitmap = extractAlbumArtBitmap(notification, controller) ?: run {
            clearCachedAlbumArt()
            return null
        }
        val fingerprint = buildAlbumArtFingerprint(bitmap)
        val existingPath = cachedAlbumArtPath
        if (fingerprint == lastAlbumArtFingerprint && existingPath != null && File(existingPath).exists()) {
            return existingPath
        }
        val outputFile = File(cacheDir, "${NOW_PLAYING_ALBUM_ART_FILE_PREFIX}.jpg")

        return runCatching {
            FileOutputStream(outputFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            }
            lastAlbumArtFingerprint = fingerprint
            cachedAlbumArtPath = outputFile.absolutePath
            outputFile.absolutePath
        }.getOrNull()
    }

    private fun buildAlbumArtFingerprint(bitmap: Bitmap): String {
        return runCatching {
            val width = bitmap.width.coerceAtLeast(1)
            val height = bitmap.height.coerceAtLeast(1)
            val points = listOf(
                0f to 0f,
                0.5f to 0.5f,
                1f to 1f,
                0.25f to 0.75f,
                0.75f to 0.25f
            )
            val samples = points.joinToString(separator = ",") { (xFactor, yFactor) ->
                val x = ((width - 1) * xFactor).toInt().coerceIn(0, width - 1)
                val y = ((height - 1) * yFactor).toInt().coerceIn(0, height - 1)
                bitmap.getPixel(x, y).toString()
            }
            "$width:$height:$samples"
        }.getOrElse { "${bitmap.width}:${bitmap.height}:${bitmap.byteCount}" }
    }

    private fun clearCachedAlbumArt() {
        cachedAlbumArtPath
            ?.let(::File)
            ?.takeIf(File::exists)
            ?.delete()
        cachedAlbumArtPath = null
        lastAlbumArtFingerprint = null
    }

    private fun extractAlbumArtBitmap(notification: Notification, controller: MediaController?): Bitmap? {
        val fromMediaSession = extractAlbumArtFromMediaSession(controller)
        if (fromMediaSession != null) return fromMediaSession

        val extras = notification.extras
        val fromExtras = listOf(
            extras?.get(Notification.EXTRA_PICTURE),
            extras?.get(Notification.EXTRA_LARGE_ICON_BIG),
            extras?.get(Notification.EXTRA_LARGE_ICON)
        ).firstNotNullOfOrNull { candidate ->
            when (candidate) {
                is Bitmap -> candidate
                is Icon -> runCatching { candidate.loadDrawable(this)?.toBitmap() }.getOrNull()
                else -> null
            }
        }
        if (fromExtras != null) return fromExtras

        return notification.largeIcon
    }

    private fun extractAlbumArtFromMediaSession(controller: MediaController?): Bitmap? {
        val metadata = controller?.metadata ?: return null
        return runCatching {
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }.getOrNull()
    }

    private data class MediaSnapshot(
        val info: NowPlayingInfo,
        val signature: String
    )

    companion object {
        private const val TAG = "AdNotificationListener"
        private const val NOW_PLAYING_ALBUM_ART_FILE_PREFIX = "now_playing_album_art"
        private const val MEDIA_REFRESH_INTERVAL_MS = 2000L
    }
}
