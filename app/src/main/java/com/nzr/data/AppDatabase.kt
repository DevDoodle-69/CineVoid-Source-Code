package com.nzr.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "watch_progress",
    primaryKeys = ["subject_id", "season", "episode"]
)
data class WatchProgress(
    val subject_id: String,
    val title: String,
    val cover: String?,
    val timestamp: Long,
    val season: Int,
    val episode: Int,
    val progressMs: Long,
    val durationMs: Long
)

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress t1 WHERE timestamp = (SELECT MAX(timestamp) FROM watch_progress t2 WHERE t1.subject_id = t2.subject_id) ORDER BY timestamp DESC LIMIT 20")
    fun getAllProgress(): Flow<List<WatchProgress>>

    @Query("SELECT * FROM watch_progress WHERE subject_id = :id AND season = :season AND episode = :episode LIMIT 1")
    suspend fun getProgress(id: String, season: Int, episode: Int): WatchProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: WatchProgress)

    @Query("DELETE FROM watch_progress WHERE subject_id = :id")
    suspend fun deleteProgress(id: String)
    
    @Query("DELETE FROM watch_progress WHERE timestamp < :time")
    suspend fun clearOldProgress(time: Long)
}

@Database(entities = [WatchProgress::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchProgressDao(): WatchProgressDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cinevoid_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
