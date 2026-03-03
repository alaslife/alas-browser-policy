package com.sun.alasbrowser.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY lastActive DESC")
    suspend fun getAll(): List<TabEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<TabEntity>)
    
    @Query("DELETE FROM tabs")
    suspend fun deleteAll()
    
    @Transaction
    suspend fun replaceAll(tabs: List<TabEntity>) {
        deleteAll()
        insertAll(tabs)
    }
}
