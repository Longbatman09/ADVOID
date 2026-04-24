Key Components,
1. Detection Layer
   Use NotificationListenerService
   Role:
   Listen for media notifications
   Identify which app and store the package name in <last_media_app>
2. Control Layer (muting logic)
   Use AudioManager
   Role:
   Mute/unmute device media volume
3. Foreground Service (stability)
   Run a persistent service to:
   Keep app alive in background (Disable battery restriction)
   Ensure mute works reliably
4. Permissions & Settings
   Notification Access (manual user enable)
   Foreground service permission
   Audio settings permission – <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>


Optional Features (make it better)
🎚️ App selection list (user chooses which apps to mute)
🔕 “Mute only when headphones disconnected”
⏰ Scheduled mute (night mode)
📊 Log of muted apps
🔔 Notification: “Muted Instagram”
