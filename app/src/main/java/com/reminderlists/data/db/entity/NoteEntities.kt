package com.reminderlists.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Notes module (TZ 4A, schema 6.1a). Long-lived cards, no firing. Tags shared via `tags` (6.2).

@Entity(tableName = "note_folders")
data class NoteFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val comment: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = NoteFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("folderId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val title: String,
    val content: String? = null,
    val date: String? = null, // free text, not validated (TZ 4A.2)
    val priority: Int = 0,
    val pinEnabled: Boolean = false,
    val pinCode: String? = null, // null = use Default PIN (TZ 3.6)
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "note_photos",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class NotePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val filePath: String,
    val position: Int,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long,
)
