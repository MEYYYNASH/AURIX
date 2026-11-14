package com.example.model

data class Song(
  val id: String,
  val title: String,
  val artist: String,
  val artworkUrl: String,
  val durationSeconds: Long,
  val streamUrl: String,
  val source: String = "iTunes 30s", // "iTunes 30s", "Local", or "Downloaded"
  val isFavorite: Boolean = false
)

data class LyricLine(
  val timestampMs: Long,
  val text: String
)

data class LyricsResult(
  val synced: List<LyricLine>?,
  val plain: String?
)
