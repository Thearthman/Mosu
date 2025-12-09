package com.mosu.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mosu.app.data.db.BeatmapEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class MusicController(context: Context) {
    
    private var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null

    private val _nowPlaying = MutableStateFlow<MediaMetadata?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            controller.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _nowPlaying.value = mediaItem?.mediaMetadata
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
            })
            // Initialize state
            _nowPlaying.value = controller.currentMediaItem?.mediaMetadata
            _isPlaying.value = controller.isPlaying
        }, MoreExecutors.directExecutor())
    }

    fun playSong(selectedBeatmap: BeatmapEntity, playlist: List<BeatmapEntity> = listOf(selectedBeatmap)) {
        val controller = this.controller ?: return
        
        // Convert playlist to MediaItems
        val mediaItems = playlist.map { beatmap ->
            val file = File(beatmap.audioPath)
            val metadata = MediaMetadata.Builder()
                .setTitle(beatmap.title)
                .setArtist(beatmap.artist)
                .setArtworkUri(Uri.fromFile(File(beatmap.coverPath)))
                .build()

            MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMediaId(beatmap.uid.toString())
                .setMediaMetadata(metadata)
                .build()
        }

        // Find the index of the selected song
        val startIndex = mediaItems.indexOfFirst { it.mediaId == selectedBeatmap.uid.toString() }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, startIndex, 0)
        controller.shuffleModeEnabled = true // Default to shuffle/random
        controller.repeatMode = Player.REPEAT_MODE_ALL // Infinite loop - never exhaust
        controller.prepare()
        controller.play()
    }
    
    fun togglePlayPause() {
        val controller = this.controller ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }
    
    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture)
    }
}

