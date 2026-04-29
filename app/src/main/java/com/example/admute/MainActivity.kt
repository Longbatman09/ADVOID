package com.example.admute

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.core.content.ContextCompat
import com.example.admute.detection.AdKeywordRules
import com.example.admute.detection.WhitelistedApps
import com.example.admute.settings.AdMuteSettings
import com.example.admute.settings.NowPlayingInfo
import com.example.admute.settings.NotificationSoundConfig
import com.example.admute.settings.NotificationSoundSettings
import com.example.admute.settings.NotificationSoundSource
import com.example.admute.settings.StockNotificationSound
import com.example.admute.settings.ThemeMode
import com.example.admute.ui.theme.ADMUTETheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
            ADMUTETheme(darkTheme = shouldUseDarkTheme) {
                var setupStep by remember {
                    mutableStateOf(
                        if (AdMuteSettings.isSetupCompleted(this@MainActivity)) SetupStep.RUNNING else SetupStep.INTRO
                    )
                }
                var isPaused by remember { mutableStateOf(false) }
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
                            SetupStep.INTRO -> IntroductionScreen(
                                onStartSetup = { setupStep = SetupStep.WHITELIST },
                                modifier = Modifier.padding(innerPadding)
                            )
                            SetupStep.WHITELIST -> WhitelistSelectionScreen(
                                currentSelection = selectedWhitelist,
                                onContinue = { selection ->
                                    WhitelistedApps.saveSelected(this@MainActivity, selection)
                                    selectedWhitelist = WhitelistedApps.getSelected(this@MainActivity)
                                    setupStep = SetupStep.DETECTION
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                            SetupStep.MANAGE_WHITELIST -> WhitelistSelectionScreen(
                                currentSelection = selectedWhitelist,
                                continueLabel = "Save and return",
                                onContinue = { selection ->
                                    WhitelistedApps.saveSelected(this@MainActivity, selection)
                                    selectedWhitelist = WhitelistedApps.getSelected(this@MainActivity)
                                    setupStep = SetupStep.RUNNING
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                            SetupStep.DETECTION -> DetectionSetupScreen(
                                keywordsPreview = AdKeywordRules.all().joinToString(),
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
                                modifier = Modifier.padding(innerPadding)
                            )
                            SetupStep.RUNNING, SetupStep.COOLDOWN, SetupStep.LOGS, SetupStep.ABOUT, SetupStep.THEME -> Box(modifier = Modifier.fillMaxSize()) {
                                RunningScreen(
                                    nowPlayingInfo = nowPlayingInfo,
                                    onChangeWhitelist = { setupStep = SetupStep.MANAGE_WHITELIST },
                                    onChangeCooldown = { setupStep = SetupStep.COOLDOWN },
                                    onModifyNotificationSounds = { setupStep = SetupStep.NOTIFICATION_SOUND },
                                    onViewLogs = { setupStep = SetupStep.LOGS },
                                    onViewAbout = { setupStep = SetupStep.ABOUT },
                                    onChangeTheme = { setupStep = SetupStep.THEME },
                                    isPaused = isPaused,
                                    onPauseToggle = { isPaused = !isPaused },
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
                                        },
                                        onDismiss = { setupStep = SetupStep.RUNNING }
                                    )
                                }
                            }
                            SetupStep.NOTIFICATION_SOUND -> NotificationSoundSettingsScreen(
                                currentSettings = notificationSoundMode,
                                onSave = { settings ->
                                    AdMuteSettings.saveNotificationSoundSettings(this@MainActivity, settings)
                                    notificationSoundMode = AdMuteSettings.getNotificationSoundSettings(this@MainActivity)
                                    setupStep = SetupStep.RUNNING
                                },
                                onBack = { setupStep = SetupStep.RUNNING },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
                
                if (
                    setupStep == SetupStep.LOGS ||
                    setupStep == SetupStep.ABOUT ||
                    setupStep == SetupStep.NOTIFICATION_SOUND ||
                    setupStep == SetupStep.THEME
                ) {
                    BackHandler {
                        setupStep = SetupStep.RUNNING
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
    AppScreenContainer(
        title = "Welcome to ADMUTE",
        subtitle = "Detects ad notifications and mutes media automatically.",
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "How it works",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "1. Select music apps to monitor.\n2. Grant required permissions.\n3. ADMUTE handles mute/unmute with cooldown.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onStartSetup, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
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
    var showApps by remember { mutableStateOf(false) }
    var otherAppsSearchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var keyboardWasVisibleInSearchSession by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val recommendedPackages = remember { WhitelistedApps.recommendedPackages().values.toSet() }
    var selectedPackages by remember(currentSelection) { mutableStateOf(currentSelection) }
    val context = LocalContext.current
    val installedApps by produceState<List<InstalledApp>?>(initialValue = null, context, recommendedPackages) {
        value = withContext(Dispatchers.IO) {
            loadInstalledApps(context.packageManager, context.packageName, recommendedPackages)
        }
    }
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val shouldPrioritizeOtherApps = isSearchFocused && isKeyboardVisible
    val appsFadeInAlpha by animateFloatAsState(
        targetValue = if (showApps) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "apps-fade-in"
    )

    if (installedApps == null) {
        showApps = false
        WhitelistLoadingScreen(modifier = modifier)
        return
    }
    LaunchedEffect(installedApps) {
        showApps = true
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
    val allGranted = postNotificationsGranted && notificationAccessGranted && batteryOptimizationGranted
    AppScreenContainer(
        title = "Detection setup",
        subtitle = "Grant permissions so ADMUTE can detect ad notifications reliably.",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PermissionStatusCard(
                label = "Push notifications",
                granted = postNotificationsGranted,
                actionLabel = "Grant permission",
                onAction = onRequestPostNotifications
            )
            PermissionStatusCard(
                label = "Notification access",
                granted = notificationAccessGranted,
                actionLabel = "Open access settings",
                onAction = onOpenNotificationAccess
            )
            PermissionStatusCard(
                label = "Battery optimization",
                granted = batteryOptimizationGranted,
                actionLabel = "Disable optimization",
                onAction = onRequestDisableBatteryOptimization
            )
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Keyword list preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(text = keywordsPreview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (allGranted) {
                AssistChip(onClick = {}, label = { Text("All set. ADMUTE can now monitor selected apps.") })
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    label: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = if (granted) "Granted" else "Not granted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
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
    onViewAbout: () -> Unit,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
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
                    ActionButtonLeftAligned("Manage whitelist apps", onChangeWhitelist, enableMarquee = true)
                    ActionButtonLeftAligned("Change cooldown", onChangeCooldown, enableMarquee = true)
                    ActionButtonLeftAligned("Modify notification sounds", onModifyNotificationSounds, enableMarquee = true)
                    ActionButtonLeftAligned("Change theme", onChangeTheme, enableMarquee = true)
                    ActionButtonLeftAligned("View ad detection logs", onViewLogs, enableMarquee = true)
                    ActionButtonLeftAligned("About", onViewAbout, enableMarquee = true)
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
                        if (isPaused) "Resume ADMUTE" else "Pause ADMUTE",
                        onPauseToggle,
                        enableMarquee = true
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "ADBLOCK IS CURRENTLY RUNNING",
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
                ActionButtonLeftAligned("Manage whitelist apps", onChangeWhitelist)
                ActionButtonLeftAligned("Change cooldown", onChangeCooldown)
                ActionButtonLeftAligned("Modify notification sounds", onModifyNotificationSounds)
                ActionButtonLeftAligned("Change theme", onChangeTheme)
                ActionButtonLeftAligned("View ad detection logs", onViewLogs)
                ActionButtonLeftAligned("About", onViewAbout)
                NowPlayingInfoBox(nowPlayingInfo = nowPlayingInfo)
                Spacer(modifier = Modifier.weight(1f))
                ActionButtonLeftAligned(
                    if (isPaused) "Resume ADMUTE" else "Pause ADMUTE",
                    onPauseToggle
                )
                // Bottom-centered running status
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "ADMUTE IS CURRENTLY RUNNING",
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
                        text = "ADMUTE IS PAUSED",
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
                    AdMuteSettings.clearAdLogs(context)
                    logs = emptyList()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
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
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ActionButtonLeftAligned(
    title: String,
    onClick: () -> Unit,
    enableMarquee: Boolean = false
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
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
            textAlign = TextAlign.Start,
            maxLines = 1
        )
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
            } catch (t: Throwable) {
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val info = nowPlayingInfo!!
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
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
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
    }

    AppScreenContainer(
        title = "Notification sounds",
        subtitle = "Configure IN (ad detected) and OUT (ad ended) sounds.",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SoundConfigSection(
                title = "IN sound",
                config = inSound,
                stockOptions = listOf(StockNotificationSound.S1_IN, StockNotificationSound.S2_IN),
                onSelectOff = { inSound = inSound.copy(source = NotificationSoundSource.OFF) },
                onSelectStock = { selected ->
                    inSound = inSound.copy(source = NotificationSoundSource.STOCK, stockSound = selected)
                },
                onSelectCustom = {
                    if (inSound.customUri != null) {
                        inSound = inSound.copy(source = NotificationSoundSource.CUSTOM)
                    }
                },
                onPickCustom = { pickInSoundLauncher.launch(arrayOf("audio/*")) },
                onClearCustom = {
                    inSound = inSound.copy(customUri = null, customDisplayName = null, source = NotificationSoundSource.OFF)
                }
            )

            SoundConfigSection(
                title = "OUT sound",
                config = outSound,
                stockOptions = listOf(StockNotificationSound.S1_OUT, StockNotificationSound.S2_OUT),
                onSelectOff = { outSound = outSound.copy(source = NotificationSoundSource.OFF) },
                onSelectStock = { selected ->
                    outSound = outSound.copy(source = NotificationSoundSource.STOCK, stockSound = selected)
                },
                onSelectCustom = {
                    if (outSound.customUri != null) {
                        outSound = outSound.copy(source = NotificationSoundSource.CUSTOM)
                    }
                },
                onPickCustom = { pickOutSoundLauncher.launch(arrayOf("audio/*")) },
                onClearCustom = {
                    outSound = outSound.copy(customUri = null, customDisplayName = null, source = NotificationSoundSource.OFF)
                }
            )
        }
        Button(
            onClick = { onSave(NotificationSoundSettings(inSound = inSound, outSound = outSound)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Save")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Back")
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
    val selectedIndex = choices.indexOfFirst { it.label == selectedLabel }.coerceAtLeast(0)
    val context = LocalContext.current
    
    val strokeColorInt = MaterialTheme.colorScheme.primary.toArgb()
    val surfaceColorInt = MaterialTheme.colorScheme.surface.toArgb()
    val textColorInt = MaterialTheme.colorScheme.onSurface.toArgb()

    ElevatedCard(shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "- Select sound",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = {
                    Spinner(context).apply {
                        adapter = ArrayAdapter(
                            context,
                            android.R.layout.simple_spinner_item,
                            choices.map { choice -> choice.label }
                        ).also { arrayAdapter ->
                            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 12f * resources.displayMetrics.density
                            setColor(surfaceColorInt)
                            setStroke((1f * resources.displayMetrics.density).toInt(), strokeColorInt)
                        }
                        minimumHeight = (50f * resources.displayMetrics.density).toInt()
                        setPadding(
                            (14f * resources.displayMetrics.density).toInt(),
                            (8f * resources.displayMetrics.density).toInt(),
                            (14f * resources.displayMetrics.density).toInt(),
                            (8f * resources.displayMetrics.density).toInt()
                        )
                        setSelection(selectedIndex, false)
                        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                (view as? TextView)?.setTextColor(textColorInt)
                                choices.getOrNull(position)?.action?.invoke()
                            }

                            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                        }
                    }
                },
                update = { spinner ->
                    val labels = choices.map { choice -> choice.label }
                    @Suppress("UNCHECKED_CAST")
                    val adapter = spinner.adapter as? ArrayAdapter<String>
                    if (adapter == null || adapter.count != labels.size) {
                        spinner.adapter = ArrayAdapter(
                            context,
                            android.R.layout.simple_spinner_item,
                            labels
                        ).also { arrayAdapter ->
                            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                    } else {
                        var changed = false
                        for (index in labels.indices) {
                            if (adapter.getItem(index) != labels[index]) {
                                changed = true
                                break
                            }
                        }
                        if (changed) {
                            adapter.clear()
                            adapter.addAll(labels)
                            adapter.notifyDataSetChanged()
                        }
                    }
                    if (spinner.selectedItemPosition != selectedIndex) {
                        spinner.setSelection(selectedIndex, false)
                    }
                    (spinner.selectedView as? TextView)?.apply {
                        setTextColor(textColorInt)
                        textSize = 18f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }
                }
            )
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
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppScreenContainer(
        title = "About ADMUTE",
        subtitle = "Information about the application.",
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        ElevatedCard(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                val context = LocalContext.current
                val appIcon = remember {
                    context.packageManager.getApplicationIcon(context.packageName).toBitmapSafely().asImageBitmap()
                }
                Image(
                    bitmap = appIcon,
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ADMUTE",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version 1.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ADMUTE is designed to detect ad notifications from your selected music apps and automatically mute your media volume, bringing peace to your listening experience.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.weight(1f))
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
            style = MaterialTheme.typography.titleMedium,
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
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = nextTheme?.label ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(nextAlpha)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IntroductionScreenPreview() {
    ADMUTETheme {
        IntroductionScreen(onStartSetup = {})
    }
}
