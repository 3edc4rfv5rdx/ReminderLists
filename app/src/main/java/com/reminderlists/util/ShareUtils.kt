package com.reminderlists.util

import android.content.Context
import android.content.Intent

// Standard Android Share Intent (TZ 3.7 / 8): one implementation app-wide, no own protocol.
object ShareUtils {

    fun shareText(context: Context, text: String, subject: String? = null) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!subject.isNullOrEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(
            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
