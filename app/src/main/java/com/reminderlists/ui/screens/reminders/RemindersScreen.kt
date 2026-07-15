package com.reminderlists.ui.screens.reminders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.reminderlists.R
import com.reminderlists.data.db.dao.ReminderWithDetails
import com.reminderlists.data.filter.FilterIndicator
import com.reminderlists.data.filter.FilterTab
import com.reminderlists.data.reminders.IntervalUnit
import com.reminderlists.data.reminders.ReminderFolder
import com.reminderlists.ui.components.AppDropdownMenu
import com.reminderlists.ui.components.FilterIndicatorBadge
import com.reminderlists.ui.components.AppFab
import com.reminderlists.ui.components.AppTopBar
import com.reminderlists.ui.components.LocalSnackController
import com.reminderlists.ui.components.ConfirmDialog
import com.reminderlists.ui.components.EmptyState
import com.reminderlists.ui.components.FabLevel
import com.reminderlists.ui.components.EditDeleteMenuButton
import com.reminderlists.ui.components.PriorityStars
import com.reminderlists.ui.components.intervalSummary
import com.reminderlists.ui.components.RowTitle
import com.reminderlists.ui.components.SwipeActionsRow
import com.reminderlists.ui.navigation.Routes
import com.reminderlists.util.Dates
import com.reminderlists.util.Weekdays
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private val ReminderFolder.labelRes: Int
    get() = when (this) {
        ReminderFolder.ONCE -> R.string.folder_once
        ReminderFolder.DAILY -> R.string.folder_daily
        ReminderFolder.PERIODS -> R.string.folder_periods
        ReminderFolder.MONTHLY -> R.string.folder_monthly
        ReminderFolder.YEARLY -> R.string.folder_yearly
        ReminderFolder.INTERVALS -> R.string.folder_intervals
    }

// Reminders tab (TZ 3.9 / 4.1 / 4.6): five fixed type-folders + the Once cards in the
// root, reminder cards inside an opened folder. Today and the filter indicator arrive
// with their features.
@Composable
fun RemindersScreen(navController: NavController, contentPadding: PaddingValues) {
    val vm: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory)
    val currentFolder by vm.currentFolder.collectAsState()
    val folderCounts by vm.folderCounts.collectAsState()
    val reminders by vm.reminders.collectAsState()

    // Publish validation snacks to the app-wide host so they draw above the bottom bar (TZ 8).
    val snackController = LocalSnackController.current
    LaunchedEffect(vm.snack) {
        vm.snack?.let { snackController?.show(it); vm.snack = null }
    }

    var topMenuOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ReminderWithDetails?>(null) }
    // Today snapshot, captured at open so the row order/divider reflect that moment (TZ 4.11).
    var todayOpen by remember { mutableStateOf<LocalDateTime?>(null) }
    val remindersEnabled by vm.remindersEnabled.collectAsState()
    val filter by vm.filter.collectAsState()

    val inFolder = currentFolder != null
    BackHandler(enabled = inFolder) { vm.openFolder(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            AppTopBar(
                title = currentFolder?.let { stringResource(it.labelRes) }
                    ?: stringResource(R.string.tab_reminders),
                onBack = if (inFolder) {
                    { vm.openFolder(null) }
                } else {
                    null
                },
                actions = {
                    IconButton(onClick = { todayOpen = LocalDateTime.now() }) {
                        Icon(Icons.Outlined.Alarm, contentDescription = stringResource(R.string.action_today))
                    }
                    // Filter indicator All/T/F/TF (TZ 3.9) — reflects this tab's filter state;
                    // when active it re-opens the filter behind it.
                    FilterIndicatorBadge(
                        indicator = filter.indicator,
                        onClick = when (filter.indicator) {
                            FilterIndicator.T -> {
                                { navController.navigate(Routes.tagFilter(FilterTab.REMINDERS)) { launchSingleTop = true } }
                            }
                            FilterIndicator.F, FilterIndicator.TF -> {
                                { navController.navigate(Routes.filters(FilterTab.REMINDERS)) { launchSingleTop = true } }
                            }
                            FilterIndicator.ALL -> null
                        },
                    )
                    IconButton(onClick = { topMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                    }
                    AppDropdownMenu(expanded = topMenuOpen, onDismissRequest = { topMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_filters)) },
                            onClick = {
                                topMenuOpen = false
                                navController.navigate(Routes.filters(FilterTab.REMINDERS)) {
                                    launchSingleTop = true
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_tag_filter)) },
                            onClick = {
                                topMenuOpen = false
                                navController.navigate(Routes.tagFilter(FilterTab.REMINDERS)) {
                                    launchSingleTop = true
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_clear_filters)) },
                            onClick = {
                                topMenuOpen = false
                                vm.clearFilters()
                            },
                        )
                    }
                },
            )

            val folder = currentFolder
            if (folder != null && reminders.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Notifications,
                    text = stringResource(R.string.empty_reminders),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (folder == null) {
                        // Root: the five fixed type-folders, then the Once cards (TZ 3.9).
                        // With a filter active, empty folders are hidden to cut clutter; without
                        // a filter all five always show (TZ 3.9 — fixed type-folders).
                        val visibleFolders = ReminderFolder.entries
                            .filter { it != ReminderFolder.ONCE }
                            .filter { !filter.isActive || (folderCounts[it] ?: 0) > 0 }
                        items(
                            visibleFolders,
                            key = { "folder-${it.name}" },
                        ) { entry ->
                            val count = folderCounts[entry] ?: 0
                            ListItem(
                                headlineContent = {
                                    // Same counter style as Lists folders (TZ 3.1): no
                                    // counter when empty, bold when non-empty.
                                    RowTitle(
                                        name = stringResource(entry.labelRes),
                                        countsText = if (count > 0) "($count)" else null,
                                        fontWeight = if (count > 0) FontWeight.Bold else null,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                // Fixed folders: tap opens, no context menu (TZ 3.9).
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.openFolder(entry) },
                            )
                            HorizontalDivider()
                        }
                    }
                    items(reminders, key = { "reminder-${it.reminder.id}" }) { detail ->
                        ReminderCard(
                            detail = detail,
                            folder = folder ?: ReminderFolder.ONCE,
                            onToggleActive = { vm.setActive(detail, it) },
                            onEdit = {
                                navController.navigate(Routes.reminderEditor(detail.reminder.id)) {
                                    launchSingleTop = true
                                }
                            },
                            onDelete = { deleteTarget = detail },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        AppFab(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.fab_new_reminder),
            onClick = {
                // Prefilled by the opened folder type; One time in the root (TZ 3.9).
                navController.navigate(
                    Routes.reminderEditor(folder = currentFolder ?: ReminderFolder.ONCE),
                ) { launchSingleTop = true }
            },
            // Fixed FAB level from the window bottom (TZ 8).
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = FabLevel.barHeight + 16.dp),
        )

    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.delete_reminder_title),
            text = target.reminder.title,
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                vm.delete(target.reminder)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    todayOpen?.let { now ->
        TodayDialog(
            items = vm.todayItems(now.toLocalDate()),
            nowTime = now.toLocalTime(),
            remindersEnabled = remindersEnabled,
            onItemClick = { id ->
                todayOpen = null
                navController.navigate(Routes.reminderEditor(id)) { launchSingleTop = true }
            },
            onDismiss = { todayOpen = null },
        )
    }
}

// Reminder card (TZ 4.6): Active checkbox column + content column. Plain tap does
// nothing; «⋯» = Edit/Delete menu (TZ 8); swipe right = edit, left = delete.
@Composable
private fun ReminderCard(
    detail: ReminderWithDetails,
    folder: ReminderFolder,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val reminder = detail.reminder
    SwipeActionsRow(onEdit = onEdit, onDelete = onDelete) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Opaque background so the swipe color never shows through.
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Checkbox(checked = reminder.active, onCheckedChange = onToggleActive)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (detail.photos.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    PriorityStars(reminder.priority, Modifier.padding(start = 8.dp))
                }
                reminder.content?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                // Tags right after Content, above the weekday row (TZ 4.6 п. 3).
                if (detail.tags.isNotEmpty()) {
                    Text(
                        text = detail.tags.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        // Tags in plain black like the rest of the text, not the theme accent;
                        // italic sets the tag line apart instead of colour (TZ 8).
                        color = MaterialTheme.colorScheme.onSurface,
                        fontStyle = FontStyle.Italic,
                    )
                }
                // Compact weekday row for types with weekdays (TZ 4.6): mtwt-ss.
                if (folder == ReminderFolder.DAILY || folder == ReminderFolder.PERIODS) {
                    reminder.weekdaysMask?.let { mask ->
                        Text(
                            text = Weekdays.compact(mask, stringResource(R.string.weekdays_compact)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                // Interval repeat condition, e.g. «Every 3 days», above the next-fire line (TZ 4.6).
                if (folder == ReminderFolder.INTERVALS) {
                    reminder.intervalCount?.let { count ->
                        Text(
                            text = intervalSummary(count, IntervalUnit.of(reminder.intervalUnit)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(
                    text = fireLine(detail, folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EditDeleteMenuButton(onEdit = onEdit, onDelete = onDelete)
        }
    }
}

// Last card line — fire date and time (TZ 4.6). Monthly/Yearly show the next computed fire
// (next_fire_at rolls forward after each fire, TZ 4.1), falling back to the anchor date when
// inactive; the other types read the raw form fields.
private fun fireLine(detail: ReminderWithDetails, folder: ReminderFolder): String {
    val reminder = detail.reminder
    return when (folder) {
        ReminderFolder.ONCE -> listOfNotNull(reminder.date, reminder.time).joinToString(" ")

        ReminderFolder.MONTHLY,
        ReminderFolder.YEARLY,
        -> reminder.nextFireAt?.let { formatFire(it) }
            ?: listOfNotNull(reminder.date, reminder.time).joinToString(" ")

        ReminderFolder.DAILY -> detail.times.map { it.time }.sorted().joinToString(", ")

        ReminderFolder.PERIODS -> listOfNotNull(
            reminder.periodFrom,
            "–",
            reminder.periodTo,
            reminder.time,
        ).joinToString(" ")

        // Interval: the next computed fire (rolls forward after each fire), falling back to the
        // start date/time when inactive (TZ 4.6).
        ReminderFolder.INTERVALS -> reminder.nextFireAt?.let { formatFire(it) }
            ?: listOfNotNull(reminder.date, reminder.time).joinToString(" ")
    }
}

private fun formatFire(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return "${Dates.format(dt.toLocalDate())} ${Dates.format(dt.toLocalTime())}"
}
