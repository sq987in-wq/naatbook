package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NaatDaoCorrectnessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, NaatDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After fun close() = database.close()

    @Test
    fun `library search executes in Room and returns lightweight summaries`() = runBlocking {
        val dao = database.naatDao()
        dao.insertNaat(
            NaatEntity(
                title = "Searchable title",
                poet = "Poet",
                category = NaatCategories.NAAT,
                lyrics = "unique lyrics token " + "x".repeat(10_000),
                audioType = "none",
                audioPath = null,
                isFavorite = false
            )
        )

        val results = dao.getFilteredSummaries("unique lyrics token", null, false).first()

        assertTrue(results.single().title == "Searchable title")
    }

    @Test
    fun `favorite toggle uses current database value on every rapid call`() = runBlocking {
        val dao = database.naatDao()
        val id = dao.insertNaat(
            NaatEntity(
                title = "Atomic favorite",
                poet = null,
                category = NaatCategories.NAAT,
                lyrics = null,
                audioType = "none",
                audioPath = null,
                isFavorite = false
            )
        ).toInt()

        dao.toggleFavorite(id)
        assertTrue(dao.getNaatById(id)!!.isFavorite)
        dao.toggleFavorite(id)
        assertFalse(dao.getNaatById(id)!!.isFavorite)
    }
}
