package com.mosu.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mosu.app.TrackItem
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.player.MusicController

@Composable
fun LibraryScreen(
    db: AppDatabase,
    musicController: MusicController
) {
    // DB Observer
    val downloadedMaps by db.beatmapDao().getAllBeatmaps().collectAsState(initial = emptyList())
    
    // Group maps by Set ID
    val groupedMaps = downloadedMaps.groupBy { it.beatmapSetId }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(16.dp)
        )
        
        LazyColumn {
            items(groupedMaps.keys.toList()) { setId ->
                val tracks = groupedMaps[setId] ?: emptyList()
                if (tracks.isEmpty()) return@items

                if (tracks.size > 1) {
                    // Album/Folder View
                    var expanded by remember { mutableStateOf(false) }
                    
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                                contentDescription = "Expand"
                            )
                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                Text(text = tracks[0].title, style = MaterialTheme.typography.titleMedium)
                                Text(text = "${tracks[0].artist} • ${tracks.size} tracks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        if (expanded) {
                            tracks.forEach { map ->
                                TrackItem(map = map, musicController = musicController)
                            }
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                    }
                } else {
                    // Single Track View - Show slightly differently to look like a song in a list
                    TrackItem(map = tracks[0], musicController = musicController)
                    Divider(modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

