package com.reminderlists.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import com.reminderlists.R
import com.reminderlists.data.filter.FilterTab
import com.reminderlists.data.reminders.ReminderFolder

// Bottom navigation tabs (TZ 3.9): Lists · Reminders · Notes · Settings. Lists is the default.
enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    LISTS("lists", R.string.tab_lists, Icons.AutoMirrored.Filled.ListAlt),
    REMINDERS("reminders", R.string.tab_reminders, Icons.Outlined.Notifications),
    NOTES("notes", R.string.tab_notes, Icons.AutoMirrored.Filled.Notes),
    SETTINGS(Routes.SETTINGS, R.string.menu_settings, Icons.Filled.Settings),
}

// Non-tab service routes (TZ 3.9): separate destinations, not tabs.
object Routes {
    const val SETTINGS = "settings"
    const val DICTIONARY = "dictionary"

    // Filters (4.3) / Tag Filter (4.4) carry which tab's filter they edit (TZ 3.9 / 4A.5).
    const val FILTERS = "filters/{tab}"
    fun filters(tab: FilterTab) = "filters/${tab.name}"
    const val TAG_FILTER = "tag_filter/{tab}"
    fun tagFilter(tab: FilterTab) = "tag_filter/${tab.name}"

    // Opened list (TZ 3.2); folder browsing is in-tab state, not a route.
    const val LIST_DETAIL = "list/{listId}"
    fun listDetail(listId: Long) = "list/$listId"

    // Item add/edit window (TZ 3.3); itemId <= 0 means a new item.
    const val ITEM_EDITOR = "item_editor/{listId}?itemId={itemId}"
    fun itemEditor(listId: Long, itemId: Long = 0) = "item_editor/$listId?itemId=$itemId"

    // Reminder add/edit form (TZ 4.2); reminderId <= 0 means a new reminder, folder
    // prefills the repeat type from the opened type-folder (TZ 3.9).
    const val REMINDER_EDITOR = "reminder_editor?reminderId={reminderId}&folder={folder}"
    fun reminderEditor(reminderId: Long = 0, folder: ReminderFolder = ReminderFolder.ONCE) =
        "reminder_editor?reminderId=$reminderId&folder=${folder.name}"

    // Note add/edit form (TZ 4A.2); noteId <= 0 means a new note, folderId (-1 = root) is the
    // folder a new note lands in — the opened folder on the Notes tab (TZ 3.9).
    const val NOTE_EDITOR = "note_editor?noteId={noteId}&folderId={folderId}"
    fun noteEditor(noteId: Long = 0, folderId: Long? = null) =
        "note_editor?noteId=$noteId&folderId=${folderId ?: -1L}"
}
