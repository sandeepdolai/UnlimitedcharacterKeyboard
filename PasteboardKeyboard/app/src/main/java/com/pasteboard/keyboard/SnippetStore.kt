package com.pasteboard.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local-only snippet storage. No app-level character cap is imposed. */
data class SavedSnippet(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val isFavorite: Boolean
)

class SnippetStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_SNIPPETS (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_TEXT TEXT NOT NULL, " +
                "$COLUMN_CREATED_AT INTEGER NOT NULL, " +
                "$COLUMN_FAVORITE INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_snippets_created ON $TABLE_SNIPPETS($COLUMN_CREATED_AT DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_SNIPPETS ADD COLUMN $COLUMN_FAVORITE INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun save(text: String): Long {
        require(text.isNotBlank()) { "Text cannot be empty." }
        val values = ContentValues().apply {
            put(COLUMN_TEXT, text)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
            put(COLUMN_FAVORITE, 0)
        }
        return writableDatabase.insertOrThrow(TABLE_SNIPPETS, null, values)
    }

    fun getAll(search: String = ""): List<SavedSnippet> {
        val selection = if (search.isBlank()) null else "instr(lower($COLUMN_TEXT), lower(?)) > 0"
        val args = if (search.isBlank()) null else arrayOf(search)
        val results = mutableListOf<SavedSnippet>()
        readableDatabase.query(
            TABLE_SNIPPETS,
            arrayOf(COLUMN_ID, COLUMN_TEXT, COLUMN_CREATED_AT, COLUMN_FAVORITE),
            selection,
            args,
            null,
            null,
            "$COLUMN_FAVORITE DESC, $COLUMN_CREATED_AT DESC"
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val textIndex = cursor.getColumnIndexOrThrow(COLUMN_TEXT)
            val createdIndex = cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)
            val favoriteIndex = cursor.getColumnIndexOrThrow(COLUMN_FAVORITE)
            while (cursor.moveToNext()) {
                results += SavedSnippet(
                    id = cursor.getLong(idIndex),
                    text = cursor.getString(textIndex),
                    createdAt = cursor.getLong(createdIndex),
                    isFavorite = cursor.getInt(favoriteIndex) == 1
                )
            }
        }
        return results
    }

    fun setFavorite(id: Long, favorite: Boolean): Int {
        val values = ContentValues().apply { put(COLUMN_FAVORITE, if (favorite) 1 else 0) }
        return writableDatabase.update(TABLE_SNIPPETS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long): Int = writableDatabase.delete(
        TABLE_SNIPPETS,
        "$COLUMN_ID = ?",
        arrayOf(id.toString())
    )

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_SNIPPETS", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    companion object {
        private const val DATABASE_NAME = "pasteboard-library.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_SNIPPETS = "snippets"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TEXT = "text"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_FAVORITE = "is_favorite"
    }
}
