package com.mosu.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.download.BeatmapDownloader
import com.mosu.app.domain.download.DownloadState
import com.mosu.app.domain.download.ZipExtractor
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SearchScreen(
    authCode: String?,
    onLoginClick: () -> Unit,
    repository: OsuRepository,
    db: AppDatabase
) {
    var statusText by remember { mutableStateOf("Ready to Login") }
    var accessToken by remember { mutableStateOf<String?>(null) }
    
    // Search Results
    var searchResults by remember { mutableStateOf<List<BeatmapsetCompact>>(emptyList()) }
    var selectedGenreId by remember { mutableStateOf<Int?>(null) }
    
    // Download State Map (SetID -> State)
    // We use a simplified single download state for MVP, or map for multiple.
    // Let's stick to single active download for simplicity first.
    var activeDownloadState by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloader = remember { BeatmapDownloader(context) }
    val extractor = remember { ZipExtractor(context) }

    val genres = listOf(
        10 to "Electronic", 3 to "Anime", 4 to "Rock", 5 to "Pop",
        2 to "Game", 9 to "Hip Hop", 11 to "Metal", 12 to "Classical",
        13 to "Folk", 14 to "Jazz", 7 to "Novelty", 6 to "Other"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (accessToken == null) {
            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login with osu!")
            }
            Text(text = statusText, modifier = Modifier.padding(top = 8.dp))
        } else {
            // Genre Filter
            Text(text = "Filter by Genre", style = MaterialTheme.typography.labelMedium)
            LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                items(genres) { (id, name) ->
                    Button(
                        onClick = {
                            selectedGenreId = if (selectedGenreId == id) null else id
                            scope.launch {
                                try {
                                    searchResults = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId)
                                } catch(e: Exception) {
                                    statusText = "Error: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedGenreId == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (selectedGenreId == id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(name)
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Search Results List
            LazyColumn {
                items(searchResults) { map ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cover Image
                        AsyncImage(
                            model = map.covers.listUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        
                        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(text = map.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(text = map.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                        }
                        
                        // Download Button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    activeDownloadState = "Downloading..."
                                    downloader.downloadBeatmap(map.id, accessToken).collect { state ->
                                        when (state) {
                                            is DownloadState.Downloading -> {
                                                activeDownloadState = "${state.progress}%"
                                            }
                                            is DownloadState.Downloaded -> {
                                                activeDownloadState = "Extracting..."
                                                try {
                                                    val extractedTracks = extractor.extractBeatmap(state.file, map.id)
                                                    extractedTracks.forEach { track ->
                                                        val entity = BeatmapEntity(
                                                            beatmapSetId = map.id,
                                                            title = track.title,
                                                            artist = track.artist,
                                                            creator = map.creator,
                                                            difficultyName = track.difficultyName,
                                                            audioPath = track.audioFile.absolutePath,
                                                            coverPath = track.coverFile?.absolutePath ?: ""
                                                        )
                                                        db.beatmapDao().insertBeatmap(entity)
                                                    }
                                                    activeDownloadState = "Done"
                                                } catch (e: Exception) {
                                                    activeDownloadState = "Error"
                                                }
                                            }
                                            is DownloadState.Error -> {
                                                activeDownloadState = "Failed"
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Download")
                        }
                    }
                    Divider(modifier = Modifier.padding(start = 64.dp))
                }
                
                // Pagination / Load More
                if (searchResults.isNotEmpty()) {
                    item {
                        Button(
                            onClick = { 
                                // TODO: Implement Offset Pagination
                                // Increment offset += 50 and append to list
                            },
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Text("Load More")
                        }
                    }
                }
            }
            
            if (activeDownloadState != null) {
                Text(text = "Status: $activeDownloadState", modifier = Modifier.padding(8.dp))
            }
        }

        // Auth Logic
        LaunchedEffect(authCode) {
            if (authCode != null && accessToken == null) {
                try {
                    val tokenResponse = repository.exchangeCodeForToken(authCode)
                    accessToken = tokenResponse.accessToken
                    // Initial fetch
                    searchResults = repository.getPlayedBeatmaps(accessToken!!)
                } catch (e: Exception) {
                    statusText = "Login Error"
                }
            }
        }
    }
}

