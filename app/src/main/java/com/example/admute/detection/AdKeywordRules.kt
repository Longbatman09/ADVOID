package com.example.admute.detection

/**
 * Central place for ad-related keywords so this list can grow during testing.
 */
object AdKeywordRules {
    private val keywords = listOf(
        "ad",
        "ads",
        "advertisement",
        "sponsorship",
        "sponsored",
        "promo",
        "promotion"
    )

    fun matches(notificationText: String): Boolean {
        val normalizedText = notificationText.lowercase()
        return keywords.any { keyword ->
            val pattern = Regex("\\b${Regex.escape(keyword)}\\b")
            pattern.containsMatchIn(normalizedText)
        }
    }

    fun all(): List<String> = keywords
}

