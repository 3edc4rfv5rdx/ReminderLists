package com.reminderlists.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import com.reminderlists.R

// Bottom navigation tabs (TZ 3.9): Lists · Reminders · Notes. Lists is the default.
enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    LISTS("lists", R.string.tab_lists, Icons.AutoMirrored.Filled.ListAlt),
    REMINDERS("reminders", R.string.tab_reminders, Icons.Filled.Notifications),
    NOTES("notes", R.string.tab_notes, Icons.Filled.Notes),
}

// Non-tab service routes (TZ 3.9): separate destinations, not tabs.
object Routes {
    const val SETTINGS = "settings"
    const val FILTERS = "filters"
    const val TAG_FILTER = "tag_filter"
    const val DICTIONARY = "dictionary"
    // Welcome (TZ 4.8) is shown as a dialog, not a route.
}
