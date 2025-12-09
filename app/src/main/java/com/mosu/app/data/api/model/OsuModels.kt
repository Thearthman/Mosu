package com.mosu.app.data.api.model

import com.google.gson.annotations.SerializedName

data class OsuTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("token_type") val tokenType: String
)

data class BeatmapsetCompact(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("creator") val creator: String,
    @SerializedName("covers") val covers: Covers
)

data class Covers(
    @SerializedName("cover") val coverUrl: String,
    @SerializedName("list") val listUrl: String
)

