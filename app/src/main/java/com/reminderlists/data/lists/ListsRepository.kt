package com.reminderlists.data.lists

import androidx.room.withTransaction
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.entity.FolderEntity
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
}
