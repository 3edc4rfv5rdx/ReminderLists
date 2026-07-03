package com.reminderlists.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reminderlists.data.db.AppDatabase

// Shared ViewModel factory: hands the singleton AppDatabase and the Application (for file
// operations like the photo store) to screen ViewModels — no DI framework.
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppDatabase, Application) -> VM,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            create(AppDatabase.get(app), app)
        }
    }
