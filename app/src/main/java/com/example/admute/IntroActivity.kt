package com.example.admute

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.admute.settings.AdMuteSettings
import com.example.admute.settings.ThemeMode
import com.example.admute.ui.theme.ADVOIDTheme
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LocalLifecycleOwner

    @SuppressLint("CustomSplashScreen")
class IntroActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode = AdMuteSettings.getThemeMode(this@IntroActivity)
            val systemDarkTheme = isSystemInDarkTheme()
            val shouldUseDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDarkTheme
            }
            ADVOIDTheme(darkTheme = shouldUseDarkTheme) {
                IntroScreen(
                    useDarkTheme = shouldUseDarkTheme,
                    onFinished = {
                        AdMuteSettings.recordSplashShown(this@IntroActivity)
                        navigateToNext(immediate = false)
                    }
                )
            }
        }
    }

    private fun navigateToNext(immediate: Boolean) {
        val destination = if (AdMuteSettings.isSetupCompleted(this)) {
            MainActivity::class.java
        } else {
            try {
                Class.forName("com.example.admute.WelcomeActivity")
            } catch (e: Exception) {
                MainActivity::class.java
            }
        }

        val intent = Intent(this, destination)
        startActivity(intent)
        if (immediate) {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }
}

@Composable
fun IntroScreen(
    useDarkTheme: Boolean,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var hasNavigated by remember { mutableStateOf(false) }
    var isMainFirstFrameRendered by remember { mutableStateOf(false) }
    var isBrandingFirstFrameRendered by remember { mutableStateOf(false) }
    var mainVideoAspectRatio by remember { mutableStateOf(1f) }
    var brandingVideoAspectRatio by remember { mutableStateOf(1f) }
    val finishSplash = remember(onFinished, hasNavigated) {
        {
            if (!hasNavigated) {
                hasNavigated = true
                onFinished()
            }
        }
    }
    
    // Video resource selection based on theme
    val mainVideoRes = if (useDarkTheme) R.raw.av_d else R.raw.av_l
    val brandingVideoRes = if (useDarkTheme) R.raw.cc_d else R.raw.cc_l

    val mainPlayer = remember(mainVideoRes) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$mainVideoRes"))
            setMediaItem(mediaItem)
            prepare()
        }
    }

    val brandingPlayer = remember(brandingVideoRes) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$brandingVideoRes"))
            setMediaItem(mediaItem)
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Lifecycle management
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                mainPlayer.playWhenReady = true
                brandingPlayer.playWhenReady = true
            }
            override fun onStop(owner: LifecycleOwner) {
                mainPlayer.playWhenReady = false
                brandingPlayer.playWhenReady = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mainPlayer.release()
            brandingPlayer.release()
        }
    }

    DisposableEffect(mainPlayer, brandingPlayer) {
        val mainFrameListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isMainFirstFrameRendered = true
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.height > 0) {
                    mainVideoAspectRatio =
                        ((videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat())
                            .coerceAtLeast(0.5f)
                }
            }
        }
        val brandingFrameListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isBrandingFirstFrameRendered = true
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.height > 0) {
                    brandingVideoAspectRatio =
                        ((videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat())
                            .coerceAtLeast(0.5f)
                }
            }
        }
        mainPlayer.addListener(mainFrameListener)
        brandingPlayer.addListener(brandingFrameListener)
        onDispose {
            mainPlayer.removeListener(mainFrameListener)
            brandingPlayer.removeListener(brandingFrameListener)
        }
    }

    // Completion and Timeout logic
    DisposableEffect(mainPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    finishSplash()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                finishSplash()
            }
        }
        mainPlayer.addListener(listener)
        onDispose {
            mainPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(mainPlayer) {
        // Safety timeout for all devices
        delay(12000L)
        if (!hasNavigated) {
            finishSplash()
        }
    }

    LaunchedEffect(isMainFirstFrameRendered) {
        // Older Android devices can hang before first frame; skip splash if that happens.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            delay(3500L)
            if (!isMainFirstFrameRendered && !hasNavigated) {
                finishSplash()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (useDarkTheme) Color(0xFF0F2B26) else Color.White)
    ) {
        // Main Animation
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { playerView ->
                playerView.player = mainPlayer
            },
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .fillMaxWidth(0.72f)
                .aspectRatio(mainVideoAspectRatio)
                .alpha(if (isMainFirstFrameRendered) 1f else 0f)
        )

        // Branding Animation
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { playerView ->
                playerView.player = brandingPlayer
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 48.dp)
                .width(90.dp)
                .aspectRatio(brandingVideoAspectRatio)
                .alpha(if (isBrandingFirstFrameRendered) 1f else 0f)
        )
    }
}
