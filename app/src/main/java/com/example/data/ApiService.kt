package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ItunesSearchResponse(
  @param:Json(name = "resultCount") val resultCount: Int = 0,
  @param:Json(name = "results") val results: List<ItunesTrack> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ItunesTrack(
  @param:Json(name = "trackId") val trackId: Long = 0L,
  @param:Json(name = "trackName") val trackName: String? = null,
  @param:Json(name = "artistName") val artistName: String? = null,
  @param:Json(name = "collectionName") val collectionName: String? = null,
  @param:Json(name = "previewUrl") val previewUrl: String? = null,
  @param:Json(name = "artworkUrl100") val artworkUrl100: String? = null,
  @param:Json(name = "artworkUrl60") val artworkUrl60: String? = null,
  @param:Json(name = "trackTimeMillis") val trackTimeMillis: Long? = null,
  @param:Json(name = "primaryGenreName") val primaryGenreName: String? = null
)

@JsonClass(generateAdapter = true)
data class DeezerTracksResponse(
  @param:Json(name = "data") val data: List<DeezerTrack> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DeezerPlaylistSearchResponse(
  @param:Json(name = "data") val data: List<DeezerPlaylistSummary> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DeezerPlaylistSummary(
  @param:Json(name = "id") val id: Long = 0L,
  @param:Json(name = "title") val title: String? = null,
  @param:Json(name = "nb_tracks") val nbTracks: Int = 0
)

@JsonClass(generateAdapter = true)
data class DeezerTrack(
  @param:Json(name = "id") val id: Long = 0L,
  @param:Json(name = "title") val title: String? = null,
  @param:Json(name = "preview") val preview: String? = null,
  @param:Json(name = "duration") val duration: Long = 0L,
  @param:Json(name = "rank") val rank: Long = 0L,
  @param:Json(name = "artist") val artist: DeezerArtist? = null,
  @param:Json(name = "album") val album: DeezerAlbum? = null
)

@JsonClass(generateAdapter = true)
data class DeezerArtist(
  @param:Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class DeezerAlbum(
  @param:Json(name = "title") val title: String? = null,
  @param:Json(name = "cover_big") val coverBig: String? = null,
  @param:Json(name = "cover_medium") val coverMedium: String? = null,
  @param:Json(name = "cover_xl") val coverXl: String? = null
)

@JsonClass(generateAdapter = true)
data class AppleChartResponse(
  @param:Json(name = "feed") val feed: AppleChartFeed? = null
)

@JsonClass(generateAdapter = true)
data class AppleChartFeed(
  @param:Json(name = "updated") val updated: String? = null,
  @param:Json(name = "results") val results: List<AppleChartEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AppleChartEntry(
  @param:Json(name = "id") val id: String? = null,
  @param:Json(name = "name") val name: String? = null,
  @param:Json(name = "artistName") val artistName: String? = null,
  @param:Json(name = "artworkUrl100") val artworkUrl100: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeOEmbedResponse(
  @param:Json(name = "title") val title: String? = null,
  @param:Json(name = "author_name") val authorName: String? = null,
  @param:Json(name = "thumbnail_url") val thumbnailUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class LrcLibResponse(
  @param:Json(name = "syncedLyrics") val syncedLyrics: String? = null,
  @param:Json(name = "plainLyrics") val plainLyrics: String? = null
)

interface ItunesApiService {
  @GET("search")
  suspend fun searchSongs(
    @Query("term") term: String,
    @Query("media") media: String = "music",
    @Query("entity") entity: String = "song",
    @Query("limit") limit: Int = 25
  ): ItunesSearchResponse

  @GET("lookup")
  suspend fun lookupTracks(
    @Query("id") ids: String
  ): ItunesSearchResponse
}

interface DeezerApiService {
  @GET("chart/0/tracks")
  suspend fun getChartTracks(
    @Query("limit") limit: Int = 30
  ): DeezerTracksResponse

  @GET("search/playlist")
  suspend fun searchPlaylists(
    @Query("q") query: String,
    @Query("limit") limit: Int = 12
  ): DeezerPlaylistSearchResponse

  @GET("playlist/{id}/tracks")
  suspend fun getPlaylistTracks(
    @Path("id") id: Long,
    @Query("limit") limit: Int = 35
  ): DeezerTracksResponse

  @GET("search")
  suspend fun searchTracks(
    @Query("q") query: String,
    @Query("limit") limit: Int = 25
  ): DeezerTracksResponse
}

interface AppleChartsApiService {
  @GET("api/v2/{country}/music/most-played/{limit}/songs.json")
  suspend fun getMostPlayed(
    @Path("country") country: String = "us",
    @Path("limit") limit: Int = 25
  ): AppleChartResponse
}

interface YouTubeOEmbedService {
  @GET("oembed")
  suspend fun getVideoInfo(
    @Query("url") url: String,
    @Query("format") format: String = "json"
  ): YouTubeOEmbedResponse
}

interface LrcLibApiService {
  @GET("api/get")
  suspend fun getLyrics(
    @Query("artist_name") artist: String,
    @Query("track_name") track: String,
    @Query("duration") duration: Long
  ): LrcLibResponse?

  @GET("api/search")
  suspend fun searchLyrics(
    @Query("q") query: String
  ): List<LrcLibResponse>?
}

object NetworkClient {
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  private val itunesRetrofit = Retrofit.Builder()
    .baseUrl("https://itunes.apple.com/")
    .client(httpClient)
    .addConverterFactory(MoshiConverterFactory.create())
    .build()

  private val deezerRetrofit = Retrofit.Builder()
    .baseUrl("https://api.deezer.com/")
    .client(httpClient)
    .addConverterFactory(MoshiConverterFactory.create())
    .build()

  private val appleChartsRetrofit = Retrofit.Builder()
    .baseUrl("https://rss.applemarketingtools.com/")
    .client(httpClient)
    .addConverterFactory(MoshiConverterFactory.create())
    .build()

  private val youtubeRetrofit = Retrofit.Builder()
    .baseUrl("https://www.youtube.com/")
    .client(httpClient)
    .addConverterFactory(MoshiConverterFactory.create())
    .build()

  private val lrcLibRetrofit = Retrofit.Builder()
    .baseUrl("https://lrclib.net/")
    .client(httpClient)
    .addConverterFactory(MoshiConverterFactory.create())
    .build()

  val itunesApi: ItunesApiService = itunesRetrofit.create(ItunesApiService::class.java)
  val deezerApi: DeezerApiService = deezerRetrofit.create(DeezerApiService::class.java)
  val appleChartsApi: AppleChartsApiService = appleChartsRetrofit.create(AppleChartsApiService::class.java)
  val youtubeOEmbedApi: YouTubeOEmbedService = youtubeRetrofit.create(YouTubeOEmbedService::class.java)
  val lrcLibApi: LrcLibApiService = lrcLibRetrofit.create(LrcLibApiService::class.java)
}
