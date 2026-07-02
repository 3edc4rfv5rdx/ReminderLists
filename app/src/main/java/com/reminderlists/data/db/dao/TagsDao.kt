package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reminderlists.data.db.entity.NoteTagCrossRef
import com.reminderlists.data.db.entity.ReminderTagCrossRef
import com.reminderlists.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagsDao {

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkReminder(ref: ReminderTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkNote(ref: NoteTagCrossRef)

    @Query("DELETE FROM reminder_tags WHERE reminderId = :reminderId")
    suspend fun clearReminderTags(reminderId: Long)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearNoteTags(noteId: Long)

    // Prune tags no longer referenced by any reminder or note (TZ 4.2).
    @Query(
        """
        DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM reminder_tags)
          AND id NOT IN (SELECT tagId FROM note_tags)
        """,
    )
    suspend fun pruneOrphanTags()

    // Usage count in the context of the active tab (TZ 4.4).
    @Query("SELECT COUNT(*) FROM reminder_tags WHERE tagId = :tagId")
    suspend fun reminderUsageCount(tagId: Long): Int

    @Query("SELECT COUNT(*) FROM note_tags WHERE tagId = :tagId")
    suspend fun noteUsageCount(tagId: Long): Int
}
