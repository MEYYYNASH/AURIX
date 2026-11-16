package com.example.model

const val APP_ICON_ARTWORK = "app_icon"

data class Song(
  val id: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val streamUrl: String,
  val source: String = "iTunes 30s", // "iTunes 30s", "Local", or "Downloaded"
  val isFavorite: Boolean = false,
  val fullDurationSeconds: Long = durationSeconds,
  val genre: String = "",
  val bpm: Int = 0,
  val mood: String = "",
  val album: String = "",
  val trackNumber: Int = 0,
  val year: String = "",
  val energy: Float = 0.5f,
  val sad: Float = 0f,
  val chill: Float = 0f,
  val happy: Float = 0f,
  val calm: Float = 0f,
  val energetic: Float = 0f,
  val romantic: Float = 0f,
  val hasVocals: Boolean = true,
  val language: String = "en",
  val hasMoodMetadata: Boolean = false
)

enum class CustomCoverMode(val id: String) {
  PHOTO("PHOTO"),
  MIX("MIX"),
  APP_ICON("APP_ICON");

  companion object {
    fun fromId(raw: String?): CustomCoverMode {
      return entries.firstOrNull { it.id == raw } ?: PHOTO
    }
  }
}

data class AlbumInfo(
  val albumId: String,
  val title: String,
  val artist: String,
  val year: String,
  val coverUrl: String?,
  val automaticCoverUrl: String?,
  val customCoverMode: CustomCoverMode? = null,
  val customSourceUris: List<String> = emptyList(),
  val tracks: List<Song> = emptyList()
)

fun computeAlbumId(albumName: String, artistName: String): String {
  val normAlbum = albumName.trim().ifBlank { "Unknown Album" }.lowercase()
  val normArtist = artistName.trim().ifBlank { "Unknown Artist" }.lowercase()
  return "${normAlbum}::${normArtist}"
}

data class LyricLine(
  val timestampMs: Long,
  val text: String
)

data class LyricsResult(
  val synced: List<LyricLine>?,
  val plain: String?,
  val matchedTitle: String? = null,
  val matchedArtist: String? = null,
  val rawLrc: String? = null
)

enum class StreamDownloadMode {
  DIRECT,
  QUERY_RANGE,
  HEADER_RANGE
}

data class StreamCandidate(
  val url: String,
  val userAgent: String,
  val downloadMode: StreamDownloadMode = StreamDownloadMode.DIRECT
)

data class AudioFormatOption(
  val id: String,
  val formatLabel: String,
  val qualityLabel: String,
  val detailLabel: String,
  val extension: String,
  val mimeType: String,
  val streamUrl: String,
  val durationSeconds: Long,
  val contentLengthBytes: Long = -1L,
  val requiredUserAgent: String? = null,
  val downloadMode: StreamDownloadMode = StreamDownloadMode.DIRECT,
  val fallbackCandidates: List<StreamCandidate> = emptyList()
)

data class YoutubeAnalysisResult(
  val videoId: String,
  val originalUrl: String,
  val videoTitle: String,
  val channelName: String,
  val thumbnailUrl: String,
  val matchedTrackTitle: String,
  val matchedArtist: String,
  val durationSeconds: Long = 0L,
  val formats: List<AudioFormatOption>
)

sealed interface YoutubeDownloadState {
  data object Idle : YoutubeDownloadState
  data class Downloading(
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val formatLabel: String
  ) : YoutubeDownloadState
  data class Saved(val song: Song) : YoutubeDownloadState
  data class Error(val message: String) : YoutubeDownloadState
}

typealias Track = Song

enum class SearchMode {
  YouTubeSearch,
  YouTubeLink,
  ITunes30s
}

sealed interface YoutubeFormatSheetState {
  data object Closed : YoutubeFormatSheetState
  data class Loading(val song: Song, val videoId: String) : YoutubeFormatSheetState
  data class Ready(
    val song: Song,
    val analysis: YoutubeAnalysisResult,
    val selectedFormatId: String,
    val downloadState: YoutubeDownloadState
  ) : YoutubeFormatSheetState
  data class Error(val song: Song, val videoId: String, val message: String) : YoutubeFormatSheetState
}

sealed interface SearchUiState {
  data object Idle : SearchUiState
  data class Loading(val mode: SearchMode, val query: String) : SearchUiState
  data class Results(val items: List<Track>) : SearchUiState
  data object Empty : SearchUiState
  data class Error(val message: String) : SearchUiState
}
