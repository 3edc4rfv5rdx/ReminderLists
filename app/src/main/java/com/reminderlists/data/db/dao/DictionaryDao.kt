package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.reminderlists.data.db.entity.DictionaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {

    @Query("SELECT * FROM dictionary ORDER BY text COLLATE NOCASE")
    fun observeAll(): Flow<List<DictionaryEntity>>

    // All texts for in-memory suggestion filtering — SQLite LIKE/lower() are ASCII-only,
    // Cyrillic case-insensitive matching is done in Kotlin (TZ 3.4).
    @Query("SELECT text FROM dictionary ORDER BY text COLLATE NOCASE")
    suspend fun getAllTexts(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM dictionary WHERE text = :text)")
    suspend fun exists(text: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: DictionaryEntity): Long

    @Update
    suspend fun update(entry: DictionaryEntity)

    @Delete
    suspend fun delete(entry: DictionaryEntity)
}
