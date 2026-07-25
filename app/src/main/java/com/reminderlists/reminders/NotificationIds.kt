package com.reminderlists.reminders

// Notification ids that are not a reminder row id (TZ 4.10). Reminder notifications are keyed
// by their row id, which is always > 0, so everything else lives in the negative range — and
// must be declared here: two features picking the same id silently overwrite each other's
// notification (a timer fire once vanished under the sound service's own entry).
object NotificationIds {
    const val MISSED_SUMMARY = -1
    const val SOUND_SERVICE = -2
    const val TIMER = -100
}
