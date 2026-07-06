package com.reminderlists.data.sound

import android.content.Context
import android.media.RingtoneManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Lists the device's alarm ringtones for the Default sound selector (TZ 5). The app ships no
// bundled sounds — a default reminder sound is a system ringtone, stored as its Uri string.
object SoundCatalog {

    data class SystemSound(val title: String, val uri: String)

    suspend fun systemAlarms(context: Context): List<SystemSound> = withContext(Dispatchers.IO) {
        val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val out = mutableListOf<SystemSound>()
        manager.cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = manager.getRingtoneUri(cursor.position).toString()
                out += SystemSound(title, uri)
            }
        }
        out
    }
}
