package com.example.admute.detection

import android.content.Context
import com.example.admute.settings.AdMuteSettings

/**
 * Central place for ad-related keywords so this list can grow during testing.
 */
object AdKeywordRules {
    private val defaultKeywords = listOf(
        "ad",
        "ads",
        "advertisement",
        "advertisements",
        "sponsorship",
        "sponsored",
        "sponsered",
        "promo",
        "promotion"
    )

    fun matches(context: Context, notificationText: String): Boolean {
        val normalizedText = notificationText.lowercase()
        return all(context).any { keyword ->
            val pattern = Regex("\\b${Regex.escape(keyword)}\\b")
            pattern.containsMatchIn(normalizedText)
        }
    }

    fun default(): List<String> = defaultKeywords

    fun all(context: Context): List<String> {
        val disabledDefaultKeywords = AdMuteSettings.getDisabledDefaultAdKeywords(context)
        val enabledDefaults = defaultKeywords.filterNot { disabledDefaultKeywords.contains(it) }
        val custom = AdMuteSettings.getCustomAdKeywords(context)
        return (enabledDefaults + custom).distinct()
    }
}

