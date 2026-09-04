package com.reminderlists.util

import dev.updater.UpdaterConfig

/**
 * What the shared updater (../updater) needs to know about this app, in one place: the
 * start-up check and the "Check for updates" entry in settings must ask the same server
 * about the same app key, and two spellings of that would drift apart on the first move.
 *
 * The server address and the check interval are the module's own defaults.
 */
val UPDATER_CONFIG = UpdaterConfig(appKey = "reminderlists")
