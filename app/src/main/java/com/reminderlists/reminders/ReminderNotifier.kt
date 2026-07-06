package com.reminderlists.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reminderlists.MainActivity
import com.reminderlists.R
import com.reminderlists.data.db.entity.ReminderEntity

// Builds and posts reminder notifications (TZ 4.10). Missed fires are presented here as a
// plain notification (never full-screen): one per reminder up to a threshold, a single
// summary beyond it. The regular on-time alert / full-screen path is TZ 4.5 (later stage).
object ReminderNotifier {

    // Below this many missed reminders show them individually, otherwise collapse to one
    // summary — few are expected in practice (TZ 4.10).
    private const val MISSED_SUMMARY_THRESHOLD = 5
    private const val MISSED_SUMMARY_ID = -1

    // On-time fire without full-screen (TZ 4.5): a plain heads-up notification on the HIGH
    // channel. The screen is woken separately by AlarmReceiver; SoundService drives the sound.
    fun notifyFired(context: Context, reminder: ReminderEntity) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(reminder.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            // +10 min postpones and clears the alert; Stop only silences the sound (TZ 4.5).
            .addAction(0, context.getString(R.string.notif_postpone_10), ReminderActionReceiver.postponeIntent(context, reminder.id))
            .addAction(0, context.getString(R.string.notif_sound_stop), ReminderActionReceiver.stopIntent(context, reminder.id))
        reminder.content?.let { builder.setContentText(it) }
        nm.notify(notifId(reminder.id), builder.build())
    }

    // On-time fire with Full screen alert on / Period (TZ 4.5): a HIGH-channel notification
    // carrying a full-screen intent to FullScreenAlertActivity. The OS launches the activity
    // over the lockscreen; unlocked it lands as heads-up and the activity opens on tap. Ongoing
    // so it can't be swiped away — the alert screen is dismissed by acting on it.
    fun notifyFullScreen(context: Context, reminder: ReminderEntity) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val alert = alertIntent(context, reminder.id)
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(reminder.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(alert)
            .setFullScreenIntent(alert, true)
        reminder.content?.let { builder.setContentText(it) }
        nm.notify(notifId(reminder.id), builder.build())
    }

    // Dismiss the fired reminder's notification once the user acted on the alert (TZ 4.5).
    fun cancel(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notifId(reminderId))
    }

    private fun alertIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, FullScreenAlertActivity::class.java)
            .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notifyMissed(context: Context, missed: List<ReminderEntity>) {
        if (missed.isEmpty()) return
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return // POST_NOTIFICATIONS onboarding is TZ 4.10 (later)

        if (missed.size > MISSED_SUMMARY_THRESHOLD) {
            val text = context.getString(R.string.notif_missed_summary, missed.size)
            nm.notify(MISSED_SUMMARY_ID, build(context, context.getString(R.string.notif_missed_summary_title), text))
        } else {
            missed.forEach { r ->
                nm.notify(notifId(r.id), build(context, r.title, context.getString(R.string.notif_missed_single)))
            }
        }
    }

    private fun build(context: Context, title: String, text: String) =
        NotificationCompat.Builder(context, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

    // Opens the app. Also used as the alarm-clock showIntent so tapping the status-bar alarm
    // affordance lands in the app rather than re-firing the alarm broadcast (TZ 4.10).
    fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    // One live notification per reminder (fired or missed collapse onto the same slot);
    // the summary uses its own reserved id.
    private fun notifId(reminderId: Long): Int = reminderId.toInt()
}
