package com.mosu.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "beatmapUid"],
    indices = [Index(value = ["playlistId"]), Index(value = ["beatmapUid"])]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val beatmapUid: Long,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistTrackCount(
    val playlistId: Long,
    val count: Int
)

