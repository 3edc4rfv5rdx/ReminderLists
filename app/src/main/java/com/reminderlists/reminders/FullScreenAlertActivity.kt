package com.reminderlists.reminders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reminderlists.ui.theme.ReminderListsTheme

// Full-screen alert host (TZ 4.5). Shown over the lockscreen when a reminder fires with
// Full screen alert enabled (forced on for Period). Lock overlay -> Postpone/OK (or
// Done/Continue for Period).
class FullScreenAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        setContent {
            ReminderListsTheme {
                FullScreenAlertContent(reminderId)
            }
        }
    }
}

@Composable
private fun FullScreenAlertContent(reminderId: Long) {
    // TODO locked state (lock overlay + swipe to unlock), then unlocked state with
    //  Postpone buttons (variant per type) + OK / Done+Continue (TZ 4.5).
    Text("Reminder #$reminderId", Modifier.fillMaxSize().wrapContentSize())
}
