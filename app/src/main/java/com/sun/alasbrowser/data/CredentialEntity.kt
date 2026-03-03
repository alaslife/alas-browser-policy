package com.sun.alasbrowser.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "credentials",
    indices = [Index(value = ["url"], unique = false)]
)
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String, // Domain or full URL
    val username: String,
    val passwordEncrypted: String,
    val timestamp: Long = System.currentTimeMillis()
)
