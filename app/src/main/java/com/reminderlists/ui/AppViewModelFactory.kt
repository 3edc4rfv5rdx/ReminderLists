package com.reminderlists.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reminderlists.data.db.AppDatabase

// Shared ViewModel factory: hands the singleton AppDatabase to screen ViewModels (no DI framework).
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppDatabase) -> VM,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            create(AppDatabase.get(app))
        }
    }
