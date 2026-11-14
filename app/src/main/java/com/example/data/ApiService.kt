package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class AudiusResponse<T>(
  @Json(name = "data") val data: T
)

@JsonClass(generateAdapter = true)
data class AudiusTrack(
  @Json(name = "id") val id: String,
  @Json(name = "title") val title: String,
  @Json(name = "duration") val duration: Long,
  @Json(name = "artwork") val artwork: AudiusArtwork?,
  @Json(name = "user") val user: AudiusUser?
)

@JsonClass(generateAdapter = true)
data class AudiusArtwork(
  @Json(name = "150x150") val size150: String?,
  @Json(name = "480x480") val size480: String?,
  @Json(name = "1000x1000") val size1000: String?
)

@JsonClass(generateAdapter = true)
data class AudiusUser(
  @Json(name = "name") val name: String?
)

@JsonClass(generateAdapter = true)
data class LrcLibResponse(
  @Json(name = "syncedLyrics") val syncedLyrics: String?,
  @Json(name = "plainLyrics") val plainLyrics: String?
)

interface AudiusApiService {
  @GET("v1/tracks/trending")
  suspend fun getTrendingTracks(
    @Query("app_name") appName: String = "aurix"
  ): AudiusResponse<List<AudiusTrack>>

  @GET("v1/tracks/search")
  suspend fun searchTracks(
    @Query("query") query: String,
    @Query("app_name") appName: String = "aurix"
  ): AudiusResponse<List<AudiusTrack>>
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
  private val audiusRetrofit = Retrofit.Builder()
    .baseUrl("https://discoveryprovider.audius.co/")
    .addConverterFactory(MoshiConverterFactory.create())
    .build()

  private val lrcLibRetrofit = Retrofit.Builder()
    .baseUrl("https://lrclib.net/")
    .addConverterFactory(MoshiConverterFactory.create())
    .build()

  val audiusApi: AudiusApiService = audiusRetrofit.create(AudiusApiService::class.java)
  val lrcLibApi: LrcLibApiService = lrcLibRetrofit.create(LrcLibApiService::class.java)
}
