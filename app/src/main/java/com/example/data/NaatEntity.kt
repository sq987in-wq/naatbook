package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "naats", indices = [Index(value = ["updatedAt"])])
data class NaatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val poet: String?,
    val category: String,
    val lyrics: String?,
    val audioType: String, // "recorded", "local_file", or "none"
    val audioPath: String?,
    val isFavorite: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    /** Changes on every persisted edit; powers the Room-backed Recent Notebooks list. */
    val updatedAt: Long = System.currentTimeMillis(),
    val secondaryAudioType: String = "none",
    val secondaryAudioPath: String? = null
)
