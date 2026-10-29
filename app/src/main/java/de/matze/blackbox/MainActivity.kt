package de.matze.blackbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.matze.blackbox.tools.mediacleaner.CleanupScheduler
import de.matze.blackbox.tools.mediacleaner.MediaCleanerScreen
import de.matze.blackbox.tools.screenlock.ScreenLockScreen
import de.matze.blackbox.ui.HubScreen

enum class AppScreen {
    HUB,
    MEDIA_CLEANER,
    SCREEN_LOCK
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize WorkManager schedule according to saved settings
        CleanupScheduler.syncSchedule(applicationContext)

        setContent {
            val darkColors = darkColorScheme(
                primary = Color(0xFF6366F1),
                secondary = Color(0xFF818CF8),
                background = Color(0xFF0A0A0D),
                surface = Color(0xFF121319),
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )

            MaterialTheme(colorScheme = darkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(AppScreen.HUB) }

                    BackHandler(enabled = currentScreen != AppScreen.HUB) {
                        currentScreen = AppScreen.HUB
                    }

                    when (currentScreen) {
                        AppScreen.HUB -> HubScreen(
                            onNavigateToCleaner = { currentScreen = AppScreen.MEDIA_CLEANER },
                            onNavigateToLock = { currentScreen = AppScreen.SCREEN_LOCK }
                        )
                        AppScreen.MEDIA_CLEANER -> MediaCleanerScreen(
                            onBack = { currentScreen = AppScreen.HUB }
                        )
                        AppScreen.SCREEN_LOCK -> ScreenLockScreen(
                            onBack = { currentScreen = AppScreen.HUB }
                        )
                    }
                }
            }
        }
    }
}
