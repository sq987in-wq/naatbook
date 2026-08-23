package com.example.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaatRepository @Inject constructor(private val naatDao: NaatDao) {
    val allNaats: Flow<List<NaatEntity>> = naatDao.getAllNaats()

    fun getNaatByIdFlow(id: Int): Flow<NaatEntity?> = naatDao.getNaatByIdFlow(id)

    suspend fun getNaatById(id: Int): NaatEntity? = naatDao.getNaatById(id)

    suspend fun insert(naat: NaatEntity): Long = naatDao.insertNaat(naat)

    suspend fun insertAll(naats: List<NaatEntity>): List<Long> = naatDao.insertNaats(naats)

    suspend fun update(naat: NaatEntity) = naatDao.updateNaat(naat)

    suspend fun toggleFavorite(id: Int): NaatEntity? {
        naatDao.toggleFavorite(id)
        return naatDao.getNaatById(id)
    }

    suspend fun delete(naat: NaatEntity) = naatDao.deleteNaat(naat)

    suspend fun deleteById(id: Int) = naatDao.deleteNaatById(id)

    suspend fun countByAudioPath(audioPath: String): Int = naatDao.countByAudioPath(audioPath)
}
