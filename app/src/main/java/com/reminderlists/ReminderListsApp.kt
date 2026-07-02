package com.reminderlists

import android.app.Application
import com.reminderlists.reminders.NotificationChannels

// Application entry point. Sets up notification channels; startup orphan sweep + logger
// toggle wiring TODO (TZ 4.10 / 8).
class ReminderListsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        // TODO: apply Logger.writeToFile from settings; PhotoManager.sweepOrphans().
    }
}
