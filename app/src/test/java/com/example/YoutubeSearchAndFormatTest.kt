package com.example

import com.example.data.StreamHeaderRegistry
import com.example.data.YoutubeExtractor
import com.example.model.AudioFormatOption
import com.example.model.SearchMode
import com.example.model.Song
import com.example.model.YoutubeAnalysisResult
import com.example.model.YoutubeDownloadState
import com.example.model.YoutubeFormatSheetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSearchAndFormatTest {

  @Test
  fun extractVideoId_handlesWatchShortMusicAndLiveUrls() {
    assertEquals("dQw4w9WgXcQ", YoutubeExtractor.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertEquals("dQw4w9WgXcQ", YoutubeExtractor.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
    assertEquals("dQw4w9WgXcQ", YoutubeExtractor.extractVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"))
    assertEquals("dQw4w9WgXcQ", YoutubeExtractor.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
    assertNull(YoutubeExtractor.extractVideoId("not a url"))
  }

  @Test
  fun parseDurationTextSeconds_parsesMinutesAndHours() {
    assertEquals(213L, YoutubeExtractor.parseDurationTextSeconds("3:33"))
    assertEquals(3661L, YoutubeExtractor.parseDurationTextSeconds("1:01:01"))
    assertEquals(0L, YoutubeExtractor.parseDurationTextSeconds(""))
    assertEquals(0L, YoutubeExtractor.parseDurationTextSeconds("LIVE"))
  }

  @Test
  fun inferPrimaryUserAgentForUrl_matchesClientParamAndSkipsNonYoutubeHosts() {
    val iosUa = YoutubeExtractor.inferPrimaryUserAgentForUrl(
      "https://rr1---sn-abc.googlevideo.com/videoplayback?itag=140&c=IOS"
    )
    assertNotNull(iosUa)
    assertTrue(iosUa!!.contains("iPhone") || iosUa.contains("iOS"))

    val vrUa = YoutubeExtractor.inferPrimaryUserAgentForUrl(
      "https://rr2---sn-xyz.googlevideo.com/videoplayback?itag=140&c=ANDROID_VR"
    )
    assertNotNull(vrUa)
    assertTrue(vrUa!!.contains("vr.oculus"))

    assertNull(YoutubeExtractor.inferPrimaryUserAgentForUrl("https://audio-ssl.itunes.apple.com/preview.m4a"))
  }

  @Test
  fun streamHeaderRegistry_registersAndResolvesValidEntries() {
    val url = "https://rr3---sn-test.googlevideo.com/videoplayback?id=test123"
    val ua = "com.google.ios.youtube/20.10.4"
    StreamHeaderRegistry.register(url, ua)
    assertEquals(ua, StreamHeaderRegistry.resolveUserAgent(url))

    StreamHeaderRegistry.register("", ua)
    assertNull(StreamHeaderRegistry.resolveUserAgent(""))

    StreamHeaderRegistry.register("https://example.com/no_ua.m4a", null)
    assertNull(StreamHeaderRegistry.resolveUserAgent("https://example.com/no_ua.m4a"))
  }

  @Test
  fun searchModeAndFormatSheetState_holdExpectedValues() {
    assertEquals(listOf(SearchMode.YouTubeSearch, SearchMode.YouTubeLink, SearchMode.ITunes30s), SearchMode.entries)

    val song = Song(
      id = "yt_abc12345678",
      title = "Night Drive",
      artist = "Artist",
      artworkUrl = "https://i.ytimg.com/vi/abc12345678/hqdefault.jpg",
      durationSeconds = 205L,
      streamUrl = "https://www.youtube.com/watch?v=abc12345678",
      source = "YouTube"
    )
    val format = AudioFormatOption(
      id = "itag_140",
      formatLabel = "M4A",
      qualityLabel = "128 kbps • High",
      detailLabel = "audio/mp4 • 3.2 MB",
      extension = "m4a",
      mimeType = "audio/mp4",
      streamUrl = "https://rr1---sn.googlevideo.com/videoplayback?itag=140",
      durationSeconds = 205L
    )
    val analysis = YoutubeAnalysisResult(
      videoId = "abc12345678",
      originalUrl = song.streamUrl,
      videoTitle = "Artist - Night Drive",
      channelName = "Artist",
      thumbnailUrl = song.artworkUrl,
      matchedTrackTitle = song.title,
      matchedArtist = song.artist,
      durationSeconds = 205L,
      formats = listOf(format)
    )
    val ready = YoutubeFormatSheetState.Ready(
      song = song,
      analysis = analysis,
      selectedFormatId = format.id,
      downloadState = YoutubeDownloadState.Idle
    )
    assertEquals("itag_140", ready.selectedFormatId)
    assertEquals(1, ready.analysis.formats.size)
  }
}
