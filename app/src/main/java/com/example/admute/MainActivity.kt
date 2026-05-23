package com.example.admute

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.example.admute.analytics.AnalyticsManager
import com.example.admute.detection.AdKeywordRules
import com.example.admute.detection.WhitelistedApps
import com.example.admute.settings.AdMuteSettings
import com.example.admute.settings.NowPlayingInfo
import com.example.admute.settings.NotificationSoundConfig
import com.example.admute.settings.NotificationSoundSettings
import com.example.admute.settings.NotificationSoundSource
import com.example.admute.settings.StockNotificationSound
import com.example.admute.settings.ThemeMode
import com.example.admute.ui.theme.ADVOIDTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SetupStep {
    INTRO,
    WHITELIST,
    MANAGE_WHITELIST,
    DETECTION,
    COOLDOWN,
    NOTIFICATION_SOUND,
    LOGS,
    MODIFY_KEYWORDS,
    RUNNING,
    ABOUT,
    THEME
}

data class InstalledApp(
    val label: String,
    val packageName: String,
    val iconBitmap: Bitmap?
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
        AnalyticsManager.initialize(this)
        refreshPermissionStates()
        enableEdgeToEdge()
        setContent {
            var themeMode by remember {
                mutableStateOf(AdMuteSettings.getThemeMode(this@MainActivity))
            }
            val systemDarkTheme = isSystemInDarkTheme()
            val shouldUseDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDarkTheme
            }
            ADVOIDTheme(darkTheme = shouldUseDarkTheme) {
                val donationUrl = "https://github.com/sponsors/Longbatman09"
                var setupStep by remember {
                    mutableStateOf(
                        if (AdMuteSettings.isSetupCompleted(this@MainActivity)) SetupStep.RUNNING else SetupStep.INTRO
                    )
                }
                var showSetupDonationDialog by remember { mutableStateOf(false) }
                var isPaused by remember { mutableStateOf(AdMuteSettings.isAppPaused(this@MainActivity)) }
                var selectedWhitelist by remember {
                    mutableStateOf(WhitelistedApps.getSelected(this@MainActivity))
                }
                val isSetupCompleted = remember { AdMuteSettings.isSetupCompleted(this@MainActivity) }
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
                LaunchedEffect(Unit) {
                    while (true) {
                        val latestPaused = AdMuteSettings.isAppPaused(this@MainActivity)
                        if (latestPaused != isPaused) {
                            isPaused = latestPaused
                        }
                        delay(1000L)
                    }
                }



                BackHandler(enabled = setupStep == SetupStep.WHITELIST || setupStep == SetupStep.MANAGE_WHITELIST) {
                    setupStep = if (isSetupCompleted) {
                        SetupStep.RUNNING
                    } else {
                        SetupStep.INTRO
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AnimatedContent(
                        targetState = setupStep,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(350))
                        },
                        label = "screen-transition"
                    ) { targetStep ->
                        when (targetStep) {
                            SetupStep.INTRO -> {
                                LaunchedEffect(Unit) { AnalyticsManager.logScreenView("Introduction", "MainActivity") }
                                IntroductionScreen(
                                    onStartSetup = { setupStep = SetupStep.WHITELIST },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            SetupStep.WHITELIST -> {
                                LaunchedEffect(Unit) { AnalyticsManager.logScreenView("WhitelistSelection", "MainActivity") }
                                WhitelistSelectionScreen(
                                    currentSelection = selectedWhitelist,
                                    onContinue = { selection ->
                                        WhitelistedApps.saveSelected(this@MainActivity, selection)
                                        selectedWhitelist = WhitelistedApps.getSelected(this@MainActivity)
                                        AnalyticsManager.logSettingsChanged("whitelist", selection.size.toString())
                                        setupStep = SetupStep.DETECTION
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            SetupStep.MANAGE_WHITELIST -> {
                                LaunchedEffect(Unit) { AnalyticsManager.logScreenView("ManageWhitelist", "MainActivity") }
                                WhitelistSelectionScreen(
                                    currentSelection = selectedWhitelist,
                                    continueLabel = "Save and return",
                                    onContinue = { selection ->
                                        WhitelistedApps.saveSelected(this@MainActivity, selection)
                                        selectedWhitelist = WhitelistedApps.getSelected(this@MainActivity)
                                        AnalyticsManager.logSettingsChanged("whitelist", selection.size.toString())
                                        setupStep = SetupStep.RUNNING
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            SetupStep.DETECTION -> {
                                LaunchedEffect(Unit) { AnalyticsManager.logScreenView("DetectionSetup", "MainActivity") }
                                DetectionSetupScreen(
                                    keywordsPreview = AdKeywordRules.all(this@MainActivity).joinToString(),
                                    postNotificationsGranted = isPostNotificationsGranted,
                                    notificationAccessGranted = isNotificationAccessGranted,
                                    batteryOptimizationGranted = isBatteryOptimizationIgnored,
                                    onRequestPostNotifications = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    onOpenNotificationAccess = {
                                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    },
                                    onRequestDisableBatteryOptimization = { requestDisableBatteryOptimization() },
                                    onSetupComplete = {
                                        AdMuteSettings.saveSetupCompleted(this@MainActivity, true)
                                        if (!AdMuteSettings.isSetupDonationPromptShown(this@MainActivity)) {
                                            showSetupDonationDialog = true
                                            AdMuteSettings.saveSetupDonationPromptShown(this@MainActivity, true)
                                        }
                                        setupStep = SetupStep.RUNNING
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                            SetupStep.RUNNING, SetupStep.COOLDOWN, SetupStep.LOGS, SetupStep.MODIFY_KEYWORDS, SetupStep.ABOUT, SetupStep.THEME -> Box(modifier = Modifier.fillMaxSize()) {
                                LaunchedEffect(setupStep) {
                                    val screenName = when(setupStep) {
                                        SetupStep.RUNNING -> "Running"
                                        SetupStep.COOLDOWN -> "CooldownSettings"
                                        SetupStep.LOGS -> "AdLogs"
                                        SetupStep.MODIFY_KEYWORDS -> "ModifyKeywords"
                                        SetupStep.ABOUT -> "About"
                                        SetupStep.THEME -> "ThemeSelection"
                                        else -> "Unknown"
                                    }
                                    AnalyticsManager.logScreenView(screenName, "MainActivity")
                                }
                                RunningScreen(
                                    nowPlayingInfo = nowPlayingInfo,
                                    onChangeWhitelist = { setupStep = SetupStep.MANAGE_WHITELIST },
                                    onChangeCooldown = { setupStep = SetupStep.COOLDOWN },
                                    onModifyNotificationSounds = { setupStep = SetupStep.NOTIFICATION_SOUND },
                                    onViewLogs = { setupStep = SetupStep.LOGS },
                                    onModifyKeywords = { setupStep = SetupStep.MODIFY_KEYWORDS },
                                    onViewAbout = { setupStep = SetupStep.ABOUT },
                                    onChangeTheme = { setupStep = SetupStep.THEME },
                                    isPaused = isPaused,
                                    onPauseToggle = {
                                        val newState = !isPaused
                                        isPaused = newState
                                        AdMuteSettings.saveAppPaused(this@MainActivity, newState)
                                        AnalyticsManager.logAction(if (newState) "pause_app" else "resume_app")
                                    },
                                    useDarkTheme = shouldUseDarkTheme,
                                    modifier = Modifier.padding(innerPadding)
                                )
                                AnimatedVisibility(
                                    visible = setupStep == SetupStep.COOLDOWN,
                                    enter = fadeIn(animationSpec = tween(durationMillis = 240)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 220))
                                ) {
                                    CooldownSettingsScreen(
                                        currentCooldownMinutes = cooldownMinutes,
                                        onSave = { minutes ->
                                            AdMuteSettings.saveCooldownMinutes(this@MainActivity, minutes)
                                            cooldownMinutes = AdMuteSettings.getCooldownMinutes(this@MainActivity)
                                            AnalyticsManager.logSettingsChanged("cooldown_minutes", minutes.toString())
                                            setupStep = SetupStep.RUNNING
                                        },
                                        onBack = { setupStep = SetupStep.RUNNING },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                                AnimatedVisibility(
                                    visible = setupStep == SetupStep.LOGS,
                                    enter = fadeIn(animationSpec = tween(durationMillis = 240)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 220))
                                ) {
                                    AdLogsScreen(
                                        onBack = { setupStep = SetupStep.RUNNING },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                                AnimatedVisibility(
                                    visible = setupStep == SetupStep.MODIFY_KEYWORDS,
                                    enter = fadeIn(animationSpec = tween(durationMillis = 240)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 220))
                                ) {
                                    ModifyKeywordsScreen(
                                        onBack = { setupStep = SetupStep.RUNNING },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                                AnimatedVisibility(
                                    visible = setupStep == SetupStep.ABOUT,
                                    enter = fadeIn(animationSpec = tween(durationMillis = 240)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 220))
                                ) {
                                    AboutScreen(
                                        onBack = { setupStep = SetupStep.RUNNING },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                                if (setupStep == SetupStep.THEME) {
                                    ThemeSelectionDialog(
                                        currentThemeMode = themeMode,
                                        onThemeSelected = { newThemeMode ->
                                            themeMode = newThemeMode
                                            AdMuteSettings.saveThemeMode(this@MainActivity, newThemeMode)
                                            AnalyticsManager.logSettingsChanged("theme_mode", newThemeMode.name)
                                        },
                                        onDismiss = { setupStep = SetupStep.RUNNING }
                                    )
                                }
                            }
                            SetupStep.NOTIFICATION_SOUND -> {
                                LaunchedEffect(Unit) { AnalyticsManager.logScreenView("NotificationSoundSettings", "MainActivity") }
                                NotificationSoundSettingsScreen(
                                    currentSettings = notificationSoundMode,
                                    onSave = { settings ->
                                        AdMuteSettings.saveNotificationSoundSettings(this@MainActivity, settings)
                                        notificationSoundMode = AdMuteSettings.getNotificationSoundSettings(this@MainActivity)
                                        AnalyticsManager.logSettingsChanged("notification_sounds", "updated")
                                        setupStep = SetupStep.RUNNING
                                    },
                                    onBack = { setupStep = SetupStep.RUNNING },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
                
                if (
                    setupStep == SetupStep.LOGS ||
                    setupStep == SetupStep.MODIFY_KEYWORDS ||
                    setupStep == SetupStep.ABOUT ||
                    setupStep == SetupStep.NOTIFICATION_SOUND ||
                    setupStep == SetupStep.THEME
                ) {
                    BackHandler {
                        setupStep = SetupStep.RUNNING
                    }
                }

                if (showSetupDonationDialog) {
                    AlertDialog(
                        onDismissRequest = { showSetupDonationDialog = false },
                        title = { Text("Support ADVOID") },
                        text = { Text("Thanks for completing setup. ADVOID is free to use — please consider supporting development with a donation.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    AnalyticsManager.logAction("donate_click")
                                    runCatching {
                                        startActivity(Intent(Intent.ACTION_VIEW, donationUrl.toUri()))
                                    }
                                    showSetupDonationDialog = false
                                }
                            ) {
                                Text("Donate")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSetupDonationDialog = false }) {
                                Text("Maybe later")
                            }
                        }
                    )
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
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

@Composable
private fun AppScreenContainer(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
fun IntroductionScreen(
    onStartSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.48f))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.branding),
                        contentDescription = "ADVOID branding",
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(72.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = "ADVOID is an app which allows users\nto block disruptive ad audio automatically.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 38.sp
                    )
                    Text(
                        text = "ADVOID helps users enjoy uninterrupted listening by muting ads from selected music apps and restoring media volume smoothly.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 38.sp
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onStartSetup,
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(
                    text = "Start Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun WhitelistSelectionScreen(
    currentSelection: Set<String>,
    onContinue: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    continueLabel: String = "Continue"
) {
    var otherAppsSearchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var keyboardWasVisibleInSearchSession by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val recommendedPackages = remember { WhitelistedApps.recommendedPackages().values.toSet() }
    // If there is no saved selection (first-run), default to selecting all recommended packages
    var selectedPackages by remember(currentSelection, recommendedPackages) {
        mutableStateOf(if (currentSelection.isEmpty()) recommendedPackages else currentSelection)
    }
    val context = LocalContext.current
    val installedApps by produceState<List<InstalledApp>?>(initialValue = null, context, recommendedPackages) {
        value = withContext(Dispatchers.IO) {
            loadInstalledApps(context.packageManager, context.packageName, recommendedPackages)
        }
    }
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val shouldPrioritizeOtherApps = isSearchFocused && isKeyboardVisible
    val appsFadeInAlpha by animateFloatAsState(
        targetValue = if (installedApps == null) 0f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "apps-fade-in"
    )

    if (installedApps == null) {
        WhitelistLoadingScreen(modifier = modifier)
        return
    }

    val resolvedApps = installedApps.orEmpty()
    val recommendedApps = remember(resolvedApps) { resolvedApps.filter { WhitelistedApps.isRecommended(it.packageName, it.label) } }
    val otherApps = remember(resolvedApps) { resolvedApps.filterNot { WhitelistedApps.isRecommended(it.packageName, it.label) } }
    val filteredOtherApps = remember(otherApps, otherAppsSearchQuery) {
        val normalizedQuery = otherAppsSearchQuery.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            otherApps
        } else {
            otherApps.filter { app ->
                app.label.lowercase().contains(normalizedQuery) ||
                    app.packageName.lowercase().contains(normalizedQuery)
            }
        }
    }
    LaunchedEffect(isKeyboardVisible, isSearchFocused) {
        if (isSearchFocused && isKeyboardVisible) {
            keyboardWasVisibleInSearchSession = true
        }
        if (isSearchFocused && !isKeyboardVisible && keyboardWasVisibleInSearchSession) {
            focusManager.clearFocus(force = true)
            isSearchFocused = false
            keyboardWasVisibleInSearchSession = false
        }
        if (!isSearchFocused) {
            keyboardWasVisibleInSearchSession = false
        }
    }
    BackHandler(enabled = isSearchFocused) {
        focusManager.clearFocus(force = true)
    }

    AppScreenContainer(
        title = "Choose whitelisted apps",
        subtitle = "Only selected apps are scanned for ad notifications.",
        modifier = modifier.alpha(appsFadeInAlpha)
    ) {
        ElevatedCard(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    AnimatedVisibility(
                        visible = !shouldPrioritizeOtherApps,
                        enter = fadeIn(animationSpec = tween(250)) + slideInVertically(
                            animationSpec = tween(250),
                            initialOffsetY = { -it / 4 }
                        ),
                        exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionTitle("Recommended apps")
                            if (recommendedApps.isEmpty()) {
                                Text("No recommended apps found on this device.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                recommendedApps.forEach { app ->
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
                        }
                    }
                }
                item {
                    AnimatedVisibility(
                        visible = !shouldPrioritizeOtherApps,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(180))
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    SectionTitle("Other installed apps")
                }
                item {
                    OutlinedTextField(
                        value = otherAppsSearchQuery,
                        onValueChange = { otherAppsSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(force = true) }
                        ),
                        label = { Text("Search other apps") },
                        placeholder = { Text("Type app name or package") }
                    )
                }
                if (filteredOtherApps.isEmpty()) {
                    item {
                        Text(
                            text = if (otherAppsSearchQuery.isBlank()) {
                                "No other installed apps found."
                            } else {
                                "No apps match \"$otherAppsSearchQuery\"."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(filteredOtherApps, key = { it.packageName }) { app ->
                    AppSelectionRow(
                        app = app,
                        checked = app.packageName in selectedPackages,
                        onCheckedChange = { checked ->
                            selectedPackages = if (checked) selectedPackages + app.packageName else selectedPackages - app.packageName
                        }
                    )
                }
            }
        }

        Button(onClick = { onContinue(selectedPackages) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text(continueLabel)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun WhitelistLoadingScreen(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton-transition")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton-alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Choose whitelisted apps",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Scanning installed apps...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(7) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulseAlpha)
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.72f)
                                        .size(width = 176.dp, height = 22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulseAlpha)
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .size(width = 300.dp, height = 18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulseAlpha * 0.9f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectionRow(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            if (app.iconBitmap != null) {
                Image(
                    bitmap = app.iconBitmap.asImageBitmap(),
                    contentDescription = "${app.label} icon",
                    modifier = Modifier
                        .padding(start = 2.dp, end = 8.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp, end = 8.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.label.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Column {
                Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionStepScreen(
    iconResId: Int,
    title: String,
    description: String,
    actionDescription: String,
    granted: Boolean,
    grantActionLabel: String,
    onGrantAction: () -> Unit,
    onContinueAction: () -> Unit,
    footerText: String
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = title,
            modifier = Modifier.size(64.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = actionDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (granted) onContinueAction() else onGrantAction()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (granted) "Continue" else grantActionLabel)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = footerText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
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
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(0) }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            slideInHorizontally(animationSpec = tween(400)) { width -> width } + fadeIn(animationSpec = tween(400)) togetherWith
            slideOutHorizontally(animationSpec = tween(400)) { width -> -width } + fadeOut(animationSpec = tween(400))
        },
        label = "permission-transition",
        modifier = modifier
    ) { step ->
        when (step) {
            0 -> {
                PermissionStepScreen(
                    iconResId = R.drawable.bell,
                    title = "Push notifications",
                    description = "ADVOID needs push notifications to let you know about background status.",
                    actionDescription = "Please grant the notification permission.",
                    granted = postNotificationsGranted,
                    grantActionLabel = "Grant permission",
                    onGrantAction = onRequestPostNotifications,
                    onContinueAction = { currentStep = 1 },
                    footerText = "ADVOID will not be able to show background status without this permission."
                )
            }
            1 -> {
                PermissionStepScreen(
                    iconResId = R.drawable.noti,
                    title = "Notification access",
                    description = "ADVOID needs notification access to detect ads from other apps.",
                    actionDescription = "Open settings and allow notification access for ADVOID.",
                    granted = notificationAccessGranted,
                    grantActionLabel = "Open access settings",
                    onGrantAction = onOpenNotificationAccess,
                    onContinueAction = { currentStep = 2 },
                    footerText = "ADVOID will not function correctly without this permission."
                )
            }
            2 -> {
                PermissionStepScreen(
                    iconResId = R.drawable.battery_opt,
                    title = "Battery optimization",
                    description = "Disable battery optimization so ADVOID can run continuously without being killed by the system.",
                    actionDescription = "Allow ADVOID to run unrestricted.",
                    granted = batteryOptimizationGranted,
                    grantActionLabel = "Disable optimization",
                    onGrantAction = onRequestDisableBatteryOptimization,
                    onContinueAction = { currentStep = 3 },
                    footerText = "ADVOID may be stopped by the system without this permission."
                )
            }
            3 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.tick),
                        contentDescription = "All set!",
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "All set!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Permissions granted. ADVOID can now monitor selected apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Keyword list preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = keywordsPreview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onSetupComplete,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Complete Setup")
                    }
                }
            }
        }
    }
}


@Composable
fun RunningScreen(
    nowPlayingInfo: NowPlayingInfo?,
    onChangeWhitelist: () -> Unit,
    onChangeCooldown: () -> Unit,
    onModifyNotificationSounds: () -> Unit,
    onViewLogs: () -> Unit,
    onModifyKeywords: () -> Unit,
    onViewAbout: () -> Unit,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    useDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    onChangeTheme: () -> Unit = {}
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionButtonLeftAligned("Manage whitelist apps", onChangeWhitelist, enableMarquee = true, iconRes = R.drawable.wl, useDarkTheme = useDarkTheme)
                    ActionButtonLeftAligned("Change cooldown", onChangeCooldown, enableMarquee = true, iconRes = R.drawable.cd, useDarkTheme = useDarkTheme)
                    ActionButtonLeftAligned("Modify notification sounds", onModifyNotificationSounds, enableMarquee = true, iconRes = R.drawable.ns, useDarkTheme = useDarkTheme)
                    ActionButtonLeftAligned("Change theme", onChangeTheme, enableMarquee = true, iconRes = R.drawable.theme, useDarkTheme = useDarkTheme)
                    ActionButtonLeftAligned("View ad detection logs", onViewLogs, enableMarquee = true, iconRes = R.drawable.log, useDarkTheme = useDarkTheme)
                    ActionButtonLeftAligned("Modify Keywords", onModifyKeywords, enableMarquee = true, iconRes = R.drawable.log, useDarkTheme = useDarkTheme)
                    ActionButtonLeftAligned("About", onViewAbout, enableMarquee = true, iconRes = R.drawable.about, useDarkTheme = useDarkTheme)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NowPlayingInfoBox(nowPlayingInfo = nowPlayingInfo)
                    Spacer(modifier = Modifier.weight(1f))
                    ActionButtonLeftAligned(
                        if (isPaused) "Resume ADVOID" else "Pause ADVOID",
                        onPauseToggle,
                        enableMarquee = true,
                        iconRes = R.drawable.pause,
                        useDarkTheme = useDarkTheme
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "ADVOID IS CURRENTLY RUNNING",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionButtonLeftAligned("Manage whitelist apps", onChangeWhitelist, iconRes = R.drawable.wl, useDarkTheme = useDarkTheme)
                ActionButtonLeftAligned("Change cooldown", onChangeCooldown, iconRes = R.drawable.cd, useDarkTheme = useDarkTheme)
                ActionButtonLeftAligned("Modify notification sounds", onModifyNotificationSounds, iconRes = R.drawable.ns, useDarkTheme = useDarkTheme)
                ActionButtonLeftAligned("Change theme", onChangeTheme, iconRes = R.drawable.theme, useDarkTheme = useDarkTheme)
                ActionButtonLeftAligned("View ad detection logs", onViewLogs, iconRes = R.drawable.log, useDarkTheme = useDarkTheme)
                ActionButtonLeftAligned("Modify Keywords", onModifyKeywords, iconRes = R.drawable.log, useDarkTheme = useDarkTheme)
                ActionButtonLeftAligned("About", onViewAbout, iconRes = R.drawable.about, useDarkTheme = useDarkTheme)
                NowPlayingInfoBox(nowPlayingInfo = nowPlayingInfo)
                Spacer(modifier = Modifier.weight(1f))
                    ActionButtonLeftAligned(
                        if (isPaused) "Resume ADVOID" else "Pause ADVOID",
                        onPauseToggle,
                        iconRes = R.drawable.pause,
                        useDarkTheme = useDarkTheme
                    )
                // Bottom-centered running status
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "ADVOID IS CURRENTLY RUNNING",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Pause Overlay
        if (isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { onPauseToggle() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "ADVOID IS PAUSED",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Ad block is temporarily paused\nclick to resume",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun AdLogsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(AdMuteSettings.getAdLogs(context)) }
    var showClearLogsDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("HH:mm:ss, dd MMM", Locale.getDefault()) }

    // Calculate total duration
    val totalDurationSeconds = remember(logs) {
        logs.sumOf { entry ->
            if (entry.endTime > entry.timestamp) {
                (entry.endTime - entry.timestamp) / 1000
            } else {
                0L
            }
        }
    }

    val totalDurationFormatted = remember(totalDurationSeconds) {
        when {
            totalDurationSeconds < 60 -> "${totalDurationSeconds}s"
            totalDurationSeconds < 3600 -> {
                val minutes = totalDurationSeconds / 60
                val seconds = totalDurationSeconds % 60
                "${minutes}m ${seconds}s"
            }
            else -> {
                val hours = totalDurationSeconds / 3600
                val minutes = (totalDurationSeconds % 3600) / 60
                val seconds = totalDurationSeconds % 60
                "${hours}h ${minutes}m ${seconds}s"
            }
        }
    }

    AppScreenContainer(
        title = "Ad Detection Logs",
        subtitle = "History of detected ads and muted apps.",
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        if (logs.isNotEmpty()) {
            // Total time saved box
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Total Time ADBlock Saved",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = totalDurationFormatted,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No logs yet", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = entry.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = dateFormatter.format(Date(entry.timestamp)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (entry.endTime > entry.timestamp) {
                                            val durationSec = (entry.endTime - entry.timestamp) / 1000
                                            Text(
                                                text = "Duration: ${durationSec}s",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = entry.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = entry.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    AnalyticsManager.logAction("clear_logs")
                    showClearLogsDialog = true
                },
                enabled = logs.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) {
                Text("Clear Logs")
            }
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Back")
            }
        }
    }

    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text("Clear logs?") },
            text = { Text("Are you sure you want to clear all AD Detection Logs? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AdMuteSettings.clearAdLogs(context)
                        logs = emptyList()
                        showClearLogsDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ActionButtonLeftAligned(
    title: String,
    onClick: () -> Unit,
    enableMarquee: Boolean = false,
    iconRes: Int? = null,
    useDarkTheme: Boolean = false
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val themedIconBitmap = remember(iconRes, useDarkTheme, context, configuration) {
        iconRes?.let { resId ->
            val config = Configuration(configuration).apply {
                val nightMode = if (useDarkTheme) {
                    Configuration.UI_MODE_NIGHT_YES
                } else {
                    Configuration.UI_MODE_NIGHT_NO
                }
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            val themedContext = context.createConfigurationContext(config)
            ContextCompat.getDrawable(themedContext, resId)?.toBitmapSafely()?.asImageBitmap()
                ?: ContextCompat.getDrawable(context, resId)?.toBitmapSafely()?.asImageBitmap()
        }
    }
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enableMarquee) {
                        Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately
                        )
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconRes != null && themedIconBitmap != null) {
                Image(
                    bitmap = themedIconBitmap,
                    contentDescription = "$title icon",
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NowPlayingInfoBox(nowPlayingInfo: NowPlayingInfo?) {
    val context = LocalContext.current
    val albumArtBitmap = remember(
        nowPlayingInfo?.albumArtPath,
        nowPlayingInfo?.updatedAtMs,
        nowPlayingInfo?.packageName,
        nowPlayingInfo?.title
    ) {
        nowPlayingInfo?.albumArtPath?.takeIf { it.isNotBlank() }?.let { path ->
            try {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeFile(path, opts)
            } catch (_: Throwable) {
                null
            }
            }
        }
    val appIconBitmap = remember(nowPlayingInfo?.packageName) {
        nowPlayingInfo?.packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmapSafely()
            }.getOrNull()
        }
    }
    val noMusicPlaying = nowPlayingInfo == null ||
        (
            (nowPlayingInfo.title.isBlank() || nowPlayingInfo.title.equals("Unknown title", true)) &&
                (nowPlayingInfo.text.isBlank() || nowPlayingInfo.text.equals("Unknown track info", true)) &&
                albumArtBitmap == null
            )

    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        if (noMusicPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No media is playing currently",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            val info = nowPlayingInfo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                ) {
                    if (albumArtBitmap != null) {
                        Image(
                            bitmap = albumArtBitmap.asImageBitmap(),
                            contentDescription = "Album art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "♪",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    appIconBitmap?.let { icon ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(24.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = "${info.appName} icon",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = info.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (info.subText.isNotBlank()) {
                        Text(
                            text = info.subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cooldown settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Swipe the number up/down to set 1 to 10 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CooldownNumberRoller(
                        minutes = cooldownMinutes,
                        onValueChange = { cooldownMinutes = it }
                    )
                    Text(
                        text = "min",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Button(
                    onClick = { onSave(cooldownMinutes.coerceIn(1, 10)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun CooldownNumberRoller(
    minutes: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val minValue = 1
    val maxValue = 10
    var accumulatedDrag by remember { mutableStateOf(0f) }

    val previousValue = (minutes - 1).takeIf { it >= minValue }
    val nextValue = (minutes + 1).takeIf { it <= maxValue }
    val previousAlpha by animateFloatAsState(targetValue = if (previousValue == null) 0f else 0.5f, label = "prev-alpha")
    val nextAlpha by animateFloatAsState(targetValue = if (nextValue == null) 0f else 0.5f, label = "next-alpha")

    Column(
        modifier = modifier.pointerInput(minutes) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    accumulatedDrag += dragAmount
                    val stepThresholdPx = 24f

                    while (abs(accumulatedDrag) >= stepThresholdPx) {
                        val steppingDown = accumulatedDrag > 0
                        val candidate = if (steppingDown) minutes - 1 else minutes + 1
                        onValueChange(candidate.coerceIn(minValue, maxValue))
                        accumulatedDrag += if (steppingDown) -stepThresholdPx else stepThresholdPx
                    }
                    change.consume()
                },
                onDragEnd = { accumulatedDrag = 0f },
                onDragCancel = { accumulatedDrag = 0f }
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = previousValue?.toString() ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(previousAlpha)
        )
        AnimatedContent(
            targetState = minutes,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInVertically { it / 2 } + fadeIn() + scaleIn(initialScale = 0.82f))
                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut() + scaleOut(targetScale = 1.18f))
                } else {
                    (slideInVertically { -it / 2 } + fadeIn() + scaleIn(initialScale = 0.82f))
                        .togetherWith(slideOutVertically { it / 2 } + fadeOut() + scaleOut(targetScale = 1.18f))
                }
            },
            label = "cooldown-roller"
        ) { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = nextValue?.toString() ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(nextAlpha)
        )
    }
}

@Composable
private fun NotificationSoundSettingsScreen(
    currentSettings: NotificationSoundSettings,
    onSave: (NotificationSoundSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inSound by remember(currentSettings) { mutableStateOf(currentSettings.inSound) }
    var outSound by remember(currentSettings) { mutableStateOf(currentSettings.outSound) }
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
    }

    fun playPreview(config: NotificationSoundConfig) {
        stopPreview()
        when (config.source) {
            NotificationSoundSource.OFF -> Unit
            NotificationSoundSource.STOCK -> {
                runCatching {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    val player = MediaPlayer.create(context, config.stockSound.resId, attributes, 0) ?: return@runCatching
                    previewPlayer = player
                    player.setOnCompletionListener {
                        if (previewPlayer === it) previewPlayer = null
                        it.release()
                    }
                    player.setOnErrorListener { mp, _, _ ->
                        if (previewPlayer === mp) previewPlayer = null
                        mp.release()
                        true
                    }
                    player.start()
                }
            }
            NotificationSoundSource.CUSTOM -> {
                val uriText = config.customUri ?: return
                runCatching {
                    val player = MediaPlayer().apply {
                        setDataSource(context, uriText.toUri())
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setOnCompletionListener {
                            if (previewPlayer === it) previewPlayer = null
                            it.release()
                        }
                        setOnErrorListener { mp, _, _ ->
                            if (previewPlayer === mp) previewPlayer = null
                            mp.release()
                            true
                        }
                        prepare()
                        previewPlayer = this
                        start()
                    }
                    player
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    BackHandler {
        stopPreview()
        onBack()
    }

    val pickInSoundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val updated = uri?.let {
            context.contentResolver.takePersistablePermissionSafely(it)
            inSound.copy(
                source = NotificationSoundSource.CUSTOM,
                customUri = it.toString(),
                customDisplayName = context.contentResolver.resolveDisplayName(it)
            )
        } ?: inSound
        inSound = updated
        if (uri != null) playPreview(updated)
    }

    val pickOutSoundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val updated = uri?.let {
            context.contentResolver.takePersistablePermissionSafely(it)
            outSound.copy(
                source = NotificationSoundSource.CUSTOM,
                customUri = it.toString(),
                customDisplayName = context.contentResolver.resolveDisplayName(it)
            )
        } ?: outSound
        outSound = updated
        if (uri != null) playPreview(updated)
    }

    AppScreenContainer(
        title = "Notification sounds",
        subtitle = "Configure IN (ad detected) and OUT (ad ended) sounds.",
        modifier = modifier
    ) {
        val inSoundSection: @Composable () -> Unit = {
            SoundConfigSection(
                title = "IN sound",
                config = inSound,
                stockOptions = listOf(StockNotificationSound.S1_IN, StockNotificationSound.S2_IN),
                onSelectOff = {
                    inSound = inSound.copy(source = NotificationSoundSource.OFF)
                    stopPreview()
                },
                onSelectStock = { selected ->
                    val updated = inSound.copy(source = NotificationSoundSource.STOCK, stockSound = selected)
                    inSound = updated
                    playPreview(updated)
                },
                onSelectCustom = {
                    if (inSound.customUri != null) {
                        val updated = inSound.copy(source = NotificationSoundSource.CUSTOM)
                        inSound = updated
                        playPreview(updated)
                    }
                },
                onPickCustom = { pickInSoundLauncher.launch(arrayOf("audio/*")) },
                onClearCustom = {
                    inSound = inSound.copy(customUri = null, customDisplayName = null, source = NotificationSoundSource.OFF)
                    stopPreview()
                }
            )
        }

        val outSoundSection: @Composable () -> Unit = {
            SoundConfigSection(
                title = "OUT sound",
                config = outSound,
                stockOptions = listOf(StockNotificationSound.S1_OUT, StockNotificationSound.S2_OUT),
                onSelectOff = {
                    outSound = outSound.copy(source = NotificationSoundSource.OFF)
                    stopPreview()
                },
                onSelectStock = { selected ->
                    val updated = outSound.copy(source = NotificationSoundSource.STOCK, stockSound = selected)
                    outSound = updated
                    playPreview(updated)
                },
                onSelectCustom = {
                    if (outSound.customUri != null) {
                        val updated = outSound.copy(source = NotificationSoundSource.CUSTOM)
                        outSound = updated
                        playPreview(updated)
                    }
                },
                onPickCustom = { pickOutSoundLauncher.launch(arrayOf("audio/*")) },
                onClearCustom = {
                    outSound = outSound.copy(customUri = null, customDisplayName = null, source = NotificationSoundSource.OFF)
                    stopPreview()
                }
            )
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    inSoundSection()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    outSoundSection()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                inSoundSection()
                outSoundSection()
            }
        }
        Button(
            onClick = { onSave(NotificationSoundSettings(inSound = inSound, outSound = outSound)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Save")
        }
        OutlinedButton(
            onClick = {
                stopPreview()
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Back")
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SoundConfigSection(
    title: String,
    config: NotificationSoundConfig,
    stockOptions: List<StockNotificationSound>,
    onSelectOff: () -> Unit,
    onSelectStock: (StockNotificationSound) -> Unit,
    onSelectCustom: () -> Unit,
    onPickCustom: () -> Unit,
    onClearCustom: () -> Unit
) {
    data class SoundChoice(val label: String, val action: () -> Unit)
    val choices = remember(config.customUri, config.customDisplayName, stockOptions) {
        buildList {
            add(SoundChoice(label = "Off", action = onSelectOff))
            stockOptions.forEach { stock ->
                add(SoundChoice(label = stock.label, action = { onSelectStock(stock) }))
            }
            if (config.customUri != null) {
                add(SoundChoice(label = config.customDisplayName ?: "Custom sound", action = onSelectCustom))
            }
        }
    }
    val selectedLabel = config.summaryLabel()
    var dropdownExpanded by remember { mutableStateOf(false) }

    ElevatedCard(shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "- Select sound",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    choices.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.label) },
                            onClick = {
                                dropdownExpanded = false
                                choice.action.invoke()
                            }
                        )
                    }
                }
            }
            OutlinedButton(onClick = onPickCustom, modifier = Modifier.fillMaxWidth()) {
                Text("Upload custom sound")
            }
            if (config.customUri != null) {
                OutlinedButton(onClick = onClearCustom, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear custom sound")
                }
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

    val launcherApps = resolveInfos.mapNotNull { info ->
        val activityInfo = info.activityInfo ?: return@mapNotNull null
        val packageName = activityInfo.packageName
        if (packageName == ownPackageName) return@mapNotNull null
        val label = info.loadLabel(packageManager).toString().ifBlank { packageName }
        val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
        InstalledApp(label, packageName, icon?.toBitmapSafely())
    }

    val recommendedInstalledApps = recommendedPackages.mapNotNull { packageName ->
        if (packageName == ownPackageName) return@mapNotNull null
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString().ifBlank { packageName }
            val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            InstalledApp(label, packageName, icon?.toBitmapSafely())
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

private fun Drawable.toBitmapSafely(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap
    }
    val width = intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

private fun android.content.ContentResolver.takePersistablePermissionSafely(uri: Uri) {
    runCatching {
        takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

@Composable
fun ModifyKeywordsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val defaultKeywords = remember { AdKeywordRules.default() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val enabledDefaultKeywords = remember {
        mutableStateOf(
            defaultKeywords.filterNot {
                AdMuteSettings.getDisabledDefaultAdKeywords(context).contains(it)
            }
        )
    }
    var lastListIndexBeforeInput by remember { mutableStateOf(0) }
    var lastListOffsetBeforeInput by remember { mutableStateOf(0) }
    var customKeywords by remember { mutableStateOf(AdMuteSettings.getCustomAdKeywords(context)) }
    var newKeyword by remember { mutableStateOf("") }
    var duplicateOrEmptyError by remember { mutableStateOf<String?>(null) }
    var pendingDefaultDeleteKeyword by remember { mutableStateOf<String?>(null) }
    var showRestoreDefaultsDialog by remember { mutableStateOf(false) }

    AppScreenContainer(
        title = "Modify Keywords",
        subtitle = "Add or remove custom ad-detection keywords.",
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        ElevatedCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Default keywords",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(enabledDefaultKeywords.value + customKeywords) { keyword ->
                        val isDefault = enabledDefaultKeywords.value.contains(keyword)
                        ElevatedCard(shape = RoundedCornerShape(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$keyword (${if (isDefault) "Default" else "Custom"})",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        if (isDefault) {
                                            pendingDefaultDeleteKeyword = keyword
                                        } else {
                                            AdMuteSettings.removeCustomAdKeyword(context, keyword)
                                            customKeywords = AdMuteSettings.getCustomAdKeywords(context)
                                        }
                                    }
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = {
                        newKeyword = it
                        duplicateOrEmptyError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            coroutineScope.launch {
                                if (focusState.isFocused) {
                                    lastListIndexBeforeInput = listState.firstVisibleItemIndex
                                    lastListOffsetBeforeInput = listState.firstVisibleItemScrollOffset
                                    listState.animateScrollToItem(0)
                                } else {
                                    listState.animateScrollToItem(lastListIndexBeforeInput, lastListOffsetBeforeInput)
                                }
                            }
                        },
                    singleLine = true,
                    label = { Text("New keyword") }
                )
                duplicateOrEmptyError?.let { errorText ->
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = {
                        val normalized = newKeyword.trim().lowercase()
                        val added = AdMuteSettings.addCustomAdKeyword(context, newKeyword)
                        if (added) {
                            customKeywords = AdMuteSettings.getCustomAdKeywords(context)
                            AnalyticsManager.logSettingsChanged("custom_keyword_added", normalized)
                            newKeyword = ""
                            duplicateOrEmptyError = null
                        } else {
                            duplicateOrEmptyError = "Keyword is empty or already exists."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Keyword")
                }
                OutlinedButton(
                    onClick = { showRestoreDefaultsDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore Default Keywords")
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }

    pendingDefaultDeleteKeyword?.let { keyword ->
        AlertDialog(
            onDismissRequest = { pendingDefaultDeleteKeyword = null },
            title = { Text("Delete default keyword?") },
            text = { Text("Are you sure you want to delete \"$keyword\" from detection keywords?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AdMuteSettings.disableDefaultAdKeyword(context, keyword)
                        enabledDefaultKeywords.value = defaultKeywords.filterNot {
                            AdMuteSettings.getDisabledDefaultAdKeywords(context).contains(it)
                        }
                        pendingDefaultDeleteKeyword = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDefaultDeleteKeyword = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRestoreDefaultsDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDefaultsDialog = false },
            title = { Text("Restore default keywords?") },
            text = { Text("Are you sure you want to restore all default keywords? Any deleted default keywords will be added back.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AdMuteSettings.restoreAllDefaultAdKeywords(context)
                        enabledDefaultKeywords.value = defaultKeywords
                        showRestoreDefaultsDialog = false
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDefaultsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val donationUrl = "https://github.com/sponsors/Longbatman09"
    val donateLogo = painterResource(id = R.drawable.donate)
    val versionLabel = remember(context) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        }.getOrDefault("1.0")
    }

    AppScreenContainer(
        title = "About ADVOID",
        subtitle = "Information about the application.",
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        ElevatedCard(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.branding),
                            contentDescription = "ADVOID branding",
                            modifier = Modifier
                                .width(140.dp)
                                .height(48.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = "Version $versionLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ADVOID is designed to detect ad notifications from your selected music apps and automatically mute your media volume, bringing peace to your listening experience.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Credits",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "B Vishal Chandrakanth",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Head of Coco Copi Developers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = donateLogo,
                                    contentDescription = "Donate",
                                    modifier = Modifier
                                        .height(60.dp)
                                        .clickable {
                                            AnalyticsManager.logAction("donate_click")
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, donationUrl.toUri())
                                                )
                                            }
                                        },
                                    contentScale = ContentScale.Fit
                                )
                                Text(
                                    text = "Support the AD free app by donating",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Image(
                        painter = painterResource(id = R.drawable.branding),
                        contentDescription = "ADVOID branding",
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(72.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = "Version $versionLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ADVOID is designed to detect ad notifications from your selected music apps and automatically mute your media volume, bringing peace to your listening experience.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Credits",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "B Vishal Chandrakanth",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Head of Coco Copi Developers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Image(
                        painter = donateLogo,
                        contentDescription = "Donate",
                        modifier = Modifier
                            .height(60.dp)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, donationUrl.toUri())
                                    )
                                }
                            },
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = "Support the AD free app by donating",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Back")
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentThemeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Theme settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Swipe up/down to select System, Light, or Dark.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                ThemeRoller(
                    currentTheme = currentThemeMode,
                    onThemeChange = onThemeSelected
                )
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun ThemeRoller(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val themes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val themeIndex = themes.indexOf(currentTheme).takeIf { it >= 0 } ?: 0
    val minThemeIndex = 0
    val maxThemeIndex = themes.size - 1
    var accumulatedDrag by remember { mutableStateOf(0f) }

    val previousThemeIndex = (themeIndex - 1).takeIf { it >= minThemeIndex }
    val nextThemeIndex = (themeIndex + 1).takeIf { it <= maxThemeIndex }
    val previousTheme = previousThemeIndex?.let { themes.getOrNull(it) }
    val nextTheme = nextThemeIndex?.let { themes.getOrNull(it) }
    val previousAlpha by animateFloatAsState(targetValue = if (previousTheme == null) 0f else 0.5f, label = "prev-theme-alpha")
    val nextAlpha by animateFloatAsState(targetValue = if (nextTheme == null) 0f else 0.5f, label = "next-theme-alpha")

    Column(
        modifier = modifier.pointerInput(themeIndex) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    accumulatedDrag += dragAmount
                    val stepThresholdPx = 24f

                    while (abs(accumulatedDrag) >= stepThresholdPx) {
                        val steppingDown = accumulatedDrag > 0
                        val candidate = if (steppingDown) themeIndex - 1 else themeIndex + 1
                        val newIndex = candidate.coerceIn(minThemeIndex, maxThemeIndex)
                        onThemeChange(themes[newIndex])
                        accumulatedDrag += if (steppingDown) -stepThresholdPx else stepThresholdPx
                    }
                    change.consume()
                },
                onDragEnd = { accumulatedDrag = 0f },
                onDragCancel = { accumulatedDrag = 0f }
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = previousTheme?.label ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(previousAlpha)
        )
        AnimatedContent(
            targetState = currentTheme,
            transitionSpec = {
                if (themes.indexOf(targetState) > themes.indexOf(initialState)) {
                    (slideInVertically { it / 2 } + fadeIn() + scaleIn(initialScale = 0.82f))
                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut() + scaleOut(targetScale = 1.18f))
                } else {
                    (slideInVertically { -it / 2 } + fadeIn() + scaleIn(initialScale = 0.82f))
                        .togetherWith(slideOutVertically { it / 2 } + fadeOut() + scaleOut(targetScale = 1.18f))
                }
            },
            label = "theme-roller"
        ) { theme ->
            Text(
                text = theme.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = nextTheme?.label ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(nextAlpha)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IntroductionScreenPreview() {
    ADVOIDTheme {
        IntroductionScreen(onStartSetup = {})
    }
}
