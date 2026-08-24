package com.example.data

/** Lightweight Library projection; large lyrics stay in Room until Reader/Editor opens. */
data class NaatSummary(
    val id: Int,
    val title: String,
    val poet: String?,
    val category: String,
    val audioType: String,
    val audioPath: String?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val secondaryAudioType: String,
    val secondaryAudioPath: String?
)

data class CategoryCount(
    val category: String,
    val count: Int
)
