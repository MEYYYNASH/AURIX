package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import com.example.data.AurixDatabase
import com.example.data.FavoriteSongEntity
import com.example.data.StreamHeaderRegistry
import com.example.data.YoutubeExtractor
import com.example.lyrics.LrcParser
import com.example.model.LyricLine
import com.example.model.LyricsResult
import com.example.model.Song
import com.example.widget.AurixWidgetController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {
  private var mediaSession: MediaSession? = null
  private var mainPlayer: ExoPlayer? = null
  private var crossfadeOutPlayer: ExoPlayer? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var monitorJob: Job? = null
  private var widgetTickerJob: Job? = null
  private var crossfadeJob: Job? = null
  private var skipFadeJob: Job? = null
  private var recoveryJob: Job? = null
  private var isCrossfadingNow = false
  private var reclaimRecoveryCount = 0

  private val playbackAudioAttributes: AudioAttributes by lazy {
    AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .build()
  }

  @OptIn(UnstableApi::class)
  private fun buildExoPlayer(handleAudioFocus: Boolean): ExoPlayer {
    val renderersFactory = if (handleAudioFocus) {
      object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(
          context: Context,
          enableFloatOutput: Boolean,
          enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
          return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(PlaybackAudioSpectrumProcessor()))
            .build()
        }
      }.setEnableDecoderFallback(true)
    } else {
      DefaultRenderersFactory(this)
        .setEnableDecoderFallback(true)
    }

    val httpFactory = DefaultHttpDataSource.Factory()
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(15_000)
      .setReadTimeoutMs(25_000)
    val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)
    val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
      runCatching {
        val url = dataSpec.uri.toString()
        val ua = StreamHeaderRegistry.resolveUserAgent(url)
          ?: YoutubeExtractor.inferPrimaryUserAgentForUrl(url)
        if (!ua.isNullOrBlank()) {
          dataSpec.withAdditionalHeaders(
            mapOf(
              "User-Agent" to ua,
              "Accept" to "*/*"
            )
          )
        } else {
          dataSpec
        }
      }.getOrDefault(dataSpec)
    }

    return ExoPlayer.Builder(this, renderersFactory)
      .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
      .setAudioAttributes(playbackAudioAttributes, handleAudioFocus)
      .apply {
        if (handleAudioFocus) {
          setHandleAudioBecomingNoisy(true)
        }
      }
      .build()
  }

  @OptIn(UnstableApi::class)
  override fun onCreate() {
    super.onCreate()

    val player = runCatching { buildExoPlayer(handleAudioFocus = true) }.getOrNull() ?: return
    mainPlayer = player

    player.addListener(object : Player.Listener {
      override fun onAudioSessionIdChanged(audioSessionId: Int) {
        runCatching { AudioEqualizerController.attachAudioSession(audioSessionId) }
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) {
          AudioSpectrumBus.reset()
        }
        updateWidgetPlayStateAndTicker(player, isPlaying)
        runCatching {
          val fadeOut = crossfadeOutPlayer
          if (!isPlaying && isCrossfadingNow && fadeOut != null) {
            fadeOut.pause()
          } else if (isPlaying && isCrossfadingNow && fadeOut != null && fadeOut.playbackState == Player.STATE_READY) {
            fadeOut.play()
          } else if (!isPlaying && !isCrossfadingNow && fadeOut != null) {
            releaseCrossfadeOutPlayer()
          }
        }
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
          AudioSpectrumBus.reset()
        }
        runCatching {
          if (playbackState == Player.STATE_READY) {
            reclaimRecoveryCount = 0
          } else if ((playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) && !isCrossfadingNow) {
            releaseCrossfadeOutPlayer()
          }
        }
      }

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        runCatching {
          reclaimRecoveryCount = 0
          if (!isCrossfadingNow && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            val settings = CrossfadeController.state.value
            if (settings.durationSeconds > 0 && settings.fadeOnManualSkip) {
              runManualSkipFadeIn(player)
            } else {
              player.volume = 1f
            }
          }
        }
        if (mediaItem != null) {
          syncMediaItemToWidgets(player, mediaItem)
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        runCatching {
          if (isRecoverableCodecError(error) && reclaimRecoveryCount < MAX_CODEC_RECOVERY_ATTEMPTS) {
            reclaimRecoveryCount += 1
            abortCrossfadeAndRestoreVolume(player)

            val itemCount = player.mediaItemCount
            if (itemCount <= 0) return
            val savedIndex = player.currentMediaItemIndex
              .takeIf { it != C.INDEX_UNSET && it in 0 until itemCount }
              ?: 0
            val savedPosition = player.currentPosition.coerceAtLeast(0L)
            val shouldPlay = player.playWhenReady

            recoveryJob?.cancel()
            recoveryJob = serviceScope.launch {
              delay(CODEC_RECOVERY_DELAY_MS)
              runCatching {
                val activePlayer = mainPlayer ?: return@launch
                if (activePlayer.mediaItemCount > 0) {
                  val targetIndex = savedIndex.coerceIn(0, activePlayer.mediaItemCount - 1)
                  activePlayer.seekTo(targetIndex, savedPosition)
                  activePlayer.prepare()
                  activePlayer.playWhenReady = shouldPlay
                }
              }
            }
          } else {
            abortCrossfadeAndRestoreVolume(player)
            runCatching {
              player.stop()
              player.clearMediaItems()
            }
          }
        }
      }
    })
    runCatching {
      if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET && player.audioSessionId > 0) {
        AudioEqualizerController.attachAudioSession(player.audioSessionId)
      }
    }

    runCatching {
      val launchIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val sessionActivityPendingIntent = PendingIntent.getActivity(
        this,
        0,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
        .build()
        .apply {
          setSmallIcon(R.drawable.ic_stat_aurix)
        }
      setMediaNotificationProvider(notificationProvider)

      mediaSession = MediaSession.Builder(this, player)
        .setSessionActivity(sessionActivityPendingIntent)
        .build()
    }

    startCrossfadeMonitor()
  }

  @OptIn(UnstableApi::class)
  private fun isRecoverableCodecError(error: PlaybackException): Boolean {
    when (error.errorCode) {
      PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
      PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
      PlaybackException.ERROR_CODE_DECODING_FAILED,
      PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
      PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> return true
    }
    var cause: Throwable? = error.cause
    var depth = 0
    while (cause != null && depth < 8) {
      if (cause is MediaCodec.CodecException) return true
      cause = cause.cause
      depth++
    }
    return false
  }

  private fun abortCrossfadeAndRestoreVolume(player: ExoPlayer) {
    skipFadeJob?.cancel()
    crossfadeJob?.cancel()
    releaseCrossfadeOutPlayer()
    runCatching { player.volume = 1f }
  }

  private fun releaseCrossfadeOutPlayer() {
    val fadeOut = crossfadeOutPlayer
    crossfadeOutPlayer = null
    if (fadeOut != null) {
      runCatching { fadeOut.volume = 0f }
      runCatching { fadeOut.stop() }
      runCatching { fadeOut.clearMediaItems() }
      runCatching { fadeOut.release() }
    }
    isCrossfadingNow = false
    CrossfadeController.setCrossfadingActive(false)
  }

  private fun startCrossfadeMonitor() {
    monitorJob?.cancel()
    monitorJob = serviceScope.launch {
      while (isActive) {
        runCatching {
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
        }
        delay(100L)
      }
    }
  }

  private fun triggerCrossfade(player: ExoPlayer, fadeDurationMs: Long) {
    runCatching {
      val outgoingItem = player.currentMediaItem ?: return
      val outgoingPosition = player.currentPosition.coerceAtLeast(0L)
      val startVolume = player.volume.coerceIn(0.1f, 1f)

      skipFadeJob?.cancel()
      crossfadeJob?.cancel()
      releaseCrossfadeOutPlayer()

      val fadeOut = buildExoPlayer(handleAudioFocus = false)
      crossfadeOutPlayer = fadeOut
      isCrossfadingNow = true
      CrossfadeController.setCrossfadingActive(true)

      fadeOut.addListener(object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
          crossfadeJob?.cancel()
          runCatching { player.volume = 1f }
          releaseCrossfadeOutPlayer()
        }
      })

      try {
        // Hand outgoing track to fadeOut player at exact timestamp
        fadeOut.setMediaItem(outgoingItem, outgoingPosition)
        fadeOut.playbackParameters = player.playbackParameters
        fadeOut.volume = startVolume
        fadeOut.prepare()
        fadeOut.play()
      } catch (_: Exception) {
        runCatching { player.volume = 1f }
        releaseCrossfadeOutPlayer()
        return
      }

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
            runCatching {
              if (player.isPlaying) {
                val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                val (outVol, inVol) = CrossfadeController.equalPowerGains(progress)
                crossfadeOutPlayer?.volume = (outVol * startVolume).coerceIn(0f, 1f)
                player.volume = inVol.coerceIn(0.02f, 1f)
              }
            }
            elapsed += stepMs
            delay(stepMs)
          }
        } finally {
          runCatching { player.volume = 1f }
          releaseCrossfadeOutPlayer()
        }
      }
    }.onFailure {
      runCatching { player.volume = 1f }
      releaseCrossfadeOutPlayer()
    }
  }

  private fun runManualSkipFadeIn(player: ExoPlayer) {
    skipFadeJob?.cancel()
    skipFadeJob = serviceScope.launch {
      val durationMs = 340L
      val stepMs = 24L
      var elapsed = 0L
      runCatching { player.volume = 0.15f }
      while (isActive && elapsed < durationMs) {
        val progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        runCatching { player.volume = (0.15f + 0.85f * progress).coerceIn(0f, 1f) }
        delay(stepMs)
        elapsed += stepMs
      }
      runCatching { player.volume = 1f }
    }
  }

  private fun updateWidgetPlayStateAndTicker(player: ExoPlayer, isPlaying: Boolean) {
    widgetTickerJob?.cancel()
    widgetTickerJob = null

    val snapshot = AurixWidgetController.readSnapshot(this)
    if (snapshot.songId.isNotBlank()) {
      val pos = player.currentPosition.coerceAtLeast(0L)
      val dur = player.duration.takeIf { it > 0L } ?: snapshot.durationMs
      if (isPlaying) {
        AurixWidgetController.onPlaybackProgressTick(this, pos, dur, isPlaying = true)
      } else {
        // Persist paused state and update Play/Pause icon once, then stop ticker to save battery
        val prefs = applicationContext.getSharedPreferences("aurix_widget_snapshot_prefs", Context.MODE_PRIVATE)
        prefs.edit()
          .putBoolean("is_playing", false)
          .putLong("position_ms", pos)
          .apply()
        AurixWidgetController.refreshAllWidgetsAsync(this, refreshMusic = true, refreshLyrics = false)
      }
    }

    if (isPlaying) {
      widgetTickerJob = serviceScope.launch {
        while (isActive) {
          delay(1_000L)
          val active = mainPlayer ?: break
          if (!active.isPlaying) break
          val pos = active.currentPosition.coerceAtLeast(0L)
          val dur = active.duration.takeIf { it > 0L } ?: 0L
          AurixWidgetController.onPlaybackProgressTick(
            context = this@PlaybackService,
            positionMs = pos,
            durationMs = dur,
            isPlaying = true
          )
        }
      }
    }
  }

  private fun syncMediaItemToWidgets(player: ExoPlayer, mediaItem: MediaItem) {
    val songId = mediaItem.mediaId.takeIf { it.isNotBlank() } ?: return
    val meta = mediaItem.mediaMetadata
    val title = meta.title?.toString()?.takeIf { it.isNotBlank() } ?: return
    val artist = meta.artist?.toString()?.takeIf { it.isNotBlank() } ?: "AURIX"
    val artworkUrl = meta.artworkUri?.toString().orEmpty()
    val streamUrl = mediaItem.localConfiguration?.uri?.toString().orEmpty()
    val isPlayingNow = player.isPlaying
    val posMs = player.currentPosition.coerceAtLeast(0L)
    val durMs = player.duration.takeIf { it > 0L } ?: 0L

    serviceScope.launch {
      val (isFav, lyricsResult, offsetMs) = withContext(Dispatchers.IO) {
        runCatching {
          val dao = AurixDatabase.getInstance(applicationContext).aurixDao()
          val fav = dao.getFavoriteById(songId) != null
          val cached = dao.getLyricsCache(songId)
          val offset = dao.getLyricOffset(songId) ?: 0L
          val lyrics = if (cached != null && cached.hasLyrics && !cached.isReportedBad) {
            val rawLrc = cached.rawLrc.orEmpty()
            val synced = if (rawLrc.isNotBlank()) {
              val parsed = LrcParser.parse(rawLrc)
              if (parsed.isSynced) parsed.lines.map { LyricLine(it.timestampMs, it.text) } else null
            } else null
            LyricsResult(
              synced = synced,
              plain = cached.plainLyrics,
              matchedTitle = cached.matchedTitle,
              matchedArtist = cached.matchedArtist,
              rawLrc = cached.rawLrc
            )
          } else null
          Triple(fav, lyrics, offset)
        }.getOrDefault(Triple(false, null, 0L))
      }

      val song = Song(
        id = songId,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        durationSeconds = (durMs / 1000L).coerceAtLeast(0L),
        streamUrl = streamUrl,
        isFavorite = isFav
      )
      AurixWidgetController.syncTrackAndLyricsState(
        context = this@PlaybackService,
        song = song,
        isPlaying = isPlayingNow,
        positionMs = posMs,
        durationMs = durMs,
        lyricsResult = lyricsResult,
        lyricOffsetMs = offsetMs
      )
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action
    if (!action.isNullOrBlank()) {
      handleWidgetAction(action)
    }
    return super.onStartCommand(intent, flags, startId)
  }

  private fun handleWidgetAction(action: String) {
    val player = mediaSession?.player ?: mainPlayer ?: return
    when (action) {
      AurixWidgetController.ACTION_WIDGET_PLAY_PAUSE -> {
        runCatching {
          if (player.isPlaying) {
            player.pause()
          } else {
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
              player.prepare()
            } else if (player.playbackState == Player.STATE_ENDED) {
              player.seekTo(0L)
            }
            player.play()
          }
        }
      }
      AurixWidgetController.ACTION_WIDGET_PREV -> {
        runCatching {
          if (player.currentPosition > 3000L) {
            player.seekTo(0L)
          } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            if (player.playbackState == Player.STATE_IDLE) {
              player.prepare()
            }
            player.play()
          } else if (player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
            player.play()
          }
        }
      }
      AurixWidgetController.ACTION_WIDGET_NEXT -> {
        runCatching {
          if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            if (player.playbackState == Player.STATE_IDLE) {
              player.prepare()
            }
            player.play()
          } else if (player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
            player.play()
          }
        }
      }
      AurixWidgetController.ACTION_WIDGET_LIKE -> {
        val snap = AurixWidgetController.readSnapshot(this)
        val songId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: snap.songId
        if (songId.isBlank()) return
        serviceScope.launch {
          val nowFav = withContext(Dispatchers.IO) {
            runCatching {
              val dao = AurixDatabase.getInstance(applicationContext).aurixDao()
              val existing = dao.getFavoriteById(songId)
              if (existing != null) {
                dao.deleteFavorite(songId)
                false
              } else {
                val meta = player.currentMediaItem?.mediaMetadata
                dao.insertFavorite(
                  FavoriteSongEntity(
                    id = songId,
                    title = meta?.title?.toString()?.takeIf { it.isNotBlank() } ?: snap.title.ifBlank { "Track" },
                    artist = meta?.artist?.toString()?.takeIf { it.isNotBlank() } ?: snap.artist.ifBlank { "Artist" },
                    artworkUrl = meta?.artworkUri?.toString()?.takeIf { it.isNotBlank() } ?: snap.artworkUrl,
                    durationSeconds = (snap.durationMs / 1000L).coerceAtLeast(0L),
                    streamUrl = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty(),
                    source = "Widget"
                  )
                )
                true
              }
            }.getOrDefault(!snap.isFavorite)
          }
          AurixWidgetController.syncFavoriteState(this@PlaybackService, songId, nowFav)
        }
      }
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
    widgetTickerJob?.cancel()
    crossfadeJob?.cancel()
    skipFadeJob?.cancel()
    recoveryJob?.cancel()
    serviceScope.cancel()
    releaseCrossfadeOutPlayer()
    AudioSpectrumBus.reset()
    runCatching { AudioEqualizerController.releaseSession() }
    runCatching {
      mediaSession?.run {
        runCatching { player.release() }
        runCatching { release() }
        mediaSession = null
      }
    }
    mainPlayer = null
    super.onDestroy()
  }

  private companion object {
    const val MAX_CODEC_RECOVERY_ATTEMPTS = 2
    const val CODEC_RECOVERY_DELAY_MS = 200L
  }
}
