package com.reminderlists.reminders

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reminderlists.R
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.sound.SoundStore
import com.reminderlists.util.Logger
import com.reminderlists.util.SettingsKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Short-lived foreground service that loops the reminder sound until the user reacts (Stop /
// Postpone / OK / unlock) or Duration elapses, then stopSelf() (TZ 4.10). Own MediaPlayer with
// USAGE_ALARM: honors Sound level and skips sound in silent/DND, while vibration still fires.
class SoundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var timeout: Job? = null
    // Id of the notification this service holds up, and whether it belongs to the fire (keep it
    // in the shade when the sound ends) or is the housekeeping fallback (drop it).
    private var fireId = SERVICE_NOTIF_ID
    private var ownsFireNotification = false
    // The fire whose notification is currently held up as the foreground one.
    private var held: FirePayload? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        Logger.i("SoundService start")
        // The fire's own notification is the service's foreground notification (TZ 4.10): one
        // shade entry for the whole fire, and no "sound is playing" service entry surfacing on
        // the 10 s deferral boundary. Without a payload (nothing to show) fall back to the
        // deferred housekeeping one, which startForeground still requires.
        val payload = intent?.let { FirePayload.from(it) }
        val previous = held
        held = payload
        fireId = payload?.notificationId ?: SERVICE_NOTIF_ID
        ownsFireNotification = payload != null
        startForeground(
            fireId,
            payload?.let { notificationFor(it) } ?: buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        // A second reminder can fire while this sound still plays (same-minute stagger, TZ 4.10).
        // Taking over the foreground slot drops the previous fire's entry, so put it back as an
        // ordinary notification — that fire is still unacknowledged.
        if (previous != null && previous.notificationId != fireId) {
            NotificationManagerCompat.from(this).notify(previous.notificationId, notificationFor(previous))
        }
        scope.launch { begin(payload?.soundUri, payload?.loopSound ?: true) }
        return START_NOT_STICKY
    }

    // Stop playing and shut down, leaving the fire's notification in the shade: the sound is
    // over but the reminder is still unacknowledged (TZ 4.5 — Stop only silences). Detaching
    // has to happen while the service is still alive; doing it from onDestroy is too late and
    // the entry goes away with the service.
    private fun finish() {
        stopForeground(if (ownsFireNotification) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Full-screen fires keep the alert-style entry, plain ones the fired entry with its action
    // buttons. The alert variant is built without the full-screen intent: it was already posted
    // by AlarmReceiver, and re-posting one here could throw the alert back onto the screen.
    private fun notificationFor(payload: FirePayload) =
        if (payload.fullScreenAlert) {
            ReminderNotifier.buildAlert(this, payload, fullScreen = false)
        } else {
            ReminderNotifier.buildFired(this, payload)
        }

    private suspend fun begin(soundUri: String?, loop: Boolean) {
        // A second reminder can fire while the previous sound still plays (same-minute stagger,
        // TZ 4.10): drop the old player and timeout so the first sound doesn't keep looping
        // unreleased and its stale stopSelf() doesn't cut the new sound short.
        timeout?.cancel()
        timeout = null
        player?.release()
        player = null

        val settings = AppDatabase.get(this).settingsDao()
        val level = settings.get(SettingsKeys.DEFAULT_SOUND_LEVEL)?.toIntOrNull()
            ?: SettingsKeys.DEFAULT_SOUND_LEVEL_VALUE
        val duration = settings.get(SettingsKeys.DEFAULT_SOUND_DURATION_SEC)?.toIntOrNull()
            ?: SettingsKeys.DEFAULT_SOUND_DURATION

        // Vibration always accompanies a fire, even in silent/DND — USAGE_ALARM bypasses the
        // interruption filter (TZ 4.10).
        vibrate(loop)

        // Sound is suppressed in silent mode / active DND, or at level 0 (TZ 4.10).
        if (level > 0 && !isSilencedOrDnd()) {
            val default = settings.get(SettingsKeys.DEFAULT_SOUND_URI)
            playSound(resolveSound(soundUri ?: default), loop && duration != 0, level)
        }

        // Duration bounds the loop; 0 means a single pass — stop once the sound has run (or now,
        // if silenced). A looping play is stopped by the timeout below.
        if (duration > 0) {
            timeout = scope.launch {
                delay(duration * 1000L)
                finish()
            }
        } else if (player == null) {
            finish()
        }
    }

    // null (no custom sound and no settings default) -> the system alarm ringtone; otherwise a
    // system Uri or a user file, resolved by the shared store (TZ 6.2 / 8).
    private fun resolveSound(name: String?): Uri? =
        if (name == null) SoundStore.systemDefaultAlarm(this) else SoundStore.mediaUri(this, name)

    private fun playSound(uri: Uri?, loop: Boolean, level: Int) {
        uri ?: return
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@SoundService, uri)
                isLooping = loop
                val volume = level / 100f
                setVolume(volume, volume)
                setOnCompletionListener { if (!loop) finish() }
                setOnPreparedListener { start() }
                prepareAsync()
            }
        } catch (e: Exception) {
            Logger.e("SoundService: sound failed", e)
            player = null
        }
    }

    private fun vibrate(loop: Boolean) {
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        val timings = longArrayOf(0, 500, 500) // off, on, off — repeats while looping
        val effect = VibrationEffect.createWaveform(timings, if (loop) 0 else -1)
        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
        vibrator.vibrate(effect, attrs)
    }

    private fun isSilencedOrDnd(): Boolean {
        val am = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        return am.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    // Housekeeping notification required by startForeground. Deferred so it only surfaces if
    // the sound outlives the ~10 s system hold-back — for typical durations it never appears
    // and the reminder's own notification/alert is the single shade entry. Deferral demands no
    // action buttons; sound Stop lives on the reminder notification / alert screen (TZ 4.10).
    private fun buildNotification() =
        NotificationCompat.Builder(this, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(getString(R.string.notif_sound_playing))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()

    override fun onDestroy() {
        timeout?.cancel()
        player?.release()
        player = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        // Detaching lives in finish(), which runs while the service is still up; here it would
        // be too late to keep the entry. This only catches a shutdown from outside (system kill).
        Logger.i("SoundService stop")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val SERVICE_NOTIF_ID = NotificationIds.SOUND_SERVICE
        private const val ACTION_STOP = "com.reminderlists.action.STOP_SOUND"

        // Started from AlarmReceiver on a fire; the exact-alarm broadcast grants the
        // background FGS-start exemption (TZ 4.10). The fire's payload travels along so the
        // service can adopt the fire's own notification as its foreground one.
        fun start(context: Context, payload: FirePayload) {
            context.startForegroundService(payload.putInto(Intent(context, SoundService::class.java)))
        }

        // Stop the looping sound early — the user reacted on the full-screen alert or the
        // notification's Stop action (TZ 4.5 / 4.10).
        fun stop(context: Context) {
            context.startService(Intent(context, SoundService::class.java).setAction(ACTION_STOP))
        }
    }
}
