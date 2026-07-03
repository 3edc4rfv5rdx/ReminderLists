package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.db.entity.ReminderEventEntity
import com.reminderlists.data.db.entity.ReminderPhotoEntity
import com.reminderlists.data.db.entity.ReminderTimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemindersDao {

    @Query("SELECT * FROM reminders ORDER BY nextFireAt IS NULL, nextFireAt")
    fun observeAll(): Flow<List<ReminderEntity>>

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

    @Query("SELECT * FROM reminder_times WHERE reminderId = :reminderId")
    suspend fun getTimes(reminderId: Long): List<ReminderTimeEntity>

    @Insert
    suspend fun insertTime(time: ReminderTimeEntity): Long

    @Query("DELETE FROM reminder_times WHERE reminderId = :reminderId")
    suspend fun deleteTimes(reminderId: Long)

    @Query("SELECT * FROM reminder_photos WHERE reminderId = :reminderId ORDER BY position")
    fun observePhotos(reminderId: Long): Flow<List<ReminderPhotoEntity>>

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
