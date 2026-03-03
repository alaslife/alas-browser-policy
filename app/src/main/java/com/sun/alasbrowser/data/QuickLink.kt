package com.sun.alasbrowser.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quick_links",
    indices = [Index(value = ["url"], unique = true)]
)
data class QuickLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val url: String,
    val iconUrl: String
)
