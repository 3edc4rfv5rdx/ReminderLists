package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.db.entity.ReminderEventEntity
import com.reminderlists.data.db.entity.ReminderPhotoEntity
import com.reminderlists.data.db.entity.ReminderTagCrossRef
import com.reminderlists.data.db.entity.ReminderTimeEntity
import com.reminderlists.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

// Reminder with everything the folder cards (TZ 4.6) and the editor (TZ 4.2) need.
data class ReminderWithDetails(
    @Embedded val reminder: ReminderEntity,
    @Relation(parentColumn = "id", entityColumn = "reminderId")
    val times: List<ReminderTimeEntity>,
    @Relation(parentColumn = "id", entityColumn = "reminderId")
    val photos: List<ReminderPhotoEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            ReminderTagCrossRef::class,
            parentColumn = "reminderId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

@Dao
interface RemindersDao {

    @Query("SELECT * FROM reminders ORDER BY nextFireAt IS NULL, nextFireAt")
    fun observeAll(): Flow<List<ReminderEntity>>

    // One live query for the whole tab: folder counts, in-folder cards, sorting (TZ 4.6).
    @Transaction
    @Query("SELECT * FROM reminders")
    fun observeAllWithDetails(): Flow<List<ReminderWithDetails>>

    @Transaction
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getWithDetails(id: Long): ReminderWithDetails?

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    // Active reminders — used by scheduler re-arm on boot / time change (TZ 4.10).
    @Query("SELECT * FROM reminders WHERE active = 1")
    suspend fun getActive(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("UPDATE reminders SET nextFireAt = :nextFireAt WHERE id = :id")
    suspend fun updateNextFire(id: Long, nextFireAt: Long?)

    // Active reminders sharing one fire minute, lowest id first — used to hand out same-minute
    // stagger slots so the first fires exactly on time and the rest are spaced (TZ 4.10).
    @Query("SELECT id FROM reminders WHERE nextFireAt = :fireAt AND active = 1 ORDER BY id")
    suspend fun idsFiringAt(fireAt: Long): List<Long>

    @Query("SELECT * FROM reminder_times WHERE reminderId = :reminderId")
    suspend fun getTimes(reminderId: Long): List<ReminderTimeEntity>

    @Insert
    suspend fun insertTime(time: ReminderTimeEntity): Long

    @Query("DELETE FROM reminder_times WHERE reminderId = :reminderId")
    suspend fun deleteTimes(reminderId: Long)

    @Query("SELECT * FROM reminder_photos WHERE reminderId = :reminderId ORDER BY position")
    fun observePhotos(reminderId: Long): Flow<List<ReminderPhotoEntity>>

    @Query("SELECT * FROM reminder_photos WHERE reminderId = :reminderId ORDER BY position")
    suspend fun getPhotos(reminderId: Long): List<ReminderPhotoEntity>

    // Deleting a reminder must also delete photo files — CASCADE only clears rows (TZ 8).
    @Query("SELECT filePath FROM reminder_photos WHERE reminderId = :reminderId")
    suspend fun photoNamesForReminder(reminderId: Long): List<String>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM reminder_photos WHERE reminderId = :reminderId")
    suspend fun nextPhotoPosition(reminderId: Long): Int

    // For the photo orphan sweep (TZ 8).
    @Query("SELECT filePath FROM reminder_photos")
    suspend fun allPhotoNames(): List<String>

    @Insert
    suspend fun insertPhoto(photo: ReminderPhotoEntity): Long

    @Delete
    suspend fun deletePhoto(photo: ReminderPhotoEntity)

    @Insert
    suspend fun insertEvent(event: ReminderEventEntity): Long

    @Query("SELECT * FROM reminder_events WHERE reminderId = :reminderId ORDER BY createdAt DESC")
    suspend fun getEvents(reminderId: Long): List<ReminderEventEntity>
}
