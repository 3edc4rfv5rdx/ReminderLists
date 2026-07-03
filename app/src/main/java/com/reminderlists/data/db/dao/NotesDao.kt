package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.reminderlists.data.db.entity.NoteEntity
import com.reminderlists.data.db.entity.NoteFolderEntity
import com.reminderlists.data.db.entity.NotePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {

    @Query("SELECT * FROM note_folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<NoteFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: NoteFolderEntity): Long

    @Delete
    suspend fun deleteFolder(folder: NoteFolderEntity)

    @Query("SELECT * FROM notes WHERE folderId IS :folderId ORDER BY date IS NULL, date, title COLLATE NOCASE")
    fun observeNotes(folderId: Long?): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT * FROM note_photos WHERE noteId = :noteId ORDER BY position")
    fun observePhotos(noteId: Long): Flow<List<NotePhotoEntity>>

    // For the photo orphan sweep (TZ 8).
    @Query("SELECT filePath FROM note_photos")
    suspend fun allPhotoNames(): List<String>

    @Insert
    suspend fun insertPhoto(photo: NotePhotoEntity): Long

    @Delete
    suspend fun deletePhoto(photo: NotePhotoEntity)
}
