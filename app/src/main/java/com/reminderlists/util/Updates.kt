package com.reminderlists.util

import dev.updater.UpdaterConfig

/**
 * What the shared updater (../updater) needs to know about this app, in one place: the
 * start-up check and the "Check for updates" entry in settings must read the same release,
 * and two spellings of that would drift apart on the first move.
 *
 * The GitHub account and the check interval are the module's own defaults; the repository
 * is named here only because it is not spelled like the app key.
 */
val UPDATER_CONFIG = UpdaterConfig(appKey = "reminderlists", repo = "ReminderLists")
