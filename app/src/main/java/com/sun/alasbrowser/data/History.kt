package com.sun.alasbrowser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class History(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val visitTime: Long = System.currentTimeMillis(),
    val favicon: String? = null,
    val visitCount: Int = 1
)
