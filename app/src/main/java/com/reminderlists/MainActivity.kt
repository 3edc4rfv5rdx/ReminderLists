package com.reminderlists

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.entity.SettingEntity
import com.reminderlists.data.reminders.RemindersRepository
import com.reminderlists.ui.navigation.AppRoot
import com.reminderlists.ui.screens.permission.NotificationPermissionDialog
import com.reminderlists.ui.screens.permission.OverlayPermissionDialog
import com.reminderlists.ui.theme.AppTheme
import com.reminderlists.ui.theme.DEFAULT_FONT_SCALE
import com.reminderlists.ui.theme.ReminderListsTheme
import com.reminderlists.ui.theme.ThemeMode
import com.reminderlists.util.SettingsKeys
import kotlinx.coroutines.launch

// Single-Activity host. Splash via androidx.core.splashscreen (TZ 7); UI is AppRoot (TZ 3.9).
class MainActivity : ComponentActivity() {

    // POST_NOTIFICATIONS onboarding (TZ 4.10): a rationale dialog before the system request —
    // without the permission reminders (full-screen included) never show. NONE hidden,
    // RATIONALE explains and asks, BLOCKED points to settings after a permanent denial.
    private var notifPrompt by mutableStateOf(NotifPrompt.NONE)

    // Overlay-grant onboarding (TZ 4.5): without "Display over other apps" the full-screen
    // alert can't launch from the alarm receiver while another app is in front. Asked once,
    // on first launch; the answer is remembered forever (OVERLAY_PROMPT_SHOWN).
    private var overlayPrompt by mutableStateOf(false)

    // On a denial that can still be re-asked, back off (the dialog returns next launch); a
    // denial that no longer prompts is permanent, so switch to the settings variant.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notifPrompt = when {
                granted -> NotifPrompt.NONE
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> NotifPrompt.NONE
                else -> NotifPrompt.BLOCKED
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!notificationsGranted()) notifPrompt = NotifPrompt.RATIONALE
        if (!Settings.canDrawOverlays(this)) {
            lifecycleScope.launch {
                val dao = AppDatabase.get(this@MainActivity).settingsDao()
                overlayPrompt = dao.get(SettingsKeys.OVERLAY_PROMPT_SHOWN) != "1"
            }
        }
        setContent {
            // Theme color preset + Light/Dark/System mode come from settings (TZ 5); observing
            // them here re-themes the whole app the moment either changes.
            val db = remember { AppDatabase.get(this) }
            val themeKey by db.settingsDao().observe(SettingsKeys.THEME).collectAsState(initial = null)
            val modeKey by db.settingsDao().observe(SettingsKeys.THEME_MODE).collectAsState(initial = null)
            val scaleKey by db.settingsDao().observe(SettingsKeys.FONT_SCALE).collectAsState(initial = null)
            ReminderListsTheme(
                theme = AppTheme.fromKey(themeKey),
                mode = ThemeMode.fromKey(modeKey),
                fontScale = scaleKey?.toFloatOrNull() ?: DEFAULT_FONT_SCALE,
            ) {
                AppRoot()
                if (notifPrompt != NotifPrompt.NONE) {
                    NotificationPermissionDialog(
                        blocked = notifPrompt == NotifPrompt.BLOCKED,
                        onConfirm = {
                            if (notifPrompt == NotifPrompt.BLOCKED) {
                                openNotificationSettings()
                            } else {
                                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            notifPrompt = NotifPrompt.NONE
                        },
                        onDismiss = { notifPrompt = NotifPrompt.NONE },
                    )
                } else if (overlayPrompt) {
                    // Queued behind the notifications dialog; either answer settles it for good.
                    OverlayPermissionDialog(
                        onConfirm = {
                            settleOverlayPrompt()
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        },
                        onDismiss = ::settleOverlayPrompt,
                    )
                }
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

    // Close the overlay dialog and never ask again — the grant is a one-time setup (TZ 4.5).
    private fun settleOverlayPrompt() {
        overlayPrompt = false
        lifecycleScope.launch {
            AppDatabase.get(this@MainActivity).settingsDao()
                .put(SettingEntity(SettingsKeys.OVERLAY_PROMPT_SHOWN, "1"))
        }
    }

    private fun notificationsGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private enum class NotifPrompt { NONE, RATIONALE, BLOCKED }
}
