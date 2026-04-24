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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import com.example.admute.detection.AdKeywordRules
import com.example.admute.detection.WhitelistedApps
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        if (!WhitelistedApps.contains(this, packageName)) return

        val notification = sbn.notification ?: return
        publishNowPlayingInfo(packageName, notification)
        val content = extractNotificationText(notification)
        val isAd = content.isNotBlank() && AdKeywordRules.matches(content)

        if (isAd) {
            if (trigger) {
                if (lastMediaApp == packageName) {
                    enforceMuteWhileActive()
                }
                return
            }

            if (isCooldownActive()) {
                Log.i(TAG, "Cooldown active, skipping mute for $packageName")
                return
            }

            startMuteCycle(packageName, content)
            return
        }

        if (trigger && lastMediaApp == packageName) {
            endMuteCycle("ad keyword removed")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!trigger) return

        val packageName = sbn.packageName
        if (packageName == lastMediaApp) {
            endMuteCycle("notification removed")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
    }

    private fun startMuteCycle(packageName: String, content: String) {
        restoreVolumeTarget = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)

        trigger = true
        lastMediaApp = packageName

        Log.i(TAG, "Ad detected from $packageName: $content")
        playConfiguredSound(isStart = true)
        showToast("Ad detected. ADMUTE is active")
    }

    private fun endMuteCycle(reason: String) {
        val restoreVolume = resolveRestoreVolume()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVolume, 0)

        val previousApp = lastMediaApp
        trigger = false
        lastMediaApp = null
        cooldownEndsAtMs = System.currentTimeMillis() + AdMuteSettings.getCooldownMs(this)

        Log.i(TAG, "Mute cycle ended for $previousApp ($reason). Restored volume to $restoreVolume")
        playConfiguredSound(isStart = false)
        showToast("Ad ended. Volume restored")
    }

    private fun enforceMuteWhileActive() {
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
            val player = MediaPlayer.create(this, config.stockSound.resId) ?: return
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

    private fun publishNowPlayingInfo(packageName: String, notification: Notification) {
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val albumArtPath = extractAndCacheAlbumArt(notification)

        val appName = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)

        AdMuteSettings.saveNowPlayingInfo(
            context = this,
            info = NowPlayingInfo(
                appName = appName,
                packageName = packageName,
                title = title.ifBlank { "Unknown title" },
                text = text.ifBlank { "Unknown track info" },
                subText = subText,
                albumArtPath = albumArtPath,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    private fun extractAndCacheAlbumArt(notification: Notification): String? {
        val bitmap = extractAlbumArtBitmap(notification) ?: return null
        val previousPath = AdMuteSettings.getNowPlayingInfo(this)?.albumArtPath
        val outputFile = File(cacheDir, "${NOW_PLAYING_ALBUM_ART_FILE_PREFIX}_${System.currentTimeMillis()}.jpg")

        return runCatching {
            FileOutputStream(outputFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            }
            previousPath
                ?.takeIf { it != outputFile.absolutePath }
                ?.let { File(it).takeIf(File::exists)?.delete() }
            outputFile.absolutePath
        }.getOrNull()
    }

    private fun extractAlbumArtBitmap(notification: Notification): Bitmap? {
        val fromMediaSession = extractAlbumArtFromMediaSession(notification)
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

    private fun extractAlbumArtFromMediaSession(notification: Notification): Bitmap? {
        val extras = notification.extras ?: return null
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        } ?: return null

        return runCatching {
            val metadata = MediaController(this, token).metadata ?: return@runCatching null
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "AdNotificationListener"
        private const val NOW_PLAYING_ALBUM_ART_FILE_PREFIX = "now_playing_album_art"
    }
}
