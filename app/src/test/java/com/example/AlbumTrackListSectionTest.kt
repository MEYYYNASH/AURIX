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
import com.example.model.AlbumInfo
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
          albumYear = "2013",
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

    // Banner and header content
    composeTestRule.onNodeWithTag("album_banner").assertExists()
    composeTestRule.onNodeWithText("Album · 3 songs · 2013").assertIsDisplayed()
    composeTestRule.onNodeWithText("Album Title").assertIsDisplayed()
    // Artist name appears in header and as muted second line in rows
    assertTrue(composeTestRule.onAllNodesWithText("Artist Name").fetchSemanticsNodes().isNotEmpty())

    // Action icon accessibility labels
    composeTestRule.onNodeWithContentDescription("Add to library").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(addedToLibrary)

    composeTestRule.onNodeWithContentDescription("Download album").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(downloadTapped)

    composeTestRule.onNodeWithContentDescription("More album options").assertIsDisplayed()

    // Zero-padded track numbers, titles, and durations
    composeTestRule.onNodeWithText("01").assertIsDisplayed()
    composeTestRule.onNodeWithText("Track Title 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("3:12").assertIsDisplayed()
    composeTestRule.onNodeWithText("02").assertIsDisplayed()
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

  @Test
  fun albumEditCoverSheet_mixMax4Guard_andAppIconSaveFlow() {
    val fourArtTracks = sampleTracks + listOf(
      Song(
        id = "album_4",
        title = "Track Title 4",
        artist = "Artist Name",
        artworkUrl = "https://example.com/art4.jpg",
        durationSeconds = 200L,
        streamUrl = "https://example.com/4.m4a",
        source = "Album",
        album = "Album Title",
        trackNumber = 4,
        year = "2013"
      ),
      Song(
        id = "album_5",
        title = "Track Title 5",
        artist = "Artist Name",
        artworkUrl = "https://example.com/art5.jpg",
        durationSeconds = 210L,
        streamUrl = "https://example.com/5.m4a",
        source = "Album",
        album = "Album Title",
        trackNumber = 5,
        year = "2013"
      )
    )
    val info = AlbumInfo(
      albumId = "test_album_id",
      title = "Album Title",
      artist = "Artist Name",
      year = "2013",
      coverUrl = "https://example.com/art1.jpg",
      automaticCoverUrl = "https://example.com/art1.jpg",
      tracks = fourArtTracks
    )

    var savedAppIcon = false

    composeTestRule.setContent {
      AurixTheme(darkTheme = true) {
        AlbumTrackListSection(
          albumInfo = info,
          tracks = fourArtTracks,
          currentSong = null,
          isPlaying = false,
          reducedMotion = true,
          onSaveCoverAppIcon = { savedAppIcon = true }
        )
      }
    }

    composeTestRule.waitForIdle()

    // Open More menu -> Edit cover
    composeTestRule.onNodeWithTag("album_more_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("album_menu_edit_cover").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    // Verify preview square and 3 tabs appear
    composeTestRule.onNodeWithTag("edit_cover_preview_square").assertIsDisplayed()
    composeTestRule.onNodeWithTag("edit_cover_tab_photo").assertIsDisplayed()
    composeTestRule.onNodeWithTag("edit_cover_tab_mix").performClick()
    composeTestRule.waitForIdle()

    // With 4 mix images prefilled from track covers, trying to add from gallery shows "Maximum 4 images"
    composeTestRule.onNodeWithTag("edit_cover_mix_add_gallery").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Maximum 4 images").assertIsDisplayed()

    // Removing slot clears error
    composeTestRule.onNodeWithTag("edit_cover_mix_remove_3").performClick()
    composeTestRule.waitForIdle()

    // Switch to App icon tab and Save
    composeTestRule.onNodeWithTag("edit_cover_tab_app_icon").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("edit_cover_use_app_icon_button").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("edit_cover_save_button").performClick()
    composeTestRule.waitForIdle()

    assertTrue(savedAppIcon)
  }
}
