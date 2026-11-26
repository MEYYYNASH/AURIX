package com.example.recap

import androidx.annotation.StringRes
import com.example.R
import kotlin.math.roundToInt

/**
 * One of the 3 trait meters displayed on Card 5 (Listening Personality).
 * [percent] is strictly clamped to 0..100.
 */
data class TraitMeter(
  @StringRes val labelRes: Int,
  val fallbackLabel: String,
  val percent: Int
)

enum class PersonalityType(
  val id: String,
  @StringRes val nameRes: Int,
  @StringRes val descriptionRes: Int,
  val fallbackName: String,
  val fallbackDescription: String
) {
  NIGHT_OWL(
    id = "night_owl",
    nameRes = R.string.recap_personality_night_owl_name,
    descriptionRes = R.string.recap_personality_night_owl_desc,
    fallbackName = "Night Owl",
    fallbackDescription = "Your best soundtracks come alive after 10 PM when the city goes quiet."
  ),
  EARLY_BIRD(
    id = "early_bird",
    nameRes = R.string.recap_personality_early_bird_name,
    descriptionRes = R.string.recap_personality_early_bird_desc,
    fallbackName = "Early Bird",
    fallbackDescription = "You set the tone before noon with crisp morning rhythms and fresh energy."
  ),
  EXPLORER(
    id = "explorer",
    nameRes = R.string.recap_personality_explorer_name,
    descriptionRes = R.string.recap_personality_explorer_desc,
    fallbackName = "Explorer",
    fallbackDescription = "Always chasing new voices, fresh genres, and hidden gems across your library."
  ),
  LOYALIST(
    id = "loyalist",
    nameRes = R.string.recap_personality_loyalist_name,
    descriptionRes = R.string.recap_personality_loyalist_desc,
    fallbackName = "Loyalist",
    fallbackDescription = "When a song hits home, you keep it on repeat and know every beat by heart."
  ),
  MARATHONER(
    id = "marathoner",
    nameRes = R.string.recap_personality_marathoner_name,
    descriptionRes = R.string.recap_personality_marathoner_desc,
    fallbackName = "Marathoner",
    fallbackDescription = "Long uninterrupted listening sessions are your signature way to stay in flow."
  )
}

data class Personality(
  val type: PersonalityType,
  @StringRes val nameRes: Int = type.nameRes,
  @StringRes val descriptionRes: Int = type.descriptionRes,
  val fallbackName: String = type.fallbackName,
  val fallbackDescription: String = type.fallbackDescription,
  val traits: List<TraitMeter>
)

/**
 * Aggregated listening signals fed into the deterministic [PersonalityClassifier].
 */
data class PersonalitySignals(
  val totalPlays: Int,
  val lateNightRatio: Float,      // Fraction of plays between 22:00 and 03:59 (0f..1f)
  val morningRatio: Float,        // Fraction of plays between 05:00 and 11:59 (0f..1f)
  val uniqueArtistRatio: Float,   // Unique artists / totalPlays (0f..1f)
  val newArtistsCount: Int,       // Artists heard for the first time in this period
  val repeatRate: Float,          // Fraction of plays that are repeats or top-artist dominance (0f..1f)
  val dailyAverageMinutes: Int,   // Average listened minutes per active day
  val averageBpm: Int             // Mean BPM across played tracks
)

/**
 * Deterministic rule-based Listening Personality classifier.
 *
 * Decision rules (evaluated in priority order so the most distinctive habit wins):
 * 1. NIGHT_OWL: >= 38% of plays occur late at night (22:00–03:59).
 * 2. EARLY_BIRD: >= 42% of plays occur in the morning (05:00–11:59).
 * 3. MARATHONER: Daily average listening >= 75 minutes/day (long flow sessions).
 * 4. EXPLORER: High artist variety (uniqueArtistRatio >= 0.45) OR >= 10 new artists discovered.
 * 5. LOYALIST: High repeat rate (>= 0.48) or fallback when the listener favors familiar tracks.
 *
 * Trait meters (0..100%) are computed directly from the same real stats so every meter reflects
 * genuine listening behavior without randomness.
 */
object PersonalityClassifier {

  fun classify(signals: PersonalitySignals): Personality {
    val nightPct = (signals.lateNightRatio * 100f).roundToInt().coerceIn(8, 100)
    val morningPct = (signals.morningRatio * 100f).roundToInt().coerceIn(8, 100)
    val discoveryPct = (
      (signals.uniqueArtistRatio * 70f) +
        (signals.newArtistsCount.coerceAtMost(15) * 2f)
      ).roundToInt().coerceIn(10, 100)
    val loyaltyPct = (signals.repeatRate * 100f).roundToInt().coerceIn(12, 100)
    val endurancePct = ((signals.dailyAverageMinutes / 120f) * 100f).roundToInt().coerceIn(12, 100)
    val tempoPct = (((signals.averageBpm - 65).coerceAtLeast(0) / 85f) * 100f).roundToInt().coerceIn(15, 100)

    val type = when {
      // Rule 1: Strong late-night listening habit (10 PM – 4 AM)
      signals.totalPlays > 0 && signals.lateNightRatio >= 0.38f -> PersonalityType.NIGHT_OWL
      // Rule 2: Strong morning listening habit (5 AM – 12 PM)
      signals.totalPlays > 0 && signals.morningRatio >= 0.42f -> PersonalityType.EARLY_BIRD
      // Rule 3: High daily endurance (long sessions averaging 75+ min/day)
      signals.dailyAverageMinutes >= 75 -> PersonalityType.MARATHONER
      // Rule 4: Broad artist discovery (45%+ unique artist ratio or 10+ brand-new artists)
      signals.totalPlays > 0 && (signals.uniqueArtistRatio >= 0.45f || signals.newArtistsCount >= 10) -> PersonalityType.EXPLORER
      // Rule 5: High replay / repeat loyalty (or default when history is focused on favorites)
      signals.repeatRate >= 0.48f -> PersonalityType.LOYALIST
      // Tie-breaker when none crossed a strict threshold: pick highest normalized habit score
      else -> {
        val scores = listOf(
          PersonalityType.NIGHT_OWL to signals.lateNightRatio * 1.15f,
          PersonalityType.EARLY_BIRD to signals.morningRatio * 1.10f,
          PersonalityType.EXPLORER to signals.uniqueArtistRatio,
          PersonalityType.LOYALIST to signals.repeatRate,
          PersonalityType.MARATHONER to (signals.dailyAverageMinutes / 100f).coerceAtMost(1f)
        )
        scores.maxByOrNull { it.second }?.first ?: PersonalityType.LOYALIST
      }
    }

    val traits = when (type) {
      PersonalityType.NIGHT_OWL -> listOf(
        TraitMeter(R.string.recap_trait_night_energy, "Night Energy", nightPct),
        TraitMeter(R.string.recap_trait_loyalty, "Loyalty", loyaltyPct),
        TraitMeter(R.string.recap_trait_endurance, "Endurance", endurancePct)
      )
      PersonalityType.EARLY_BIRD -> listOf(
        TraitMeter(R.string.recap_trait_morning_spark, "Morning Spark", morningPct),
        TraitMeter(R.string.recap_trait_tempo_drive, "Tempo Drive", tempoPct),
        TraitMeter(R.string.recap_trait_discovery, "Discovery", discoveryPct)
      )
      PersonalityType.EXPLORER -> listOf(
        TraitMeter(R.string.recap_trait_discovery, "Discovery", discoveryPct),
        TraitMeter(R.string.recap_trait_tempo_drive, "Tempo Drive", tempoPct),
        TraitMeter(R.string.recap_trait_endurance, "Endurance", endurancePct)
      )
      PersonalityType.LOYALIST -> listOf(
        TraitMeter(R.string.recap_trait_loyalty, "Loyalty", loyaltyPct),
        TraitMeter(R.string.recap_trait_endurance, "Endurance", endurancePct),
        TraitMeter(R.string.recap_trait_night_energy, "Night Energy", nightPct)
      )
      PersonalityType.MARATHONER -> listOf(
        TraitMeter(R.string.recap_trait_endurance, "Endurance", endurancePct),
        TraitMeter(R.string.recap_trait_loyalty, "Loyalty", loyaltyPct),
        TraitMeter(R.string.recap_trait_discovery, "Discovery", discoveryPct)
      )
    }

    return Personality(type = type, traits = traits)
  }
}
