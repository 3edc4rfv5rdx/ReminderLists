package com.reminderlists.data.notes

import android.content.Context
import androidx.room.withTransaction
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.dao.FolderNoteCount
import com.reminderlists.data.db.dao.NoteWithDetails
import com.reminderlists.data.db.entity.NoteEntity
import com.reminderlists.data.db.entity.NoteFolderEntity
import com.reminderlists.data.db.entity.NotePhotoEntity
import com.reminderlists.data.db.entity.NoteTagCrossRef
import com.reminderlists.data.db.entity.TagEntity
import com.reminderlists.data.photo.PhotoManager
import kotlinx.coroutines.flow.Flow

// Notes module data operations (TZ 4A). Устроен как «Списки»: folders (4A.1) + cards (4A.3),
// плюс поля/теги/фото напоминания. No firing. Holds a context because deleting a note must
// also delete its photo files — Room CASCADE only clears rows (TZ 8).
class NotesRepository(private val db: AppDatabase, context: Context) {

    private val appContext = context.applicationContext
    private val dao = db.notesDao()
    private val tagsDao = db.tagsDao()

    // Folders (TZ 4A.1)

    fun observeFolders(): Flow<List<NoteFolderEntity>> = dao.observeFolders()

    fun observeFolderNoteCounts(): Flow<List<FolderNoteCount>> = dao.observeFolderNoteCounts()

    suspend fun createFolder(name: String, comment: String?) {
        dao.upsertFolder(
            NoteFolderEntity(
                name = name,
                comment = comment?.takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun renameFolder(folder: NoteFolderEntity, name: String) {
        dao.upsertFolder(folder.copy(name = name))
    }

    suspend fun updateFolderComment(folder: NoteFolderEntity, comment: String?) {
        dao.upsertFolder(folder.copy(comment = comment?.takeIf { it.isNotBlank() }))
    }

    // Delete folder; notes inside are either deleted or moved to root (FK SET_NULL) — TZ 4A.1.
    suspend fun deleteFolder(folder: NoteFolderEntity, deleteNotes: Boolean) {
        val photoNames = if (deleteNotes) dao.photoNamesForFolder(folder.id) else emptyList()
        db.withTransaction {
            if (deleteNotes) dao.deleteNotesInFolder(folder.id)
            dao.deleteFolder(folder)
        }
        PhotoManager.deleteAll(appContext, photoNames)
        tagsDao.pruneOrphanTags()
    }

    // Notes (TZ 4A.2 / 4A.3)

    fun observeAll(): Flow<List<NoteWithDetails>> = dao.observeAllWithDetails()

    suspend fun getWithDetails(id: Long): NoteWithDetails? = dao.getWithDetails(id)

    // Save the whole form in one transaction: entity + normalized tags (TZ 4A.2 / 4.2 п. 3).
    // Returns the note id (new notes get their photos attached right after).
    suspend fun save(note: NoteEntity, tags: List<String>): Long = db.withTransaction {
        val id = dao.upsert(note)
        tagsDao.clearNoteTags(id)
        for (name in tags) {
            val tagId = tagsDao.insertTag(TagEntity(name = name)).takeIf { it > 0 }
                ?: tagsDao.findByName(name)?.id
                ?: continue
            tagsDao.linkNote(NoteTagCrossRef(noteId = id, tagId = tagId))
        }
        // Tags left with no reminder and no note are dropped from the dictionary (TZ 4.2).
        tagsDao.pruneOrphanTags()
        id
    }

    suspend fun delete(note: NoteEntity) {
        val photoNames = dao.photoNamesForNote(note.id)
        dao.delete(note)
        tagsDao.pruneOrphanTags()
        PhotoManager.deleteAll(appContext, photoNames)
    }

    suspend fun moveNote(note: NoteEntity, folderId: Long?) {
        dao.update(note.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    }

    // PIN protection (TZ 4A.4 / 3.6): customPin == null means "use the default PIN from Settings".
    suspend fun setNoteProtection(note: NoteEntity, enabled: Boolean, customPin: String?) {
        dao.update(
            note.copy(
                pinEnabled = enabled,
                pinCode = customPin.takeIf { enabled },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    // Photos (shared photo module, TZ 8).

    suspend fun getPhotos(noteId: Long): List<NotePhotoEntity> = dao.getPhotos(noteId)

    suspend fun addPhoto(noteId: Long, fileName: String) {
        db.withTransaction {
            dao.insertPhoto(
                NotePhotoEntity(
                    noteId = noteId,
                    filePath = fileName,
                    position = dao.nextPhotoPosition(noteId),
                ),
            )
        }
    }

    suspend fun deletePhoto(photo: NotePhotoEntity) {
        dao.deletePhoto(photo)
        PhotoManager.delete(appContext, photo.filePath)
    }
}
