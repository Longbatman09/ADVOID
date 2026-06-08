package com.example.admute.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import com.example.admute.MainActivity
import com.example.admute.R
import com.example.admute.analytics.AnalyticsManager
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
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private var trigger = false
    private var lastMediaApp: String? = null
    private var restoreVolumeTarget = 0
    private var cooldownEndsAtMs = 0L
    private var muteStartTimeMs = 0L
    private val activeAdNotificationKeys = mutableSetOf<String>()
    private var currentFadeRunnable: Runnable? = null
    private var isMediaRefreshRunning = false
    private var isAdMonitorRunning = false
    private var lastPublishedMediaSignature: String? = null
    private var lastAlbumArtFingerprint: String? = null
    private var cachedAlbumArtPath: String? = null
    private var lastStatusNotificationSignature: String? = null
    private var lastObservedPausedState: Boolean? = null
    private var lastAdLogSignature: String? = null
    private var lastAdLogTimeMs: Long = 0L

    private val mediaRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isMediaRefreshRunning) return
            refreshNowPlayingState()
            if (!isMediaRefreshRunning) return
            mainHandler.postDelayed(this, MEDIA_REFRESH_INTERVAL_MS)
        }
    }

    private val adMonitorRunnable = object : Runnable {
        override fun run() {
            if (!isAdMonitorRunning) return
            monitorTriggeredAppState()
            if (!isAdMonitorRunning) return
            mainHandler.postDelayed(this, AD_MONITOR_INTERVAL_MS)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (AdMuteSettings.isAppPaused(this)) {
            refreshNowPlayingState()
            updateStatusNotification()
            return
        }

        val packageName = sbn.packageName
        val isWhitelistedApp = WhitelistedApps.contains(this, packageName)
        val notification = sbn.notification ?: return
        val notificationKey = buildNotificationKey(sbn)
        val content = extractNotificationText(notification)
        val isAd = content.isNotBlank() && AdKeywordRules.matches(this, content)

        when {
            isAd && isWhitelistedApp -> {
                if (!trigger) {
                    if (isCooldownActive()) {
                        Log.i(TAG, "Cooldown active, skipping mute for $packageName")
                    } else {
                        startMuteCycle(packageName, content, notificationKey)
                    }
                } else if (lastMediaApp == packageName) {
                    activeAdNotificationKeys.add(notificationKey)
                    enforceMuteWhileActive()
                } else {
                    Log.i(TAG, "Ad detected from different app while muted, ignoring: $packageName")
                }
            }
            trigger && lastMediaApp == packageName -> {
                // Non-ad notification from same app while in cycle, check if we should end
                if (activeAdNotificationKeys.remove(notificationKey) && activeAdNotificationKeys.isEmpty()) {
                    if (!hasActiveAdNotificationForPackage(packageName)) {
                        endMuteCycle("ad notification content removed")
                    }
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
                    if (!hasActiveAdNotificationForPackage(packageName)) {
                        endMuteCycle("ad notification removed")
                    }
                }
            }
        }

        refreshNowPlayingState()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AnalyticsManager.initialize(this)
        Log.i(TAG, "Notification listener connected")
        startMediaMonitoring()
        startAdMonitoring()
        refreshNowPlayingState()
        updateStatusNotification()
    }

    override fun onListenerDisconnected() {
        stopAdMonitoring()
        stopMediaMonitoring()
        cancelStatusNotification()
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
    }

    override fun onDestroy() {
        stopAdMonitoring()
        stopMediaMonitoring()
        cancelStatusNotification()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleNotificationAction(intent?.action)
        return START_STICKY
    }

    private fun startMuteCycle(packageName: String, content: String, notificationKey: String) {
        restoreVolumeTarget = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        activeAdNotificationKeys.clear()
        activeAdNotificationKeys.add(notificationKey)
        trigger = true
        muteStartTimeMs = System.currentTimeMillis()
        lastMediaApp = packageName
        playConfiguredSound(isStart = true)
        setMusicVolumeImmediately(0)
        Log.i(TAG, "Ad detected from $packageName. Mute started.")
        logAdDetection(packageName, content)
        
        val appName = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
        AnalyticsManager.logAdDetected(packageName, appName)

        showToast("Ad detected. Playing IN sound and muting")
        updateStatusNotification()
    }

    private fun logAdDetection(packageName: String, content: String) {
        val appName = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)

        val now = System.currentTimeMillis()
        val normalizedContent = content.trim().lowercase()
        val signature = "$packageName|$normalizedContent"
        if (signature == lastAdLogSignature && now - lastAdLogTimeMs < 2000L) return
        lastAdLogSignature = signature
        lastAdLogTimeMs = now

        AdMuteSettings.addAdLog(
            context = this,
            entry = AdLogEntry(
                appName = appName,
                packageName = packageName,
                timestamp = now,
                content = ""
            )
        )
    }

    private fun endMuteCycle(reason: String, applyCooldown: Boolean = true) {
        val now = System.currentTimeMillis()
        AdMuteSettings.updateLastLogEndTime(this, now)
        
        val durationSeconds = (now - muteStartTimeMs) / 1000
        AnalyticsManager.logMuteCycleEnded(durationSeconds)

        val restoreVolume = resolveRestoreVolume()
        fadeVolume(restoreVolume, durationMs = 700L) {
            playConfiguredSound(isStart = false)
        }

        val previousApp = lastMediaApp
        trigger = false
        lastMediaApp = null
        activeAdNotificationKeys.clear()
        cooldownEndsAtMs = if (applyCooldown) {
            System.currentTimeMillis() + AdMuteSettings.getCooldownMs(this)
        } else {
            0L
        }

        Log.i(TAG, "Mute cycle ended for $previousApp ($reason). Restored volume to $restoreVolume")
        showToast("Ad ended. Volume restored")
        updateStatusNotification()
    }

    private fun enforceMuteWhileActive() {
        if (currentFadeRunnable != null) return

        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVolume != 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            showToast("ADVOID is active. Keeping media muted")
        }
    }

    private fun resolveRestoreVolume(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
        return restoreVolumeTarget.coerceIn(0, max)
    }

    private fun isCooldownActive(): Boolean = System.currentTimeMillis() < cooldownEndsAtMs

    private fun hasActiveAdNotificationForPackage(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return getActiveNotifications()
            .asSequence()
            .filter { it.packageName == packageName }
            .mapNotNull { it.notification }
            .map(::extractNotificationText)
            .any { content -> content.isNotBlank() && AdKeywordRules.matches(this, content) }
    }

    private fun monitorTriggeredAppState() {
        val isPaused = AdMuteSettings.isAppPaused(this)

        if (lastObservedPausedState != isPaused) {
            lastObservedPausedState = isPaused
            if (isPaused) {
                if (trigger) {
                    endMuteCycle("paused while muted", applyCooldown = false)
                }
                updateStatusNotification(force = true)
                return
            }

            refreshNowPlayingState()
            resumeDetectionFromActiveNotifications()
            updateStatusNotification(force = true)
        }

        if (isPaused) {
            return
        }
        if (!trigger) return
        val app = lastMediaApp ?: return
        
        // Check if mute duration exceeds 5 minutes
        val muteDurationMs = System.currentTimeMillis() - muteStartTimeMs
        val maxMuteDurationMs = 5 * 60 * 1000 // 5 minutes
        if (muteDurationMs > maxMuteDurationMs) {
            Log.i(TAG, "Ad mute exceeded 5 minutes for $app. Unmuting and removing log.")
            removeLastLogEntry()
            endMuteCycle("mute duration exceeded 5 minutes")
            return
        }
        
        if (hasActiveAdNotificationForPackage(app)) {
            enforceMuteWhileActive()
        } else {
            endMuteCycle("keywords cleared for active app")
        }
    }
    
    private fun removeLastLogEntry() {
        val logs = AdMuteSettings.getAdLogs(this).toMutableList()
        if (logs.isNotEmpty()) {
            logs.removeAt(0) // Remove the most recent entry
            AdMuteSettings.clearAdLogs(this)
            logs.forEach { entry ->
                AdMuteSettings.addAdLog(this, entry)
            }
        }
    }

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

    private fun startAdMonitoring() {
        if (isAdMonitorRunning) return
        isAdMonitorRunning = true
        mainHandler.removeCallbacks(adMonitorRunnable)
        mainHandler.post(adMonitorRunnable)
    }

    private fun stopAdMonitoring() {
        isAdMonitorRunning = false
        mainHandler.removeCallbacks(adMonitorRunnable)
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

    private fun fadeVolume(targetVolume: Int, durationMs: Long, onComplete: (() -> Unit)? = null) {
        currentFadeRunnable?.let { mainHandler.removeCallbacks(it) }

        val startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (startVolume == targetVolume) {
            currentFadeRunnable = null
            onComplete?.invoke()
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
                    onComplete?.invoke()
                }
            }
        }
        currentFadeRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun setMusicVolumeImmediately(targetVolume: Int, onComplete: (() -> Unit)? = null) {
        currentFadeRunnable?.let { mainHandler.removeCallbacks(it) }
        currentFadeRunnable = null
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
        val volume = targetVolume.coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        onComplete?.invoke()
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

    private fun handleNotificationAction(action: String?) {
        when (action) {
            ACTION_TOGGLE_PAUSE -> {
                val newPausedState = !AdMuteSettings.isAppPaused(this)
                AdMuteSettings.saveAppPaused(this, newPausedState)
                if (newPausedState && trigger) {
                    endMuteCycle("paused via notification", applyCooldown = false)
                } else if (!newPausedState) {
                    refreshNowPlayingState()
                    resumeDetectionFromActiveNotifications()
                    updateStatusNotification(force = true)
                } else {
                    updateStatusNotification(force = true)
                }
                showToast(if (newPausedState) "ADMUTE paused" else "ADMUTE resumed")
            }

            ACTION_UNMUTE_ONCE -> {
                if (trigger) {
                    endMuteCycle("manual unmute action", applyCooldown = false)
                    showToast("Unmuted for this ad")
                } else {
                    showToast("No active ad mute")
                    updateStatusNotification(force = true)
                }
            }
        }
    }

    private fun resumeDetectionFromActiveNotifications() {
        if (trigger || AdMuteSettings.isAppPaused(this) || isCooldownActive()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val activeAdNotification = getActiveNotifications()
            .asSequence()
            .filter { WhitelistedApps.contains(this, it.packageName) }
            .sortedByDescending { it.postTime }
            .firstOrNull { sbn ->
                val content = extractNotificationText(sbn.notification)
                content.isNotBlank() && AdKeywordRules.matches(this, content)
            } ?: return

        val packageName = activeAdNotification.packageName
        val content = extractNotificationText(activeAdNotification.notification)
        val notificationKey = buildNotificationKey(activeAdNotification)
        startMuteCycle(packageName, content, notificationKey)
    }

    private fun updateStatusNotification(force: Boolean = false) {
        if (!canPostStatusNotification()) return
        createStatusNotificationChannelIfNeeded()

        val isPaused = AdMuteSettings.isAppPaused(this)
        val showUnmuteAction = trigger && !isPaused
        val pauseLabel = if (isPaused) "Resume ADMUTE" else "Pause ADMUTE"
        val contentText = when {
            isPaused -> "ADMUTE is paused"
            trigger -> "AD detected. Media is muted."
            else -> "ADVOID is running in the background"
        }
        val statusSignature = "$isPaused|$trigger|${lastMediaApp.orEmpty()}"
        if (!force && statusSignature == lastStatusNotificationSignature) return

        val pauseIntent = Intent(this, AdNotificationListenerService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val unmuteIntent = Intent(this, AdNotificationListenerService::class.java).apply {
            action = ACTION_UNMUTE_ONCE
        }
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pausePendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_TOGGLE_PAUSE,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val unmutePendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_UNMUTE_ONCE,
            unmuteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, STATUS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.vector_image)
            .setContentTitle("ADVOID is running")
            .setContentText(contentText)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, pauseLabel, pausePendingIntent)

        if (showUnmuteAction) {
            builder.addAction(0, "UNMUTE this time", unmutePendingIntent)
        }

        val notification = builder.build()

        notificationManager.notify(STATUS_NOTIFICATION_ID, notification)
        lastStatusNotificationSignature = statusSignature
    }

    private fun createStatusNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(STATUS_NOTIFICATION_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            STATUS_NOTIFICATION_CHANNEL_ID,
            "ADVOID status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows whether ADVOID is running and lets you pause or unmute."
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun canPostStatusNotification(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun cancelStatusNotification() {
        notificationManager.cancel(STATUS_NOTIFICATION_ID)
        lastStatusNotificationSignature = null
    }

    private data class MediaSnapshot(
        val info: NowPlayingInfo,
        val signature: String
    )

    companion object {
        private const val TAG = "AdNotificationListener"
        private const val NOW_PLAYING_ALBUM_ART_FILE_PREFIX = "now_playing_album_art"
        private const val MEDIA_REFRESH_INTERVAL_MS = 2000L
        private const val AD_MONITOR_INTERVAL_MS = 350L
        private const val STATUS_NOTIFICATION_CHANNEL_ID = "advoid_running_status"
        private const val STATUS_NOTIFICATION_ID = 1001
        private const val ACTION_TOGGLE_PAUSE = "com.example.admute.action.TOGGLE_PAUSE"
        private const val ACTION_UNMUTE_ONCE = "com.example.admute.action.UNMUTE_ONCE"
        private const val REQUEST_CODE_TOGGLE_PAUSE = 10011
        private const val REQUEST_CODE_UNMUTE_ONCE = 10012
        private const val REQUEST_CODE_OPEN_APP = 10013
    }
}
