package com.example.admute.settings

import android.content.Context
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
    private const val KEY_NOW_PLAYING_APP_NAME = "now_playing_app_name"
    private const val KEY_NOW_PLAYING_PACKAGE = "now_playing_package"
    private const val KEY_NOW_PLAYING_TITLE = "now_playing_title"
    private const val KEY_NOW_PLAYING_TEXT = "now_playing_text"
    private const val KEY_NOW_PLAYING_SUBTEXT = "now_playing_subtext"
    private const val KEY_NOW_PLAYING_ALBUM_ART_PATH = "now_playing_album_art_path"
    private const val KEY_NOW_PLAYING_UPDATED_AT = "now_playing_updated_at"

    private const val DEFAULT_COOLDOWN_MINUTES = 3
    private const val MIN_COOLDOWN_MINUTES = 1
    private const val MAX_COOLDOWN_MINUTES = 10

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

    fun isSetupCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SETUP_COMPLETED, false)
    }

    fun saveSetupCompleted(context: Context, completed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
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
}





