package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey
    val queryKey: String, // e.g., "played_genre_10"
    val resultsJson: String, // Serialized list of BeatmapsetCompact
    val cachedAt: Long = System.currentTimeMillis()
)

