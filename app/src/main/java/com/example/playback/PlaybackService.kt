package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
  private var mediaSession: MediaSession? = null
  private var mainPlayer: ExoPlayer? = null
  private var crossfadeOutPlayer: ExoPlayer? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var monitorJob: Job? = null
  private var crossfadeJob: Job? = null
  private var skipFadeJob: Job? = null
  private var isCrossfadingNow = false

  @OptIn(UnstableApi::class)
  override fun onCreate() {
    super.onCreate()
    val audioAttributes = AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .build()

    val player = ExoPlayer.Builder(this)
      .setAudioAttributes(audioAttributes, true)
      .setHandleAudioBecomingNoisy(true)
      .build()
    mainPlayer = player

    // Secondary player for outgoing track overlap (must NOT steal AudioFocus from primary player)
    val fadeOut = ExoPlayer.Builder(this)
      .setAudioAttributes(audioAttributes, false)
      .build()
    crossfadeOutPlayer = fadeOut

    player.addListener(object : Player.Listener {
      override fun onAudioSessionIdChanged(audioSessionId: Int) {
        AudioEqualizerController.attachAudioSession(audioSessionId)
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying && isCrossfadingNow) {
          fadeOut.pause()
        } else if (isPlaying && isCrossfadingNow && fadeOut.playbackState == Player.STATE_READY) {
          fadeOut.play()
        }
      }

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!isCrossfadingNow && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
          val settings = CrossfadeController.state.value
          if (settings.durationSeconds > 0 && settings.fadeOnManualSkip) {
            runManualSkipFadeIn(player)
          } else {
            player.volume = 1f
          }
        }
      }
    })
    if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET && player.audioSessionId > 0) {
      AudioEqualizerController.attachAudioSession(player.audioSessionId)
    }

    val launchIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val sessionActivityPendingIntent = PendingIntent.getActivity(
      this,
      0,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    mediaSession = MediaSession.Builder(this, player)
      .setSessionActivity(sessionActivityPendingIntent)
      .build()

    startCrossfadeMonitor()
  }

  private fun startCrossfadeMonitor() {
    monitorJob?.cancel()
    monitorJob = serviceScope.launch {
      while (isActive) {
        val player = mainPlayer
        val settings = CrossfadeController.state.value
        if (
          player != null &&
          !isCrossfadingNow &&
          settings.durationSeconds > 0 &&
          player.isPlaying &&
          player.playbackState == Player.STATE_READY &&
          player.repeatMode != Player.REPEAT_MODE_ONE &&
          player.hasNextMediaItem()
        ) {
          val duration = player.duration
          val position = player.currentPosition
          if (duration > 4000L && position >= 0L) {
            val maxSafeFadeMs = (duration / 2L).coerceAtLeast(1000L)
            val targetFadeMs = (settings.durationSeconds * 1000L).coerceAtMost(maxSafeFadeMs)
            val remaining = duration - position
            if (remaining in 180L..targetFadeMs) {
              triggerCrossfade(player, remaining)
            }
          }
        }
        delay(100L)
      }
    }
  }

  private fun triggerCrossfade(player: ExoPlayer, fadeDurationMs: Long) {
    val fadeOut = crossfadeOutPlayer ?: return
    val outgoingItem = player.currentMediaItem ?: return
    val outgoingPosition = player.currentPosition.coerceAtLeast(0L)
    val startVolume = player.volume.coerceIn(0.1f, 1f)

    skipFadeJob?.cancel()
    crossfadeJob?.cancel()
    isCrossfadingNow = true
    CrossfadeController.setCrossfadingActive(true)

    // Hand outgoing track to fadeOut player at exact timestamp
    fadeOut.stop()
    fadeOut.clearMediaItems()
    fadeOut.setMediaItem(outgoingItem, outgoingPosition)
    fadeOut.playbackParameters = player.playbackParameters
    fadeOut.volume = startVolume
    fadeOut.prepare()
    fadeOut.play()

    // Start incoming track on primary MediaSession player
    player.volume = 0.02f
    player.seekToNextMediaItem()
    player.play()

    crossfadeJob = serviceScope.launch {
      val stepMs = 33L
      val totalDuration = fadeDurationMs.coerceAtLeast(400L)
      var elapsed = 0L
      try {
        while (isActive && elapsed < totalDuration) {
          if (player.isPlaying) {
            val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
            val (outVol, inVol) = CrossfadeController.equalPowerGains(progress)
            fadeOut.volume = (outVol * startVolume).coerceIn(0f, 1f)
            player.volume = inVol.coerceIn(0.02f, 1f)
            elapsed += stepMs
          }
          delay(stepMs)
        }
      } finally {
        player.volume = 1f
        fadeOut.volume = 0f
        fadeOut.stop()
        fadeOut.clearMediaItems()
        isCrossfadingNow = false
        CrossfadeController.setCrossfadingActive(false)
      }
    }
  }

  private fun runManualSkipFadeIn(player: ExoPlayer) {
    skipFadeJob?.cancel()
    skipFadeJob = serviceScope.launch {
      val durationMs = 340L
      val stepMs = 24L
      var elapsed = 0L
      player.volume = 0.15f
      while (isActive && elapsed < durationMs) {
        val progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        player.volume = (0.15f + 0.85f * progress).coerceIn(0f, 1f)
        delay(stepMs)
        elapsed += stepMs
      }
      player.volume = 1f
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
    return mediaSession
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    val player = mediaSession?.player
    if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
      stopSelf()
    }
  }

  override fun onDestroy() {
    monitorJob?.cancel()
    crossfadeJob?.cancel()
    skipFadeJob?.cancel()
    serviceScope.cancel()
    CrossfadeController.setCrossfadingActive(false)
    AudioEqualizerController.releaseSession()
    crossfadeOutPlayer?.release()
    crossfadeOutPlayer = null
    mediaSession?.run {
      player.release()
      release()
      mediaSession = null
    }
    mainPlayer = null
    super.onDestroy()
  }
}
