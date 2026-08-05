package com.reminderlists.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = AppDatabase.DB_VERSION,
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
        // Single source of the schema version — the @Database annotation above and the
        // backup manifest/restore validation (BackupManager) must never drift apart.
        const val DB_VERSION = 4

        private const val DB_NAME = "reminderlists.db"

        // v2: Interval reminder type (TZ 4.2 f‴ / 6.2) — two nullable columns on reminders.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN intervalCount INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN intervalUnit INTEGER")
            }
        }

        // v3: delete protection for lists (TZ 3.2a / 6.1) — one column on lists.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lists ADD COLUMN deleteLocked INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v4: Period Done skips the rest of the current window instead of clearing Active
        // (TZ 4.5 / 6.2) — one nullable column on reminders.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN periodSkipUntil INTEGER")
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
