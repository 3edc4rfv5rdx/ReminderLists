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

    // Autocomplete suggestions by prefix/substring (TZ 3.4).
    @Query("SELECT * FROM dictionary WHERE text LIKE :query || '%' ORDER BY text COLLATE NOCASE LIMIT 10")
    suspend fun suggest(query: String): List<DictionaryEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM dictionary WHERE text = :text)")
    suspend fun exists(text: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: DictionaryEntity): Long

    @Update
    suspend fun update(entry: DictionaryEntity)

    @Delete
    suspend fun delete(entry: DictionaryEntity)
}
