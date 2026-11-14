package com.example

import com.example.data.LrcLibApiService
import com.example.data.LrcLibResponse
import com.example.lyrics.LyricsMatchOutcome
import com.example.lyrics.LyricsMatcher
import com.example.model.Song
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class LyricsMatcherTest {

  @Test
  fun normalizePreservesKhmerAndStripsNoiseAndBrackets() {
    val raw = "ចិត្តនឹកអតីតកាល (Official Music Video) [HD] feat. Suly Pheng - Lyrics!"
    val normalized = LyricsMatcher.normalizeText(raw)
    assertTrue(normalized.contains("ចិត្តនឹកអតីតកាល"))
    assertTrue(normalized.contains("suly pheng"))
    assertTrue(!normalized.contains("official"))
    assertTrue(!normalized.contains("video"))
    assertTrue(!normalized.contains("hd"))
    assertTrue(!normalized.contains("feat"))
    assertTrue(!normalized.contains("lyrics"))
    assertTrue(!normalized.contains("("))
    assertTrue(!normalized.contains("["))
    assertTrue(!normalized.contains("!"))
  }

  @Test
  fun similarityScoresExactContainsAndWordOverlap() {
    assertEquals(1.0, LyricsMatcher.similarity("Cruel Summer", "cruel summer (official audio)"), 0.0001)
    assertEquals(0.85, LyricsMatcher.similarity("Love Story", "Love Story Taylor Version"), 0.0001)
    // "my sweet love" vs "my sweet heart" -> intersection=2 (my, sweet), union=4 -> 0.5
    assertEquals(0.5, LyricsMatcher.similarity("My Sweet Love", "My Sweet Heart"), 0.0001)
  }

  @Test
  fun rejectsWrongSongEvenIfFirstCandidateAndAcceptsStrictBestMatch() {
    val song = Song(
      id = "itunes_123",
      title = "Cruel Summer",
      artist = "Taylor Swift",
      artworkUrl = "",
      durationSeconds = 30L,
      streamUrl = "https://example.com/preview.m4a",
      source = "iTunes 30s",
      fullDurationSeconds = 178L
    )

    val wrongArtist = LrcLibResponse(
      id = 1L,
      trackName = "Cruel Summer",
      artistName = "Bananarama",
      duration = 215.0,
      syncedLyrics = "[00:01.00]Hot summer streets"
    )
    val wrongDuration = LrcLibResponse(
      id = 2L,
      trackName = "Cruel Summer",
      artistName = "Taylor Swift",
      duration = 210.0, // >10s off from 178s
      syncedLyrics = "[00:01.00]Live stadium version"
    )
    val instrumentalCandidate = LrcLibResponse(
      id = 3L,
      trackName = "Cruel Summer",
      artistName = "Taylor Swift",
      duration = 178.0,
      instrumental = true,
      syncedLyrics = "[00:01.00]Instrumental"
    )
    val exactMatch = LrcLibResponse(
      id = 4L,
      trackName = "Cruel Summer",
      artistName = "Taylor Swift",
      duration = 179.0,
      syncedLyrics = "[00:08.10]Fever dream high in the quiet of the night"
    )

    assertNull(LyricsMatcher.evaluateCandidate(song, wrongArtist))
    assertNull(LyricsMatcher.evaluateCandidate(song, wrongDuration))
    assertNull(LyricsMatcher.evaluateCandidate(song, instrumentalCandidate))

    val best = LyricsMatcher.selectBestCandidate(
      song,
      listOf(wrongArtist, wrongDuration, instrumentalCandidate, exactMatch)
    )
    assertNotNull(best)
    assertEquals(4L, best?.id)
  }

  @Test
  fun rejectsWeakWeightedScoreBelowPointSevenFive() {
    val song = Song(
      id = "local_1",
      title = "Starlight Night",
      artist = "John Doe",
      artworkUrl = "",
      durationSeconds = 200L,
      streamUrl = "file:///music/starlight.mp3",
      source = "Local",
      fullDurationSeconds = 200L
    )
    // titleSim: "starlight night" vs "starlight" -> contains = 0.85 (0.85 * 0.5 = 0.425)
    // artistSim: "john doe" vs "john smith" -> word overlap 1/3 = 0.333 (< 0.4 rejected)
    val weakArtist = LrcLibResponse(
      id = 10L,
      trackName = "Starlight",
      artistName = "John Smith",
      duration = 200.0,
      plainLyrics = "Some lyrics"
    )
    assertNull(LyricsMatcher.evaluateCandidate(song, weakArtist))

    // titleSim: word overlap "starlight night" vs "starlight dream" -> 1/3 = 0.33 (< 0.6 rejected)
    // Instead test passing individual minimums (title 0.6, artist 0.5) but duration off by 9s -> 0.30 + 0.15 + 0.02 = 0.47 < 0.75
    val lowTotal = LrcLibResponse(
      id = 11L,
      trackName = "A B C D",
      artistName = "Artist One",
      duration = 209.0,
      plainLyrics = "Some lyrics"
    )
    val song2 = song.copy(title = "A B C E", artist = "Artist Two")
    // titleSim = 3/5 = 0.60, artistSim = 1/3 = 0.33 (<0.4), even with Artist One Two: 1/3 or 2/3 -> 0.5*0.6 + 0.3*0.5 + 0.2*0.1 = 0.47 < 0.75
    assertNull(LyricsMatcher.evaluateCandidate(song2, lowTotal))
  }

  @Test
  fun networkErrorIsReportedDistinctlyFromNoLyrics() = runTest {
    val song = Song(
      id = "test_net",
      title = "Any Song",
      artist = "Any Artist",
      artworkUrl = "",
      durationSeconds = 180L,
      streamUrl = "",
      source = "Local",
      fullDurationSeconds = 180L
    )

    val failingApi = object : LrcLibApiService {
      override suspend fun getLyrics(artist: String, track: String, duration: Long): LrcLibResponse? = null
      override suspend fun searchLyrics(query: String): List<LrcLibResponse>? = emptyList()
      override suspend fun searchLyricsByTrackAndArtist(trackName: String, artistName: String): List<LrcLibResponse>? {
        throw IOException("Network timeout")
      }
    }

    val netOutcome = LyricsMatcher.matchWithOutcome(song, api = failingApi)
    assertTrue(netOutcome is LyricsMatchOutcome.NetworkError)

    val emptyApi = object : LrcLibApiService {
      override suspend fun getLyrics(artist: String, track: String, duration: Long): LrcLibResponse? = null
      override suspend fun searchLyrics(query: String): List<LrcLibResponse>? = emptyList()
      override suspend fun searchLyricsByTrackAndArtist(trackName: String, artistName: String): List<LrcLibResponse>? = emptyList()
    }

    val noLyricsOutcome = LyricsMatcher.matchWithOutcome(song, api = emptyApi)
    assertTrue(noLyricsOutcome is LyricsMatchOutcome.NoLyrics)

    val server500Api = object : LrcLibApiService {
      override suspend fun getLyrics(artist: String, track: String, duration: Long): LrcLibResponse? = null
      override suspend fun searchLyrics(query: String): List<LrcLibResponse>? = emptyList()
      override suspend fun searchLyricsByTrackAndArtist(trackName: String, artistName: String): List<LrcLibResponse>? {
        throw HttpException(Response.error<List<LrcLibResponse>>(503, "Unavailable".toResponseBody()))
      }
    }
    assertTrue(LyricsMatcher.matchWithOutcome(song, api = server500Api) is LyricsMatchOutcome.NetworkError)
  }
}
