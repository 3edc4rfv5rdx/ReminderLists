package com.reminderlists

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.reminders.RemindersRepository
import com.reminderlists.ui.navigation.AppRoot
import com.reminderlists.ui.theme.ReminderListsTheme
import kotlinx.coroutines.launch

// Single-Activity host. Splash via androidx.core.splashscreen (TZ 7); UI is AppRoot (TZ 3.9).
class MainActivity : ComponentActivity() {

    // POST_NOTIFICATIONS is a runtime permission on API 33+ (minSdk 33), and without it
    // reminders never show (TZ 4.10). TODO onboarding rationale screen (TZ 4.10).
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            // TODO read theme from settings for the ReminderListsTheme(theme = ...) argument (TZ 5).
            ReminderListsTheme {
                AppRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Auto-remove after firing (TZ 4.2 h): sweep on every foreground, not just cold start, so
        // a reminder finished on an earlier day is removed as soon as the app is opened.
        lifecycleScope.launch {
            RemindersRepository(AppDatabase.get(this@MainActivity), this@MainActivity).sweepAutoRemoved()
        }
    }
}
