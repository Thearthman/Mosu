package com.mosu.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mosu.app.data.api.RetrofitClient
import com.mosu.app.data.api.model.BeatmapPlaycount
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.OsuTokenResponse
import com.mosu.app.data.api.model.OsuUserCompact
import com.mosu.app.data.db.SearchCacheDao
import com.mosu.app.data.db.SearchCacheEntity

class OsuRepository(private val searchCacheDao: SearchCacheDao? = null) {
    private val api = RetrofitClient.api
    private val gson = Gson()
    
    private val redirectUri = "mosu://callback"
    
    // Cache TTL: 5 minutes
    private val CACHE_TTL_MS = 5 * 60 * 1000L

    suspend fun exchangeCodeForToken(code: String, clientId: String, clientSecret: String): OsuTokenResponse {
        return api.getToken(
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            grantType = "authorization_code",
            redirectUri = redirectUri
        )
    }

    suspend fun getMe(accessToken: String): OsuUserCompact {
        return api.getMe("Bearer $accessToken")
    }

    suspend fun getUserMostPlayed(accessToken: String, userId: String): List<BeatmapPlaycount> {
        return api.getUserMostPlayed("Bearer $accessToken", userId)
    }

    suspend fun getPlayedBeatmaps(accessToken: String, genreId: Int? = null, cursorString: String? = null, searchQuery: String? = null): Pair<List<BeatmapsetCompact>, String?> {
        // Generate cache key (only cache first page without search query)
        val cacheKey = "played_genre_${genreId ?: "all"}_query_${searchQuery ?: "none"}_initial"
        
        // Only use cache for initial load (no cursor) without search query
        if (cursorString == null && searchQuery.isNullOrEmpty()) {
            val cached = searchCacheDao?.getCachedResult(cacheKey)
            if (cached != null && (System.currentTimeMillis() - cached.cachedAt) < CACHE_TTL_MS) {
                // Cache hit and fresh - note: we don't cache cursor, so this is only good for initial page
                val type = object : TypeToken<List<BeatmapsetCompact>>() {}.type
                val results: List<BeatmapsetCompact> = gson.fromJson(cached.resultsJson, type)
                return Pair(results, null) // Return cached results with null cursor (can't paginate from cache)
            }
        }
        
        // Fetch from API
        val response = api.searchBeatmapsets(
            authHeader = "Bearer $accessToken",
            played = "played",
            genre = genreId,
            cursorString = cursorString,
            query = searchQuery
        )
        
        // Save to cache only for initial load without search
        if (cursorString == null && searchQuery.isNullOrEmpty()) {
            searchCacheDao?.let {
                val json = gson.toJson(response.beatmapsets)
                it.insertCache(SearchCacheEntity(queryKey = cacheKey, resultsJson = json))
                // Clean up old cache entries
                it.clearExpired(System.currentTimeMillis() - CACHE_TTL_MS)
            }
        }
        
        return Pair(response.beatmapsets, response.cursorString)
    }
}

