package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NaatDao {
    @Query("SELECT * FROM naats ORDER BY createdAt DESC")
    fun getAllNaats(): Flow<List<NaatEntity>>

    @Query("SELECT * FROM naats WHERE id = :id")
    suspend fun getNaatById(id: Int): NaatEntity?

    @Query("SELECT * FROM naats WHERE id = :id")
    fun getNaatByIdFlow(id: Int): Flow<NaatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNaat(naat: NaatEntity): Long

    @Update
    suspend fun updateNaat(naat: NaatEntity)

    @Delete
    suspend fun deleteNaat(naat: NaatEntity)

    @Query("DELETE FROM naats WHERE id = :id")
    suspend fun deleteNaatById(id: Int)
}
