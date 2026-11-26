package com.example.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/** Copy across independent connections so ATTACH cannot replace Room's live connection. */
internal object OfflineRestoreRowCopy {
    fun replace(
        read: (String) -> Cursor,
        target: SupportSQLiteDatabase,
        tables: List<String>,
        clearOnly: Set<String> = emptySet(),
        allSequences: Boolean = true,
        beforeCommit: () -> Unit = {}
    ) {
        target.beginTransaction()
        try {
            target.execSQL("PRAGMA defer_foreign_keys=ON")
            for (table in tables + clearOnly) target.execSQL("DELETE FROM `$table`")
            for (table in tables) copy(read, target, table, "")
            // Keep the sequence scope used by the archive format. Table names come from the compiled inventory.
            val filter = if (allSequences) "" else tables.joinToString(",", " WHERE name IN (", ")") { "'$it'" }
            target.execSQL("DELETE FROM sqlite_sequence$filter")
            copy(read, target, "sqlite_sequence", filter)
            for (table in tables) verify(read, target, table, "")
            verify(read, target, "sqlite_sequence", filter)
            require(!target.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
            beforeCommit()
            target.setTransactionSuccessful()
        } finally {
            target.endTransaction()
        }
    }

    private fun copy(read: (String) -> Cursor, target: SupportSQLiteDatabase, table: String, filter: String) {
        read("SELECT * FROM `$table`$filter ORDER BY rowid").use { rows ->
            val names = rows.columnNames.joinToString(",") { "`$it`" }
            val parameters = rows.columnNames.joinToString(",") { "?" }
            target.compileStatement("INSERT INTO `$table` ($names) VALUES ($parameters)").use { insert ->
                while (rows.moveToNext()) {
                    for (column in 0 until rows.columnCount) {
                        val index = column + 1
                        when (rows.getType(column)) {
                            Cursor.FIELD_TYPE_NULL -> insert.bindNull(index)
                            Cursor.FIELD_TYPE_INTEGER -> insert.bindLong(index, rows.getLong(column))
                            Cursor.FIELD_TYPE_FLOAT -> insert.bindDouble(index, rows.getDouble(column))
                            Cursor.FIELD_TYPE_STRING -> insert.bindString(index, rows.getString(column))
                            Cursor.FIELD_TYPE_BLOB -> insert.bindBlob(index, rows.getBlob(column))
                            else -> error("Unbekannter SQLite-Datentyp")
                        }
                    }
                    insert.executeInsert()
                }
            }
        }
    }

    private fun verify(read: (String) -> Cursor, target: SupportSQLiteDatabase, table: String, filter: String) {
        val columns = target.query("PRAGMA table_info(`$table`)").use { info ->
            buildList<Pair<Int, String>> {
                while (info.moveToNext()) {
                    val primary = info.getInt(info.getColumnIndexOrThrow("pk"))
                    if (primary > 0) add(primary to info.getString(info.getColumnIndexOrThrow("name")))
                }
            }
        }
        val order = if (table == "sqlite_sequence") "name, seq" else {
            require(columns.isNotEmpty())
            columns.sortedBy { it.first }.joinToString(",") { "`${it.second}`" }
        }
        val sql = "SELECT * FROM `$table`$filter ORDER BY $order"
        read(sql).use { expected ->
            target.query(sql).use { actual ->
                require(expected.columnNames.contentEquals(actual.columnNames))
                while (expected.moveToNext()) {
                    require(actual.moveToNext())
                    for (i in 0 until expected.columnCount) {
                        val type = expected.getType(i)
                        require(type == actual.getType(i))
                        require(when (type) {
                            Cursor.FIELD_TYPE_NULL -> true
                            Cursor.FIELD_TYPE_INTEGER -> expected.getLong(i) == actual.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> expected.getDouble(i) == actual.getDouble(i)
                            Cursor.FIELD_TYPE_STRING -> expected.getString(i) == actual.getString(i)
                            Cursor.FIELD_TYPE_BLOB -> expected.getBlob(i).contentEquals(actual.getBlob(i))
                            else -> false
                        })
                    }
                }
                require(!actual.moveToNext())
            }
        }
    }
}
