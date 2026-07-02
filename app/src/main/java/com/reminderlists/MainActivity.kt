package com.reminderlists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.reminderlists.ui.navigation.AppRoot
import com.reminderlists.ui.theme.ReminderListsTheme

// Single-Activity host. Splash via androidx.core.splashscreen (TZ 7); UI is AppRoot (TZ 3.9).
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // TODO read theme from settings for the ReminderListsTheme(theme = ...) argument (TZ 5).
            ReminderListsTheme {
                AppRoot()
            }
        }
    }
}
