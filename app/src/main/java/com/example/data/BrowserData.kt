package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_items")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey val id: String, // Unique identifier (e.g., URL hash or UUID)
    val url: String,
    val filename: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String, // "DOWNLOADING", "COMPLETED", "FAILED"
    val localFilePath: String? = null,
    val mimeType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAO (Data Access Object) ---

@Dao
interface BrowserDao {
    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isBookmarked(url: String): Boolean

    // History
    @Query("SELECT * FROM history_items ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(historyItem: HistoryItem)

    @Query("DELETE FROM history_items WHERE id = :id")
    suspend fun deleteHistoryItemById(id: Int)

    @Query("DELETE FROM history_items")
    suspend fun clearHistory()

    // Download Tasks
    @Query("SELECT * FROM download_tasks ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadTask)

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadTask?

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteDownloadById(id: String)
}

// --- App Database ---

@Database(entities = [Bookmark::class, HistoryItem::class, DownloadTask::class], version = 2, exportSchema = false)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "browser_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository Pattern ---

class BrowserRepository(private val browserDao: BrowserDao) {
    val bookmarks: Flow<List<Bookmark>> = browserDao.getAllBookmarks()
    val historyItems: Flow<List<HistoryItem>> = browserDao.getRecentHistory()
    val downloads: Flow<List<DownloadTask>> = browserDao.getAllDownloads()

    suspend fun addBookmark(url: String, title: String) {
        browserDao.insertBookmark(Bookmark(url, title))
    }

    suspend fun removeBookmark(url: String) {
        browserDao.deleteBookmark(Bookmark(url, ""))
    }

    suspend fun isBookmarked(url: String): Boolean {
        return browserDao.isBookmarked(url)
    }

    suspend fun addToHistory(url: String, title: String) {
        browserDao.insertHistoryItem(HistoryItem(url = url, title = title))
    }

    suspend fun deleteHistoryItem(id: Int) {
        browserDao.deleteHistoryItemById(id)
    }

    suspend fun clearHistory() {
        browserDao.clearHistory()
    }

    // Download management
    suspend fun saveDownload(download: DownloadTask) {
        browserDao.insertDownload(download)
    }

    suspend fun getDownloadById(id: String): DownloadTask? {
        return browserDao.getDownloadById(id)
    }

    suspend fun deleteDownload(id: String) {
        browserDao.deleteDownloadById(id)
    }
}
