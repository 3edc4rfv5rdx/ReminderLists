package com.reminderlists.reminders

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.reminderlists.util.Logger

// Short-lived foreground service that loops the reminder sound until the user reacts
// (OK / Stop / Postpone / unlock) or Duration elapses, then stopSelf() (TZ 4.10).
// Own MediaPlayer: honors Sound level, skips sound in silent/DND (vibration still fires).
class SoundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i("SoundService start")
        // TODO startForeground with SERVICE channel, play looped sound honoring settings/DND,
        //  stop on user reaction or Duration timeout (TZ 4.10).
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
