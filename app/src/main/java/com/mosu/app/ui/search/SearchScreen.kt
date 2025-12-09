package com.mosu.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.download.BeatmapDownloader
import com.mosu.app.domain.download.DownloadState
import com.mosu.app.domain.download.ZipExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class DownloadProgress(
    val progress: Int, // 0-100
    val status: String // "Downloading", "Extracting", "Done", "Error"
)

@Composable
fun SearchScreen(
    authCode: String?,
    repository: OsuRepository,
    db: AppDatabase,
    accessToken: String?,
    clientId: String,
    clientSecret: String,
    settingsManager: com.mosu.app.data.SettingsManager,
    musicController: com.mosu.app.player.MusicController,
    onTokenReceived: (String) -> Unit,
    scrollToTop: Boolean = false,
    onScrolledToTop: () -> Unit = {}
) {
    var statusText by remember { mutableStateOf("") }
    
    // Search Query
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter Mode: "played", "all", or "favorite"
    var filterMode by remember { mutableStateOf("played") }
    
    // Played filter mode from settings
    val playedFilterMode by settingsManager.playedFilterMode.collectAsState(initial = "url")
    var userId by remember { mutableStateOf<String?>(null) }
    
    // Search Results
    var searchResults by remember { mutableStateOf<List<BeatmapsetCompact>>(emptyList()) }
    var selectedGenreId by remember { mutableStateOf<Int?>(null) }
    var currentCursor by remember { mutableStateOf<String?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    
    // Download States Map (BeatmapSetId -> DownloadProgress)
    var downloadStates by remember { mutableStateOf<Map<Long, DownloadProgress>>(emptyMap()) }
    
    // Downloaded BeatmapSet IDs (from database)
    var downloadedBeatmapSetIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    
    // Load downloaded beatmap IDs from database
    LaunchedEffect(Unit) {
        db.beatmapDao().getAllBeatmaps().collect { beatmaps ->
            downloadedBeatmapSetIds = beatmaps.map { it.beatmapSetId }.toSet()
        }
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloader = remember { BeatmapDownloader(context) }
    val extractor = remember { ZipExtractor(context) }
    
    // Scroll state for collapsing header
    val listState = rememberLazyListState()
    
    // Scroll to top when requested
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            listState.animateScrollToItem(0)
            onScrolledToTop()
        }
    }

    val genres = listOf(
        10 to "Electronic", 3 to "Anime", 4 to "Rock", 5 to "Pop",
        2 to "Game", 9 to "Hip Hop", 11 to "Metal", 12 to "Classical",
        13 to "Folk", 14 to "Jazz", 7 to "Novelty", 6 to "Other"
    )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )

        if (accessToken == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Not Logged In", style = MaterialTheme.typography.titleMedium)
                    Text("Please go to the Profile tab to configure credentials and login.", style = MaterialTheme.typography.bodyMedium)
                    if (statusText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            // Collapsible header with search bar and genre filter
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Header: Search bar and Genre filter
                item {
                    Column {
                        // Search Bar
                        TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = { 
                    Text(
                        "Search by title or artist...",
                        style = MaterialTheme.typography.bodySmall
                    ) 
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                // Refresh results without search query
                                scope.launch {
                                    try {
                                        val (results, cursor) = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, null, null, filterMode, playedFilterMode, userId)
                                        searchResults = results
                                        currentCursor = cursor
                                    } catch (e: Exception) {
                                        statusText = "Error: ${e.message}"
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        
                        // Filter Mode Toggle Button
                        Button(
                            onClick = {
                                filterMode = when (filterMode) {
                                    "played" -> "all"
                                    "all" -> "favorite"
                                    else -> "played"
                                }
                                // Refresh results with new filter
                                scope.launch {
                                    try {
                                        currentCursor = null
                                        val (results, cursor) = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, null, searchQuery.trim().ifEmpty { null }, filterMode, playedFilterMode, userId)
                                        searchResults = results
                                        currentCursor = cursor
                                    } catch (e: Exception) {
                                        statusText = "Error: ${e.message}"
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(85.dp)
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (filterMode) {
                                    "all" -> MaterialTheme.colorScheme.secondaryContainer
                                    "favorite" -> androidx.compose.ui.graphics.Color(0xFFFFD059) // Gold
                                    else -> MaterialTheme.colorScheme.primary // "played"
                                },
                                contentColor = when (filterMode) {
                                    "all" -> MaterialTheme.colorScheme.onSecondaryContainer
                                    "favorite" -> androidx.compose.ui.graphics.Color.Black
                                    else -> MaterialTheme.colorScheme.onPrimary
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = when (filterMode) {
                                    "played" -> "Played"
                                    "all" -> "All"
                                    else -> "Favorite"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        scope.launch {
                            try {
                                currentCursor = null
                                val (results, cursor) = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, null, searchQuery.trim(), filterMode, playedFilterMode, userId)
                                searchResults = results
                                currentCursor = cursor
                            } catch (e: Exception) {
                                statusText = "Search Error: ${e.message}"
                            }
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
                            )
                            
                            // Genre Filter
                            Text(text = "Filter by Genre", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                            LazyRow(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                                items(genres) { (id, name) ->
                                    Button(
                                        onClick = {
                                            selectedGenreId = if (selectedGenreId == id) null else id
                                            currentCursor = null // Reset cursor when changing genre
                                            scope.launch {
                                                try {
                                                    val (results, cursor) = repository.getPlayedBeatmaps(accessToken, selectedGenreId, null, searchQuery.trim().ifEmpty { null }, filterMode, playedFilterMode, userId)
                                                    searchResults = results
                                                    currentCursor = cursor
                                                } catch(e: Exception) {
                                                    statusText = "Error: ${e.message}"
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(end = 8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedGenreId == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (selectedGenreId == id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Text(name, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                
                // Search Results
                items(searchResults) { map ->
                    val downloadProgress = downloadStates[map.id]
                    val isDownloaded = downloadedBeatmapSetIds.contains(map.id)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isDownloaded) {
                                    // Play the song if downloaded
                                    scope.launch {
                                        val tracks = db.beatmapDao().getTracksForSet(map.id)
                                        if (tracks.isNotEmpty()) {
                                            // TODO: Ideally we should construct a playlist of all downloaded songs in the search results
                                            // For now, let's just play this one song, or query all downloaded songs as context?
                                            // Playing just this song for now to avoid complexity in fetching all tracks for all results
                                            // User requested playlist system "defaults to shuffle". 
                                            // If playing from search, maybe just shuffle all DOWNLOADED songs in the DB?
                                            // Or shuffle filtered results?
                                            
                                            // Let's fetch all downloaded songs to use as playlist context
                                            val allDownloaded = db.beatmapDao().getAllBeatmaps().first()
                                            musicController.playSong(tracks[0], allDownloaded)
                                        }
                                    }
                                }
                            }
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
                        
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1f)
                        ) {
                            Text(text = map.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(text = map.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                            
                            // Download Progress Bar
                            if (downloadProgress != null) {
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    if (downloadProgress.status == "Downloading" && downloadProgress.progress < 100) {
                                        LinearProgressIndicator(
                                            progress = downloadProgress.progress / 100f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Text(
                                        text = downloadProgress.status,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        // Download Button
                        IconButton(
                            onClick = {
                                if (!isDownloaded) {
                                    scope.launch {
                                        downloadStates = downloadStates + (map.id to DownloadProgress(0, "Starting..."))
                                        downloader.downloadBeatmap(map.id, accessToken).collect { state ->
                                            when (state) {
                                                is DownloadState.Downloading -> {
                                                    downloadStates = downloadStates + (map.id to DownloadProgress(state.progress, "Downloading"))
                                                }
                                                is DownloadState.Downloaded -> {
                                                    downloadStates = downloadStates + (map.id to DownloadProgress(100, "Extracting..."))
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
                                                            coverPath = track.coverFile?.absolutePath ?: "",
                                                            genreId = map.genreId
                                                        )
                                                        db.beatmapDao().insertBeatmap(entity)
                                                    }
                                                        downloadStates = downloadStates + (map.id to DownloadProgress(100, "Done ✓"))
                                                        // Remove from download states after 2 seconds
                                                        kotlinx.coroutines.delay(2000)
                                                        downloadStates = downloadStates - map.id
                                                    } catch (e: Exception) {
                                                        downloadStates = downloadStates + (map.id to DownloadProgress(0, "Error: ${e.message}"))
                                                    }
                                                }
                                                is DownloadState.Error -> {
                                                    downloadStates = downloadStates + (map.id to DownloadProgress(0, "Failed"))
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isDownloaded && downloadProgress == null // Disable if already downloaded or downloading
                        ) {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Add,
                                contentDescription = if (isDownloaded) "Downloaded" else "Download",
                                tint = if (isDownloaded) {
                                    MaterialTheme.colorScheme.primary
                                } else if (downloadProgress != null) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
                
                // Pagination / Load More
                if (searchResults.isNotEmpty() && currentCursor != null) {
                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoadingMore = true
                                    statusText = "Loading more..."
                                    try {
                                        val (moreResults, nextCursor) = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId, currentCursor, searchQuery.trim().ifEmpty { null }, filterMode, playedFilterMode, userId)
                                        if (moreResults.isNotEmpty()) {
                                            searchResults = searchResults + moreResults
                                            currentCursor = nextCursor
                                            statusText = "Loaded ${moreResults.size} more results"
                                        } else {
                                            statusText = "No more results available"
                                            currentCursor = null
                                        }
                                    } catch (e: Exception) {
                                        statusText = "Load More Error: ${e.message}"
                                    } finally {
                                        isLoadingMore = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            enabled = !isLoadingMore
                        ) {
                            Text(if (isLoadingMore) "Loading..." else "Load More")
                        }
                    }
                }
                
                // Status/Error Message Display
                if (statusText.isNotEmpty()) {
                    item {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (statusText.contains("Error") || statusText.contains("Failed")) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }
            
        // Initial Load - Fetch results when logged in
        LaunchedEffect(accessToken, filterMode) {
            if (accessToken != null) {
                try {
                    if (userId == null) {
                        val user = repository.getMe(accessToken)
                        userId = user.id.toString()
                    }
                    val (results, cursor) = repository.getPlayedBeatmaps(accessToken, null, null, null, filterMode, playedFilterMode, userId)
                    searchResults = results
                    currentCursor = cursor
                } catch (e: Exception) {
                    statusText = "Failed to load: ${e.message}"
                }
            }
        }
    }
}
}


