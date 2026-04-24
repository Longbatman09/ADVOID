package com.example.admute.detection

import android.content.Context

object WhitelistedApps {
    private const val PREFS_NAME = "admute_prefs"
    private const val KEY_SELECTED_WHITELIST = "selected_whitelist_apps"

    private val recommendedPackages = linkedMapOf(
        "Spotify" to "com.spotify.music",
        "Apple Music" to "com.apple.android.music",
        "YouTube Music" to "com.google.android.apps.youtube.music",
        "Amazon Music" to "com.amazon.mp3",
        "SoundCloud" to "com.soundcloud.android",
        "Tidal" to "com.aspiro.tidal",
        "Deezer" to "deezer.android.app",
        "Pandora" to "com.pandora.android",
        "iHeartRadio" to "com.clearchannel.iheartradio.controller",
        "JioSaavn" to "com.jio.media.jiobeats",
        "Gaana" to "com.gaana",
        "Wynk Music" to "com.bsbportal.music",
        "Hungama Music" to "com.hungama.myplay.activity"
    )

    private val defaultPackages = setOf("com.spotify.music")
    private val recommendedNames = recommendedPackages.keys.map { it.lowercase() }.toSet()

    fun contains(context: Context, packageName: String): Boolean {
        return packageName in getSelected(context)
    }

    fun getSelected(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_SELECTED_WHITELIST, null)
        return if (stored.isNullOrEmpty()) defaultPackages else stored.toSet()
    }

    fun saveSelected(context: Context, packages: Set<String>) {
        val normalized = if (packages.isEmpty()) defaultPackages else packages
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SELECTED_WHITELIST, normalized).apply()
    }

    fun isRecommended(packageName: String, label: String): Boolean {
        if (packageName in recommendedPackages.values) return true
        return label.trim().lowercase() in recommendedNames
    }

    fun recommendedPackages(): Map<String, String> = recommendedPackages
}
