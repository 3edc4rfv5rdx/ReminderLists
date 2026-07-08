package com.reminderlists.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reminderlists.data.filter.FilterTab
import com.reminderlists.data.reminders.ReminderFolder
import com.reminderlists.ui.components.AppSnackbar
import com.reminderlists.ui.components.FabLevel
import com.reminderlists.ui.components.LocalSnackController
import com.reminderlists.ui.components.SnackController
import com.reminderlists.ui.screens.dictionary.DictionaryScreen
import com.reminderlists.ui.screens.filters.FiltersScreen
import com.reminderlists.ui.screens.filters.TagFilterScreen
import com.reminderlists.ui.screens.lists.ItemEditorScreen
import com.reminderlists.ui.screens.lists.ListDetailScreen
import com.reminderlists.ui.screens.lists.ListsScreen
import com.reminderlists.ui.screens.notes.NoteEditorScreen
import com.reminderlists.ui.screens.notes.NotesScreen
import com.reminderlists.ui.screens.reminders.ReminderEditorScreen
import com.reminderlists.ui.screens.reminders.RemindersScreen
import com.reminderlists.ui.screens.settings.SettingsScreen

// Compact bottom-bar content height (excl. the system gesture inset); M3 default is 80dp.
private val BOTTOM_BAR_HEIGHT = 64.dp

// Root: single Scaffold + bottom navigation shared by all tabs (TZ 3.9). Tab state is
// preserved via saveState/restoreState. Service screens are separate routes.
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    val onTabRoute = Tab.entries.any { tab ->
        currentRoute?.hierarchy?.any { it.route == tab.route } == true
    }
    val density = LocalDensity.current
    val snackController = remember { SnackController() }

    Box(Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalSnackController provides snackController) {
        Scaffold(
        // Status-bar inset is handled by AppTopBar itself; without this the content padding
        // would add it a second time and push the top bar down.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTabRoute) {
                // Shorter than the M3 default 80dp: fixed content height + the gesture inset
                // so the bar clears the system nav area but stays compact.
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                NavigationBar(
                    // Measure the bar once at startup: every FAB on every screen is drawn at
                    // this level from the window bottom, so it never jumps (TZ 8).
                    modifier = Modifier
                        .height(BOTTOM_BAR_HEIGHT + bottomInset)
                        .onSizeChanged { size ->
                            FabLevel.barHeight = with(density) { size.height.toDp() }
                        },
                ) {
                    Tab.entries.forEach { tab ->
                        val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                androidx.compose.material3.Icon(
                                    tab.icon,
                                    contentDescription = stringResource(tab.labelRes),
                                )
                            },
                            // Keep the label on one line at any font scale (TZ 5/8):
                            // a large scale must not wrap "Reminders" onto two lines.
                            label = {
                                Text(
                                    stringResource(tab.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            // Strong primary pill behind the active tab so the current
                            // section is unmistakable (user request).
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.LISTS.route,
        ) {
            composable(Tab.LISTS.route) { ListsScreen(navController, padding) }
            composable(Tab.REMINDERS.route) { RemindersScreen(navController, padding) }
            composable(Tab.NOTES.route) { NotesScreen(navController, padding) }

            composable(
                Routes.LIST_DETAIL,
                arguments = listOf(navArgument("listId") { type = NavType.LongType }),
            ) { entry ->
                ListDetailScreen(navController, entry.arguments?.getLong("listId") ?: 0L)
            }

            composable(
                Routes.ITEM_EDITOR,
                arguments = listOf(
                    navArgument("listId") { type = NavType.LongType },
                    navArgument("itemId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                ItemEditorScreen(
                    navController,
                    listId = entry.arguments?.getLong("listId") ?: 0L,
                    itemId = entry.arguments?.getLong("itemId") ?: 0L,
                )
            }

            composable(
                Routes.REMINDER_EDITOR,
                arguments = listOf(
                    navArgument("reminderId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("folder") {
                        type = NavType.StringType
                        defaultValue = ReminderFolder.ONCE.name
                    },
                ),
            ) { entry ->
                ReminderEditorScreen(
                    navController,
                    reminderId = entry.arguments?.getLong("reminderId") ?: 0L,
                    folder = ReminderFolder.valueOf(
                        entry.arguments?.getString("folder") ?: ReminderFolder.ONCE.name,
                    ),
                )
            }

            composable(
                Routes.NOTE_EDITOR,
                arguments = listOf(
                    navArgument("noteId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("folderId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                NoteEditorScreen(
                    navController,
                    noteId = entry.arguments?.getLong("noteId") ?: 0L,
                    // -1 encodes "root" (no folder) — the nav arg can't carry a nullable Long.
                    folderId = entry.arguments?.getLong("folderId")?.takeIf { it >= 0 },
                )
            }

            composable(Routes.DICTIONARY) { DictionaryScreen(navController) }

            composable(Routes.SETTINGS) { SettingsScreen(navController, padding) }
            composable(
                Routes.FILTERS,
                arguments = listOf(navArgument("tab") { type = NavType.StringType }),
            ) { entry ->
                FiltersScreen(
                    navController,
                    tab = FilterTab.valueOf(
                        entry.arguments?.getString("tab") ?: FilterTab.REMINDERS.name,
                    ),
                )
            }
            composable(
                Routes.TAG_FILTER,
                arguments = listOf(navArgument("tab") { type = NavType.StringType }),
            ) { entry ->
                TagFilterScreen(
                    navController,
                    tab = FilterTab.valueOf(
                        entry.arguments?.getString("tab") ?: FilterTab.REMINDERS.name,
                    ),
                )
            }
        }
        }
      }

      // Single app-wide snackbar host, fixed in the lower sixth of the screen and above the
      // bottom bar by Z (TZ 8).
      AppSnackbar(
          event = snackController.event,
          onDismiss = { snackController.dismiss() },
          modifier = Modifier.align(BiasAlignment(0f, 0.8f)),
      )
    }
}
