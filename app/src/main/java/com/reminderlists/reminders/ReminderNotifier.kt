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

    // On-time fire (TZ 4.5): a heads-up notification on the HIGH channel. The full-screen
    // variant for fullScreenAlert reminders and the looping sound are later stages.
    fun notifyFired(context: Context, reminder: ReminderEntity) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO dedicated status-bar icon
            .setContentTitle(reminder.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
        reminder.content?.let { builder.setContentText(it) }
        nm.notify(notifId(reminder.id), builder.build())
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
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO dedicated status-bar icon
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    // One live notification per reminder (fired or missed collapse onto the same slot);
    // the summary uses its own reserved id.
    private fun notifId(reminderId: Long): Int = reminderId.toInt()
}
