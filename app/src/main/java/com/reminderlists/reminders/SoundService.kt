package com.reminderlists.reminders

import android.app.NotificationManager
import android.app.PendingIntent
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        Logger.i("SoundService start")
        startForeground(
            SERVICE_NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        val soundUri = intent?.getStringExtra(EXTRA_SOUND_URI)
        val loop = intent?.getBooleanExtra(EXTRA_LOOP, true) ?: true
        scope.launch { begin(soundUri, loop) }
        return START_NOT_STICKY
    }

    private suspend fun begin(soundUri: String?, loop: Boolean) {
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
                stopSelf()
            }
        } else if (player == null) {
            stopSelf()
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
                setOnCompletionListener { if (!loop) stopSelf() }
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

    private fun buildNotification() =
        NotificationCompat.Builder(this, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO dedicated status-bar icon
            .setContentTitle(getString(R.string.notif_sound_playing))
            .setOngoing(true)
            .addAction(0, getString(R.string.notif_sound_stop), stopIntent())
            .build()

    private fun stopIntent(): PendingIntent {
        val intent = Intent(this, SoundService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onDestroy() {
        timeout?.cancel()
        player?.release()
        player = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        Logger.i("SoundService stop")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val SERVICE_NOTIF_ID = -2
        private const val ACTION_STOP = "com.reminderlists.action.STOP_SOUND"
        private const val EXTRA_SOUND_URI = "sound_uri"
        private const val EXTRA_LOOP = "loop"

        // Started from AlarmReceiver on a fire; the exact-alarm broadcast grants the
        // background FGS-start exemption (TZ 4.10).
        fun start(context: Context, soundUri: String?, loop: Boolean) {
            val intent = Intent(context, SoundService::class.java)
                .putExtra(EXTRA_SOUND_URI, soundUri)
                .putExtra(EXTRA_LOOP, loop)
            context.startForegroundService(intent)
        }
    }
}
