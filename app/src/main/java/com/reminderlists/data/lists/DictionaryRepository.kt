package com.reminderlists.data.lists

import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.entity.DictionaryEntity
import com.reminderlists.util.TextFormat
import kotlinx.coroutines.flow.Flow

// App-wide autocomplete dictionary (TZ 3.4): "Xxxxxxxxx" formatting, no duplicates.
class DictionaryRepository(db: AppDatabase) {

    private val dao = db.dictionaryDao()

    fun observeAll(): Flow<List<DictionaryEntity>> = dao.observeAll()

    // Prefix suggestions from 2 typed characters. The query is normalized to the dictionary
    // form first — SQLite LIKE is not case-insensitive for Cyrillic, entries are stored formatted.
    suspend fun suggest(input: String): List<String> {
        val query = TextFormat.toDictionaryForm(input)
        if (query.length < 2) return emptyList()
        return dao.suggest(query).map { it.text }
    }

    // Add with formatting; duplicate forms are silently ignored (unique index, TZ 3.4).
    suspend fun add(raw: String) {
        val text = TextFormat.toDictionaryForm(raw)
        if (text.isEmpty()) return
        dao.insert(DictionaryEntity(text = text, createdAt = System.currentTimeMillis()))
    }

    // Rename keeps the dictionary duplicate-free: no-op if the new form already exists.
    suspend fun rename(entry: DictionaryEntity, raw: String) {
        val text = TextFormat.toDictionaryForm(raw)
        if (text.isEmpty() || text == entry.text || dao.exists(text)) return
        dao.update(entry.copy(text = text))
    }

    suspend fun delete(entry: DictionaryEntity) = dao.delete(entry)
}
