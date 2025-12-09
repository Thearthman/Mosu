package com.mosu.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.player.MusicController

@Composable
fun TrackItem(map: BeatmapEntity, musicController: MusicController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { musicController.playSong(map) }
            .padding(8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play")
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = map.difficultyName, style = MaterialTheme.typography.bodyMedium)
            Text(text = map.artist, style = MaterialTheme.typography.bodySmall)
        }
    }
}
