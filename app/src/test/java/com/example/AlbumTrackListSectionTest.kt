package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.model.AlbumInfo
import com.example.model.Song
import com.example.ui.screens.AlbumTrackListSection
import com.example.ui.screens.LibraryScreen
import com.example.ui.theme.AurixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlbumTrackListSectionTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val sampleTracks = listOf(
    Song(
      id = "album_1",
      title = "Track Title 1",
      artist = "Artist Name",
      artworkUrl = "https://example.com/art1.jpg",
      durationSeconds = 192L,
      streamUrl = "https://example.com/1.m4a",
      source = "Album",
      album = "Album Title",
      trackNumber = 1,
      year = "2013"
    ),
    Song(
      id = "album_2",
      title = "Track Title 2",
      artist = "Artist Name",
      artworkUrl = "https://example.com/art2.jpg",
      durationSeconds = 218L,
      streamUrl = "https://example.com/2.m4a",
      source = "Album",
      album = "Album Title",
      trackNumber = 2,
      year = "2013"
    ),
    Song(
      id = "album_3",
      title = "Track Title 3",
      artist = "Artist Name",
      artworkUrl = "https://example.com/art3.jpg",
      durationSeconds = 174L,
      streamUrl = "https://example.com/3.m4a",
      source = "Album",
      album = "Album Title",
      trackNumber = 3,
      year = "2013"
    )
  )

  @Test
  fun albumAddSongsSheet_allowsSelectingAndAddingSongsToAlbum() {
    val existing = sampleTracks.take(1)
    val extraSong = sampleTracks[1]
    val addedSongs = mutableListOf<Song>()

    composeTestRule.setContent {
      AurixTheme(darkTheme = true) {
        AlbumTrackListSection(
          albumTitle = "Custom Mix",
          artistName = "Artist Name",
          tracks = existing,
          currentSong = null,
          isPlaying = false,
          reducedMotion = true,
          availableSongsToAdd = sampleTracks,
          onAddSongsToAlbum = { chosen -> addedSongs.addAll(chosen) }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("album_add_songs_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("add_songs_row_${extraSong.id}").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("add_songs_confirm_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(extraSong.id), addedSongs.map { it.id })
  }

  @Test
  fun libraryAlbums_holdAlbumCard_showsDeleteFeatureAndDeletesAlbum() {
    val targetAlbum = AlbumInfo(
      albumId = "album_hold_test",
      title = "Hold Test Album",
      artist = "Artist Name",
      year = "2026",
      coverUrl = null,
      automaticCoverUrl = null,
      tracks = sampleTracks
    )
    var deletedAlbumId: String? = null

    composeTestRule.setContent {
      AurixTheme(darkTheme = true) {
        LibraryScreen(
          localSongs = emptyList(),
          favorites = emptyList(),
          recentlyPlayed = emptyList(),
          albums = listOf(targetAlbum),
          currentSong = null,
          isPlaying = false,
          darkTheme = true,
          reducedMotion = true,
          selectedTab = 3,
          onAddLocalSong = {},
          onCustomizeSongPhoto = { _, _ -> },
          onDeleteAlbum = { album, _ -> deletedAlbumId = album.albumId },
          onSongClick = {},
          onFavoriteClick = {}
        )
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("library_album_card_${targetAlbum.albumId}")
      .assertIsDisplayed()
      .performTouchInput { longClick() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("album_hold_delete_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("confirm_delete_album_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    assertEquals(targetAlbum.albumId, deletedAlbumId)
  }
}
