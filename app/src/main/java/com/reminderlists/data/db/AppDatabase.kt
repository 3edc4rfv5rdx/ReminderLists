package com.reminderlists.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.reminderlists.data.db.dao.DictionaryDao
import com.reminderlists.data.db.dao.ListsDao
import com.reminderlists.data.db.dao.NotesDao
import com.reminderlists.data.db.dao.RemindersDao
import com.reminderlists.data.db.dao.SettingsDao
import com.reminderlists.data.db.dao.TagsDao
import com.reminderlists.data.db.entity.DictionaryEntity
import com.reminderlists.data.db.entity.FolderEntity
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.data.db.entity.ItemPhotoEntity
import com.reminderlists.data.db.entity.ListEntity
import com.reminderlists.data.db.entity.NoteEntity
import com.reminderlists.data.db.entity.NoteFolderEntity
import com.reminderlists.data.db.entity.NotePhotoEntity
import com.reminderlists.data.db.entity.NoteTagCrossRef
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.db.entity.ReminderEventEntity
import com.reminderlists.data.db.entity.ReminderPhotoEntity
import com.reminderlists.data.db.entity.ReminderTagCrossRef
import com.reminderlists.data.db.entity.ReminderTimeEntity
import com.reminderlists.data.db.entity.SettingEntity
import com.reminderlists.data.db.entity.TagEntity

// Single SQLite database for all three modules (TZ 6). Full migrations only in release —
// no fallbackToDestructiveMigration (TZ 6.4). Bump version + add a Migration per schema change.
@Database(
    entities = [
        // Lists (6.1)
        FolderEntity::class,
        ListEntity::class,
        ItemEntity::class,
        ItemPhotoEntity::class,
        DictionaryEntity::class,
        // Notes (6.1a)
        NoteFolderEntity::class,
        NoteEntity::class,
        NotePhotoEntity::class,
        NoteTagCrossRef::class,
        // Reminders (6.2)
        ReminderEntity::class,
        ReminderTimeEntity::class,
        ReminderPhotoEntity::class,
        TagEntity::class,
        ReminderTagCrossRef::class,
        ReminderEventEntity::class,
        // Settings (6.3)
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listsDao(): ListsDao
    abstract fun notesDao(): NotesDao
    abstract fun remindersDao(): RemindersDao
    abstract fun tagsDao(): TagsDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val DB_NAME = "reminderlists.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        // Close and drop the singleton so the next get() reopens from disk. Used by Restore
        // (TZ 3.8) before swapping the DB file underneath; the app restarts right after.
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        fun databaseFile(context: Context): java.io.File =
            context.getDatabasePath(DB_NAME)

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                // Foreign keys enforced (ON DELETE CASCADE / SET NULL, see entities).
                .build()
    }
}
