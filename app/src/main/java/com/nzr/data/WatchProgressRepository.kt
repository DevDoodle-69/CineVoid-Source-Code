package com.nzr.data

import kotlinx.coroutines.flow.Flow

class WatchProgressRepository(private val dao: WatchProgressDao) {
    val allProgress: Flow<List<WatchProgress>> = dao.getAllProgress()

    suspend fun getProgress(id: String, season: Int, episode: Int): WatchProgress? {
        return dao.getProgress(id, season, episode)
    }

    suspend fun insert(progress: WatchProgress) {
        dao.insertProgress(progress)
    }

    suspend fun deleteById(id: String) {
        dao.deleteProgress(id)
    }

    suspend fun clearOldProgress(time: Long) {
        dao.clearOldProgress(time)
    }
}
