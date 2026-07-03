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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: ListEntity): Long

    @Update
    suspend fun updateList(list: ListEntity)

    @Delete
    suspend fun deleteList(list: ListEntity)

    // Items — active first then done, by position (TZ 3.3).
    @Query("SELECT * FROM items WHERE listId = :listId ORDER BY isDone, position")
    fun observeItems(listId: Long): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ItemEntity): Long

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE listId = :listId AND isDone = 1")
    suspend fun deleteDoneItems(listId: Long)

    @Query("UPDATE items SET isDone = 0, doneAt = NULL WHERE listId = :listId")
    suspend fun clearAllDone(listId: Long)

    @Query("SELECT * FROM item_photos WHERE itemId = :itemId ORDER BY position")
    fun observeItemPhotos(itemId: Long): Flow<List<ItemPhotoEntity>>

    @Insert
    suspend fun insertItemPhoto(photo: ItemPhotoEntity): Long

    @Delete
    suspend fun deleteItemPhoto(photo: ItemPhotoEntity)
}
