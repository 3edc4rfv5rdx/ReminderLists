package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

@Dao
interface ListsDao {

    // Folders — sorted alphabetically (TZ 3.1).
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity): Long

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

    @Query("SELECT listId, SUM(isDone) AS done, COUNT(*) AS total FROM items GROUP BY listId")
    fun observeListItemCounts(): Flow<List<ListItemCounts>>

    @Query("SELECT folderId, COUNT(*) AS total FROM lists WHERE folderId IS NOT NULL GROUP BY folderId")
    fun observeFolderListCounts(): Flow<List<FolderListCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: ListEntity): Long

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ItemEntity): Long

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

    @Insert
    suspend fun insertItemPhoto(photo: ItemPhotoEntity): Long

    @Delete
    suspend fun deleteItemPhoto(photo: ItemPhotoEntity)
}
