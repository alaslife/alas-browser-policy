package com.sun.alasbrowser.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickLinkDao {
    @Query("SELECT * FROM quick_links ORDER BY id ASC")
    fun getAllQuickLinks(): Flow<List<QuickLinkEntity>>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(quickLink: QuickLinkEntity)
    
    @Delete
    suspend fun delete(quickLink: QuickLinkEntity)
    
    @Query("DELETE FROM quick_links WHERE id = :id")
    suspend fun deleteById(id: Int)
    
    @Query("SELECT * FROM quick_links WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): QuickLinkEntity?
    
    @Query("SELECT COUNT(*) FROM quick_links")
    suspend fun getCount(): Int
    
    @Query("DELETE FROM quick_links")
    suspend fun deleteAll()
    
    @Query("""
        DELETE FROM quick_links 
        WHERE id NOT IN (
            SELECT MIN(id) 
            FROM quick_links 
            GROUP BY url
        )
    """)
    suspend fun removeDuplicates()
}
