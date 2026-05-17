package com.example.admute.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.admute.R

enum class NotificationSoundSource(val storageValue: String) {
    STOCK("stock"),
    CUSTOM("custom"),
    OFF("off");

    companion object {
        fun fromStorage(value: String?): NotificationSoundSource {
            return entries.firstOrNull { it.storageValue == value } ?: STOCK
        }
    }
}

enum class StockNotificationSound(val storageValue: String, val label: String, val resId: Int) {
    S1_IN("s1_in", "Stock IN 1", R.raw.s1_in),
    S2_IN("s2_in", "Stock IN 2", R.raw.s2_in),
    S1_OUT("s1_out", "Stock OUT 1", R.raw.s1_out),
    S2_OUT("s2_out", "Stock OUT 2", R.raw.s2_out);

    companion object {
        fun fromStorage(value: String?, fallback: StockNotificationSound): StockNotificationSound {
            return entries.firstOrNull { it.storageValue == value } ?: fallback
        }
    }
}

enum class ThemeMode(val storageValue: String, val label: String) {
    LIGHT("light", "Light Mode"),
    DARK("dark", "Dark Mode"),
    SYSTEM("system", "System Theme");

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}

data class NotificationSoundConfig(
    val source: NotificationSoundSource,
    val stockSound: StockNotificationSound,
    val customUri: String?,
    val customDisplayName: String?
)

data class NotificationSoundSettings(
    val inSound: NotificationSoundConfig,
    val outSound: NotificationSoundConfig
)

data class NowPlayingInfo(
    val appName: String,
    val packageName: String,
    val title: String,
    val text: String,
    val subText: String,
    val albumArtPath: String?,
    val updatedAtMs: Long
)

data class AdLogEntry(
    val appName: String,
    val packageName: String,
    val timestamp: Long,
    val endTime: Long = 0L,
    val content: String
)

object AdMuteSettings {
    private const val PREFS_NAME = "admute_prefs"
    private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
    private const val KEY_IN_SOUND_SOURCE = "in_sound_source"
    private const val KEY_IN_STOCK_SOUND = "in_stock_sound"
    private const val KEY_IN_CUSTOM_URI = "in_custom_uri"
    private const val KEY_IN_CUSTOM_NAME = "in_custom_name"
    private const val KEY_OUT_SOUND_SOURCE = "out_sound_source"
    private const val KEY_OUT_STOCK_SOUND = "out_stock_sound"
    private const val KEY_OUT_CUSTOM_URI = "out_custom_uri"
    private const val KEY_OUT_CUSTOM_NAME = "out_custom_name"
    private const val KEY_SETUP_COMPLETED = "setup_completed"
    private const val KEY_WHITELIST_ACTION_ENABLED = "whitelist_action_enabled"
    private const val KEY_COOLDOWN_ACTION_ENABLED = "cooldown_action_enabled"
    private const val KEY_NOTIFICATION_SOUND_ACTION_ENABLED = "notification_sound_action_enabled"
    private const val KEY_APP_PAUSED = "app_paused"
    private const val KEY_NOW_PLAYING_APP_NAME = "now_playing_app_name"
    private const val KEY_NOW_PLAYING_PACKAGE = "now_playing_package"
    private const val KEY_NOW_PLAYING_TITLE = "now_playing_title"
    private const val KEY_NOW_PLAYING_TEXT = "now_playing_text"
    private const val KEY_NOW_PLAYING_SUBTEXT = "now_playing_subtext"
    private const val KEY_NOW_PLAYING_ALBUM_ART_PATH = "now_playing_album_art_path"
    private const val KEY_NOW_PLAYING_UPDATED_AT = "now_playing_updated_at"
    private const val KEY_AD_LOGS = "ad_logs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_LAST_SPLASH_TIME = "last_splash_time"
    private const val KEY_SETUP_DONATION_PROMPT_SHOWN = "setup_donation_prompt_shown"
    private const val KEY_CUSTOM_AD_KEYWORDS = "custom_ad_keywords"
    private const val KEY_DISABLED_DEFAULT_AD_KEYWORDS = "disabled_default_ad_keywords"

    private const val MAX_LOG_ENTRIES = 50

    private const val DEFAULT_COOLDOWN_MINUTES = 1
    private const val MIN_COOLDOWN_MINUTES = 1
    private const val MAX_COOLDOWN_MINUTES = 10
    private const val SPLASH_COOLDOWN_MS = 0L

    fun shouldShowSplash(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_LAST_SPLASH_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastTime) > SPLASH_COOLDOWN_MS
    }

    fun recordSplashShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SPLASH_TIME, System.currentTimeMillis()).apply()
    }

    fun getCooldownMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_COOLDOWN_MINUTES, DEFAULT_COOLDOWN_MINUTES)
        return stored.coerceIn(MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES)
    }

    fun saveCooldownMinutes(context: Context, minutes: Int) {
        val normalized = minutes.coerceIn(MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_COOLDOWN_MINUTES, normalized).apply()
    }

    fun getCooldownMs(context: Context): Long = getCooldownMinutes(context) * 60 * 1000L

    fun getNotificationSoundSettings(context: Context): NotificationSoundSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val inSound = NotificationSoundConfig(
            source = NotificationSoundSource.fromStorage(prefs.getString(KEY_IN_SOUND_SOURCE, NotificationSoundSource.STOCK.storageValue)),
            stockSound = StockNotificationSound.fromStorage(
                prefs.getString(KEY_IN_STOCK_SOUND, StockNotificationSound.S1_IN.storageValue),
                fallback = StockNotificationSound.S1_IN
            ),
            customUri = prefs.getString(KEY_IN_CUSTOM_URI, null),
            customDisplayName = prefs.getString(KEY_IN_CUSTOM_NAME, null)
        )

        val outSound = NotificationSoundConfig(
            source = NotificationSoundSource.fromStorage(prefs.getString(KEY_OUT_SOUND_SOURCE, NotificationSoundSource.STOCK.storageValue)),
            stockSound = StockNotificationSound.fromStorage(
                prefs.getString(KEY_OUT_STOCK_SOUND, StockNotificationSound.S1_OUT.storageValue),
                fallback = StockNotificationSound.S1_OUT
            ),
            customUri = prefs.getString(KEY_OUT_CUSTOM_URI, null),
            customDisplayName = prefs.getString(KEY_OUT_CUSTOM_NAME, null)
        )

        return NotificationSoundSettings(inSound = inSound, outSound = outSound)
    }

    fun saveNotificationSoundSettings(context: Context, settings: NotificationSoundSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_IN_SOUND_SOURCE, settings.inSound.source.storageValue)
            .putString(KEY_IN_STOCK_SOUND, settings.inSound.stockSound.storageValue)
            .putString(KEY_IN_CUSTOM_URI, settings.inSound.customUri)
            .putString(KEY_IN_CUSTOM_NAME, settings.inSound.customDisplayName)
            .putString(KEY_OUT_SOUND_SOURCE, settings.outSound.source.storageValue)
            .putString(KEY_OUT_STOCK_SOUND, settings.outSound.stockSound.storageValue)
            .putString(KEY_OUT_CUSTOM_URI, settings.outSound.customUri)
            .putString(KEY_OUT_CUSTOM_NAME, settings.outSound.customDisplayName)
            .apply()
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationListenerAccess(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return enabledListeners.contains(context.packageName)
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isSetupCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isCompleted = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        val hasPermissions = hasPostNotificationsPermission(context) &&
                hasNotificationListenerAccess(context) &&
                isIgnoringBatteryOptimizations(context)
        return isCompleted && hasPermissions
    }

    fun saveSetupCompleted(context: Context, completed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
    }

    fun isSetupDonationPromptShown(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SETUP_DONATION_PROMPT_SHOWN, false)
    }

    fun saveSetupDonationPromptShown(context: Context, shown: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SETUP_DONATION_PROMPT_SHOWN, shown).apply()
    }

    fun isWhitelistActionEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WHITELIST_ACTION_ENABLED, true)
    }

    fun saveWhitelistActionEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WHITELIST_ACTION_ENABLED, enabled).apply()
    }

    fun isCooldownActionEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_COOLDOWN_ACTION_ENABLED, true)
    }

    fun saveCooldownActionEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_COOLDOWN_ACTION_ENABLED, enabled).apply()
    }

    fun isNotificationSoundActionEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIFICATION_SOUND_ACTION_ENABLED, true)
    }

    fun saveNotificationSoundActionEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NOTIFICATION_SOUND_ACTION_ENABLED, enabled).apply()
    }

    fun isAppPaused(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_APP_PAUSED, false)
    }

    fun saveAppPaused(context: Context, paused: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_APP_PAUSED, paused).apply()
    }

    fun saveNowPlayingInfo(context: Context, info: NowPlayingInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_NOW_PLAYING_APP_NAME, info.appName)
            .putString(KEY_NOW_PLAYING_PACKAGE, info.packageName)
            .putString(KEY_NOW_PLAYING_TITLE, info.title)
            .putString(KEY_NOW_PLAYING_TEXT, info.text)
            .putString(KEY_NOW_PLAYING_SUBTEXT, info.subText)
            .putString(KEY_NOW_PLAYING_ALBUM_ART_PATH, info.albumArtPath)
            .putLong(KEY_NOW_PLAYING_UPDATED_AT, info.updatedAtMs)
            .apply()
    }

    fun getNowPlayingInfo(context: Context): NowPlayingInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val packageName = prefs.getString(KEY_NOW_PLAYING_PACKAGE, null) ?: return null
        val appName = prefs.getString(KEY_NOW_PLAYING_APP_NAME, packageName).orEmpty()
        val title = prefs.getString(KEY_NOW_PLAYING_TITLE, "Unknown title").orEmpty()
        val text = prefs.getString(KEY_NOW_PLAYING_TEXT, "Unknown track info").orEmpty()
        val subText = prefs.getString(KEY_NOW_PLAYING_SUBTEXT, "").orEmpty()
        val albumArtPath = prefs.getString(KEY_NOW_PLAYING_ALBUM_ART_PATH, null)
        val updatedAtMs = prefs.getLong(KEY_NOW_PLAYING_UPDATED_AT, 0L)

        return NowPlayingInfo(
            appName = appName,
            packageName = packageName,
            title = title,
            text = text,
            subText = subText,
            albumArtPath = albumArtPath,
            updatedAtMs = updatedAtMs
        )
    }

    fun clearNowPlayingInfo(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_NOW_PLAYING_APP_NAME)
            .remove(KEY_NOW_PLAYING_PACKAGE)
            .remove(KEY_NOW_PLAYING_TITLE)
            .remove(KEY_NOW_PLAYING_TEXT)
            .remove(KEY_NOW_PLAYING_SUBTEXT)
            .remove(KEY_NOW_PLAYING_ALBUM_ART_PATH)
            .remove(KEY_NOW_PLAYING_UPDATED_AT)
            .apply()
    }

    fun addAdLog(context: Context, entry: AdLogEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentLogs = getAdLogs(context).toMutableList()
        currentLogs.add(0, entry)
        val limitedLogs = currentLogs.take(MAX_LOG_ENTRIES)
        
        val logStrings = limitedLogs.map { 
            val app = it.appName.replace("|", " ").replace(";", " ")
            val pkg = it.packageName.replace("|", " ").replace(";", " ")
            val content = it.content.replace("|", " ").replace(";", " ")
            "$app|$pkg|${it.timestamp}|${it.endTime}|$content"
        }
        
        val serialized = logStrings.joinToString(";")
        prefs.edit().putString(KEY_AD_LOGS + "_ordered", serialized).apply()
    }

    fun getAdLogs(context: Context): List<AdLogEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_AD_LOGS + "_ordered", null) ?: return emptyList()
        return serialized.split(";").filter { it.isNotBlank() }.mapNotNull {
            val parts = it.split("|")
            if (parts.size >= 5) {
                AdLogEntry(
                    appName = parts[0],
                    packageName = parts[1],
                    timestamp = parts[2].toLongOrNull() ?: 0L,
                    endTime = parts[3].toLongOrNull() ?: 0L,
                    content = parts[4]
                )
            } else if (parts.size == 4) {
                // Backward compatibility
                AdLogEntry(
                    appName = parts[0],
                    packageName = parts[1],
                    timestamp = parts[2].toLongOrNull() ?: 0L,
                    endTime = 0L,
                    content = parts[3]
                )
            } else null
        }
    }

    fun updateLastLogEndTime(context: Context, endTime: Long) {
        val currentLogs = getAdLogs(context).toMutableList()
        if (currentLogs.isNotEmpty()) {
            val last = currentLogs[0]
            if (last.endTime == 0L) {
                currentLogs[0] = last.copy(endTime = endTime)
                saveLogs(context, currentLogs)
            }
        }
    }

    private fun saveLogs(context: Context, logs: List<AdLogEntry>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logStrings = logs.take(MAX_LOG_ENTRIES).map { 
            val app = it.appName.replace("|", " ").replace(";", " ")
            val pkg = it.packageName.replace("|", " ").replace(";", " ")
            val content = it.content.replace("|", " ").replace(";", " ")
            "$app|$pkg|${it.timestamp}|${it.endTime}|$content"
        }
        val serialized = logStrings.joinToString(";")
        prefs.edit().putString(KEY_AD_LOGS + "_ordered", serialized).apply()
    }

    fun clearAdLogs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_AD_LOGS + "_ordered").apply()
    }

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.storageValue) ?: ThemeMode.SYSTEM.storageValue
        return ThemeMode.fromStorage(stored)
    }

    fun saveThemeMode(context: Context, themeMode: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, themeMode.storageValue).apply()
    }

    fun getCustomAdKeywords(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_CUSTOM_AD_KEYWORDS, "").orEmpty()
        if (serialized.isBlank()) return emptyList()
        return serialized
            .split("|")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun addCustomAdKeyword(context: Context, keyword: String): Boolean {
        val normalized = keyword.trim().lowercase()
        if (normalized.isBlank()) return false
        val current = getCustomAdKeywords(context).toMutableList()
        if (current.contains(normalized)) return false
        current.add(normalized)
        saveCustomAdKeywords(context, current)
        return true
    }

    fun removeCustomAdKeyword(context: Context, keyword: String) {
        val normalized = keyword.trim().lowercase()
        if (normalized.isBlank()) return
        val updated = getCustomAdKeywords(context).filterNot { it == normalized }
        saveCustomAdKeywords(context, updated)
    }

    fun getDisabledDefaultAdKeywords(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_DISABLED_DEFAULT_AD_KEYWORDS, "").orEmpty()
        if (serialized.isBlank()) return emptySet()
        return serialized
            .split("|")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun disableDefaultAdKeyword(context: Context, keyword: String) {
        val normalized = keyword.trim().lowercase()
        if (normalized.isBlank()) return
        val updated = getDisabledDefaultAdKeywords(context).toMutableSet()
        updated.add(normalized)
        saveDisabledDefaultAdKeywords(context, updated)
    }

    fun restoreAllDefaultAdKeywords(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_DISABLED_DEFAULT_AD_KEYWORDS).apply()
    }

    private fun saveCustomAdKeywords(context: Context, keywords: List<String>) {
        val sanitized = keywords
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_AD_KEYWORDS, sanitized.joinToString("|")).apply()
    }

    private fun saveDisabledDefaultAdKeywords(context: Context, keywords: Set<String>) {
        val sanitized = keywords
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISABLED_DEFAULT_AD_KEYWORDS, sanitized.joinToString("|")).apply()
    }
}





