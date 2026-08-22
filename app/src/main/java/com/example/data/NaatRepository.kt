package com.example.data

import kotlinx.coroutines.flow.Flow

class NaatRepository(private val naatDao: NaatDao) {
    val allNaats: Flow<List<NaatEntity>> = naatDao.getAllNaats()

    fun getNaatByIdFlow(id: Int): Flow<NaatEntity?> = naatDao.getNaatByIdFlow(id)

    suspend fun getNaatById(id: Int): NaatEntity? = naatDao.getNaatById(id)

    suspend fun insert(naat: NaatEntity): Long = naatDao.insertNaat(naat)

    suspend fun update(naat: NaatEntity) = naatDao.updateNaat(naat)

    suspend fun delete(naat: NaatEntity) = naatDao.deleteNaat(naat)

    suspend fun deleteById(id: Int) = naatDao.deleteNaatById(id)
}
