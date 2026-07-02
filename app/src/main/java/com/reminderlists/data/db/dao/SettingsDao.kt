package com.reminderlists.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reminderlists.data.db.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE key = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}
