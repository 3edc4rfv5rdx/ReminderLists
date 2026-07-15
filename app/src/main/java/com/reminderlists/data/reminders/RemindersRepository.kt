package com.reminderlists.data.reminders

import android.content.Context
import androidx.room.withTransaction
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.dao.ReminderWithDetails
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.db.entity.ReminderPhotoEntity
import com.reminderlists.data.db.entity.ReminderTagCrossRef
import com.reminderlists.data.db.entity.ReminderTimeEntity
import com.reminderlists.data.db.entity.TagEntity
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.reminders.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

// Reminders module data operations (TZ 4.2 / 4.6). Holds a context because deleting a
// reminder must also delete its photo files — Room CASCADE only clears rows (TZ 8).
// Every mutation that affects firing goes through ReminderScheduler.reschedule, which
// recomputes next_fire_at and arms/cancels the alarm (TZ 4.10).
class RemindersRepository(private val db: AppDatabase, context: Context) {

    private val appContext = context.applicationContext
    private val dao = db.remindersDao()
    private val tagsDao = db.tagsDao()

    fun observeAll(): Flow<List<ReminderWithDetails>> = dao.observeAllWithDetails()

    suspend fun getWithDetails(id: Long): ReminderWithDetails? = dao.getWithDetails(id)

    // Save the whole form in one transaction: entity + Daily times + normalized tags
    // (TZ 4.2), then recompute next_fire_at and arm the alarm (TZ 4.10). Returns the id.
    suspend fun save(reminder: ReminderEntity, dailyTimes: List<String>, tags: List<String>): Long {
        val id = db.withTransaction {
            // Insert vs update branch — a REPLACE-upsert would cascade-delete photos/events.
            val id = if (reminder.id == 0L) {
                dao.insert(reminder)
            } else {
                dao.update(reminder)
                reminder.id
            }
            dao.deleteTimes(id)
            dailyTimes.forEach { dao.insertTime(ReminderTimeEntity(reminderId = id, time = it)) }
            tagsDao.clearReminderTags(id)
            for (name in tags) {
                val tagId = tagsDao.insertTag(TagEntity(name = name)).takeIf { it > 0 }
                    ?: tagsDao.findByName(name)?.id
                    ?: continue
                tagsDao.linkReminder(ReminderTagCrossRef(reminderId = id, tagId = tagId))
            }
            // Tags left with no reminder and no note are dropped from the dictionary (TZ 4.2).
            tagsDao.pruneOrphanTags()
            id
        }
        ReminderScheduler.reschedule(appContext, db, id)
        return id
    }

    suspend fun setActive(reminder: ReminderEntity, active: Boolean) {
        dao.update(reminder.copy(active = active, updatedAt = System.currentTimeMillis()))
        // Inactive computes to null next_fire_at, so this also cancels the alarm (TZ 4.10).
        ReminderScheduler.reschedule(appContext, db, reminder.id)
    }

    suspend fun delete(reminder: ReminderEntity) {
        ReminderScheduler.cancel(appContext, reminder.id)
        val photoNames = dao.photoNamesForReminder(reminder.id)
        dao.delete(reminder)
        tagsDao.pruneOrphanTags()
        PhotoManager.deleteAll(appContext, photoNames)
    }

    // Auto-remove after firing (TZ 4.2 h): drop auto-remove reminders that fired and are done
    // (not postponed, not Monthly/Yearly), once the fire day has rolled over. Runs whenever the
    // app is foregrounded (no background job — TZ 9), so removal happens the first time the app is
    // opened on or after the next day.
    suspend fun sweepAutoRemoved() {
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.autoRemovableBefore(dayStart).forEach { delete(it) }
    }

    // Photos (shared photo module, TZ 8).

    suspend fun getPhotos(reminderId: Long): List<ReminderPhotoEntity> = dao.getPhotos(reminderId)

    suspend fun addPhoto(reminderId: Long, fileName: String) {
        db.withTransaction {
            dao.insertPhoto(
                ReminderPhotoEntity(
                    reminderId = reminderId,
                    filePath = fileName,
                    position = dao.nextPhotoPosition(reminderId),
                ),
            )
        }
    }

    suspend fun deletePhoto(photo: ReminderPhotoEntity) {
        dao.deletePhoto(photo)
        PhotoManager.delete(appContext, photo.filePath)
    }
}
