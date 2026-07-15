package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.reminderlists.data.db.entity.NoteEntity
import com.reminderlists.data.db.entity.NoteFolderEntity
import com.reminderlists.data.db.entity.NotePhotoEntity
import com.reminderlists.data.db.entity.NoteTagCrossRef
import com.reminderlists.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

// Note with everything the cards (TZ 4A.3) and the editor (TZ 4A.2) need. Tags shared via `tags`.
data class NoteWithDetails(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val photos: List<NotePhotoEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            NoteTagCrossRef::class,
            parentColumn = "noteId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

// Notes per folder, for the folder counters (styled like TZ 3.1 / 4A.1).
data class FolderNoteCount(val folderId: Long, val total: Int)

@Dao
interface NotesDao {

    // Folders (TZ 4A.1)

    @Query("SELECT * FROM note_folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<NoteFolderEntity>>

    // Plain insert + update, never INSERT OR REPLACE: REPLACE deletes the existing row first,
    // and the FK SET NULL would kick every note in the folder out to root.
    @Insert
    suspend fun insertFolder(folder: NoteFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: NoteFolderEntity)

    @Delete
    suspend fun deleteFolder(folder: NoteFolderEntity)

    @Query("DELETE FROM notes WHERE folderId = :folderId")
    suspend fun deleteNotesInFolder(folderId: Long)

    @Query("SELECT folderId, COUNT(*) AS total FROM notes WHERE folderId IS NOT NULL GROUP BY folderId")
    fun observeFolderNoteCounts(): Flow<List<FolderNoteCount>>

    // Notes (TZ 4A.2 / 4A.3)

    // One live query for the whole tab: folder counts, in-folder cards, sorting (TZ 4A.5).
    @Transaction
    @Query("SELECT * FROM notes")
    fun observeAllWithDetails(): Flow<List<NoteWithDetails>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getWithDetails(id: Long): NoteWithDetails?

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    // Plain insert + update, never INSERT OR REPLACE: REPLACE deletes the existing row first,
    // and the FK CASCADE would wipe the note's photos and tags.
    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    // Photos (shared photo module, TZ 8)

    @Query("SELECT * FROM note_photos WHERE noteId = :noteId ORDER BY position")
    suspend fun getPhotos(noteId: Long): List<NotePhotoEntity>

    // Deleting a note/folder must also delete photo files — CASCADE only clears rows (TZ 8).
    @Query("SELECT filePath FROM note_photos WHERE noteId = :noteId")
    suspend fun photoNamesForNote(noteId: Long): List<String>

    @Query("SELECT filePath FROM note_photos WHERE noteId IN (SELECT id FROM notes WHERE folderId = :folderId)")
    suspend fun photoNamesForFolder(folderId: Long): List<String>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM note_photos WHERE noteId = :noteId")
    suspend fun nextPhotoPosition(noteId: Long): Int

    // For the photo orphan sweep (TZ 8).
    @Query("SELECT filePath FROM note_photos")
    suspend fun allPhotoNames(): List<String>

    @Insert
    suspend fun insertPhoto(photo: NotePhotoEntity): Long

    @Delete
    suspend fun deletePhoto(photo: NotePhotoEntity)
}
