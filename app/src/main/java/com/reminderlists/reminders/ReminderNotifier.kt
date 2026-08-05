package com.reminderlists.reminders

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reminderlists.MainActivity
import com.reminderlists.R
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.reminders.IntervalUnit
import com.reminderlists.data.reminders.RepeatType

// Builds and posts reminder notifications (TZ 4.10). Missed fires are presented here as a
// plain notification (never full-screen): one per reminder up to a threshold, a single
// summary beyond it. The regular on-time alert / full-screen path is TZ 4.5 (later stage).
object ReminderNotifier {

    // Below this many missed reminders show them individually, otherwise collapse to one
    // summary — few are expected in practice (TZ 4.10).
    private const val MISSED_SUMMARY_THRESHOLD = 5
    private const val MISSED_SUMMARY_ID = NotificationIds.MISSED_SUMMARY

    // On-time fire without full-screen (TZ 4.5): a plain heads-up notification on the HIGH
    // channel. The screen is woken separately by AlarmReceiver; SoundService drives the sound
    // and re-posts this very notification as its foreground one, so no service entry of its
    // own ever shows up (TZ 4.10).
    fun notifyFired(context: Context, payload: FirePayload) {
        post(context, payload.notificationId, buildFired(context, payload))
    }

    // The single posting point of the app (TZ 8): the runtime grant (POST_NOTIFICATIONS,
    // required since Android 13 — the whole minSdk range) and the user's notification switch
    // are checked here, so a denied grant silently drops the post instead of throwing.
    fun post(context: Context, id: Int, notification: Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        nm.notify(id, notification)
    }

    // The fired notification itself, so SoundService can hand the same object to
    // startForeground instead of inventing a second, service-looking entry (TZ 4.10 / 8).
    fun buildFired(context: Context, payload: FirePayload): Notification {
        val reminder = payload.asReminder()
        val timer = payload.takeIf { it.isTimer }
        val minuteInterval = isMinuteInterval(reminder)
        val isInterval = RepeatType.of(reminder.repeatType) == RepeatType.INTERVAL
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(reminder.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
        // +10 min postpones and clears the alert; Stop only silences the sound (TZ 4.5). On a
        // minute interval +10 min makes no sense (it re-fires within the minute), so it's dropped.
        if (!minuteInterval) {
            builder.addAction(0, context.getString(R.string.notif_postpone_10), ReminderActionReceiver.postponeIntent(context, reminder.id, timer))
        }
        builder.addAction(0, context.getString(R.string.notif_sound_stop), ReminderActionReceiver.stopIntent(context, reminder.id, timer))
        // Interval: "Stop repeating" deactivates it so it stops re-firing (TZ 4.5).
        if (isInterval) {
            builder.addAction(0, context.getString(R.string.notif_interval_stop), ReminderActionReceiver.dismissIntent(context, reminder.id))
        }
        reminder.content?.let { builder.setContentText(it) }
        // Minute-interval reminders fire often — auto-expire a stale one from the shade after
        // 20s so it doesn't linger until the next fire replaces it (TZ 4.10).
        if (minuteInterval) builder.setTimeoutAfter(20_000)
        return builder.build()
    }

    private fun isMinuteInterval(reminder: ReminderEntity): Boolean =
        RepeatType.of(reminder.repeatType) == RepeatType.INTERVAL &&
            IntervalUnit.of(reminder.intervalUnit) == IntervalUnit.MINUTES

    // On-time fire with Full screen alert on / Period (TZ 4.5): a HIGH-channel notification
    // carrying a full-screen intent to FullScreenAlertActivity. The OS launches the activity
    // over the lockscreen; unlocked it lands as heads-up and the activity opens on tap. Ongoing
    // so it can't be swiped away — the alert screen is dismissed by acting on it. While the
    // alert itself is visible the activity cancels this entry (a shade duplicate) and re-posts
    // it via notifyAlertPending if it's left without an action.
    fun notifyFullScreen(context: Context, payload: FirePayload) {
        post(context, payload.notificationId, buildAlert(context, payload, fullScreen = true))
    }

    // Shade fallback when the alert is left without acting (Home / back / screen off): the same
    // alert-opening entry but without the full-screen intent, so posting it can't relaunch the
    // alert by itself (an FSI re-post on screen-off would light the screen right back up).
    fun notifyAlertPending(context: Context, payload: FirePayload) {
        post(context, payload.notificationId, buildAlert(context, payload, fullScreen = false))
    }

    // fullScreen = false is also what SoundService hands to startForeground: the alert entry
    // without the full-screen intent, so re-posting it as the service notification can never
    // relaunch the alert (TZ 4.5 / 4.10).
    fun buildAlert(context: Context, payload: FirePayload, fullScreen: Boolean): Notification {
        val timer = payload.takeIf { it.isTimer }
        val alert = alertIntent(context, payload.id, timer)
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(payload.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(alert)
        if (fullScreen) builder.setFullScreenIntent(alert, true)
        payload.content?.let { builder.setContentText(it) }
        return builder.build()
    }

    // Dismiss the fired reminder's notification once the user acted on the alert (TZ 4.5).
    fun cancel(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notifId(reminderId))
    }

    private fun alertIntent(context: Context, reminderId: Long, timer: FirePayload? = null): PendingIntent {
        val intent = Intent(context, FullScreenAlertActivity::class.java)
            .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // The alert has no row to load for a timer — it rebuilds itself from these extras.
        timer?.putInto(intent)
        return PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notifyMissed(context: Context, missed: List<ReminderEntity>) {
        if (missed.isEmpty()) return
        if (missed.size > MISSED_SUMMARY_THRESHOLD) {
            val text = context.resources.getQuantityString(
                R.plurals.notif_missed_summary,
                missed.size,
                missed.size,
            )
            post(context, MISSED_SUMMARY_ID, build(context, context.getString(R.string.notif_missed_summary_title), text))
        } else {
            missed.forEach { r ->
                post(context, notifId(r.id), build(context, r.title, context.getString(R.string.notif_missed_single)))
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
