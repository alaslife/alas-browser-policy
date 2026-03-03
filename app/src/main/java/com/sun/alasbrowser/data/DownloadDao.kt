package com.sun.alasbrowser.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = :status")
    suspend fun getDownloadsByStatus(status: Int): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)
    
    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    fun updateStatusSync(id: Long, status: Int)

    @Query("UPDATE downloads SET downloadedSize = :downloadedSize WHERE id = :id")
    suspend fun updateProgress(id: Long, downloadedSize: Long)
    
    @Query("UPDATE downloads SET downloadedSize = :downloadedSize WHERE id = :id")
    fun updateProgressSync(id: Long, downloadedSize: Long)

    @Query("SELECT * FROM downloads WHERE filePath = :path LIMIT 1")
    suspend fun getDownloadByPath(path: String): DownloadEntity?

    @Query("UPDATE downloads SET totalSize = :totalSize WHERE id = :id")
    suspend fun updateTotalSize(id: Long, totalSize: Long)
    
    @Query("UPDATE downloads SET totalSize = :totalSize WHERE id = :id")
    fun updateTotalSizeSync(id: Long, totalSize: Long)

    @Query("UPDATE downloads SET filePath = :path WHERE id = :id")
    suspend fun updateFilePath(id: Long, path: String)

    @Query("UPDATE downloads SET filePath = :path WHERE id = :id")
    fun updateFilePathSync(id: Long, path: String)

    @Query("UPDATE downloads SET fileName = :name, title = :name WHERE id = :id")
    fun updateFileNameSync(id: Long, name: String)

    @Query("UPDATE downloads SET fileName = :name, title = :name WHERE id = :id")
    suspend fun updateFileName(id: Long, name: String)

    // Methods for stuck download detection
    @Query("UPDATE downloads SET lastProgressUpdate = :timestamp WHERE id = :id")
    suspend fun updateLastProgressTime(id: Long, timestamp: Long)

    @Query("UPDATE downloads SET lastProgressUpdate = :timestamp WHERE id = :id")
    fun updateLastProgressTimeSync(id: Long, timestamp: Long)

    @Query("SELECT * FROM downloads WHERE status = :status AND lastProgressUpdate < :threshold")
    suspend fun getStaleDownloads(status: Int, threshold: Long): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = 1 AND lastProgressUpdate > 0 AND lastProgressUpdate < :threshold")
    suspend fun getStuckRunningDownloads(threshold: Long): List<DownloadEntity>
}
