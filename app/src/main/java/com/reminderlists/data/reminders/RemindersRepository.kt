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
import kotlinx.coroutines.flow.Flow

// Reminders module data operations (TZ 4.2 / 4.6). Holds a context because deleting a
// reminder must also delete its photo files — Room CASCADE only clears rows (TZ 8).
// next_fire_at stays null and no alarm is armed yet: NextFireCalculator and the scheduler
// wiring are the engine stage (TZ 4.10).
class RemindersRepository(private val db: AppDatabase, context: Context) {

    private val appContext = context.applicationContext
    private val dao = db.remindersDao()
    private val tagsDao = db.tagsDao()

    fun observeAll(): Flow<List<ReminderWithDetails>> = dao.observeAllWithDetails()

    suspend fun getWithDetails(id: Long): ReminderWithDetails? = dao.getWithDetails(id)

    // Save the whole form in one transaction: entity + Daily times + normalized tags
    // (TZ 4.2). Returns the reminder id. TODO recompute next_fire_at and arm the alarm
    // (TZ 4.10) once the engine exists.
    suspend fun save(reminder: ReminderEntity, dailyTimes: List<String>, tags: List<String>): Long =
        db.withTransaction {
            val id = dao.upsert(reminder)
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

    suspend fun setActive(reminder: ReminderEntity, active: Boolean) {
        // TODO cancel / re-arm the alarm (TZ 4.10) once the engine exists.
        dao.update(reminder.copy(active = active, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(reminder: ReminderEntity) {
        // TODO cancel the armed alarm (TZ 4.10) once the engine exists.
        val photoNames = dao.photoNamesForReminder(reminder.id)
        dao.delete(reminder)
        tagsDao.pruneOrphanTags()
        PhotoManager.deleteAll(appContext, photoNames)
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
