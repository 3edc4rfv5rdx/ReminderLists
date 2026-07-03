package com.reminderlists

import android.app.Application
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.reminders.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Application entry point. Sets up notification channels and sweeps orphan photo files;
// logger toggle wiring TODO (TZ 8).
class ReminderListsApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        // TODO: apply Logger.writeToFile from settings.

        // Photo files with no DB reference are leftovers (crash mid-edit, restore) — drop them (TZ 8).
        appScope.launch {
            val db = AppDatabase.get(this@ReminderListsApp)
            val referenced = buildSet {
                addAll(db.listsDao().allPhotoNames())
                addAll(db.notesDao().allPhotoNames())
                addAll(db.remindersDao().allPhotoNames())
            }
            PhotoManager.sweepOrphans(this@ReminderListsApp, referenced)
        }
    }
}
