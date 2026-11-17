package com.example.data

import android.content.Context
import com.example.model.APP_ICON_ARTWORK
import com.example.model.AudioFormatOption
import com.example.model.Song
import com.example.model.YoutubeAnalysisResult
import com.example.model.YoutubeDownloadState
import com.example.recommendation.TrackMetadataClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrackDownloadNotificationInfo(
  val key: String,
  val title: String,
  val artist: String,
  val fraction: Float,
  val statusHint: String = ""
)

data class DownloadFeedbackEvent(
  val message: String,
  val isError: Boolean = false
)

object BackgroundDownloadRegistry {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _songDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  val songDownloadProgress: StateFlow<Map<String, Float>> = _songDownloadProgress.asStateFlow()

  private val _downloadedSongIds = MutableStateFlow<Set<String>>(emptySet())
  val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds.asStateFlow()

  private val _activeTrackNotifications = MutableStateFlow<Map<String, TrackDownloadNotificationInfo>>(emptyMap())
  val activeTrackNotifications: StateFlow<Map<String, TrackDownloadNotificationInfo>> = _activeTrackNotifications.asStateFlow()

  // Keyed by analysis videoId so both YoutubeLinkGlassPane and YoutubeFormatPickerSheet remain synced
  private val _videoDownloadStates = MutableStateFlow<Map<String, YoutubeDownloadState>>(emptyMap())
  val videoDownloadStates: StateFlow<Map<String, YoutubeDownloadState>> = _videoDownloadStates.asStateFlow()

  private val _feedbackEvents = MutableSharedFlow<DownloadFeedbackEvent>(extraBufferCapacity = 16)
  val feedbackEvents: SharedFlow<DownloadFeedbackEvent> = _feedbackEvents.asSharedFlow()

  fun markDownloadedSongId(songId: String) {
    _downloadedSongIds.update { it + songId }
  }

  fun clearVideoDownloadState(videoId: String) {
    _videoDownloadStates.update { it - videoId }
  }

  fun hasActiveSongDownloads(): Boolean = _activeTrackNotifications.value.isNotEmpty()

  fun enqueueSongDownload(context: Context, song: Song) {
    val appContext = context.applicationContext
    if (_songDownloadProgress.value.containsKey(song.id)) return

    _songDownloadProgress.update { it + (song.id to 0.05f) }
    _activeTrackNotifications.update {
      it + (song.id to TrackDownloadNotificationInfo(song.id, song.title, song.artist, 0.05f))
    }
    BackgroundDownloadService.startSync(appContext)
    _feedbackEvents.tryEmit(DownloadFeedbackEvent("Downloading in background: ${song.title}"))

    scope.launch {
      try {
        val result = SongDownloader.downloadSongToDevice(appContext, song) { progress ->
          _songDownloadProgress.update { it + (song.id to progress) }
          _activeTrackNotifications.update { current ->
            val existing = current[song.id]
            if (existing != null) {
              current + (song.id to existing.copy(fraction = progress))
            } else {
              current
            }
          }
        }

        _songDownloadProgress.update { it - song.id }
        _activeTrackNotifications.update { it - song.id }

        result.onSuccess { savedAudio ->
          _downloadedSongIds.update { it + song.id }
          val cachedCover = SongDownloader.cacheRemoteArtwork(appContext, savedAudio.uri.toString(), song.artworkUrl)
          val entity = LocalSongEntity(
            id = savedAudio.uri.toString(),
            title = song.title,
            artist = song.artist,
            artworkUrl = cachedCover ?: song.artworkUrl.ifBlank { APP_ICON_ARTWORK },
            durationSeconds = savedAudio.durationSeconds.takeIf { it > 0L } ?: song.durationSeconds,
            streamUrl = savedAudio.uri.toString(),
            source = "Downloaded"
          )
          AurixDatabase.getInstance(appContext).aurixDao().insertLocalSong(entity)
          AppNotificationHelper.notifySongDownloadComplete(appContext, song.id, song.title, song.artist)
          _feedbackEvents.tryEmit(DownloadFeedbackEvent("Saved to My songs & Music/AURIX: ${song.title}"))
        }.onFailure { err ->
          val msg = err.localizedMessage ?: "Download failed"
          AppNotificationHelper.notifySongDownloadFailed(appContext, song.id, song.title, msg)
          _feedbackEvents.tryEmit(DownloadFeedbackEvent(msg, isError = true))
        }
      } catch (e: Exception) {
        _songDownloadProgress.update { it - song.id }
        _activeTrackNotifications.update { it - song.id }
        val msg = e.localizedMessage ?: "Download failed"
        AppNotificationHelper.notifySongDownloadFailed(appContext, song.id, song.title, msg)
        _feedbackEvents.tryEmit(DownloadFeedbackEvent(msg, isError = true))
      }
    }
  }

  fun enqueueFormatDownload(
    context: Context,
    songIdForBadge: String,
    analysis: YoutubeAnalysisResult,
    initialFormat: AudioFormatOption,
    isFavoriteProvider: (String) -> Boolean = { false }
  ) {
    val appContext = context.applicationContext
    val currentState = _videoDownloadStates.value[analysis.videoId]
    if (currentState is YoutubeDownloadState.Downloading) return

    val notifKey = "yt_fmt_${analysis.videoId}"
    var activeFormat = initialFormat
    var formatLabel = "${activeFormat.formatLabel} (${activeFormat.qualityLabel})"

    fun updateVideoState(dlState: YoutubeDownloadState) {
      _videoDownloadStates.update { it + (analysis.videoId to dlState) }
    }

    updateVideoState(YoutubeDownloadState.Downloading(0.02f, 0L, activeFormat.contentLengthBytes, formatLabel))
    _activeTrackNotifications.update {
      it + (notifKey to TrackDownloadNotificationInfo(
        key = notifKey,
        title = analysis.matchedTrackTitle,
        artist = analysis.matchedArtist,
        fraction = 0.02f,
        statusHint = formatLabel
      ))
    }
    BackgroundDownloadService.startSync(appContext)

    scope.launch {
      try {
        var downloadRes = SongDownloader.downloadFormatToApp(
          context = appContext,
          title = analysis.matchedTrackTitle,
          artist = analysis.matchedArtist,
          format = activeFormat
        ) { progressInfo ->
          updateVideoState(
            YoutubeDownloadState.Downloading(
              progress = progressInfo.fraction,
              downloadedBytes = progressInfo.downloadedBytes,
              totalBytes = progressInfo.totalBytes,
              formatLabel = formatLabel
            )
          )
          _activeTrackNotifications.update { current ->
            val existing = current[notifKey]
            if (existing != null) {
              current + (notifKey to existing.copy(fraction = progressInfo.fraction, statusHint = formatLabel))
            } else {
              current
            }
          }
        }

        if (downloadRes.isFailure) {
          updateVideoState(
            YoutubeDownloadState.Downloading(
              progress = 0.04f,
              downloadedBytes = 0L,
              totalBytes = activeFormat.contentLengthBytes,
              formatLabel = "Switching stream mirror..."
            )
          )
          _activeTrackNotifications.update { current ->
            val existing = current[notifKey]
            if (existing != null) {
              current + (notifKey to existing.copy(fraction = 0.04f, statusHint = "Switching stream mirror..."))
            } else {
              current
            }
          }
          val refreshed = YoutubeExtractor.extractFreshWorkingFormat(
            videoId = analysis.videoId,
            preferredExtension = activeFormat.extension,
            failedFormatId = activeFormat.id
          )
          if (refreshed != null) {
            activeFormat = refreshed
            formatLabel = "${activeFormat.formatLabel} (${activeFormat.qualityLabel})"
            downloadRes = SongDownloader.downloadFormatToApp(
              context = appContext,
              title = analysis.matchedTrackTitle,
              artist = analysis.matchedArtist,
              format = activeFormat
            ) { progressInfo ->
              updateVideoState(
                YoutubeDownloadState.Downloading(
                  progress = progressInfo.fraction,
                  downloadedBytes = progressInfo.downloadedBytes,
                  totalBytes = progressInfo.totalBytes,
                  formatLabel = formatLabel
                )
              )
              _activeTrackNotifications.update { current ->
                val existing = current[notifKey]
                if (existing != null) {
                  current + (notifKey to existing.copy(fraction = progressInfo.fraction, statusHint = formatLabel))
                } else {
                  current
                }
              }
            }
          }
        }

        _activeTrackNotifications.update { it - notifKey }

        downloadRes.onSuccess { savedAudio ->
          _downloadedSongIds.update { it + songIdForBadge + "yt_${analysis.videoId}" }
          val cachedCover = SongDownloader.cacheRemoteArtwork(
            context = appContext,
            songId = savedAudio.uri.toString(),
            imageUrl = analysis.thumbnailUrl
          )
          val artwork = cachedCover ?: analysis.thumbnailUrl.ifBlank { APP_ICON_ARTWORK }
          val finalDuration = savedAudio.durationSeconds
            .takeIf { it > 0L }
            ?: analysis.durationSeconds.takeIf { it > 0L }
            ?: activeFormat.durationSeconds

          val entity = LocalSongEntity(
            id = savedAudio.uri.toString(),
            title = analysis.matchedTrackTitle,
            artist = analysis.matchedArtist,
            artworkUrl = artwork,
            durationSeconds = finalDuration,
            streamUrl = savedAudio.uri.toString(),
            source = "Downloaded"
          )
          AurixDatabase.getInstance(appContext).aurixDao().insertLocalSong(entity)

          val savedSong = TrackMetadataClassifier.enrich(
            Song(
              id = entity.id,
              title = entity.title,
              artist = entity.artist,
              artworkUrl = entity.artworkUrl,
              durationSeconds = entity.durationSeconds,
              streamUrl = entity.streamUrl,
              source = entity.source,
              isFavorite = isFavoriteProvider(entity.id),
              fullDurationSeconds = entity.durationSeconds
            )
          )
          updateVideoState(YoutubeDownloadState.Saved(savedSong))
          AppNotificationHelper.notifySongDownloadComplete(
            appContext,
            notifKey,
            savedSong.title,
            savedSong.artist
          )
          _feedbackEvents.tryEmit(
            DownloadFeedbackEvent(
              "Saved full song (${YoutubeExtractor.formatDurationShort(finalDuration)}): ${savedSong.title}"
            )
          )
        }.onFailure { err ->
          val msg = err.localizedMessage ?: "Download failed"
          updateVideoState(YoutubeDownloadState.Error(msg))
          AppNotificationHelper.notifySongDownloadFailed(appContext, notifKey, analysis.matchedTrackTitle, msg)
          _feedbackEvents.tryEmit(DownloadFeedbackEvent(msg, isError = true))
        }
      } catch (e: Exception) {
        _activeTrackNotifications.update { it - notifKey }
        val msg = e.localizedMessage ?: "Download failed"
        updateVideoState(YoutubeDownloadState.Error(msg))
        AppNotificationHelper.notifySongDownloadFailed(appContext, notifKey, analysis.matchedTrackTitle, msg)
        _feedbackEvents.tryEmit(DownloadFeedbackEvent(msg, isError = true))
      }
    }
  }
}
