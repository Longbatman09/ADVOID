package com.example.admute

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.admute.detection.AdKeywordRules
import com.example.admute.detection.WhitelistedApps
import com.example.admute.settings.AdMuteSettings
import com.example.admute.settings.NowPlayingInfo
import com.example.admute.settings.NotificationSoundConfig
import com.example.admute.settings.NotificationSoundSettings
import com.example.admute.settings.NotificationSoundSource
import com.example.admute.settings.StockNotificationSound
import com.example.admute.ui.theme.ADMUTETheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SetupStep {
    INTRO,
    WHITELIST,
    MANAGE_WHITELIST,
    DETECTION,
    COOLDOWN,
    NOTIFICATION_SOUND,
    RUNNING
}

data class InstalledApp(
    val label: String,
    val packageName: String
)

class MainActivity : ComponentActivity() {
    private var isPostNotificationsGranted by mutableStateOf(false)
    private var isNotificationAccessGranted by mutableStateOf(false)
    private var isBatteryOptimizationIgnored by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            isPostNotificationsGranted = hasPostNotificationsPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionStates()
        enableEdgeToEdge()
        setContent {
            ADMUTETheme {
                var setupStep by remember {
                    mutableStateOf(
                        if (AdMuteSettings.isSetupCompleted(this@MainActivity)) SetupStep.RUNNING else SetupStep.INTRO
                    )
                }
                var selectedWhitelist by remember {
                    mutableStateOf(WhitelistedApps.getSelected(this@MainActivity))
                }
                var cooldownMinutes by remember {
                    mutableStateOf(AdMuteSettings.getCooldownMinutes(this@MainActivity))
                }
                var notificationSoundMode by remember {
                    mutableStateOf(AdMuteSettings.getNotificationSoundSettings(this@MainActivity))
                }
                val nowPlayingInfo by produceState<NowPlayingInfo?>(
                    initialValue = AdMuteSettings.getNowPlayingInfo(this@MainActivity)
                ) {
                    while (true) {
                        value = AdMuteSettings.getNowPlayingInfo(this@MainActivity)
                        delay(1000L)
                    }
                }

                LaunchedEffect(
                    setupStep,
                    isPostNotificationsGranted,
                    isNotificationAccessGranted,
                    isBatteryOptimizationIgnored
                ) {
                    if (
                        setupStep == SetupStep.DETECTION &&
                        isPostNotificationsGranted &&
                        isNotificationAccessGranted &&
                        isBatteryOptimizationIgnored
                    ) {
                        AdMuteSettings.saveSetupCompleted(this@MainActivity, true)
                        setupStep = SetupStep.RUNNING
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (setupStep) {
                        SetupStep.INTRO -> {
                            IntroductionScreen(
                                modifier = Modifier.padding(innerPadding),
                                onStartSetup = { setupStep = SetupStep.WHITELIST }
                            )
                        }

                        SetupStep.WHITELIST -> {
                            WhitelistSelectionScreen(
                                currentSelection = selectedWhitelist,
                                modifier = Modifier.padding(innerPadding),
                                onContinue = { selection ->
                                    WhitelistedApps.saveSelected(this@MainActivity, selection)
                                    selectedWhitelist = WhitelistedApps.getSelected(this@MainActivity)
                                    setupStep = SetupStep.DETECTION
                                }
                            )
                        }

                        SetupStep.MANAGE_WHITELIST -> {
                            WhitelistSelectionScreen(
                                currentSelection = selectedWhitelist,
                                continueLabel = "Save and return",
                                modifier = Modifier.padding(innerPadding),
                                onContinue = { selection ->
                                    WhitelistedApps.saveSelected(this@MainActivity, selection)
                                    selectedWhitelist = WhitelistedApps.getSelected(this@MainActivity)
                                    setupStep = SetupStep.RUNNING
                                }
                            )
                        }

                        SetupStep.DETECTION -> {
                            DetectionSetupScreen(
                                keywordsPreview = AdKeywordRules.all().joinToString(),
                                postNotificationsGranted = isPostNotificationsGranted,
                                notificationAccessGranted = isNotificationAccessGranted,
                                batteryOptimizationGranted = isBatteryOptimizationIgnored,
                                modifier = Modifier.padding(innerPadding),
                                onRequestPostNotifications = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onOpenNotificationAccess = {
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                onRequestDisableBatteryOptimization = {
                                    requestDisableBatteryOptimization()
                                }
                            )
                        }

                        SetupStep.RUNNING -> {
                            RunningScreen(
                                nowPlayingInfo = nowPlayingInfo,
                                modifier = Modifier.padding(innerPadding),
                                onChangeWhitelist = { setupStep = SetupStep.MANAGE_WHITELIST },
                                onChangeCooldown = { setupStep = SetupStep.COOLDOWN },
                                onModifyNotificationSounds = { setupStep = SetupStep.NOTIFICATION_SOUND }
                            )
                        }

                        SetupStep.COOLDOWN -> {
                            CooldownSettingsScreen(
                                currentCooldownMinutes = cooldownMinutes,
                                modifier = Modifier.padding(innerPadding),
                                onSave = { minutes ->
                                    AdMuteSettings.saveCooldownMinutes(this@MainActivity, minutes)
                                    cooldownMinutes = AdMuteSettings.getCooldownMinutes(this@MainActivity)
                                    setupStep = SetupStep.RUNNING
                                },
                                onBack = { setupStep = SetupStep.RUNNING }
                            )
                        }

                        SetupStep.NOTIFICATION_SOUND -> {
                            NotificationSoundSettingsScreen(
                                currentSettings = notificationSoundMode,
                                modifier = Modifier.padding(innerPadding),
                                onSave = { settings ->
                                    AdMuteSettings.saveNotificationSoundSettings(this@MainActivity, settings)
                                    notificationSoundMode = AdMuteSettings.getNotificationSoundSettings(this@MainActivity)
                                    setupStep = SetupStep.RUNNING
                                },
                                onBack = { setupStep = SetupStep.RUNNING }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun refreshPermissionStates() {
        isPostNotificationsGranted = hasPostNotificationsPermission()
        isNotificationAccessGranted = hasNotificationListenerAccess()
        isBatteryOptimizationIgnored = isIgnoringBatteryOptimizations()
    }

    private fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationListenerAccess(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            .orEmpty()
        return enabledListeners.contains(packageName)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestDisableBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            return
        }

        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

@Composable
fun IntroductionScreen(
    onStartSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Welcome to ADMUTE",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "ADMUTE watches selected music app notifications and mutes media when an ad is detected.",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "When the ad ends, your volume is restored. Each mute+unmute cycle has a 3-minute cooldown.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onStartSetup) {
            Text(text = "Start setup")
        }
    }
}

@Composable
fun WhitelistSelectionScreen(
    currentSelection: Set<String>,
    onContinue: (Set<String>) -> Unit,
    continueLabel: String = "Continue",
    modifier: Modifier = Modifier
) {
    val recommendedPackages = remember { WhitelistedApps.recommendedPackages().values.toSet() }
    var selectedPackages by remember(currentSelection) { mutableStateOf(currentSelection) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val installedApps by produceState<List<InstalledApp>?>(
        initialValue = null,
        context,
        recommendedPackages
    ) {
        value = withContext(Dispatchers.IO) {
            loadInstalledApps(
                packageManager = context.packageManager,
                ownPackageName = context.packageName,
                recommendedPackages = recommendedPackages
            )
        }
    }

    if (installedApps == null) {
        WhitelistLoadingScreen(modifier = modifier)
        return
    }

    val resolvedApps = installedApps.orEmpty()

    val recommendedApps = remember(resolvedApps) {
        resolvedApps.filter { WhitelistedApps.isRecommended(it.packageName, it.label) }
    }
    val otherApps = remember(resolvedApps) {
        resolvedApps.filterNot { WhitelistedApps.isRecommended(it.packageName, it.label) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Choose whitelisted apps",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Recommended apps",
            style = MaterialTheme.typography.titleMedium
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (recommendedApps.isEmpty()) {
                item {
                    Text(
                        text = "No recommended apps found on this device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(recommendedApps, key = { it.packageName }) { app ->
                    AppSelectionRow(
                        app = app,
                        checked = app.packageName in selectedPackages,
                        onCheckedChange = { checked ->
                            selectedPackages = if (checked) {
                                selectedPackages + app.packageName
                            } else {
                                selectedPackages - app.packageName
                            }
                        }
                    )
                }
            }

            item {
                Text(
                    text = "Other installed apps",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(otherApps, key = { it.packageName }) { app ->
                AppSelectionRow(
                    app = app,
                    checked = app.packageName in selectedPackages,
                    onCheckedChange = { checked ->
                        selectedPackages = if (checked) {
                            selectedPackages + app.packageName
                        } else {
                            selectedPackages - app.packageName
                        }
                    }
                )
            }
        }

        Button(
            onClick = { onContinue(selectedPackages) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = continueLabel)
        }
    }
}

@Composable
private fun WhitelistLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading installed apps...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AppSelectionRow(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DetectionSetupScreen(
    keywordsPreview: String,
    postNotificationsGranted: Boolean,
    notificationAccessGranted: Boolean,
    batteryOptimizationGranted: Boolean,
    onRequestPostNotifications: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestDisableBatteryOptimization: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "ADMUTE detection setup",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(text = "Ad keywords: $keywordsPreview")
        Text(text = "Grant the permissions below so ADMUTE can read music notifications and alert you reliably.")
        Text(
            text = "Push notifications: ${if (postNotificationsGranted) "Granted" else "Not granted"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Notification access: ${if (notificationAccessGranted) "Granted" else "Not granted"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Battery optimization disabled: ${if (batteryOptimizationGranted) "Granted" else "Not granted"}",
            style = MaterialTheme.typography.bodyMedium
        )
        if (!postNotificationsGranted) {
            Button(
                onClick = onRequestPostNotifications,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Grant push notification permission")
            }
        }
        if (!notificationAccessGranted) {
            Button(
                onClick = onOpenNotificationAccess,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Grant notification access")
            }
        }
        if (!batteryOptimizationGranted) {
            Button(
                onClick = onRequestDisableBatteryOptimization,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Disable battery optimization")
            }
        }
        if (postNotificationsGranted && notificationAccessGranted && batteryOptimizationGranted) {
            Text(
                text = "All set. ADMUTE can now monitor selected apps.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(text = "ADMUTE restores volume automatically after ads and applies a cooldown per cycle.")
    }
}

@Composable
fun RunningScreen(
    nowPlayingInfo: NowPlayingInfo?,
    onChangeWhitelist: () -> Unit,
    onChangeCooldown: () -> Unit,
    onModifyNotificationSounds: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TopHotbar()
            ActionButtonLeftAligned(title = "Change whitelist app", onClick = onChangeWhitelist)
            ActionButtonLeftAligned(title = "Change cooldown", onClick = onChangeCooldown)
            ActionButtonLeftAligned(title = "Modify notification sounds", onClick = onModifyNotificationSounds)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NowPlayingInfoBox(nowPlayingInfo = nowPlayingInfo)
        }

        Text(
            text = "ADMUTE is currently running",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TopHotbar() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp))
        }
    }
}

@Composable
private fun ActionButtonLeftAligned(title: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun NowPlayingInfoBox(nowPlayingInfo: NowPlayingInfo?) {
    val albumArtBitmap = remember(nowPlayingInfo?.albumArtPath, nowPlayingInfo?.updatedAtMs) {
        nowPlayingInfo?.albumArtPath
            ?.takeIf { it.isNotBlank() }
            ?.let(BitmapFactory::decodeFile)
    }
    val noMusicPlaying = nowPlayingInfo == null || ((
        nowPlayingInfo.title.isBlank() || nowPlayingInfo.title.equals("Unknown title", ignoreCase = true)
        ) && (
        nowPlayingInfo.text.isBlank() || nowPlayingInfo.text.equals("Unknown track info", ignoreCase = true)
        ) && albumArtBitmap == null)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Now playing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "App: ${nowPlayingInfo?.appName ?: "Unknown"}")
                        Text(text = "Music: ${nowPlayingInfo?.title ?: "Unknown title"}")
                        Text(text = "Info: ${nowPlayingInfo?.text ?: "Unknown track info"}")
                        if (!nowPlayingInfo?.subText.isNullOrBlank()) {
                            Text(text = "More: ${nowPlayingInfo?.subText}")
                        }
                    }

                    if (albumArtBitmap != null) {
                        Image(
                            bitmap = albumArtBitmap.asImageBitmap(),
                            contentDescription = "Album art",
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            if (noMusicPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No music is playing",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CooldownSettingsScreen(
    currentCooldownMinutes: Int,
    onSave: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cooldownMinutes by remember(currentCooldownMinutes) { mutableStateOf(currentCooldownMinutes) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Change cooldown", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Set mute cycle cooldown in minutes.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                cooldownMinutes = (cooldownMinutes - 1).coerceAtLeast(1)
            }) {
                Text(text = "-")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            if (albumArtBitmap != null) {
                                Image(
                                    bitmap = albumArtBitmap.asImageBitmap(),
                                    contentDescription = "Album art",
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.55f))
                            )

            Text(text = "$cooldownMinutes min", style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                cooldownMinutes = (cooldownMinutes + 1).coerceAtMost(10)
            }) {
                Text(text = "+")
            }
        }
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
            Text(text = "Save")
                                Text(text = "App: ${nowPlayingInfo?.appName ?: "Unknown"}", color = Color.White)
                                Text(text = "Music: ${nowPlayingInfo?.title ?: "Unknown title"}", color = Color.White)
                                Text(text = "Info: ${nowPlayingInfo?.text ?: "Unknown track info"}", color = Color.White)
                                if (!nowPlayingInfo?.subText.isNullOrBlank()) {
                                    Text(text = "More: ${nowPlayingInfo?.subText}", color = Color.White)
                customDisplayName = displayName
            )
        } ?: inSound
        inSound = updated
    }

    val pickOutSoundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val updated = uri?.let {
            context.contentResolver.takePersistablePermissionSafely(it)
            val displayName = context.contentResolver.resolveDisplayName(it)
            outSound.copy(
                source = NotificationSoundSource.CUSTOM,
                customUri = it.toString(),
                customDisplayName = displayName
            )
        } ?: outSound
        outSound = updated
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Modify notification sounds", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Pick stock sounds or upload custom IN and OUT sounds.")

        SoundConfigSection(
            title = "IN sound (ad detected)",
            config = inSound,
            stockOptions = listOf(StockNotificationSound.S1_IN, StockNotificationSound.S2_IN),
            onSelectOff = { inSound = inSound.copy(source = NotificationSoundSource.OFF) },
            onSelectStock = { selected ->
                inSound = inSound.copy(source = NotificationSoundSource.STOCK, stockSound = selected)
            },
            onPickCustom = { pickInSoundLauncher.launch(arrayOf("audio/*")) },
            onClearCustom = {
                inSound = inSound.copy(customUri = null, customDisplayName = null, source = NotificationSoundSource.OFF)
            }
        )

        SoundConfigSection(
            title = "OUT sound (ad ended)",
            config = outSound,
            stockOptions = listOf(StockNotificationSound.S1_OUT, StockNotificationSound.S2_OUT),
            onSelectOff = { outSound = outSound.copy(source = NotificationSoundSource.OFF) },
            onSelectStock = { selected ->
                outSound = outSound.copy(source = NotificationSoundSource.STOCK, stockSound = selected)
            },
            onPickCustom = { pickOutSoundLauncher.launch(arrayOf("audio/*")) },
            onClearCustom = {
                outSound = outSound.copy(customUri = null, customDisplayName = null, source = NotificationSoundSource.OFF)
            }
        )

        Button(onClick = {
            onSave(NotificationSoundSettings(inSound = inSound, outSound = outSound))
        }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Save")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Back")
        }
    }
}

@Composable
private fun SoundConfigSection(
    title: String,
    config: NotificationSoundConfig,
    stockOptions: List<StockNotificationSound>,
    onSelectOff: () -> Unit,
    onSelectStock: (StockNotificationSound) -> Unit,
    onPickCustom: () -> Unit,
    onClearCustom: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = "Selected: ${config.summaryLabel()}")
        Button(onClick = onSelectOff, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (config.source == NotificationSoundSource.OFF) "Sound off selected" else "Turn sound off")
        }
        stockOptions.forEach { stock ->
            Button(onClick = { onSelectStock(stock) }, modifier = Modifier.fillMaxWidth()) {
                val selected = config.source == NotificationSoundSource.STOCK && config.stockSound == stock
                Text(text = if (selected) "${stock.label} selected" else "Use ${stock.label}")
            }
        }
        Button(onClick = onPickCustom, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Upload custom sound")
        }
        if (config.customUri != null) {
            Button(onClick = onClearCustom, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Clear custom sound")
            }
        }
    }
}

private fun loadInstalledApps(
    packageManager: PackageManager,
    ownPackageName: String,
    recommendedPackages: Set<String>
): List<InstalledApp> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
    val launcherApps = resolveInfos
        .mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName
            if (packageName == ownPackageName) return@mapNotNull null

            val label = info.loadLabel(packageManager).toString().ifBlank { packageName }
            InstalledApp(label = label, packageName = packageName)
        }

    // Fallback: explicitly resolve recommended package IDs in case launcher query visibility is restricted.
    val recommendedInstalledApps = recommendedPackages.mapNotNull { packageName ->
        if (packageName == ownPackageName) return@mapNotNull null
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString().ifBlank { packageName }
            InstalledApp(label = label, packageName = packageName)
        } catch (_: NameNotFoundException) {
            null
        }
    }

    return (launcherApps + recommendedInstalledApps)
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun NotificationSoundConfig.summaryLabel(): String {
    return when (source) {
        NotificationSoundSource.OFF -> "Off"
        NotificationSoundSource.STOCK -> stockSound.label
        NotificationSoundSource.CUSTOM -> customDisplayName ?: "Custom sound"
    }
}

private fun android.content.ContentResolver.takePersistablePermissionSafely(uri: Uri) {
    runCatching {
        takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun android.content.ContentResolver.resolveDisplayName(uri: Uri): String {
    return runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return@runCatching cursor.getString(index)
            }
            null
        }
    }.getOrNull() ?: "Custom sound"
}

@Preview(showBackground = true)
@Composable
fun IntroductionScreenPreview() {
    ADMUTETheme {
        IntroductionScreen(onStartSetup = {})
    }
}

@Preview(showBackground = true)
@Composable
fun DetectionSetupScreenPreview() {
    ADMUTETheme {
        DetectionSetupScreen(
            keywordsPreview = "ad, advertisement, sponsorship, sponsored, promo, promotion, premium, Premium",
            postNotificationsGranted = false,
            notificationAccessGranted = false,
            batteryOptimizationGranted = false,
            onRequestPostNotifications = {},
            onOpenNotificationAccess = {},
            onRequestDisableBatteryOptimization = {}
        )
    }
}