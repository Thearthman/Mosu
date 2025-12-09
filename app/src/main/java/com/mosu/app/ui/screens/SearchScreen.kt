package com.mosu.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    repository: OsuRepository,
    db: AppDatabase,
    clientId: String,
    redirectUri: String
) {
    var statusText by remember { mutableStateOf("Ready to Login") }
    // State to hold the token
    var accessToken by remember { mutableStateOf<String?>(null) }
    // State to hold the first beatmap ID found
    var firstBeatmapId by remember { mutableStateOf<Long?>(null) }
    // Hold full beatmapset info for the first map to save to DB
    var firstBeatmapTitle by remember { mutableStateOf("") }
    var firstBeatmapArtist by remember { mutableStateOf("") }
    var firstBeatmapCreator by remember { mutableStateOf("") }
    
    // Genre Filter State
    var selectedGenreId by remember { mutableStateOf<Int?>(null) }
    
    val genres = listOf(
        10 to "Electronic", 3 to "Anime", 4 to "Rock", 5 to "Pop",
        2 to "Game", 9 to "Hip Hop", 11 to "Metal", 12 to "Classical",
        13 to "Folk", 14 to "Jazz", 7 to "Novelty", 6 to "Other"
    )

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloader = remember { BeatmapDownloader(context) }
    val extractor = remember { ZipExtractor(context) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (accessToken == null) {
            Button(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://osu.ppy.sh/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=public+identify")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Login with osu!")
            }
        } else {
            // Genre Filter Row
            Text(text = "Filter by Genre:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                items(genres) { (id, name) ->
                    Button(
                        onClick = {
                            selectedGenreId = if (selectedGenreId == id) null else id
                            // Refresh list
                            scope.launch {
                                try {
                                    statusText = "Filtering by $name..."
                                    val beatmaps = repository.getPlayedBeatmaps(accessToken!!, selectedGenreId)
                                    statusText = "Found ${beatmaps.size} '$name' maps."
                                    
                                    if (beatmaps.isNotEmpty()) {
                                        val map = beatmaps[0]
                                        firstBeatmapId = map.id
                                        firstBeatmapTitle = map.title
                                        firstBeatmapArtist = map.artist
                                        firstBeatmapCreator = map.creator
                                        statusText += "\nSelected: ${map.title}"
                                    } else {
                                        firstBeatmapId = null
                                        statusText += "\nNo maps found for this genre."
                                    }
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
            
            // Test Download Button (Only visible if we have an ID)
            if (firstBeatmapId != null) {
                Button(
                    onClick = {
                        scope.launch {
                            statusText += "\nStarting download for ID: $firstBeatmapId..."
                            downloader.downloadBeatmap(firstBeatmapId!!, accessToken).collect { state ->
                                when (state) {
                                    is DownloadState.Downloading -> {
                                        statusText = "Downloading from ${state.source}\nProgress: ${state.progress}%"
                                    }
                                    is DownloadState.Downloaded -> {
                                        statusText = "Downloaded .osz! Extracting..."
                                        try {
                                            // Save to DB (Handle multiple tracks)
                                            val extractedTracks = extractor.extractBeatmap(state.file, firstBeatmapId!!)
                                            statusText = "Success! Extracted ${extractedTracks.size} tracks."
                                            
                                            extractedTracks.forEach { track ->
                                                val entity = BeatmapEntity(
                                                    beatmapSetId = firstBeatmapId!!,
                                                    title = track.title,
                                                    artist = track.artist,
                                                    creator = firstBeatmapCreator,
                                                    difficultyName = track.difficultyName,
                                                    audioPath = track.audioFile.absolutePath,
                                                    coverPath = track.coverFile?.absolutePath ?: ""
                                                )
                                                db.beatmapDao().insertBeatmap(entity)
                                            }
                                            statusText += "\nSaved to Database!"
                                            
                                        } catch (e: Exception) {
                                            statusText = "Extraction/DB Failed: ${e.message}"
                                            e.printStackTrace()
                                        }
                                    }
                                    is DownloadState.Error -> {
                                        statusText = "Download Failed: ${state.message}"
                                    }
                                    else -> {}
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Download Selected: $firstBeatmapTitle")
                }
            }
        }

        Text(
            text = statusText,
            modifier = Modifier.padding(top = 16.dp)
        )

        LaunchedEffect(authCode) {
            if (authCode != null) {
                statusText = "Got Code! Exchanging for token..."
                try {
                    val tokenResponse = repository.exchangeCodeForToken(authCode)
                    accessToken = tokenResponse.accessToken
                    statusText = "Success! Logged in."
                    
                    // Fetch Current User
                    val me = repository.getMe(tokenResponse.accessToken)
                    statusText += "\nUser: ${me.username}"
                    
                } catch (e: Exception) {
                    statusText = "Login Error: ${e.message}"
                    e.printStackTrace()
                }
            }
        }
    }
}

