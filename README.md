# ADVOID - Avoid Your Ads

ADVOID is an Android app that automatically mutes ad audio from selected music apps by monitoring media-style notifications and keyword matches. It restores volume after the ad ends and keeps a short cooldown to avoid rapid toggling.

## Features
- Ad detection from notification text using a configurable keyword list.
- Per-app whitelist (music apps) to limit detection scope.
- Automatic mute with smooth volume fade and restore.
- Optional IN/OUT notification sounds (stock or custom audio).
- Status notification with pause/unmute actions.
- Local ad detection logs and total time saved.
- Light/Dark/System theme support.

## How It Works
- `AdNotificationListenerService` listens to notification updates from whitelisted apps.
- The app checks the notification text against keyword rules.
- On ad detection, it fades music volume to 0 and logs the event.
- When the ad ends (keyword removed), it restores volume and applies a cooldown.

## Permissions
ADVOID requests these runtime permissions:
- `POST_NOTIFICATIONS` (status updates on Android 13+)
- `MODIFY_AUDIO_SETTINGS` (mute/unmute media volume)
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (keep service alive)
- Notification access (user must enable in settings)

## Build and Run
From the project root:

```powershell
cd "C:\Users\Admin\AndroidStudioProjects\ADMUTE"
.\gradlew.bat assembleDebug
```

Install on a device/emulator:

```powershell
.\gradlew.bat installDebug
```

## Tests
Unit tests:

```powershell
.\gradlew.bat test
```

Instrumented tests:

```powershell
.\gradlew.bat connectedAndroidTest
```

## Project Structure
- `app/src/main/java/com/example/admute/MainActivity.kt` - Compose UI, setup flow, settings screens.
- `app/src/main/java/com/example/admute/service/AdNotificationListenerService.kt` - Notification listening, mute logic, status notification.
- `app/src/main/java/com/example/admute/detection/AdKeywordRules.kt` - Keyword matching logic.
- `app/src/main/java/com/example/admute/detection/WhitelistedApps.kt` - Default/recommended app whitelist.
- `app/src/main/java/com/example/admute/settings/AdMuteSettings.kt` - Preferences and state storage.

## Configuration
- **Keyword list**: Modify default keywords or add custom ones via the app UI.
- **Whitelisted apps**: Choose which music apps should be monitored.
- **Notification sounds**: Pick stock tones or a custom file for IN/OUT alerts.

## Analytics
Firebase Analytics is used for screen views and key actions (see `AnalyticsManager`).

## Additional Documentation
- `MUTING_SEQUENCE_IMPLEMENTATION.md`
- `TESTING_GUIDE.md`

