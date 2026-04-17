package com.psimandan.neuread

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.psimandan.neuread.ui.navigation.NavigationViewModel
import com.psimandan.neuread.ui.about.AboutScreen
import com.psimandan.neuread.ui.library.LibraryScreenView
import com.psimandan.neuread.ui.library.LibraryScreenViewModel
import com.psimandan.neuread.ui.init.SplashScreenView
import com.psimandan.neuread.ui.player.PlayerScreenView
import com.psimandan.neuread.ui.player.PlayerViewModel
import com.psimandan.neuread.ui.settings.BookSettingsScreenView
import com.psimandan.neuread.ui.settings.BookSettingsViewModel
import com.psimandan.neuread.ui.settings.SettingsScreenView
import com.psimandan.neuread.ui.settings.SettingsViewModel
import com.psimandan.neuread.ui.theme.NeuReadTheme
import com.psimandan.neuread.ui.voicecloning.VoiceCloningRecordingScreenView
import com.psimandan.neuread.ui.voicecloning.VoiceCloningScreenView
import com.psimandan.neuread.voice.VoiceSelectorViewModel
import com.psimandan.neuread.ui.player.components.MiniPlayerView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import androidx.core.net.toUri
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp


sealed class Screen(val route: String) {
    data object Splash : Screen("init")
    data object Home : Screen("home")
    data object BookSettings : Screen("book_settings")
    data object Settings : Screen("settings")
    data object About : Screen("about")
    data object VoiceCloning : Screen("voice_cloning")
    data object VoiceCloningRecording : Screen("voice_cloning_recording")
    data object Player : Screen("player")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val navigationViewModel by viewModels<NavigationViewModel>()
    private val libraryViewModel by viewModels<LibraryScreenViewModel>()
    private val playerViewModel by viewModels<PlayerViewModel>()
    private val bookSettingsViewModel by viewModels<BookSettingsViewModel>()
    private val settingsViewModel by viewModels<SettingsViewModel>()
    private val voiceSelectorViewModel by viewModels<VoiceSelectorViewModel>()
    private val voiceCloningViewModel by viewModels<com.psimandan.neuread.ui.voicecloning.VoiceCloningViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setTheme(R.style.Theme_NeuRead)
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
        }
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            // Observe navigation events
            LaunchedEffect(navController) {
                navigationViewModel.onNavigationEvents(navController)
            }
            LaunchedEffect("init") {
                voiceSelectorViewModel.loadVoices()
            }

            val accentColorInt by settingsViewModel.accentColor.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()

            val accentColor = accentColorInt?.let { Color(it) }
            val darkTheme = when (themeMode) {
                1 -> false // Light
                2 -> true  // Dark
                else -> androidx.compose.foundation.isSystemInDarkTheme() // Auto
            }

            NeuReadTheme(accentColor = accentColor, darkTheme = darkTheme) {
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                val showMiniPlayer = currentRoute != Screen.Player.route &&
                        currentRoute != Screen.BookSettings.route &&
                        currentRoute != Screen.Splash.route

                val playerUiState by playerViewModel.viewState.collectAsState()

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showMiniPlayer && playerViewModel.book != null) {
                            MiniPlayerView(
                                book = playerViewModel.book,
                                uiState = playerUiState,
                                onPlayPauseClick = {
                                    if (playerUiState.isSpeaking) playerViewModel.onPause()
                                    else playerViewModel.onPlay()
                                },
                                onClick = {
                                    navigationViewModel.navigateTo(Screen.Player)
                                },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Splash.route,
                        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(400)
                            ) + fadeIn(animationSpec = tween(400))
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(400)
                            ) + fadeOut(animationSpec = tween(400))
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(400)
                            ) + fadeIn(animationSpec = tween(400))
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(400)
                            ) + fadeOut(animationSpec = tween(400))
                        }
                    ) {
                        composable(Screen.Splash.route,
                            enterTransition = { fadeIn(tween(500)) },
                            exitTransition = { fadeOut(tween(500)) }
                        ) {
                            SplashScreenView(onNavigate = { screen ->
                                navigationViewModel.resetAndNavigateTo(screen)
                            }, libraryViewModel)
                        }
                        composable(Screen.Home.route) {
                            LibraryScreenView(
                                viewModel = libraryViewModel,
                                workManager = androidx.work.WorkManager.getInstance(this@MainActivity),
                                onSelect = { book ->
                                    libraryViewModel.onSelectBook(book)
                                    navigationViewModel.navigateTo(Screen.Player)
                                },
                                onSettingsClicked = {
                                    navigationViewModel.navigateTo(Screen.Settings)
                                },
                                onFileSelected = {
                                    bookSettingsViewModel.createANewBook(it)
                                    navigationViewModel.navigateTo(Screen.BookSettings)
                                }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreenView(
                                viewModel = settingsViewModel,
                                onNavigateBack = { navigationViewModel.popBack() },
                                onAboutClicked = { navigationViewModel.navigateTo(Screen.About) },
                                onVoiceCloningClicked = { navigationViewModel.navigateTo(Screen.VoiceCloning) }
                            )
                        }
                        composable(Screen.BookSettings.route) {
                            BookSettingsScreenView(
                                onBookDeleted = {
                                    navigationViewModel.resetAndNavigateTo(Screen.Home)
                                },
                                onNavigateBack = { book ->
                                    if (book == null) { // creation canceled
                                        navigationViewModel.resetAndNavigateTo(Screen.Home)
                                    } else {
                                        libraryViewModel.onSelectBook(book)
                                        if (navController.previousBackStackEntry == null) {
                                            navigationViewModel.resetAndNavigateTo(Screen.Player)
                                        } else {
                                            navigationViewModel.popBack()
                                        }
                                    }
                                },
                                viewModel = bookSettingsViewModel,
                                voiceSelector = voiceSelectorViewModel
                            )
                        }
                        composable(Screen.About.route) {
                            AboutScreen {
                                navigationViewModel.popBack()
                            }
                        }
                        composable(Screen.VoiceCloning.route) {
                            VoiceCloningScreenView(
                                viewModel = voiceSelectorViewModel,
                                onNavigateBack = { navigationViewModel.popBack() },
                                onAddVoiceClicked = { navigationViewModel.navigateTo(Screen.VoiceCloningRecording) }
                            )
                        }
                        composable(Screen.VoiceCloningRecording.route) {
                            VoiceCloningRecordingScreenView(
                                viewModel = voiceCloningViewModel,
                                voiceSelectorViewModel = voiceSelectorViewModel,
                                onNavigateBack = { navigationViewModel.popBack() }
                            )
                        }
                        composable(Screen.Player.route) {
                            PlayerScreenView(
                                onBackToLibrary = {
                                    navigationViewModel.resetAndNavigateTo(Screen.Home)
                                }, onNavigateToSettings = { book ->
                                    navigationViewModel.navigateTo(Screen.BookSettings)
                                },
                                viewModel = playerViewModel,
                                playbackProgressCallBack = {
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    fun openAppRating(context: Context) {
        val packageName = context.packageName
        try {
            // Open the Play Store app if available
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "market://details?id=$packageName".toUri()
                ).apply {
                    setPackage("com.android.vending") // Ensure only Play Store handles it
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
        } catch (e: ActivityNotFoundException) {
            // Open in browser if Play Store is unavailable
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$packageName".toUri()
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
        }
    }

    fun openExternalLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }

    fun sendEmailToSupport() {
        val text = prepareBugReport()
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_EMAIL, "support@psimandan.net")
            putExtra(Intent.EXTRA_SUBJECT, "NeuRead Support")
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "NeuRead Support")
        startActivity(shareIntent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun prepareBugReport(name: String = "NeuRead"): String {
        val versionCode: Int = BuildConfig.VERSION_CODE
        val versionName: String = BuildConfig.VERSION_NAME
        val model = Build.MODEL
        val version = Build.VERSION.RELEASE


        val messageToSend = StringBuffer(
            """
$name Bug Report
Support Email: support@psimandan.net
Feedback Email: feedback@psimandan.net

==Report Begins/Issue/Feedback==========

Please provide here all possible details.
Providing these details can help customer support quickly identify the problem and provide you with the best solution possible.

==Report Ends============

OS Version: $version

Model: $model

App Version: $versionName($versionCode)

For more information, please visit: https://psimandan.net
"""
        )
        return messageToSend.toString()
    }

}
