package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NaatDao {
    @Query("SELECT * FROM naats ORDER BY createdAt DESC")
    fun getAllNaats(): Flow<List<NaatEntity>>

    @Query("""
        SELECT id, title, poet, category, audioType, audioPath, isFavorite, createdAt,
               secondaryAudioType, secondaryAudioPath
        FROM naats ORDER BY createdAt DESC
    """)
    fun getAllSummaries(): Flow<List<NaatSummary>>

    /** Strict bounded recent feed; edits rise to the top without loading all summaries. */
    @Query("""
        SELECT id, title, poet, category, audioType, audioPath, isFavorite, createdAt,
               secondaryAudioType, secondaryAudioPath
        FROM naats
        ORDER BY updatedAt DESC
        LIMIT 10
    """)
    fun getRecentSummaries(): Flow<List<NaatSummary>>

    @Query("SELECT category, COUNT(*) AS count FROM naats GROUP BY category")
    fun getCategoryCounts(): Flow<List<CategoryCount>>

    @Query("""
        SELECT id, title, poet, category, audioType, audioPath, isFavorite, createdAt,
               secondaryAudioType, secondaryAudioPath
        FROM naats
        WHERE (:folder IS NULL OR category = :folder COLLATE NOCASE)
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:query = '' OR title LIKE '%' || :query || '%' COLLATE NOCASE
               OR poet LIKE '%' || :query || '%' COLLATE NOCASE
               OR lyrics LIKE '%' || :query || '%' COLLATE NOCASE)
        ORDER BY createdAt DESC
    """)
    fun getFilteredSummaries(
        query: String,
        folder: String?,
        favoritesOnly: Boolean
    ): Flow<List<NaatSummary>>

    @Query("SELECT * FROM naats WHERE id = :id")
    suspend fun getNaatById(id: Int): NaatEntity?

    @Query("SELECT * FROM naats WHERE id = :id")
    fun getNaatByIdFlow(id: Int): Flow<NaatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNaat(naat: NaatEntity): Long

    /** One generated Room transaction: either the complete restore is inserted or none is. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNaats(naats: List<NaatEntity>): List<Long>

    @Update
    suspend fun updateNaat(naat: NaatEntity)

    /** Toggles from the value currently stored in Room, never from a stale UI snapshot. */
    @Query("UPDATE naats SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Int): Int

    @Delete
    suspend fun deleteNaat(naat: NaatEntity)

    @Query("DELETE FROM naats WHERE id = :id")
    suspend fun deleteNaatById(id: Int)

    @Query("SELECT COUNT(*) FROM naats WHERE audioPath = :audioPath OR secondaryAudioPath = :audioPath")
    suspend fun countByAudioPath(audioPath: String): Int
}
