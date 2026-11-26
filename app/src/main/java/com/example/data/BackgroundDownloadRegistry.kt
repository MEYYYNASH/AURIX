package com.example.data

import android.content.Context
import com.example.model.APP_ICON_ARTWORK
import com.example.model.AudioFormatOption
import com.example.model.Song
import com.example.model.YoutubeAnalysisResult
import com.example.model.YoutubeDownloadState
import com.example.recommendation.TrackMetadataClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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

enum class DownloadQueueStatus {
  QUEUED,
  DOWNLOADING,
  COMPLETED,
  FAILED,
  CANCELLED
}

data class DownloadQueueItem(
  val key: String,
  val songId: String,
  val batchId: String = "single",
  val song: Song,
  val status: DownloadQueueStatus = DownloadQueueStatus.QUEUED,
  val progress: Float = 0f,
  val statusHint: String = "",
  val errorMessage: String? = null,
  val updatedAtMs: Long = System.currentTimeMillis(),
  val retryAnalysis: YoutubeAnalysisResult? = null,
  val retryFormat: AudioFormatOption? = null
) {
  val isActive: Boolean
    get() = status == DownloadQueueStatus.QUEUED || status == DownloadQueueStatus.DOWNLOADING
}

object BackgroundDownloadRegistry {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val songDownloadSemaphore = Semaphore(permits = 2)
  private val queueLock = Any()
  private val queueMap = LinkedHashMap<String, DownloadQueueItem>()
  private val activeJobs = ConcurrentHashMap<String, Job>()

  private val _queueItems = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
  val queueItems: StateFlow<List<DownloadQueueItem>> = _queueItems.asStateFlow()

  private val _songDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  val songDownloadProgress: StateFlow<Map<String, Float>> = _songDownloadProgress.asStateFlow()

  private val _downloadedSongIds = MutableStateFlow<Set<String>>(emptySet())
  val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds.asStateFlow()

  private val _activeTrackNotifications = MutableStateFlow<Map<String, TrackDownloadNotificationInfo>>(emptyMap())
  val activeTrackNotifications: StateFlow<Map<String, TrackDownloadNotificationInfo>> = _activeTrackNotifications.asStateFlow()

  // Keyed by analysis videoId so both YoutubeLinkGlassPane and YoutubeFormatPickerSheet remain synced
  private val _videoDownloadStates = MutableStateFlow<Map<String, YoutubeDownloadState>>(emptyMap())
  val videoDownloadStates: StateFlow<Map<String, YoutubeDownloadState>> = _videoDownloadStates.asStateFlow()

  private val _feedbackEvents = MutableSharedFlow<DownloadFeedbackEvent>(extraBufferCapacity = 24)
  val feedbackEvents: SharedFlow<DownloadFeedbackEvent> = _feedbackEvents.asSharedFlow()

  fun markDownloadedSongId(songId: String) {
    _downloadedSongIds.update { it + songId }
  }

  fun clearVideoDownloadState(videoId: String) {
    _videoDownloadStates.update { it - videoId }
  }

  fun hasActiveSongDownloads(): Boolean = _activeTrackNotifications.value.isNotEmpty()

  private fun publishQueueLocked() {
    val active = mutableListOf<DownloadQueueItem>()
    val failed = mutableListOf<DownloadQueueItem>()
    val done = mutableListOf<DownloadQueueItem>()
    for (item in queueMap.values) {
      when (item.status) {
        DownloadQueueStatus.DOWNLOADING,
        DownloadQueueStatus.QUEUED -> active.add(item)
        DownloadQueueStatus.FAILED -> failed.add(item)
        DownloadQueueStatus.COMPLETED,
        DownloadQueueStatus.CANCELLED -> done.add(item)
      }
    }
    _queueItems.value = active + failed + done.reversed()
  }

  private fun upsertQueueItem(item: DownloadQueueItem) {
    synchronized(queueLock) {
      queueMap[item.key] = item
      if (queueMap.size > 150) {
        val removableKeys = queueMap.values
          .filter { !it.isActive }
          .sortedBy { it.updatedAtMs }
          .take(queueMap.size - 150)
          .map { it.key }
        removableKeys.forEach { queueMap.remove(it) }
      }
      publishQueueLocked()
    }
  }

  private fun updateQueueItem(key: String, transform: (DownloadQueueItem) -> DownloadQueueItem) {
    synchronized(queueLock) {
      val existing = queueMap[key] ?: return
      queueMap[key] = transform(existing)
      publishQueueLocked()
    }
  }

  private fun isYoutubeWatchLink(song: Song): Boolean {
    return song.id.startsWith("yt_") &&
      (song.streamUrl.contains("youtube.com/watch", ignoreCase = true) ||
        song.streamUrl.contains("youtu.be/", ignoreCase = true))
  }

  fun enqueueSongDownload(
    context: Context,
    song: Song,
    silentToast: Boolean = false,
    batchId: String = "single"
  ) {
    val appContext = context.applicationContext
    if (_songDownloadProgress.value.containsKey(song.id)) return

    upsertQueueItem(
      DownloadQueueItem(
        key = song.id,
        songId = song.id,
        batchId = batchId,
        song = song,
        status = DownloadQueueStatus.QUEUED,
        progress = 0.02f,
        statusHint = "Queued",
        errorMessage = null,
        updatedAtMs = System.currentTimeMillis()
      )
    )
    _songDownloadProgress.update { it + (song.id to 0.03f) }
    _activeTrackNotifications.update {
      it + (song.id to TrackDownloadNotificationInfo(song.id, song.title, song.artist, 0.03f, "Queued"))
    }
    BackgroundDownloadService.startSync(appContext)
    if (!silentToast) {
      _feedbackEvents.tryEmit(DownloadFeedbackEvent("Downloading in background: ${song.title}"))
    }

    val job = scope.launch {
      try {
        songDownloadSemaphore.withPermit {
          updateQueueItem(song.id) {
            it.copy(
              status = DownloadQueueStatus.DOWNLOADING,
              progress = it.progress.coerceAtLeast(0.05f),
              statusHint = "Connecting...",
              updatedAtMs = System.currentTimeMillis()
            )
          }
          val youtubeVideoId = if (isYoutubeWatchLink(song)) {
            song.id.removePrefix("yt_").takeIf { it.isNotBlank() }
              ?: YoutubeExtractor.extractVideoId(song.streamUrl)
          } else {
            null
          }

          val result = if (youtubeVideoId != null) {
            val extracted = YoutubeExtractor.extractFullAudio(youtubeVideoId)
            val initialFormat = extracted?.audioFormats?.let { fmts ->
              fmts.firstOrNull { it.extension.equals("m4a", ignoreCase = true) }
                ?: fmts.firstOrNull { it.extension.equals("mp4", ignoreCase = true) }
                ?: fmts.firstOrNull()
            }
            if (initialFormat == null) {
              Result.failure(
                IllegalStateException(
                  extracted?.extractorError ?: "Could not extract audio stream for ${song.title}"
                )
              )
            } else {
              var activeFmt = initialFormat
              var fmtLabel = "${activeFmt.formatLabel} (${activeFmt.qualityLabel})"
              var dlRes = SongDownloader.downloadFormatToApp(
                context = appContext,
                title = song.title,
                artist = song.artist,
                format = activeFmt
              ) { info ->
                _songDownloadProgress.update { it + (song.id to info.fraction) }
                _activeTrackNotifications.update { current ->
                  val existing = current[song.id]
                  if (existing != null) {
                    current + (song.id to existing.copy(fraction = info.fraction, statusHint = fmtLabel))
                  } else {
                    current
                  }
                }
                updateQueueItem(song.id) {
                  it.copy(
                    status = DownloadQueueStatus.DOWNLOADING,
                    progress = info.fraction,
                    statusHint = fmtLabel,
                    updatedAtMs = System.currentTimeMillis()
                  )
                }
              }
              if (dlRes.isFailure) {
                updateQueueItem(song.id) {
                  it.copy(
                    statusHint = "Switching stream mirror...",
                    updatedAtMs = System.currentTimeMillis()
                  )
                }
                val refreshed = YoutubeExtractor.extractFreshWorkingFormat(
                  videoId = youtubeVideoId,
                  preferredExtension = activeFmt.extension.lowercase(Locale.US),
                  failedFormatId = activeFmt.id
                )
                if (refreshed != null) {
                  activeFmt = refreshed
                  fmtLabel = "${activeFmt.formatLabel} (${activeFmt.qualityLabel})"
                  dlRes = SongDownloader.downloadFormatToApp(
                    context = appContext,
                    title = song.title,
                    artist = song.artist,
                    format = activeFmt
                  ) { info ->
                    _songDownloadProgress.update { it + (song.id to info.fraction) }
                    _activeTrackNotifications.update { current ->
                      val existing = current[song.id]
                      if (existing != null) {
                        current + (song.id to existing.copy(fraction = info.fraction, statusHint = fmtLabel))
                      } else {
                        current
                      }
                    }
                    updateQueueItem(song.id) {
                      it.copy(
                        status = DownloadQueueStatus.DOWNLOADING,
                        progress = info.fraction,
                        statusHint = fmtLabel,
                        updatedAtMs = System.currentTimeMillis()
                      )
                    }
                  }
                }
              }
              dlRes
            }
          } else {
            SongDownloader.downloadSongToDevice(appContext, song) { progress ->
              _songDownloadProgress.update { it + (song.id to progress) }
              _activeTrackNotifications.update { current ->
                val existing = current[song.id]
                if (existing != null) {
                  current + (song.id to existing.copy(fraction = progress))
                } else {
                  current
                }
              }
              updateQueueItem(song.id) {
                it.copy(
                  status = DownloadQueueStatus.DOWNLOADING,
                  progress = progress,
                  statusHint = "Downloading...",
                  updatedAtMs = System.currentTimeMillis()
                )
              }
            }
          }

          _songDownloadProgress.update { it - song.id }
          _activeTrackNotifications.update { it - song.id }
          activeJobs.remove(song.id)

          result.onSuccess { savedAudio ->
            _downloadedSongIds.update { current ->
              if (youtubeVideoId != null) current + song.id + "yt_$youtubeVideoId" else current + song.id
            }
            val cachedCover = SongDownloader.cacheRemoteArtwork(appContext, savedAudio.uri.toString(), song.artworkUrl)
            val entity = LocalSongEntity(
              id = savedAudio.uri.toString(),
              title = song.title,
              artist = song.artist,
              artworkUrl = cachedCover ?: song.artworkUrl.ifBlank { APP_ICON_ARTWORK },
              durationSeconds = savedAudio.durationSeconds.takeIf { it > 0L } ?: song.durationSeconds,
              streamUrl = savedAudio.uri.toString(),
              source = "Downloaded",
              album = song.album.trim(),
              trackNumber = song.trackNumber,
              year = song.year
            )
            AurixDatabase.getInstance(appContext).aurixDao().insertLocalSong(entity)
            updateQueueItem(song.id) {
              it.copy(
                status = DownloadQueueStatus.COMPLETED,
                progress = 1f,
                statusHint = "Saved to Library",
                errorMessage = null,
                updatedAtMs = System.currentTimeMillis()
              )
            }
            AppNotificationHelper.notifySongDownloadComplete(appContext, song.id, song.title, song.artist)
            if (!silentToast) {
              _feedbackEvents.tryEmit(DownloadFeedbackEvent("Saved to My songs & Music/AURIX: ${song.title}"))
            }
          }.onFailure { err ->
            val msg = err.localizedMessage ?: "Download failed"
            updateQueueItem(song.id) {
              it.copy(
                status = DownloadQueueStatus.FAILED,
                statusHint = "Failed",
                errorMessage = msg,
                updatedAtMs = System.currentTimeMillis()
              )
            }
            runCatching { AppNotificationHelper.notifySongDownloadFailed(appContext, song.id, song.title, msg) }
            _feedbackEvents.tryEmit(DownloadFeedbackEvent(msg, isError = true))
          }
        }
      } catch (ce: CancellationException) {
        _songDownloadProgress.update { it - song.id }
        _activeTrackNotifications.update { it - song.id }
        activeJobs.remove(song.id)
        updateQueueItem(song.id) {
          if (it.status == DownloadQueueStatus.QUEUED || it.status == DownloadQueueStatus.DOWNLOADING) {
            it.copy(
              status = DownloadQueueStatus.CANCELLED,
              statusHint = "Stopped",
              errorMessage = null,
              updatedAtMs = System.currentTimeMillis()
            )
          } else {
            it
          }
        }
        throw ce
      } catch (e: Throwable) {
        _songDownloadProgress.update { it - song.id }
        _activeTrackNotifications.update { it - song.id }
        activeJobs.remove(song.id)
        val msg = e.localizedMessage ?: "Download failed"
        updateQueueItem(song.id) {
          it.copy(
            status = DownloadQueueStatus.FAILED,
            statusHint = "Failed",
            errorMessage = msg,
            updatedAtMs = System.currentTimeMillis()
          )
        }
        runCatching { AppNotificationHelper.notifySongDownloadFailed(appContext, song.id, song.title, msg) }
        _feedbackEvents.tryEmit(DownloadFeedbackEvent(msg, isError = true))
      }
    }
    activeJobs[song.id] = job
  }

  fun enqueueFormatDownload(
    context: Context,
    songIdForBadge: String,
    analysis: YoutubeAnalysisResult,
    initialFormat: AudioFormatOption,
    batchId: String = "single",
    isFavoriteProvider: (String) -> Boolean = { false }
  ) {
    val appContext = context.applicationContext
    val currentState = _videoDownloadStates.value[analysis.videoId]
    if (currentState is YoutubeDownloadState.Downloading) return

    val notifKey = "yt_fmt_${analysis.videoId}"
    var activeFormat = initialFormat
    var formatLabel = "${activeFormat.formatLabel} (${activeFormat.qualityLabel})"

    val displaySong = Song(
      id = songIdForBadge,
      title = analysis.matchedTrackTitle,
      artist = analysis.matchedArtist,
      artworkUrl = analysis.thumbnailUrl,
      durationSeconds = analysis.durationSeconds,
      streamUrl = "https://www.youtube.com/watch?v=${analysis.videoId}",
      source = "YouTube"
    )

    fun updateVideoState(dlState: YoutubeDownloadState) {
      _videoDownloadStates.update { it + (analysis.videoId to dlState) }
    }

    upsertQueueItem(
      DownloadQueueItem(
        key = notifKey,
        songId = songIdForBadge,
        batchId = batchId,
        song = displaySong,
        status = DownloadQueueStatus.DOWNLOADING,
        progress = 0.02f,
        statusHint = formatLabel,
        errorMessage = null,
        updatedAtMs = System.currentTimeMillis(),
        retryAnalysis = analysis,
        retryFormat = initialFormat
      )
    )
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

    val job = scope.launch {
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
          updateQueueItem(notifKey) {
            it.copy(
              status = DownloadQueueStatus.DOWNLOADING,
              progress = progressInfo.fraction,
              statusHint = formatLabel,
              updatedAtMs = System.currentTimeMillis()
            )
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
          updateQueueItem(notifKey) {
            it.copy(statusHint = "Switching stream mirror...", updatedAtMs = System.currentTimeMillis())
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
              updateQueueItem(notifKey) {
                it.copy(
                  status = DownloadQueueStatus.DOWNLOADING,
                  progress = progressInfo.fraction,
                  statusHint = formatLabel,
                  updatedAtMs = System.currentTimeMillis()
                )
              }
            }
          }
        }

        _activeTrackNotifications.update { it - notifKey }
        activeJobs.remove(notifKey)

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
            source = "Downloaded",
            album = ""
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
          updateQueueItem(notifKey) {
            it.copy(
              status = DownloadQueueStatus.COMPLETED,
              progress = 1f,
              statusHint = "Saved ($formatLabel)",
              errorMessage = null,
              updatedAtMs = System.currentTimeMillis()
            )
          }
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
          updateQueueItem(notifKey) {
            it.copy(
              status = DownloadQueueStatus.FAILED,
              statusHint = "Failed",
              errorMessage = msg,
              updatedAtMs = System.currentTimeMillis()
            )
          }
          runCatching { AppNotificationHelper.notifySongDownloadFailed(appContext, notifKey, analysis.matchedTrackTitle, msg) }
          _feedbackEvents.tryEmit(DownloadFeedbackEvent(msg, isError = true))
        }
      } catch (ce: CancellationException) {
        _activeTrackNotifications.update { it - notifKey }
        activeJobs.remove(notifKey)
        updateVideoState(YoutubeDownloadState.Idle)
        updateQueueItem(notifKey) {
          if (it.status == DownloadQueueStatus.QUEUED || it.status == DownloadQueueStatus.DOWNLOADING) {
            it.copy(
              status = DownloadQueueStatus.CANCELLED,
              statusHint = "Stopped",
              errorMessage = null,
              updatedAtMs = System.currentTimeMillis()
            )
          } else {
            it
          }
        }
        throw ce
      } catch (e: Throwable) {
        _activeTrackNotifications.update { it - notifKey }
        activeJobs.remove(notifKey)
        val msg = e.localizedMessage ?: "Download failed"
        updateVideoState(YoutubeDownloadState.Error(msg))
        updateQueueItem(notifKey) {
          it.copy(
            status = DownloadQueueStatus.FAILED,
            statusHint = "Failed",
            errorMessage = msg,
            updatedAtMs = System.currentTimeMillis()
          )
        }
        runCatching { AppNotificationHelper.notifySongDownloadFailed(appContext, notifKey, analysis.matchedTrackTitle, msg) }
        _feedbackEvents.tryEmit(DownloadFeedbackEvent(msg, isError = true))
      }
    }
    activeJobs[notifKey] = job
  }

  fun cancelDownload(key: String, silentToast: Boolean = false): Boolean {
    val cancelledJob = activeJobs.remove(key)
    cancelledJob?.cancel()
    var hadActiveItem = false
    var videoIdToReset: String? = null
    synchronized(queueLock) {
      val item = queueMap[key]
      if (item != null && item.isActive) {
        hadActiveItem = true
        videoIdToReset = item.retryAnalysis?.videoId
        queueMap[key] = item.copy(
          status = DownloadQueueStatus.CANCELLED,
          statusHint = "Stopped",
          errorMessage = null,
          updatedAtMs = System.currentTimeMillis()
        )
        publishQueueLocked()
      }
    }
    _songDownloadProgress.update { it - key }
    _activeTrackNotifications.update { it - key }
    videoIdToReset?.let { vid ->
      _videoDownloadStates.update { it + (vid to YoutubeDownloadState.Idle) }
    }
    if ((cancelledJob != null || hadActiveItem) && !silentToast) {
      _feedbackEvents.tryEmit(DownloadFeedbackEvent("Download stopped"))
    }
    return cancelledJob != null || hadActiveItem
  }

  fun cancelBatch(batchId: String, silentToast: Boolean = false): Int {
    val keysToCancel = synchronized(queueLock) {
      queueMap.values
        .filter { it.batchId == batchId && it.isActive }
        .map { it.key }
    }
    var count = 0
    keysToCancel.forEach { key ->
      if (cancelDownload(key, silentToast = true)) {
        count++
      }
    }
    if (count > 0 && !silentToast) {
      _feedbackEvents.tryEmit(DownloadFeedbackEvent("Stopped $count active downloads"))
    }
    return count
  }

  fun cancelAll(silentToast: Boolean = false): Int {
    val activeKeys = (synchronized(queueLock) {
      queueMap.values.filter { it.isActive }.map { it.key }
    } + activeJobs.keys.toList()).distinct()
    var count = 0
    activeKeys.forEach { key ->
      if (cancelDownload(key, silentToast = true)) {
        count++
      }
    }
    if (count > 0 && !silentToast) {
      _feedbackEvents.tryEmit(DownloadFeedbackEvent("Stopped all downloads ($count)"))
    }
    return count
  }

  fun retryDownload(
    context: Context,
    key: String,
    isFavoriteProvider: (String) -> Boolean = { false }
  ) {
    val item = synchronized(queueLock) { queueMap[key] } ?: return
    if (item.isActive) return
    val analysis = item.retryAnalysis
    val format = item.retryFormat
    if (analysis != null && format != null) {
      _videoDownloadStates.update { it + (analysis.videoId to YoutubeDownloadState.Idle) }
      enqueueFormatDownload(
        context = context,
        songIdForBadge = item.songId,
        analysis = analysis,
        initialFormat = format,
        batchId = item.batchId,
        isFavoriteProvider = isFavoriteProvider
      )
    } else {
      enqueueSongDownload(
        context = context,
        song = item.song,
        silentToast = false,
        batchId = item.batchId
      )
    }
  }

  fun retryAllFailed(
    context: Context,
    isFavoriteProvider: (String) -> Boolean = { false }
  ): Int {
    val failedKeys = synchronized(queueLock) {
      queueMap.values.filter { it.status == DownloadQueueStatus.FAILED }.map { it.key }
    }
    failedKeys.forEach { key ->
      retryDownload(context, key, isFavoriteProvider)
    }
    return failedKeys.size
  }

  fun clearFinished() {
    synchronized(queueLock) {
      val toRemove = queueMap.values
        .filter { it.status == DownloadQueueStatus.COMPLETED || it.status == DownloadQueueStatus.CANCELLED }
        .map { it.key }
      toRemove.forEach { queueMap.remove(it) }
      publishQueueLocked()
    }
  }
}
