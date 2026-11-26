package com.example.recap

import androidx.annotation.StringRes
import com.example.R

/**
 * Distinct facial & orbital expression rendered on Orbi for Card 6 (Orbi Mood Badge).
 */
enum class OrbiExpression {
  DREAMY,
  ELECTRIC,
  CHILL,
  HYPED,
  FOCUSED,
  NOSTALGIC
}

/**
 * Recap Orbi Mood badge computed from real audio tags, BPM tempo, and listening time of day.
 */
data class OrbiMood(
  val id: String,
  @StringRes val nameRes: Int,
  val fallbackName: String,
  val emoji: String,
  val primaryColorHex: Long,
  val secondaryColorHex: Long,
  val expression: OrbiExpression
) {
  companion object {
    val DREAMY = OrbiMood(
      id = "dreamy",
      nameRes = R.string.recap_mood_dreamy,
      fallbackName = "Dreamy",
      emoji = "✨",
      primaryColorHex = 0xFF7965FF,
      secondaryColorHex = 0xFF43C7F4,
      expression = OrbiExpression.DREAMY
    )
    val ELECTRIC = OrbiMood(
      id = "electric",
      nameRes = R.string.recap_mood_electric,
      fallbackName = "Electric",
      emoji = "⚡",
      primaryColorHex = 0xFF43C7F4,
      secondaryColorHex = 0xFFA855F7,
      expression = OrbiExpression.ELECTRIC
    )
    val CHILL = OrbiMood(
      id = "chill",
      nameRes = R.string.recap_mood_chill,
      fallbackName = "Chill",
      emoji = "🌊",
      primaryColorHex = 0xFF38BDF8,
      secondaryColorHex = 0xFF2DD4BF,
      expression = OrbiExpression.CHILL
    )
    val HYPED = OrbiMood(
      id = "hyped",
      nameRes = R.string.recap_mood_hyped,
      fallbackName = "Hyped",
      emoji = "🔥",
      primaryColorHex = 0xFFA855F7,
      secondaryColorHex = 0xFFFF5E7E,
      expression = OrbiExpression.HYPED
    )
    val FOCUSED = OrbiMood(
      id = "focused",
      nameRes = R.string.recap_mood_focused,
      fallbackName = "Focused",
      emoji = "🎧",
      primaryColorHex = 0xFF43C7F4,
      secondaryColorHex = 0xFF6366F1,
      expression = OrbiExpression.FOCUSED
    )
    val NOSTALGIC = OrbiMood(
      id = "nostalgic",
      nameRes = R.string.recap_mood_nostalgic,
      fallbackName = "Nostalgic",
      emoji = "🌙",
      primaryColorHex = 0xFF8B5CF6,
      secondaryColorHex = 0xFFF472B6,
      expression = OrbiExpression.NOSTALGIC
    )
  }
}

data class OrbiMoodSignals(
  val averageBpm: Int,
  val peakHour: Int,             // 0..23
  val dominantTags: Set<String>, // Lowercased genre/mood tags from top played tracks
  val energy: Float,
  val chill: Float,
  val calm: Float,
  val sad: Float,
  val romantic: Float,
  val energetic: Float,
  val instrumentalRatio: Float
)

/**
 * Deterministic rule-based Orbi Mood classifier combining:
 * - Audio tags (genre + mood keywords)
 * - Tempo (mean BPM of most-played tracks)
 * - Time of day (peak listening hour)
 */
object OrbiMoodClassifier {

  fun classify(signals: OrbiMoodSignals): OrbiMood {
    val tags = signals.dominantTags
    val isLateNight = signals.peakHour >= 22 || signals.peakHour <= 3
    val isWorkHours = signals.peakHour in 9..17

    // Rule 1: Hyped — very high BPM (126+) with strong energetic tags or phonk/workout/rock
    if (
      (signals.averageBpm >= 126 && (signals.energetic >= 0.62f || signals.energy >= 0.66f)) ||
      tags.any { it.contains("phonk") || it.contains("workout") || it.contains("hype") }
    ) {
      return OrbiMood.HYPED
    }

    // Rule 2: Electric — brisk dance/electronic/pop tempo (115+ BPM) with lively energy
    if (
      signals.averageBpm >= 115 && signals.energy >= 0.56f ||
      tags.any { it.contains("electronic") || it.contains("edm") || it.contains("dance") || it.contains("energetic") }
    ) {
      return OrbiMood.ELECTRIC
    }

    // Rule 3: Focused — lo-fi/instrumental/focus tags, low vocals, or daytime calm tempo
    if (
      signals.instrumentalRatio >= 0.35f ||
      tags.any { it.contains("focus") || it.contains("lo-fi") || it.contains("lofi") || it.contains("instrumental") } ||
      (isWorkHours && signals.calm >= 0.60f && signals.averageBpm <= 102)
    ) {
      return OrbiMood.FOCUSED
    }

    // Rule 4: Nostalgic — emotional/sad/ballad/indie leaning tracks
    if (
      signals.sad >= 0.50f ||
      tags.any { it.contains("sad") || it.contains("ballad") || it.contains("indie") || it.contains("acoustic") }
    ) {
      return OrbiMood.NOSTALGIC
    }

    // Rule 5: Dreamy — late-night listening or romantic/r&b atmospheric tracks
    if (
      isLateNight ||
      signals.romantic >= 0.52f ||
      tags.any { it.contains("night") || it.contains("r&b") || it.contains("dream") }
    ) {
      return OrbiMood.DREAMY
    }

    // Rule 6: Chill — balanced relaxed tempo & smooth grooves
    return OrbiMood.CHILL
  }
}
