package com.example.recommendation

import com.example.data.PlayLogEntity
import com.example.model.Song
import com.example.playback.CrossfadeController
import kotlin.math.abs

enum class SmartPlaylistCategory(val id: String) {
  MOOD("mood"),
  BPM("bpm"),
  ARTIST("artist")
}

data class SmartPlaylist(
  val id: String,
  val title: String,
  val subtitle: String,
  val category: SmartPlaylistCategory,
  val badgeLabel: String,
  val bpmRangeLabel: String,
  val averageBpm: Int,
  val tracks: List<Song>,
  val accentColorHex: Long,
  val secondaryColorHex: Long
)

data class DjTransitionInfo(
  val beatfadeSeconds: Int,
  val bpmMatchPercent: Int,
  val fromBpm: Int,
  val toBpm: Int
)

/**
 * Deterministic auto-playlist generator and DJ Auto-Mix BPM sequencer.
 * Groups tracks from the user's real library + catalog + play logs by Mood, BPM tempo band,
 * and Top Artists, and orders tracks to minimize harmonic BPM jumps during beatfade transitions.
 */
object SmartPlaylistEngine {

  private const val MIN_TRACKS_PER_PLAYLIST = 2
  private const val MAX_TRACKS_PER_PLAYLIST = 25

  fun generateSmartPlaylists(
    songs: List<Song>,
    playLogs: List<PlayLogEntity> = emptyList()
  ): List<SmartPlaylist> {
    if (songs.isEmpty()) return emptyList()

    val enriched = songs
      .distinctBy { it.id }
      .map { song ->
        if (song.bpm > 0 && song.hasMoodMetadata) song else TrackMetadataClassifier.enrich(song)
      }

    // Weight favorite/frequently-played songs higher when picking playlist order
    val playCountBySongId = playLogs
      .filter { !it.skipped }
      .groupingBy { it.songId }
      .eachCount()

    fun rankScore(song: Song): Int {
      val plays = playCountBySongId[song.id] ?: 0
      val favBonus = if (song.isFavorite) 4 else 0
      return plays * 2 + favBonus
    }

    val playlists = mutableListOf<SmartPlaylist>()

    // 1. MOOD SMART PLAYLISTS
    val chillTracks = enriched
      .filter { it.chill >= 0.58f || it.calm >= 0.60f || it.mood.equals("Chill", ignoreCase = true) }
      .sortedByDescending { it.chill * 10f + rankScore(it) }
      .take(MAX_TRACKS_PER_PLAYLIST)
    if (chillTracks.size >= MIN_TRACKS_PER_PLAYLIST) {
      playlists += buildPlaylist(
        id = "smart_mood_chill",
        title = "Chill & Late Night",
        subtitle = "Soft grooves, warm chords & easy tempo",
        category = SmartPlaylistCategory.MOOD,
        badgeLabel = "MOOD",
        tracks = orderForDjAutoMix(chillTracks),
        accentColorHex = 0xFF43C7F4,
        secondaryColorHex = 0xFF7965FF
      )
    }

    val hypeTracks = enriched
      .filter { it.energetic >= 0.60f || it.energy >= 0.65f || it.mood.equals("Energetic", ignoreCase = true) }
      .sortedByDescending { it.energetic * 10f + rankScore(it) }
      .take(MAX_TRACKS_PER_PLAYLIST)
    if (hypeTracks.size >= MIN_TRACKS_PER_PLAYLIST) {
      playlists += buildPlaylist(
        id = "smart_mood_hyped",
        title = "Pulse & Workout",
        subtitle = "High-energy drops tuned for momentum",
        category = SmartPlaylistCategory.MOOD,
        badgeLabel = "MOOD",
        tracks = orderForDjAutoMix(hypeTracks),
        accentColorHex = 0xFFFF5E7E,
        secondaryColorHex = 0xFFA855F7
      )
    }

    val focusTracks = enriched
      .filter {
        it.calm >= 0.56f ||
          !it.hasVocals ||
          it.mood.equals("Focus", ignoreCase = true) ||
          it.genre.equals("Lo-fi", ignoreCase = true) ||
          it.genre.equals("Instrumental", ignoreCase = true)
      }
      .sortedByDescending { (if (!it.hasVocals) 3f else 0f) + it.calm * 10f + rankScore(it) }
      .take(MAX_TRACKS_PER_PLAYLIST)
    if (focusTracks.size >= MIN_TRACKS_PER_PLAYLIST) {
      playlists += buildPlaylist(
        id = "smart_mood_focus",
        title = "Deep Focus Flow",
        subtitle = "Steady low-distraction beats for study & work",
        category = SmartPlaylistCategory.MOOD,
        badgeLabel = "MOOD",
        tracks = orderForDjAutoMix(focusTracks),
        accentColorHex = 0xFF38BDF8,
        secondaryColorHex = 0xFF2DD4BF
      )
    }

    val romanticTracks = enriched
      .filter { it.romantic >= 0.52f || it.sad >= 0.56f }
      .sortedByDescending { (it.romantic + it.sad * 0.5f) * 10f + rankScore(it) }
      .take(MAX_TRACKS_PER_PLAYLIST)
    if (romanticTracks.size >= MIN_TRACKS_PER_PLAYLIST) {
      playlists += buildPlaylist(
        id = "smart_mood_velvet",
        title = "Velvet & Melodic",
        subtitle = "Heartfelt vocals & atmospheric ballads",
        category = SmartPlaylistCategory.MOOD,
        badgeLabel = "MOOD",
        tracks = orderForDjAutoMix(romanticTracks),
        accentColorHex = 0xFFC084FC,
        secondaryColorHex = 0xFFF472B6
      )
    }

    // 2. BPM / TEMPO SMART PLAYLISTS
    val slowGroove = enriched
      .filter { it.bpm in 1..95 }
      .sortedBy { it.bpm }
      .take(MAX_TRACKS_PER_PLAYLIST)
    if (slowGroove.size >= MIN_TRACKS_PER_PLAYLIST) {
      playlists += buildPlaylist(
        id = "smart_bpm_slow",
        title = "Slow Groove · <95 BPM",
        subtitle = "Laid-back tempo band for smooth beatfades",
        category = SmartPlaylistCategory.BPM,
        badgeLabel = "BPM",
        tracks = orderForDjAutoMix(slowGroove),
        accentColorHex = 0xFF6366F1,
        secondaryColorHex = 0xFF43C7F4
      )
    }

    val midTempo = enriched
      .filter { it.bpm in 96..121 }
      .sortedBy { it.bpm }
      .take(MAX_TRACKS_PER_PLAYLIST)
    if (midTempo.size >= MIN_TRACKS_PER_PLAYLIST) {
      playlists += buildPlaylist(
        id = "smart_bpm_mid",
        title = "Cruise Tempo · 96–121 BPM",
        subtitle = "Balanced groove locked for seamless DJ sets",
        category = SmartPlaylistCategory.BPM,
        badgeLabel = "BPM",
        tracks = orderForDjAutoMix(midTempo),
        accentColorHex = 0xFF14B8A6,
        secondaryColorHex = 0xFF43C7F4
      )
    }

    val highTempo = enriched
      .filter { it.bpm >= 122 }
      .sortedBy { it.bpm }
      .take(MAX_TRACKS_PER_PLAYLIST)
    if (highTempo.size >= MIN_TRACKS_PER_PLAYLIST) {
      playlists += buildPlaylist(
        id = "smart_bpm_high",
        title = "Club Drive · 122+ BPM",
        subtitle = "Peak-hour tempo with tight beat-matched blends",
        category = SmartPlaylistCategory.BPM,
        badgeLabel = "BPM",
        tracks = orderForDjAutoMix(highTempo),
        accentColorHex = 0xFFA855F7,
        secondaryColorHex = 0xFFEC4899
      )
    }

    // 3. TOP ARTIST SMART PLAYLISTS
    val artistPlayCounts = playLogs
      .filter { !it.skipped && it.artist.isNotBlank() }
      .groupingBy { normalizeArtistKey(it.artist) }
      .eachCount()

    val artistGroups = enriched
      .filter { it.artist.isNotBlank() && !it.artist.equals("Unknown Artist", ignoreCase = true) }
      .groupBy { normalizeArtistKey(it.artist) }
      .filter { (_, group) -> group.size >= MIN_TRACKS_PER_PLAYLIST }
      .entries
      .sortedByDescending { (key, group) ->
        (artistPlayCounts[key] ?: 0) * 3 + group.size * 2
      }
      .take(3)

    val artistPalettes = listOf(
      0xFF7965FFL to 0xFF43C7F4L,
      0xFFA855F7L to 0xFF38BDF8L,
      0xFF2DD4BFL to 0xFF7965FFL
    )

    artistGroups.forEachIndexed { idx, (key, group) ->
      val displayArtist = group.first().artist.trim()
      val ordered = orderForDjAutoMix(
        group.sortedByDescending { rankScore(it) }.take(MAX_TRACKS_PER_PLAYLIST)
      )
      val (c1, c2) = artistPalettes[idx % artistPalettes.size]
      playlists += buildPlaylist(
        id = "smart_artist_${key.hashCode() and 0x7FFFFFFF}",
        title = "$displayArtist Essentials",
        subtitle = "Auto-mixed spotlight from your library",
        category = SmartPlaylistCategory.ARTIST,
        badgeLabel = "ARTIST",
        tracks = ordered,
        accentColorHex = c1,
        secondaryColorHex = c2
      )
    }

    return playlists
  }

  /**
   * Orders tracks into a DJ Auto-Mix flow that minimizes harmonic BPM jumps (accounting for
   * direct, half-time, and double-time matches) while keeping energy transitions gradual.
   */
  fun orderForDjAutoMix(
    tracks: List<Song>,
    anchorSong: Song? = null
  ): List<Song> {
    if (tracks.size <= 2) return tracks
    val enriched = tracks.map { if (it.bpm > 0 && it.hasMoodMetadata) it else TrackMetadataClassifier.enrich(it) }
    val remaining = enriched.toMutableList()
    val result = ArrayList<Song>(enriched.size)

    val first = if (anchorSong != null) {
      val idx = remaining.indexOfFirst { it.id == anchorSong.id }
      if (idx >= 0) remaining.removeAt(idx) else remaining.removeAt(0)
    } else {
      // Start near the lower-middle BPM so energy builds smoothly upward
      val sortedByBpm = remaining.sortedBy { it.bpm.takeIf { b -> b > 0 } ?: 105 }
      val pick = sortedByBpm[sortedByBpm.size / 4]
      remaining.remove(pick)
      pick
    }
    result += first

    while (remaining.isNotEmpty()) {
      val current = result.last()
      val curBpm = current.bpm.takeIf { it > 0 } ?: 105
      var bestIdx = 0
      var bestCost = Float.MAX_VALUE
      for (i in remaining.indices) {
        val candidate = remaining[i]
        val candBpm = candidate.bpm.takeIf { it > 0 } ?: 105
        val bpmDelta = CrossfadeController.effectiveBpmDelta(curBpm, candBpm)
        val energyDelta = abs(current.energy - candidate.energy) * 18f
        // Gently favor slight upward or steady tempo progression
        val directionPenalty = if (candBpm < curBpm - 6) 2.5f else 0f
        val cost = bpmDelta + energyDelta + directionPenalty
        if (cost < bestCost) {
          bestCost = cost
          bestIdx = i
        }
      }
      result += remaining.removeAt(bestIdx)
    }
    return result
  }

  fun describeTransition(from: Song, to: Song): DjTransitionInfo {
    val enrichedFrom = if (from.bpm > 0) from else TrackMetadataClassifier.enrich(from)
    val enrichedTo = if (to.bpm > 0) to else TrackMetadataClassifier.enrich(to)
    val fromBpm = enrichedFrom.bpm.coerceAtLeast(60)
    val toBpm = enrichedTo.bpm.coerceAtLeast(60)
    return DjTransitionInfo(
      beatfadeSeconds = CrossfadeController.computeBeatfadeDurationSeconds(fromBpm, toBpm),
      bpmMatchPercent = CrossfadeController.computeBpmMatchPercent(fromBpm, toBpm),
      fromBpm = fromBpm,
      toBpm = toBpm
    )
  }

  private fun buildPlaylist(
    id: String,
    title: String,
    subtitle: String,
    category: SmartPlaylistCategory,
    badgeLabel: String,
    tracks: List<Song>,
    accentColorHex: Long,
    secondaryColorHex: Long
  ): SmartPlaylist {
    val bpms = tracks.mapNotNull { it.bpm.takeIf { b -> b > 0 } }
    val minBpm = bpms.minOrNull() ?: 96
    val maxBpm = bpms.maxOrNull() ?: 120
    val avgBpm = if (bpms.isEmpty()) 108 else bpms.sum() / bpms.size
    val bpmRangeLabel = if (minBpm == maxBpm) "$avgBpm BPM" else "$minBpm–$maxBpm BPM"
    return SmartPlaylist(
      id = id,
      title = title,
      subtitle = subtitle,
      category = category,
      badgeLabel = badgeLabel,
      bpmRangeLabel = bpmRangeLabel,
      averageBpm = avgBpm,
      tracks = tracks,
      accentColorHex = accentColorHex,
      secondaryColorHex = secondaryColorHex
    )
  }

  private fun normalizeArtistKey(artist: String): String {
    return artist
      .lowercase()
      .replace(Regex("[,&]|feat\\.?|ft\\.?|x\\s+"), "|")
      .split("|")
      .firstOrNull { it.isNotBlank() }
      ?.trim()
      .orEmpty()
  }
}
