package com.reminderlists.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.reminderlists.ui.screens.notes.NotesScreen
import com.reminderlists.ui.screens.reminders.ReminderEditorScreen
import com.reminderlists.ui.screens.reminders.RemindersScreen
import com.reminderlists.ui.screens.settings.SettingsScreen

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
                NavigationBar(
                    // Measure the bar once at startup: every FAB on every screen is drawn at
                    // this level from the window bottom, so it never jumps (TZ 8).
                    modifier = Modifier.onSizeChanged { size ->
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
                            icon = { androidx.compose.material3.Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
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

            composable(Routes.DICTIONARY) { DictionaryScreen(navController) }

            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.FILTERS) { FiltersScreen(navController) }
            composable(Routes.TAG_FILTER) { TagFilterScreen(navController) }
            // Welcome (TZ 4.8) is a dialog, not a route — see WelcomeDialog.
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
