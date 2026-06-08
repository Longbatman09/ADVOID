# ADVOID - Avoid the ads

Introducing ADVOID – the game-changing Android app that revolutionizes your music experience! Say goodbye to annoying ad audio from your favorite music apps! ADVOID works its magic by smartly monitoring media notifications and matching keywords, seamlessly muting those pesky ads. But that’s not all – once the ad is over, it effortlessly restores your volume, ensuring you never miss a beat! Plus, with a clever cooldown feature, you won’t have to worry about any rapid toggling. Enjoy your music uninterrupted with ADVOID and take control of your listening experience!

![image alt](https://github.com/Longbatman09/ADVOID/blob/88841fea7c06c151b186b671496149b1c85d7bd8/Banner.png)

## Features
- Ad detection from notification text using a configurable keyword list.
- Per-app whitelist (music apps) to limit detection scope.
- Automatic mute with smooth volume fade and restore.
- Optional IN/OUT notification sounds (stock or custom audio).
- Status notification with pause/unmute actions.
- Local ad detection logs and total time saved.
- Light/Dark/System theme support.

## About UI
This UI uses a minimalist and flat design style with Material Design-inspired elements.
It focuses on a clean utility-dashboard layout using large rounded buttons and simple icons.
The limited teal-and-gray color palette gives it a modern, functional appearance.
Generous spacing and large touch targets improve readability and accessibility.
Overall, it follows a functional-first Android utility app design philosophy.

![image alt](https://github.com/Longbatman09/ADVOID/blob/877bc056bcbd5d9bbeaf0303c66c27671ae2c9b4/Banner2.png)

## How It Works
- `AdNotificationListenerService` listens to notification updates from whitelisted apps.
- The app checks the notification text against keyword rules.
- On ad detection, it fades music volume to 0 and logs the event.
- When the ad ends (keyword removed), it restores volume and applies a cooldown.

![image alt](https://github.com/Longbatman09/ADVOID/blob/6a3390992f60beb014c9e5abeeec81f4825bb3e2/howitworks.png)

## Permissions
ADVOID requests these runtime permissions:
- `POST_NOTIFICATIONS` (status updates on Android 13+)
- `MODIFY_AUDIO_SETTINGS` (mute/unmute media volume)
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (keep service alive)
- Notification access (user must enable in settings)

## Installation 

Download the latest APK from the [Releases](https://github.com/Longbatman09/ADVOID/releases) page.

> [!CAUTION]
> The release version on GitHub might be flagged or blocked by **Google Play Protect** during installation. For a smoother experience, it is recommended to install the APK using **ADB** (`adb install dale-app.apk`) or to temporarily disable Play Protect.

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

## Credits
Solo developer - B.Vishal Chandrakanth (Founder of Coco Copi Developers)

