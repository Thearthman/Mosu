package com.mosu.app.domain.model

import java.io.File

data class ExtractedTrack(
    val audioFile: File,
    val coverFile: File?,
    val title: String,
    val artist: String,
    val difficultyName: String
)

