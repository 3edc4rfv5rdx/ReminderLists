package com.reminderlists.data.lists

import androidx.room.withTransaction
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.dao.FolderListCount
import com.reminderlists.data.db.dao.ListItemCounts
import com.reminderlists.data.db.entity.FolderEntity
import com.reminderlists.data.db.entity.ItemEntity
import com.reminderlists.data.db.entity.ListEntity
import kotlinx.coroutines.flow.Flow

// Lists module data operations (TZ 3.1 / 3.2). Item-level operations arrive with TZ 3.3.
class ListsRepository(private val db: AppDatabase) {

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
        db.withTransaction {
            if (deleteLists) dao.deleteListsInFolder(folder.id)
            dao.deleteFolder(folder)
        }
    }

    // Lists (TZ 3.2)

    fun observeLists(folderId: Long?): Flow<List<ListEntity>> = dao.observeLists(folderId)

    fun observeList(id: Long): Flow<ListEntity?> = dao.observeList(id)

    fun observeListItemCounts(): Flow<List<ListItemCounts>> = dao.observeListItemCounts()

    fun observeFolderListCounts(): Flow<List<FolderListCount>> = dao.observeFolderListCounts()

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

    suspend fun deleteList(list: ListEntity) = dao.deleteList(list)

    // Items (TZ 3.3). Positions are group-local: active and done items each keep their own
    // sequence; ordering key is (isDone, position), so gaps inside a group are harmless.

    fun observeItems(listId: Long): Flow<List<ItemEntity>> = dao.observeItems(listId)

    suspend fun getItem(id: Long): ItemEntity? = dao.getItem(id)

    suspend fun addItem(listId: Long, text: String, quantity: String?, unit: String?) {
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

    suspend fun deleteItem(item: ItemEntity) = dao.deleteItem(item)

    suspend fun deleteDoneItems(listId: Long) = dao.deleteDoneItems(listId)

    suspend fun uncheckAll(listId: Long) {
        db.withTransaction {
            dao.uncheckAllDone(listId, dao.nextActivePosition(listId))
        }
    }
}
