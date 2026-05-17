package com.example.admute.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsManager {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
        }
    }

    fun logEvent(eventName: String, params: Bundle? = null) {
        firebaseAnalytics?.logEvent(eventName, params)
    }

    fun logScreenView(screenName: String, screenClass: String?) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    fun logAdDetected(packageName: String, appName: String, content: String) {
        val bundle = Bundle().apply {
            putString("package_name", packageName)
            putString("app_name", appName)
            putString("content_preview", content.take(100))
        }
        logEvent("ad_detected", bundle)
    }

    fun logMuteCycleEnded(durationSeconds: Long) {
        val bundle = Bundle().apply {
            putLong("duration_seconds", durationSeconds)
        }
        logEvent("mute_cycle_ended", bundle)
    }

    fun logSettingsChanged(settingName: String, value: String) {
        val bundle = Bundle().apply {
            putString("setting_name", settingName)
            putString("value", value)
        }
        logEvent("settings_changed", bundle)
    }

    fun logAction(actionName: String) {
        logEvent("user_action", Bundle().apply { putString("action", actionName) })
    }
}
