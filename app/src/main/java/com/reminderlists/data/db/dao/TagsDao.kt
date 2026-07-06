package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reminderlists.data.db.entity.NoteTagCrossRef
import com.reminderlists.data.db.entity.ReminderTagCrossRef
import com.reminderlists.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

// A tag with its usage count in the context of the active tab, for the Tag Filter cloud (TZ 4.4).
data class TagUsage(
    val id: Long,
    val name: String,
    val count: Int,
)

// A record<->tag link, used to compute how many records a drafted tag filter would match
// (AND mode can yield zero even when each tag is used on its own, TZ 4.4).
data class TagLink(
    val recordId: Long,
    val tagId: Long,
)

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

    // Tag cloud for the Tag Filter screen (TZ 4.4): only tags used on the active tab, most
    // frequent first (JOIN excludes zero-count tags — a tag used only on the other tab is hidden).
    @Query(
        """
        SELECT t.id AS id, t.name AS name, COUNT(rt.reminderId) AS count
        FROM tags t JOIN reminder_tags rt ON rt.tagId = t.id
        GROUP BY t.id, t.name
        ORDER BY count DESC, t.name COLLATE NOCASE
        """,
    )
    fun observeReminderTagUsage(): Flow<List<TagUsage>>

    @Query(
        """
        SELECT t.id AS id, t.name AS name, COUNT(nt.noteId) AS count
        FROM tags t JOIN note_tags nt ON nt.tagId = t.id
        GROUP BY t.id, t.name
        ORDER BY count DESC, t.name COLLATE NOCASE
        """,
    )
    fun observeNoteTagUsage(): Flow<List<TagUsage>>

    // All record<->tag links for the active tab, to preview a drafted filter's match count (TZ 4.4).
    @Query("SELECT reminderId AS recordId, tagId FROM reminder_tags")
    fun observeReminderTagLinks(): Flow<List<TagLink>>

    @Query("SELECT noteId AS recordId, tagId FROM note_tags")
    fun observeNoteTagLinks(): Flow<List<TagLink>>
}
