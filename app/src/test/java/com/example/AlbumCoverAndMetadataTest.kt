package com.example

import com.example.data.AlbumCoverEntity
import com.example.data.AlbumCoverStorage
import com.example.data.AudioMetadataReader
import com.example.model.CustomCoverMode
import com.example.model.Song
import com.example.model.computeAlbumId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlbumCoverAndMetadataTest {

  @Test
  fun stripFileExtension_removesAudioExtensionAndPreservesDots() {
    assertEquals("Midnight City", AudioMetadataReader.stripFileExtension("Midnight City.mp3"))
    assertEquals("track.v2.final", AudioMetadataReader.stripFileExtension("track.v2.final.flac"))
    assertEquals("NoExtension", AudioMetadataReader.stripFileExtension("NoExtension"))
    assertEquals("", AudioMetadataReader.stripFileExtension("   "))
    assertEquals("", AudioMetadataReader.stripFileExtension(null))
  }

  @Test
  fun parseTrackNumber_handlesDiscFormatAndInvalidInput() {
    assertEquals(3, AudioMetadataReader.parseTrackNumber("3"))
    assertEquals(4, AudioMetadataReader.parseTrackNumber("04/12"))
    assertEquals(11, AudioMetadataReader.parseTrackNumber("11 / 17"))
    assertEquals(0, AudioMetadataReader.parseTrackNumber("0"))
    assertEquals(0, AudioMetadataReader.parseTrackNumber("abc"))
    assertEquals(0, AudioMetadataReader.parseTrackNumber(null))
  }

  @Test
  fun parseReleaseYear_extractsFourDigitYearFromDateStrings() {
    assertEquals("2013", AudioMetadataReader.parseReleaseYear("2013"))
    assertEquals("2021", AudioMetadataReader.parseReleaseYear("2021-05-14T00:00:00Z"))
    assertEquals("1998", AudioMetadataReader.parseReleaseYear("Released 1998"))
    assertEquals("", AudioMetadataReader.parseReleaseYear("unknown"))
    assertEquals("", AudioMetadataReader.parseReleaseYear(null))
  }

  @Test
  fun deriveAlbumHeaderFromTracks_usesTagsFallbacksAndCustomCoverOverride() {
    val missingTagsTracks = listOf(
      Song(
        id = "local_1",
        title = "raw_demo",
        artist = "",
        artworkUrl = "",
        durationSeconds = 185L,
        streamUrl = "file:///tmp/raw_demo.mp3",
        source = "Local",
        album = ""
      )
    )
    val unknownHeader = AudioMetadataReader.deriveAlbumHeaderFromTracks(missingTagsTracks)
    assertEquals("Unknown Album", unknownHeader.title)
    assertEquals("Unknown Artist", unknownHeader.artist)
    assertEquals(null, unknownHeader.coverUrl)

    val taggedTracks = listOf(
      Song(
        id = "local_2",
        title = "Track One",
        artist = "Aurora Synth",
        artworkUrl = "file:///embedded/cover1.jpg",
        durationSeconds = 210L,
        streamUrl = "file:///tmp/track1.mp3",
        source = "Local",
        album = "Midnight Signals",
        trackNumber = 1,
        year = "2024"
      )
    )
    val albumId = computeAlbumId("Midnight Signals", "Aurora Synth")
    val customEntity = AlbumCoverEntity(
      albumId = albumId,
      coverPath = "file:///custom/cover.jpg",
      mode = CustomCoverMode.MIX.id,
      sourceUris = "file:///a.jpg\nfile:///b.jpg"
    )
    val customHeader = AudioMetadataReader.deriveAlbumHeaderFromTracks(
      tracks = taggedTracks,
      customCovers = mapOf(albumId to customEntity)
    )
    assertEquals("Midnight Signals", customHeader.title)
    assertEquals("Aurora Synth", customHeader.artist)
    assertEquals("2024", customHeader.year)
    assertEquals("file:///custom/cover.jpg", customHeader.coverUrl)
    assertEquals("file:///embedded/cover1.jpg", customHeader.automaticCoverUrl)
    assertEquals(CustomCoverMode.MIX, customHeader.customCoverMode)
    assertEquals(listOf("file:///a.jpg", "file:///b.jpg"), customHeader.customSourceUris)
  }

  @Test
  fun computeAlbumId_isCaseInsensitiveAndDistinctAcrossAlbums() {
    val id1 = computeAlbumId("Album Title", "Artist Name")
    val id2 = computeAlbumId("  album title ", "artist name")
    val id3 = computeAlbumId("Different Album", "Artist Name")
    assertEquals(id1, id2)
    assertNotEquals(id1, id3)
  }

  @Test
  fun albumCoverStorage_centerCropAndMixGapMathAreValid() {
    val gap = AlbumCoverStorage.computeMixGapPx(1024)
    assertTrue(gap in 20f..28f)

    val rectLandscape = AlbumCoverStorage.computeCenterCropSrcRect(
      srcWidth = 2000,
      srcHeight = 1000,
      dstWidth = 1024f,
      dstHeight = 1024f,
      offsetXFraction = 0f,
      offsetYFraction = 0f,
      zoom = 1f
    )
    assertEquals(500, rectLandscape.left)
    assertEquals(0, rectLandscape.top)
    assertEquals(1500, rectLandscape.right)
    assertEquals(1000, rectLandscape.bottom)

    val zoomedRect = AlbumCoverStorage.computeCenterCropSrcRect(
      srcWidth = 1000,
      srcHeight = 1000,
      dstWidth = 1024f,
      dstHeight = 1024f,
      offsetXFraction = 1f,
      offsetYFraction = -1f,
      zoom = 2f
    )
    assertEquals(500, zoomedRect.width())
    assertEquals(500, zoomedRect.height())
    assertTrue(zoomedRect.left >= 0)
    assertTrue(zoomedRect.top >= 0)
  }
}
