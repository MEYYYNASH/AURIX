package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
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
  val youtubeOEmbedApi: YouTubeOEmbedService = youtubeRetrofit.create(YouTubeOEmbedService::class.java)
  val lrcLibApi: LrcLibApiService = lrcLibRetrofit.create(LrcLibApiService::class.java)
}
