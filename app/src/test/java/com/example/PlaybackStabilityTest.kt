package com.example

import android.app.Application
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.lyrics.ParsedLyricLine
import com.example.lyrics.ParsedLyricsDocument
import com.example.lyrics.SyncedLyricsAdapter
import com.example.viewmodel.AurixViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackStabilityTest {

  @Test
  fun syncedLyricsAdapter_safeNotificationAndStyleChanges() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val adapter = SyncedLyricsAdapter {}

    val doc = ParsedLyricsDocument(
      lines = listOf(
        ParsedLyricLine(0L, 1000L, "Hello world", emptyList(), intArrayOf(0, 11)),
        ParsedLyricLine(1000L, 2000L, "Aurix music player", emptyList(), intArrayOf(0, 18))
      ),
      isSynced = true,
      offsetMs = 0L
    )

    adapter.submitDocument(doc)
    assertEquals(2, adapter.itemCount)

    adapter.setStyle(Color.RED, Color.WHITE)
    assertEquals(Color.RED, adapter.getAccentColor())

    adapter.setActiveIndex(0)
    assertEquals(0, adapter.getActiveIndex())

    adapter.setActiveIndex(1)
    assertEquals(1, adapter.getActiveIndex())

    adapter.clear()
    assertEquals(0, adapter.itemCount)
  }

  @Test
  fun aurixViewModel_playbackStartupAndStateStability() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AurixViewModel(app)

    val initialized = withTimeout(5_000L) {
      viewModel.isAppInitialized.first { it }
    }
    assertTrue(initialized)
    assertEquals(false, viewModel.isPlaying.value)
    assertEquals(0L, viewModel.currentPosition.value)
  }
}
