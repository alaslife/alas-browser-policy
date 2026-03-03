package com.sun.alasbrowser.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitTime DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<History>>

    @Query("""
        SELECT h1.* FROM history h1
        INNER JOIN (
            SELECT url, MAX(visitTime) as maxTime 
            FROM history 
            GROUP BY url
        ) h2 ON h1.url = h2.url AND h1.visitTime = h2.maxTime
        ORDER BY h1.visitTime DESC
    """)
    fun getAllHistory(): Flow<List<History>>
    
    @Query("""
        SELECT h1.* FROM history h1
        INNER JOIN (
            SELECT url, MAX(visitTime) as maxTime 
            FROM history 
            GROUP BY url
        ) h2 ON h1.url = h2.url AND h1.visitTime = h2.maxTime
        ORDER BY h1.visitTime DESC
    """)
    suspend fun getAllHistorySync(): List<History>
    
    @Query("""
        SELECT * FROM history 
        GROUP BY url 
        ORDER BY visitCount DESC, visitTime DESC 
        LIMIT :limit
    """)
    fun getTopVisited(limit: Int): Flow<List<History>>
    
    @Query("SELECT * FROM history WHERE url LIKE :domain ORDER BY visitTime DESC")
    suspend fun getHistoryByDomain(domain: String): List<History>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: History)
    
    @Query("""
        UPDATE history 
        SET visitCount = visitCount + 1, visitTime = :visitTime 
        WHERE url = :url
    """)
    suspend fun updateVisitCount(url: String, visitTime: Long)
    
    @Query("SELECT EXISTS(SELECT 1 FROM history WHERE url = :url LIMIT 1)")
    suspend fun urlExists(url: String): Boolean

    @Query("DELETE FROM history")
    suspend fun clearHistory()
    
    @Query("DELETE FROM history")
    suspend fun deleteAllHistory()

    @Query("DELETE FROM history WHERE id = :historyId")
    suspend fun deleteHistoryById(historyId: Long)

    @Delete
    suspend fun deleteHistory(history: History)
}
