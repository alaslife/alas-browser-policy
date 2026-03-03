package com.sun.alasbrowser.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials WHERE url LIKE '%' || :domain || '%' ORDER BY timestamp DESC")
    suspend fun getCredentialsForDomain(domain: String): List<CredentialEntity>

    @Query("SELECT * FROM credentials ORDER BY timestamp DESC")
    fun getAllCredentials(): Flow<List<CredentialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credential: CredentialEntity)

    @Delete
    suspend fun delete(credential: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("SELECT * FROM credentials WHERE username = :username AND url LIKE '%' || :domain || '%' LIMIT 1")
    suspend fun getCredential(domain: String, username: String): CredentialEntity?
}
