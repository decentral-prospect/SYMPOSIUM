package com.decentralprospect.symposium

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

private const val LOCALIZATION_DATABASE_NAME = "localization.db"
private const val LOCALIZATION_DATABASE_VERSION = 17
private const val LOCALIZATION_CATALOG_ASSET = "localization/catalog.tsv"

internal data class LocalizationCatalog(
    val translations: Map<String, Map<TextId, String>>,
    val aliases: Map<String, TextId>
)

internal class LocalizationDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    LOCALIZATION_DATABASE_NAME,
    null,
    LOCALIZATION_DATABASE_VERSION
) {
    private val appContext = context.applicationContext

    override fun onCreate(database: SQLiteDatabase) {
        createSchema(database)
        seedCatalog(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        database.execSQL("DROP TABLE IF EXISTS localized_text")
        database.execSQL("DROP TABLE IF EXISTS localized_text_alias")
        createSchema(database)
        seedCatalog(database)
    }

    fun loadCatalog(): LocalizationCatalog {
        val database = readableDatabase
        val translations = mutableMapOf<String, MutableMap<TextId, String>>()
        database.query(
            "localized_text",
            arrayOf("text_id", "language_code", "text_value"),
            null,
            null,
            null,
            null,
            "text_id, language_code"
        ).use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow("text_id")
            val languageColumn = cursor.getColumnIndexOrThrow("language_code")
            val valueColumn = cursor.getColumnIndexOrThrow("text_value")
            while (cursor.moveToNext()) {
                val id = TextId.fromValue(cursor.getInt(idColumn))
                translations.getOrPut(cursor.getString(languageColumn)) { mutableMapOf() }[id] =
                    cursor.getString(valueColumn)
            }
        }

        val aliases = mutableMapOf<String, TextId>()
        database.query(
            "localized_text_alias",
            arrayOf("text_alias", "text_id"),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val aliasColumn = cursor.getColumnIndexOrThrow("text_alias")
            val idColumn = cursor.getColumnIndexOrThrow("text_id")
            while (cursor.moveToNext()) {
                aliases[cursor.getString(aliasColumn)] = TextId.fromValue(cursor.getInt(idColumn))
            }
        }

        return LocalizationCatalog(
            translations = translations.mapValues { it.value.toMap() },
            aliases = aliases.toMap()
        )
    }

    private fun createSchema(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE localized_text (
                text_id INTEGER NOT NULL,
                language_code TEXT NOT NULL,
                text_value TEXT NOT NULL,
                PRIMARY KEY (text_id, language_code)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE localized_text_alias (
                text_alias TEXT NOT NULL PRIMARY KEY,
                text_id INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX localized_text_language_index ON localized_text(language_code)"
        )
    }

    private fun seedCatalog(database: SQLiteDatabase) {
        val knownIds = TextId.entries.toSet()
        val seen = mutableMapOf<TextId, MutableSet<String>>()
        database.beginTransaction()
        try {
            appContext.assets.open(LOCALIZATION_CATALOG_ASSET).bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, rawLine ->
                    if (rawLine.isBlank() || rawLine.startsWith("#")) return@forEachIndexed
                    val columns = rawLine.split('\t', limit = 3)
                    require(columns.size == 3) {
                        "Invalid localization catalog row ${index + 1}"
                    }
                    val numericId = columns[0].toIntOrNull()
                        ?: error("Invalid numeric text id '${columns[0]}' at catalog row ${index + 1}")
                    val id = runCatching { TextId.fromValue(numericId) }.getOrElse {
                        error("Unknown text id '$numericId' at catalog row ${index + 1}")
                    }
                    require(id in knownIds)
                    val languageCode = columns[1].lowercase(Locale.ROOT)
                    require(languageCode == "en" || languageCode == "ru") {
                        "Unsupported language '$languageCode' at catalog row ${index + 1}"
                    }
                    val value = unescapeCatalogValue(columns[2])
                    require(value.isNotBlank()) { "Blank text for $id/$languageCode" }
                    require(seen.getOrPut(id) { mutableSetOf() }.add(languageCode)) {
                        "Duplicate text for $id/$languageCode"
                    }

                    database.insertOrThrow(
                        "localized_text",
                        null,
                        ContentValues().apply {
                            put("text_id", id.value)
                            put("language_code", languageCode)
                            put("text_value", value)
                        }
                    )
                    database.insertWithOnConflict(
                        "localized_text_alias",
                        null,
                        ContentValues().apply {
                            put("text_alias", value)
                            put("text_id", id.value)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }

            val incomplete = knownIds.filter { seen[it] != setOf("en", "ru") }
            require(incomplete.isEmpty()) {
                "Missing translations for: ${incomplete.joinToString { it.name }}"
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}

private fun unescapeCatalogValue(encoded: String): String {
    val result = StringBuilder(encoded.length)
    var index = 0
    while (index < encoded.length) {
        val char = encoded[index]
        if (char != '\\' || index == encoded.lastIndex) {
            result.append(char)
            index += 1
            continue
        }
        when (val escaped = encoded[index + 1]) {
            'n' -> result.append('\n')
            't' -> result.append('\t')
            '\\' -> result.append('\\')
            else -> {
                result.append('\\')
                result.append(escaped)
            }
        }
        index += 2
    }
    return result.toString()
}
