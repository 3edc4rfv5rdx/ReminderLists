package com.reminderlists.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Reminders module (TZ 4, schema 6.2). Folder-type (Once/Daily/Periods/Monthly/Yearly)
// is derived from these flags, not stored (TZ 4.1).

@Entity(
    tableName = "reminders",
    indices = [Index("nextFireAt")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String? = null,
    val priority: Int = 0,

    val active: Boolean = true,
    val fullScreenAlert: Boolean = false,

    // 0 = One time, 1 = Daily, 2 = Period
    val repeatType: Int = 0,

    // One time (and derived Monthly / Yearly)
    val date: String? = null, // 'YYYY-MM-DD'
    val time: String? = null, // 'HH:MM'
    val monthlyRepeat: Boolean = false,
    val yearlyRepeat: Boolean = false,
    val autoRemove: Boolean = false,

    // Period
    val periodFrom: String? = null,
    val periodTo: String? = null,

    // Weekday bitmask for Daily / Period: bit0 = Mon .. bit6 = Sun
    val weekdaysMask: Int? = null,

    val loopSound: Boolean = true,
    // null = Default (settings); 'builtin:<id>' = bundled; else file name in sounds/
    val soundUri: String? = null,

    // Denormalized next-fire cache (unixtime, wall-clock derived). Source of truth for
    // scheduler (4.10), folder sorting (4.6), Today (4.11). null = nothing to fire.
    val nextFireAt: Long? = null,

    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "reminder_times",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reminderId")],
)
data class ReminderTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val time: String, // 'HH:MM'
)

@Entity(
    tableName = "reminder_photos",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reminderId")],
)
data class ReminderPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val filePath: String,
    val position: Int,
)

// Tag dictionary, shared between Reminders and Notes (TZ 4.2 / 4.4). name stored normalized.
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "reminder_tags",
    primaryKeys = ["reminderId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class ReminderTagCrossRef(
    val reminderId: Long,
    val tagId: Long,
)

// Firing history/state: postpone, Done/Continue, Monthly/Yearly roll-over, deferred auto-remove (TZ 6.2).
@Entity(
    tableName = "reminder_events",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reminderId")],
)
data class ReminderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val firedAt: Long? = null,
    val action: String? = null, // 'postpone' | 'ok' | 'done' | 'continue'
    val postponeUntil: Long? = null,
    val createdAt: Long,
)
