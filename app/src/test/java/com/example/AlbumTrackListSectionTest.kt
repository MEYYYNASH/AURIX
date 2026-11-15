package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.model.Song
import com.example.ui.screens.AlbumTrackListSection
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
    Song("album_1", "Track Title 1", "Artist Name", "", 192L, "https://example.com/1.m4a", "Album"),
    Song("album_2", "Track Title 2", "Artist Name", "", 218L, "https://example.com/2.m4a", "Album"),
    Song("album_3", "Track Title 3", "Artist Name", "", 174L, "https://example.com/3.m4a", "Album")
  )

  @Test
  fun albumHeader_actionIcons_playShuffle_andTrackRows_renderAndInteractCorrectly() {
    var currentSong by mutableStateOf<Song?>(null)
    var isPlaying by mutableStateOf(false)
    var isShuffle by mutableStateOf(false)
    var addedToLibrary by mutableStateOf(false)
    var downloadTapped = false

    composeTestRule.setContent {
      AurixTheme(darkTheme = true) {
        AlbumTrackListSection(
          albumTitle = "Album Title",
          artistName = "Artist Name",
          coverUrl = null,
          tracks = sampleTracks,
          currentSong = currentSong,
          isPlaying = isPlaying,
          isShuffleEnabled = isShuffle,
          isAddedToLibrary = addedToLibrary,
          reducedMotion = true,
          onAddToLibraryClick = { addedToLibrary = !addedToLibrary },
          onDownloadAlbumClick = { downloadTapped = true },
          onPlayClick = {
            isShuffle = false
            currentSong = sampleTracks.first()
            isPlaying = true
          },
          onShuffleClick = {
            isShuffle = true
            currentSong = sampleTracks.last()
            isPlaying = true
          },
          onTrackClick = { song ->
            if (currentSong?.id == song.id) {
              isPlaying = !isPlaying
            } else {
              currentSong = song
              isPlaying = true
            }
          }
        )
      }
    }

    composeTestRule.waitForIdle()

    // Header content & placeholder names only (artist appears once in header, not repeated on rows)
    composeTestRule.onNodeWithText("Album, 3 songs").assertIsDisplayed()
    composeTestRule.onNodeWithText("Album Title").assertIsDisplayed()
    assertEquals(1, composeTestRule.onAllNodesWithText("Artist Name").fetchSemanticsNodes().size)

    // Action icon accessibility labels
    composeTestRule.onNodeWithContentDescription("Add to library").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(addedToLibrary)

    composeTestRule.onNodeWithContentDescription("Download album").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(downloadTapped)

    composeTestRule.onNodeWithContentDescription("More album options").assertIsDisplayed()

    // Track rows render title + formatted duration
    composeTestRule.onNodeWithText("Track Title 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("3:12").assertIsDisplayed()
    composeTestRule.onNodeWithText("Track Title 2").assertIsDisplayed()
    composeTestRule.onNodeWithText("3:38").assertIsDisplayed()

    // Play starts from track 1
    composeTestRule.onNodeWithTag("album_play_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("album_1", currentSong?.id)
    assertTrue(isPlaying)
    assertFalse(isShuffle)

    // Tapping currently playing row toggles pause
    composeTestRule.onNodeWithTag("album_track_row_album_1").performClick()
    composeTestRule.waitForIdle()
    assertFalse(isPlaying)

    // Tapping it again resumes playback
    composeTestRule.onNodeWithTag("album_track_row_album_1").performClick()
    composeTestRule.waitForIdle()
    assertTrue(isPlaying)

    // Tapping another row plays it
    composeTestRule.onNodeWithTag("album_track_row_album_2").performClick()
    composeTestRule.waitForIdle()
    assertEquals("album_2", currentSong?.id)
    assertTrue(isPlaying)

    // Shuffle enables shuffle mode and picks a track
    composeTestRule.onNodeWithTag("album_shuffle_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(isShuffle)
    assertEquals("album_3", currentSong?.id)
    assertTrue(isPlaying)
  }
}
