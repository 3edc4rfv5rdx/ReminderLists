package com.reminderlists.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Lists module (TZ 3, schema 6.1).

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val comment: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "lists",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("folderId")],
)
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val name: String,
    val comment: String? = null,
    val pinEnabled: Boolean = false,
    val pinCode: String? = null, // null = use Default PIN (TZ 3.6)
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["listId", "isDone", "position"])],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val text: String,
    val quantity: String? = null,
    val unit: String? = null,
    val isDone: Boolean = false,
    val position: Int,
    val createdAt: Long,
    val doneAt: Long? = null, // metadata only, not used for ordering (TZ 3.3)
)

@Entity(
    tableName = "item_photos",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class ItemPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val filePath: String,
    val position: Int,
)

// Autocomplete dictionary, shared app-wide (TZ 3.4).
@Entity(tableName = "dictionary", indices = [Index(value = ["text"], unique = true)])
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
)
