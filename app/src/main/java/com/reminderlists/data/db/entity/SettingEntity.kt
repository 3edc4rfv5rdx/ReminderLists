package com.reminderlists.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Key-value settings (TZ 6.3). Keys enumerated in SettingsKeys.
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String? = null,
)
