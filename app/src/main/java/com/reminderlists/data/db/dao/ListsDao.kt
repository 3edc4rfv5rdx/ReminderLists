package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.reminderlists.data.db.entity.FolderEntity
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.data.db.entity.ItemPhotoEntity
import com.reminderlists.data.db.entity.ListEntity
import kotlinx.coroutines.flow.Flow

// Per-list item counters for the lists overview: "(done/total)" (TZ 3.2).
data class ListItemCounts(val listId: Long, val done: Int, val total: Int)

// Lists-per-folder counters for the folders overview: "(N)" (TZ 3.1).
data class FolderListCount(val folderId: Long, val total: Int)

// Photos-per-item counters for the item rows photo icon (TZ 3.3).
data class ItemPhotoCount(val itemId: Long, val count: Int)

// Destination list for the "move items" picker: list + its folder name, if any (TZ 3.3).
data class ListPickerEntry(val id: Long, val name: String, val folderName: String?)

@Dao
interface ListsDao {

    // Folders — sorted alphabetically (TZ 3.1).
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    // Plain insert + update, never INSERT OR REPLACE: REPLACE deletes the existing row first,
    // and the FK SET NULL would kick every list in the folder out to root.
    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: Long): FolderEntity?

    // Lists — sorted alphabetically within folder/root (TZ 3.2).
    @Query("SELECT * FROM lists WHERE folderId IS :folderId ORDER BY name COLLATE NOCASE")
    fun observeLists(folderId: Long?): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun getList(id: Long): ListEntity?

    @Query("SELECT * FROM lists WHERE id = :id")
    fun observeList(id: Long): Flow<ListEntity?>

    @Query("DELETE FROM lists WHERE folderId = :folderId")
    suspend fun deleteListsInFolder(folderId: Long)

    // Deleting a folder's contents while its delete-protected lists survive (TZ 3.2a):
    // the unprotected ones go, the protected ones are moved out to root.
    @Query("DELETE FROM lists WHERE folderId = :folderId AND deleteLocked = 0")
    suspend fun deleteUnlockedListsInFolder(folderId: Long)

    @Query("UPDATE lists SET folderId = NULL WHERE folderId = :folderId AND deleteLocked = 1")
    suspend fun unfileLockedListsInFolder(folderId: Long)

    @Query("SELECT listId, SUM(isDone) AS done, COUNT(*) AS total FROM items GROUP BY listId")
    fun observeListItemCounts(): Flow<List<ListItemCounts>>

    @Query("SELECT folderId, COUNT(*) AS total FROM lists WHERE folderId IS NOT NULL GROUP BY folderId")
    fun observeFolderListCounts(): Flow<List<FolderListCount>>

    // Delete-protected lists per folder — the folder delete dialog offers a third option
    // only when the folder holds any (TZ 3.2a).
    @Query(
        "SELECT folderId, COUNT(*) AS total FROM lists " +
            "WHERE folderId IS NOT NULL AND deleteLocked = 1 GROUP BY folderId",
    )
    fun observeFolderLockedCounts(): Flow<List<FolderListCount>>

    // All lists for the "move items" picker, root first (NULL folder sorts first in ASC),
    // then by folder (TZ 3.3).
    @Query(
        "SELECT l.id, l.name, f.name AS folderName FROM lists l " +
            "LEFT JOIN folders f ON l.folderId = f.id " +
            "ORDER BY f.name COLLATE NOCASE, l.name COLLATE NOCASE",
    )
    fun observeListPickerEntries(): Flow<List<ListPickerEntry>>

    // Plain insert — a REPLACE on an existing id would cascade-delete the list's items.
    @Insert
    suspend fun insertList(list: ListEntity): Long

    @Update
    suspend fun updateList(list: ListEntity)

    @Delete
    suspend fun deleteList(list: ListEntity)

    // Items — active first then done, by position (TZ 3.3).
    @Query("SELECT * FROM items WHERE listId = :listId ORDER BY isDone, position")
    fun observeItems(listId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItem(id: Long): ItemEntity?

    // Next free position at the end of the active group. MAX+1 instead of COUNT — checking an
    // item out of the middle leaves position gaps, and COUNT could collide with a live position.
    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM items WHERE listId = :listId AND isDone = 0")
    suspend fun nextActivePosition(listId: Long): Int

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM items WHERE listId = :listId AND isDone = 1")
    suspend fun nextDonePosition(listId: Long): Int

    // Plain insert — a REPLACE on an existing id would cascade-delete the item's photos.
    @Insert
    suspend fun insertItem(item: ItemEntity): Long

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("UPDATE items SET position = :position WHERE id = :id")
    suspend fun setItemPosition(id: Long, position: Int)

    // Make room at the top of the done group — a newly checked item goes right under the divider (TZ 3.3).
    @Query("UPDATE items SET position = position + 1 WHERE listId = :listId AND isDone = 1")
    suspend fun shiftDoneItemsDown(listId: Long)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE listId = :listId AND isDone = 1")
    suspend fun deleteDoneItems(listId: Long)

    // Uncheck all: done items keep their relative order and append after the active group (TZ 3.2 / 3.3).
    @Query("UPDATE items SET position = position + :offset, isDone = 0, doneAt = NULL WHERE listId = :listId AND isDone = 1")
    suspend fun uncheckAllDone(listId: Long, offset: Int)

    @Query("SELECT * FROM item_photos WHERE itemId = :itemId ORDER BY position")
    fun observeItemPhotos(itemId: Long): Flow<List<ItemPhotoEntity>>

    @Query("SELECT * FROM item_photos WHERE itemId = :itemId ORDER BY position")
    suspend fun getItemPhotos(itemId: Long): List<ItemPhotoEntity>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM item_photos WHERE itemId = :itemId")
    suspend fun nextPhotoPosition(itemId: Long): Int

    // Per-item photo counts of a list — drives the photo icon in item rows (TZ 3.3 p.3).
    @Query(
        "SELECT p.itemId AS itemId, COUNT(*) AS count FROM item_photos p " +
            "JOIN items i ON p.itemId = i.id WHERE i.listId = :listId GROUP BY p.itemId",
    )
    fun observeItemPhotoCounts(listId: Long): Flow<List<ItemPhotoCount>>

    // File names for cascade cleanup — Room CASCADE clears rows, files are deleted by hand (TZ 8).
    @Query("SELECT filePath FROM item_photos WHERE itemId = :itemId")
    suspend fun photoNamesForItem(itemId: Long): List<String>

    @Query("SELECT p.filePath FROM item_photos p JOIN items i ON p.itemId = i.id WHERE i.listId = :listId")
    suspend fun photoNamesForList(listId: Long): List<String>

    @Query(
        "SELECT p.filePath FROM item_photos p JOIN items i ON p.itemId = i.id " +
            "WHERE i.listId = :listId AND i.isDone = 1",
    )
    suspend fun photoNamesForDoneItems(listId: Long): List<String>

    @Query(
        "SELECT p.filePath FROM item_photos p JOIN items i ON p.itemId = i.id " +
            "JOIN lists l ON i.listId = l.id WHERE l.folderId = :folderId",
    )
    suspend fun photoNamesForFolder(folderId: Long): List<String>

    @Query(
        "SELECT p.filePath FROM item_photos p JOIN items i ON p.itemId = i.id " +
            "JOIN lists l ON i.listId = l.id WHERE l.folderId = :folderId AND l.deleteLocked = 0",
    )
    suspend fun photoNamesForUnlockedInFolder(folderId: Long): List<String>

    @Query("SELECT filePath FROM item_photos")
    suspend fun allPhotoNames(): List<String>

    @Insert
    suspend fun insertItemPhoto(photo: ItemPhotoEntity): Long

    @Delete
    suspend fun deleteItemPhoto(photo: ItemPhotoEntity)
}
