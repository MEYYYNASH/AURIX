package com.example.lyrics

import android.content.Context
import android.provider.Settings

/**
 * The 5 synced lyrics line-transition animation styles supported across AURIX.
 * Persisted via DataStore under key `lyrics_anim` (default: `slide_up`) and optional
 * independent widget key `lyrics_widget_anim`.
 */
enum class LyricsAnimationStyle(val id: String) {
  SLIDE_UP("slide_up"),
  BLUR_FADE("blur_fade"),
  KARAOKE_FILL("karaoke_fill"),
  TYPEWRITER("typewriter"),
  POP_ZOOM("pop_zoom");

  /**
   * Home screen RemoteViews widgets support XML ViewFlipper animations for slide_up,
   * blur_fade (fade only), and pop_zoom. Karaoke fill and typewriter fall back to slide_up.
   */
  val isWidgetFallback: Boolean
    get() = this == KARAOKE_FILL || this == TYPEWRITER

  fun widgetEffectiveStyle(): LyricsAnimationStyle {
    return if (isWidgetFallback) SLIDE_UP else this
  }

  companion object {
    const val DEFAULT_ID = "slide_up"

    fun fromId(raw: String?): LyricsAnimationStyle {
      return entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: SLIDE_UP
    }

    /**
     * Respects both the in-app Reduced Motion toggle and the Android system
     * "Remove animations" (ANIMATOR_DURATION_SCALE == 0) accessibility setting.
     */
    fun isMotionDisabled(context: Context, appReducedMotion: Boolean): Boolean {
      if (appReducedMotion) return true
      val scale = runCatching {
        Settings.Global.getFloat(
          context.contentResolver,
          Settings.Global.ANIMATOR_DURATION_SCALE,
          1.0f
        )
      }.getOrDefault(1.0f)
      return scale == 0f
    }
  }
}

/**
 * Target selector in the Lyrics Animation picker sheet allowing the user to apply
 * one shared style to both App & Widget, or customize App Lyrics and Widget independently.
 */
enum class LyricsAnimationTarget {
  BOTH,
  APP,
  WIDGET
}
