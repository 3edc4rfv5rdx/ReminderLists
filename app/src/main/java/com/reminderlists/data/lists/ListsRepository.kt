package com.reminderlists.data.lists

import android.content.Context
import androidx.room.withTransaction
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.dao.FolderListCount
import com.reminderlists.data.db.dao.ItemPhotoCount
import com.reminderlists.data.db.dao.ListItemCounts
import com.reminderlists.data.db.dao.ListPickerEntry
import com.reminderlists.data.db.entity.FolderEntity
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.data.db.entity.ItemPhotoEntity
import com.reminderlists.data.db.entity.ListEntity
import com.reminderlists.data.photo.PhotoManager
import kotlinx.coroutines.flow.Flow

// Lists module data operations (TZ 3.1 / 3.2 / 3.3). Holds a context because deleting
// records must also delete their photo files — Room CASCADE only clears rows (TZ 8).
class ListsRepository(private val db: AppDatabase, context: Context) {

    private val appContext = context.applicationContext
    private val dao = db.listsDao()

    // Folders (TZ 3.1)

    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    suspend fun createFolder(name: String, comment: String?) {
        dao.upsertFolder(
            FolderEntity(
                name = name,
                comment = comment?.takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun renameFolder(folder: FolderEntity, name: String) {
        dao.upsertFolder(folder.copy(name = name))
    }

    suspend fun updateFolderComment(folder: FolderEntity, comment: String?) {
        dao.upsertFolder(folder.copy(comment = comment?.takeIf { it.isNotBlank() }))
    }

    // Delete folder; lists inside are either deleted or moved to root (FK SET_NULL) — TZ 3.1.
    suspend fun deleteFolder(folder: FolderEntity, deleteLists: Boolean) {
        val photoNames = if (deleteLists) dao.photoNamesForFolder(folder.id) else emptyList()
        db.withTransaction {
            if (deleteLists) dao.deleteListsInFolder(folder.id)
            dao.deleteFolder(folder)
        }
        PhotoManager.deleteAll(appContext, photoNames)
    }

    // Lists (TZ 3.2)

    fun observeLists(folderId: Long?): Flow<List<ListEntity>> = dao.observeLists(folderId)

    fun observeList(id: Long): Flow<ListEntity?> = dao.observeList(id)

    fun observeListItemCounts(): Flow<List<ListItemCounts>> = dao.observeListItemCounts()

    fun observeFolderListCounts(): Flow<List<FolderListCount>> = dao.observeFolderListCounts()

    fun observeListPickerEntries(): Flow<List<ListPickerEntry>> = dao.observeListPickerEntries()

    suspend fun createList(folderId: Long?, name: String, comment: String?) {
        val now = System.currentTimeMillis()
        dao.upsertList(
            ListEntity(
                folderId = folderId,
                name = name,
                comment = comment?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun editList(list: ListEntity, name: String, comment: String?) {
        dao.updateList(
            list.copy(
                name = name,
                comment = comment?.takeIf { it.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun moveList(list: ListEntity, folderId: Long?) {
        dao.updateList(list.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteList(list: ListEntity) {
        val photoNames = dao.photoNamesForList(list.id)
        dao.deleteList(list)
        PhotoManager.deleteAll(appContext, photoNames)
    }

    // Items (TZ 3.3). Positions are group-local: active and done items each keep their own
    // sequence; ordering key is (isDone, position), so gaps inside a group are harmless.

    fun observeItems(listId: Long): Flow<List<ItemEntity>> = dao.observeItems(listId)

    suspend fun getItem(id: Long): ItemEntity? = dao.getItem(id)

    suspend fun addItem(listId: Long, text: String, quantity: String?, unit: String?): Long =
        db.withTransaction {
            dao.upsertItem(
                ItemEntity(
                    listId = listId,
                    text = text,
                    quantity = quantity?.trim()?.takeIf { it.isNotEmpty() },
                    unit = unit?.trim()?.takeIf { it.isNotEmpty() },
                    position = dao.nextActivePosition(listId),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

    suspend fun editItem(item: ItemEntity, text: String, quantity: String?, unit: String?) {
        dao.updateItem(
            item.copy(
                text = text,
                quantity = quantity?.trim()?.takeIf { it.isNotEmpty() },
                unit = unit?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    // Check: item becomes the first of the done group. Uncheck: goes to the end of the
    // active group (TZ 3.3 default).
    suspend fun setItemDone(item: ItemEntity, done: Boolean) {
        db.withTransaction {
            if (done) {
                dao.shiftDoneItemsDown(item.listId)
                dao.updateItem(item.copy(isDone = true, position = 0, doneAt = System.currentTimeMillis()))
            } else {
                val end = dao.nextActivePosition(item.listId)
                dao.updateItem(item.copy(isDone = false, position = end, doneAt = null))
            }
        }
    }

    // Manual drag order of the active group (TZ 3.3).
    suspend fun reorderActiveItems(orderedIds: List<Long>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> dao.setItemPosition(id, index) }
        }
    }

    // Move or copy items to another list (TZ 3.3): each lands at the end of its status
    // group in the target list, keeping done state and the current display order.
    // Copy duplicates the items and their photo files; move re-points the originals
    // (photos follow via the itemId FK).
    suspend fun moveItemsToList(itemIds: Collection<Long>, targetListId: Long, copy: Boolean) {
        db.withTransaction {
            var activePos = dao.nextActivePosition(targetListId)
            var donePos = dao.nextDonePosition(targetListId)
            val items = itemIds.mapNotNull { dao.getItem(it) }
                .filter { it.listId != targetListId }
                .sortedWith(compareBy({ it.isDone }, { it.position }))
            for (item in items) {
                val position = if (item.isDone) donePos++ else activePos++
                if (copy) {
                    val newId = dao.upsertItem(
                        item.copy(id = 0, listId = targetListId, position = position, createdAt = System.currentTimeMillis()),
                    )
                    for (photo in dao.getItemPhotos(item.id)) {
                        val copyName = PhotoManager.copyPhoto(appContext, photo.filePath) ?: continue
                        dao.insertItemPhoto(photo.copy(id = 0, itemId = newId, filePath = copyName))
                    }
                } else {
                    dao.updateItem(item.copy(listId = targetListId, position = position))
                }
            }
        }
    }

    suspend fun deleteItem(item: ItemEntity) {
        val photoNames = dao.photoNamesForItem(item.id)
        dao.deleteItem(item)
        PhotoManager.deleteAll(appContext, photoNames)
    }

    suspend fun deleteDoneItems(listId: Long) {
        val photoNames = dao.photoNamesForDoneItems(listId)
        dao.deleteDoneItems(listId)
        PhotoManager.deleteAll(appContext, photoNames)
    }

    suspend fun uncheckAll(listId: Long) {
        db.withTransaction {
            dao.uncheckAllDone(listId, dao.nextActivePosition(listId))
        }
    }

    // Item photos (TZ 3.3, shared photo module TZ 8).

    fun observeItemPhotos(itemId: Long): Flow<List<ItemPhotoEntity>> = dao.observeItemPhotos(itemId)

    fun observeItemPhotoCounts(listId: Long): Flow<List<ItemPhotoCount>> =
        dao.observeItemPhotoCounts(listId)

    suspend fun getItemPhotos(itemId: Long): List<ItemPhotoEntity> = dao.getItemPhotos(itemId)

    suspend fun addItemPhoto(itemId: Long, fileName: String) {
        db.withTransaction {
            dao.insertItemPhoto(
                ItemPhotoEntity(
                    itemId = itemId,
                    filePath = fileName,
                    position = dao.nextPhotoPosition(itemId),
                ),
            )
        }
    }

    suspend fun deleteItemPhoto(photo: ItemPhotoEntity) {
        dao.deleteItemPhoto(photo)
        PhotoManager.delete(appContext, photo.filePath)
    }
}
