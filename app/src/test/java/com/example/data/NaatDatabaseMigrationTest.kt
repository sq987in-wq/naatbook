package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NaatDatabaseMigrationTest {

    private lateinit var context: Context
    private var database: NaatDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun `migration 1 to 2 preserves rows and normalizes categories`() {
        createVersionOneDatabase()

        database = Room.databaseBuilder(context, NaatDatabase::class.java, TEST_DATABASE)
            .addMigrations(NaatDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val migrated = linkedMapOf<String, String>()
        database!!.openHelper.writableDatabase
            .query("SELECT title, category FROM naats ORDER BY id")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    migrated[cursor.getString(0)] = cursor.getString(1)
                }
            }

        assertEquals(
            linkedMapOf(
                "Legacy mixed" to NaatCategories.NAAT,
                "Legacy salam" to NaatCategories.SALAM,
                "Own poetry" to NaatCategories.MY_KALAM,
                "Audio entry" to NaatCategories.MY_KALAM,
                "Already current" to NaatCategories.HAMD,
                "Unknown" to NaatCategories.OTHERS
            ),
            migrated
        )
    }

    private fun createVersionOneDatabase() {
        val file = context.getDatabasePath(TEST_DATABASE)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS naats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    poet TEXT,
                    category TEXT NOT NULL,
                    lyrics TEXT,
                    audioType TEXT NOT NULL,
                    audioPath TEXT,
                    isFavorite INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            listOf(
                "Legacy mixed" to "Hamd-o-Naat",
                "Legacy salam" to "Salam & Qasida",
                "Own poetry" to "My Own Poetry",
                "Audio entry" to "Audio Only",
                "Already current" to "Hamd",
                "Unknown" to "Old custom folder"
            ).forEachIndexed { index, (title, category) ->
                db.execSQL(
                    "INSERT INTO naats " +
                        "(title, poet, category, lyrics, audioType, audioPath, isFavorite, createdAt) " +
                        "VALUES (?, NULL, ?, NULL, 'none', NULL, 0, ?)",
                    arrayOf(title, category, index.toLong())
                )
            }
            db.version = 1
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-1-2-test.db"
    }
}
