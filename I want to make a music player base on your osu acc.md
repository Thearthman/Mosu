
# I want to make a music player base on your osu account. basically in osu when a player has buyed contributer(subscription), they have access to their played beatmaps, which I think is a great way to make playlists out of. it is even more convenient when osu let's you download all information regarding the music and it's cover. I am thinking of an Android app approach since it's more reasonable to cache/store the songs (there are a lot of them) and you won't have to download them again. search detail into some apis and technical implementations I can make use of.

Great! Now I have comprehensive information. Let me provide you with a detailed technical breakdown:

## osu! Music Player Android App - Technical Implementation Guide

Based on your requirements, here's a comprehensive breakdown of APIs, architectures, and technical approaches you can use:

***

## Part 1: osu! API \& Data Access

### **Authentication \& User Data**

**osu!api v2** (Recommended)

- **Auth Method**: OAuth 2.0 - requires creating an app in your osu! account settings
- **Endpoint**: `https://osu.ppy.sh/api/v2/`
- **Getting User's Played Beatmaps**:

```
GET /api/v2/users/{user_id}/beatmapsets/most_played
GET /api/v2/users/{user_id}/beatmapsets/ranked
GET /api/v2/users/{user_id}/beatmapsets/loved
```

- **Key Libraries**:
    - **Python**: `ossapi` (uses OAuth client credentials)
    - **Rust**: `osynic_osuapi` (modern, supports all V2 endpoints)
    - **Java**: Direct HTTP requests with `OkHttp3` or `Retrofit`

**Rate Limiting**: osu!api v2 is relatively generous, but respects the `X-RateLimit-*` headers in responses[^1]

### **Beatmap Metadata \& Download**

**Direct osu! Download** (Requires Authentication)

- Endpoint: `/beatmapsets/{beatmapset_id}/download`
- Requires OAuth token with user credentials
- Returns `.osz` file (zipped beatmap bundle)
- `.osz` contains:
    - `.osu` files (beatmap metadata in text format)
    - Audio file (usually `.mp3` or `.wav`)
    - Cover art image (usually `.jpg`)
    - Storyboard/skin files (optional)

**Alternative Mirror APIs** (No auth required, often faster)[^2][^3]:

- **chimu.moe**: `GET /api/v2/search?query=...` then `GET /download/{beatmapset_id}`
- **Beatconnect**: `https://beatconnect.io/` (currently limited availability)
- **SayoBot**: `GET /api/beatmaps?search=...`

**Metadata Parsing**:

- Extract `.osu` files from `.osz` to parse:
    - `Title:`, `Artist:`, `Creator:` (metadata)
    - `AudioFilename:` (points to audio file in package)
    - Difficulty ratings and settings
- Cover art is typically `cover.jpg` in the root of `.osz`

***

## Part 2: Android Architecture for Offline Caching

### **Recommended Stack** (Production-Ready)[^4][^5]

```
┌─────────────────────────────────────────┐
│      UI Layer (Jetpack Compose)         │
│  - Playlist view, Now Playing, Search   │
└────────────┬────────────────────────────┘
             │
┌────────────▼────────────────────────────┐
│      ViewModel (MVVM/Clean Arch)        │
│  - State management                     │
│  - Playlist logic                       │
└────────────┬────────────────────────────┘
             │
┌────────────▼────────────────────────────┐
│      Repository Layer                   │
│  - osu! API calls (Retrofit)            │
│  - Local database queries               │
│  - Download management                  │
└────────────┬────────────────────────────┘
             │
    ┌────────┴─────────┬──────────────┐
    │                  │              │
┌───▼─────────┐  ┌────▼──────┐  ┌───▼──────┐
│  Room DB    │  │  ExoPlayer│  │ File Cache│
│ (metadata)  │  │ (playback)│  │ (songs)   │
└─────────────┘  └───────────┘  └───────────┘
```


### **1. Room Database (Local Metadata Storage)**

Store beatmap metadata locally to avoid repeated API calls:

```kotlin
@Entity
data class BeatmapEntity(
    @PrimaryKey
    val beatmapsetId: Int,
    val title: String,
    val artist: String,
    val creator: String,
    val audioFilename: String,
    val coverUrl: String,
    val playCount: Int,
    val downloadedAt: Long,
    val filePath: String  // Local path to .osz or extracted files
)

@Dao
interface BeatmapDao {
    @Query("SELECT * FROM beatmap WHERE beatmapsetId = :id")
    suspend fun getBeatmap(id: Int): BeatmapEntity?
    
    @Query("SELECT * FROM beatmap ORDER BY playCount DESC")
    fun getAllBeatmaps(): Flow<List<BeatmapEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeatmap(beatmap: BeatmapEntity)
}
```

**Benefits**:

- Query local data instantly (0ms vs 100-500ms API)
- Offline-first experience
- Sync in background when connected


### **2. Media3 ExoPlayer with Cache**

For local music playback with intelligent caching:

```kotlin
// Application singleton
class MusicPlayerApp : Application() {
    companion object {
        var simpleCache: SimpleCache? = null
    }
    
    override fun onCreate() {
        super.onCreate()
        // Initialize cache with LRU eviction (e.g., 500MB max)
        val cacheDir = File(cacheDir, "music_cache")
        simpleCache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024L)  // 500MB
        )
    }
}

// In your Activity/Fragment
val cache = MusicPlayerApp.simpleCache ?: return

// For LOCAL files (already downloaded .osz)
val mediaItem = MediaItem.Builder()
    .setUri(localBeatmapUri)  // file:/// URI to extracted MP3
    .build()

val mediaSource = ProgressiveMediaSource.Factory(
    CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
).createMediaSource(mediaItem)

player.setMediaSource(mediaSource)
player.prepare()
```


### **3. Intelligent Caching Strategy**

**Three-Tier Approach**:


| Tier | Storage | Capacity | Use Case |
| :-- | :-- | :-- | :-- |
| **L1: RAM Cache** | In-memory | ~10-20MB | Current playing track + next in queue |
| **L2: App Cache** | `context.cacheDir/` | 50-200MB | Recently played, frequently accessed |
| **L3: External Cache** | `getExternalFilesDir()` or scoped storage | 500MB-2GB | Full song library with metadata |

```kotlin
// Automatic cache cleanup
class CacheManager(val context: Context) {
    private val cacheDir = context.getExternalFilesDir("beatmaps")
    private val maxCacheSize = 500 * 1024 * 1024L  // 500MB
    
    suspend fun ensureSpaceAvailable(requiredSize: Long) {
        val currentSize = cacheDir?.walk()
            ?.sumOf { it.length() } ?: 0L
        
        if (currentSize + requiredSize > maxCacheSize) {
            // Delete oldest accessed files (LRU)
            cacheDir?.walk()
                ?.sortedBy { it.lastModified() }
                ?.take((currentSize + requiredSize - maxCacheSize) / (1024 * 1024))
                ?.forEach { it.delete() }
        }
    }
}
```


***

## Part 3: Download \& Extraction Pipeline

### **Download Manager Architecture**

```kotlin
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Downloaded : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class BeatmapDownloadManager(
    private val context: Context,
    private val ossuApiService: OsuApiService,  // Retrofit service
    private val beatmapDao: BeatmapDao
) {
    fun downloadBeatmap(beatmapsetId: Int): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Downloading(0))
            
            // Step 1: Get metadata from osu!api
            val beatmapset = ossuApiService.getBeatmapset(beatmapsetId)
            
            // Step 2: Download .osz file
            val oszFile = File(context.getExternalFilesDir("beatmaps"), 
                               "${beatmapsetId}.osz")
            
            ossuApiService.downloadBeatmap(beatmapsetId)
                .byteStream()
                .use { input ->
                    oszFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        val totalSize = // get from response header
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            emit(DownloadState.Downloading((totalRead * 100 / totalSize).toInt()))
                        }
                    }
                }
            
            // Step 3: Extract audio & cover
            extractBeatmapFiles(oszFile, beatmapsetId)
            
            // Step 4: Store metadata in Room
            beatmapDao.insertBeatmap(
                BeatmapEntity(
                    beatmapsetId = beatmapsetId,
                    title = beatmapset.title,
                    artist = beatmapset.artist,
                    creator = beatmapset.creator,
                    audioFilename = beatmapset.availableDifficulties.first(),
                    coverUrl = beatmapset.coverUrl,
                    playCount = 0,
                    downloadedAt = System.currentTimeMillis(),
                    filePath = oszFile.absolutePath
                )
            )
            
            emit(DownloadState.Downloaded)
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown error"))
        }
    }
    
    private fun extractBeatmapFiles(oszFile: File, beatmapsetId: Int) {
        ZipFile(oszFile).use { zip ->
            // Extract audio file
            zip.entries().find { it.name.endsWith(".mp3") || it.name.endsWith(".wav") }?.let { audioEntry ->
                val audioDir = File(context.getExternalFilesDir("beatmaps"), 
                                   "${beatmapsetId}_audio")
                audioDir.mkdirs()
                zip.getInputStream(audioEntry).use { input ->
                    File(audioDir, audioEntry.name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            // Extract cover
            zip.entries().find { it.name == "cover.jpg" || it.name == "cover.png" }?.let { coverEntry ->
                val coverDir = File(context.getExternalFilesDir("beatmaps"), 
                                   "${beatmapsetId}_cover")
                coverDir.mkdirs()
                zip.getInputStream(coverEntry).use { input ->
                    File(coverDir, coverEntry.name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
```


***

## Part 4: API Integration (Retrofit Setup)

```kotlin
// osu!api Service
interface OsuApiService {
    @GET("/api/v2/users/{user_id}/beatmapsets/most_played")
    suspend fun getUserMostPlayedBeatmaps(
        @Path("user_id") userId: Int,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): List<BeatmapsetDto>
    
    @GET("/api/v2/beatmapsets/{beatmapset_id}")
    suspend fun getBeatmapset(
        @Path("beatmapset_id") beatmapsetId: Int
    ): BeatmapsetDto
    
    @Streaming
    @GET("/beatmapsets/{beatmapset_id}/download")
    suspend fun downloadBeatmap(
        @Path("beatmapset_id") beatmapsetId: Int
    ): ResponseBody
}

// OAuth Interceptor
class OAuthInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}

// Retrofit client
val retrofit = Retrofit.Builder()
    .baseUrl("https://osu.ppy.sh/")
    .client(OkHttpClient.Builder()
        .addInterceptor(OAuthInterceptor(osuOAuthToken))
        .build())
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val ossuService = retrofit.create(OsuApiService::class.java)
```


***

## Part 5: Storage \& Permissions

### **Storage Strategy** (Android 11+)

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />

<!-- Scoped storage via getExternalFilesDir() -->
<!-- No WRITE_EXTERNAL_STORAGE needed if using app-specific directory -->
```


### **File Organization**

```
/Android/data/{package_name}/files/
├── beatmaps/
│   ├── 123456/
│   │   ├── audio.mp3
│   │   ├── cover.jpg
│   │   └── metadata.json
│   ├── 789012/
│   │   ├── audio.mp3
│   │   ├── cover.jpg
│   │   └── metadata.json
│   └── *.osz  (optional: keep raw files)
├── cache/
│   └── (ExoPlayer's SimpleCache)
└── playlists.db  (Room database)
```


***

## Part 6: Alternative APIs \& Mirrors

| Mirror | Auth | Speed | Pros | Cons |
| :-- | :-- | :-- | :-- | :-- |
| **osu!api v2** | OAuth | Moderate | Official, complete metadata | Rate limited, auth required |
| **chimu.moe** | None | Fast | No auth, large mirror | Community-maintained |
| **SayoBot** | None | Fast | Chinese CDN (fast in Asia) | Language barrier |
| **Beatconnect** | None | Very Fast | Best for batch downloads | Currently limited |

**API Endpoint Examples**:

```bash
# chimu.moe - Get beatmapset info
curl "https://chimu.moe/api/v2/search?query=beatmapset_id:123456"

# Direct download from chimu
curl -O "https://chimu.moe/download/123456"

# SayoBot - Search
curl "https://api.sayobot.cn/beatmaplist?T=1&bid=123456"
```


***

## Part 7: Recommended Implementation Order

1. **Phase 1: MVP**
    - Retrofit + osu!api v2 OAuth setup
    - Room database for beatmap metadata
    - Basic playlist fetching
2. **Phase 2: Offline**
    - Download manager with progress tracking
    - ExoPlayer integration for local playback
    - SimpleCache for offline audio
3. **Phase 3: Polish**
    - Background download service
    - Sync with osu! account when online
    - Search \& filtering UI
4. **Phase 4: Optimization**
    - LRU cache cleanup
    - Bandwidth estimation
    - Resume interrupted downloads

***

## Key Technical Considerations

**Storage Limits**:

- Android's scoped storage limits app-specific cache (~2GB typical)
- For large libraries (1000+ songs), consider:
    - Delta sync (only new/updated maps)
    - User-selected "favorites" subset
    - Streaming fallback for non-cached songs

**Bandwidth**:

- Average `.osz` file: 10-30MB
- Large mapsets: up to 100MB+
- Consider WiFi-only downloads for initial sync
- Resume capability via `Range` headers in Retrofit

**Battery**:

- ExoPlayer with local files = very efficient
- Disk I/O is cheaper than network streaming
- Background playback needs `MediaSession` + foreground service

***

Given your technical background, I'd suggest starting with **Rust** for a backend download service (using `osynic_downloader` or `vielpork`), then **Kotlin + Jetpack Compose** for the Android frontend. This decouples concerns and lets you batch-sync beatmaps efficiently.

Would you like me to create a starter template for any specific component (Room entity setup, Retrofit service, ExoPlayer integration, or download manager)?
<span style="display:none">[^10][^11][^12][^13][^14][^15][^16][^17][^18][^6][^7][^8][^9]</span>

<div align="center">⁂</div>

[^1]: https://beatconnect.io/api/infos

[^2]: https://osu.ppy.sh/community/forums/topics/1325949

[^3]: https://docs.rs/osynic_downloader

[^4]: https://www.youtube.com/watch?v=5icBjnJprhU

[^5]: https://stackoverflow.com/questions/28700391/using-cache-in-exoplayer

[^6]: https://osu.ppy.sh/community/forums/topics/1643063

[^7]: https://stackoverflow.com/questions/12701249/getting-access-to-media-player-cache

[^8]: https://osu.ppy.sh/community/forums/topics/1725042

[^9]: https://developer.android.com/training/data-storage/shared/media

[^10]: https://stackoverflow.com/questions/75793044/im-trying-to-get-information-from-a-beatmap-from-osu-the-game-but-i-keep-get

[^11]: https://stackoverflow.com/questions/71817457/osu-download-links-open-beatmap-page-instead-of-downloading-the-beatmap-file

[^12]: https://osu.ppy.sh/community/forums/topics/1254713

[^13]: https://pypi.org/project/ossapi/2.2.5/

[^14]: https://docs.rs/osynic_osuapi

[^15]: https://www.reddit.com/r/osugame/comments/poi5bb/does_a_tool_to_search_and_download_beatmaps_still/

[^16]: https://osu.ppy.sh/docs/

[^17]: https://beatconnect.io

[^18]: https://stackoverflow.com/questions/60790000/using-simpleexoplayer-with-room-as-offline-cache

