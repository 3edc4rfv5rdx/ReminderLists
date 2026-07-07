package com.reminderlists

import android.app.Application
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.reminders.NotificationChannels
import com.reminderlists.reminders.ReminderScheduler
import com.reminderlists.util.Logger
import com.reminderlists.util.SettingsKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Application entry point. Sets up notification channels, wires the file logger and sweeps
// orphan photo files.
class ReminderListsApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        Logger.init(this)

        // Photo files with no DB reference are leftovers (crash mid-edit, restore) — drop them (TZ 8).
        appScope.launch {
            val db = AppDatabase.get(this@ReminderListsApp)
            // Apply the persisted "Write logs to file" toggle before anything else logs (TZ 5 / 8).
            Logger.writeToFile = db.settingsDao().get(SettingsKeys.WRITE_LOGS_TO_FILE) == "1"
            val referenced = buildSet {
                addAll(db.listsDao().allPhotoNames())
                addAll(db.notesDao().allPhotoNames())
                addAll(db.remindersDao().allPhotoNames())
            }
            PhotoManager.sweepOrphans(this@ReminderListsApp, referenced)

            // A force-stop clears armed alarms and blocks receivers until the app is opened,
            // so re-arm on every start (also surfaces missed fires) — TZ 4.10.
            ReminderScheduler.rearmAll(this@ReminderListsApp, db)
        }
    }
}
