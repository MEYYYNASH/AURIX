package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R

/**
 * The 5 Orbi adaptive launcher icons (108dp canvas, 66dp safe zone):
 * 1. Classic: soft gray gradient background, white planet with two black eyes, dark orbit ring.
 * 2. Midnight: deep navy-black background, white planet, cyan-to-violet gradient ring.
 * 3. Ember: dark background, white planet, red ring (#EF4444).
 * 4. AMOLED: pure black background, white planet, thin white ring.
 * 5. Pearl: white-lavender background, lavender planet, violet ring.
 */
enum class OrbiAppIcon(
  val id: String,
  val displayName: String,
  val subtitle: String,
  val aliasClassName: String,
  @DrawableRes val previewResId: Int,
  val bgTopColor: Color,
  val bgBottomColor: Color,
  val planetTopColor: Color,
  val planetBottomColor: Color,
  val eyeColor: Color,
  val ringColors: List<Color>,
  val isThinRing: Boolean = false
) {
  CLASSIC(
    id = "classic",
    displayName = "Classic",
    subtitle = "Obsidian aurora, 3D porcelain Orbi & cyan-violet ring",
    aliasClassName = "com.example.MainActivityAliasClassic",
    previewResId = R.drawable.ic_orbi_preview_classic,
    bgTopColor = Color(0xFF0E1728),
    bgBottomColor = Color(0xFF080C14),
    planetTopColor = Color(0xFFFFFFFF),
    planetBottomColor = Color(0xFFD2DCEE),
    eyeColor = Color(0xFF0B0C10),
    ringColors = listOf(Color(0xFF43C7F4), Color(0xFFFFFFFF), Color(0xFF7965FF))
  ),
  MIDNIGHT(
    id = "midnight",
    displayName = "Midnight",
    subtitle = "Deep navy-black, white planet & cyan-violet ring",
    aliasClassName = "com.example.MainActivityAliasMidnight",
    previewResId = R.drawable.ic_orbi_preview_midnight,
    bgTopColor = Color(0xFF0D1426),
    bgBottomColor = Color(0xFF060813),
    planetTopColor = Color(0xFFFFFFFF),
    planetBottomColor = Color(0xFFFFFFFF),
    eyeColor = Color(0xFF0B0C10),
    ringColors = listOf(
      Color(0xFF5CE6FF),
      Color(0xFF4F7CFF),
      Color(0xFF7A5CFF),
      Color(0xFFA585FF)
    )
  ),
  EMBER(
    id = "ember",
    displayName = "Ember",
    subtitle = "Dark crimson-slate, white planet & #EF4444 red ring",
    aliasClassName = "com.example.MainActivityAliasEmber",
    previewResId = R.drawable.ic_orbi_preview_ember,
    bgTopColor = Color(0xFF1E1115),
    bgBottomColor = Color(0xFF0E080A),
    planetTopColor = Color(0xFFFFFFFF),
    planetBottomColor = Color(0xFFFFFFFF),
    eyeColor = Color(0xFF0B0C10),
    ringColors = listOf(Color(0xFFEF4444), Color(0xFFEF4444))
  ),
  AMOLED(
    id = "amoled",
    displayName = "AMOLED",
    subtitle = "Pure black background, white planet & thin white ring",
    aliasClassName = "com.example.MainActivityAliasAmoled",
    previewResId = R.drawable.ic_orbi_preview_amoled,
    bgTopColor = Color(0xFF000000),
    bgBottomColor = Color(0xFF000000),
    planetTopColor = Color(0xFFFFFFFF),
    planetBottomColor = Color(0xFFFFFFFF),
    eyeColor = Color(0xFF000000),
    ringColors = listOf(Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
    isThinRing = true
  ),
  PEARL(
    id = "pearl",
    displayName = "Pearl",
    subtitle = "White-lavender background, lavender planet & violet ring",
    aliasClassName = "com.example.MainActivityAliasPearl",
    previewResId = R.drawable.ic_orbi_preview_pearl,
    bgTopColor = Color(0xFFF5F3FF),
    bgBottomColor = Color(0xFFDDD6FE),
    planetTopColor = Color(0xFFFFFFFF),
    planetBottomColor = Color(0xFFA78BFA),
    eyeColor = Color(0xFF141522),
    ringColors = listOf(Color(0xFF7C3AED), Color(0xFF7C3AED))
  );

  companion object {
    fun fromId(id: String?): OrbiAppIcon {
      if (id.isNullOrBlank()) return CLASSIC
      return entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: CLASSIC
    }
  }
}

/**
 * Manages reading and switching the active launcher `<activity-alias>` for the 5 Orbi icons.
 *
 * Rules:
 * - Reads the real enabled state from [PackageManager] on launch so the picker always reflects truth,
 *   syncing preferences with the active component state.
 * - On apply: enables the chosen alias FIRST (`COMPONENT_ENABLED_STATE_ENABLED`, `DONT_KILL_APP`),
 *   then disables the other four (`COMPONENT_ENABLED_STATE_DISABLED`, `DONT_KILL_APP`), and persists
 *   the selected icon id in SharedPreferences.
 */
object IconManager {
  private const val PREFS_NAME = "aurix_app_icon_prefs"
  private const val KEY_SELECTED_ICON_ID = "selected_orbi_icon_id"

  fun componentNameFor(context: Context, icon: OrbiAppIcon): ComponentName {
    return ComponentName(context.packageName, icon.aliasClassName)
  }

  /**
   * Reads the real enabled `<activity-alias>` from [PackageManager] so the picker always shows
   * the true active launcher icon, falling back to saved preferences or [OrbiAppIcon.CLASSIC].
   */
  fun getCurrentIcon(context: Context): OrbiAppIcon {
    val pm = context.packageManager
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 1. Check explicit PackageManager enabled states first
    for (icon in OrbiAppIcon.entries) {
      val state = runCatching {
        pm.getComponentEnabledSetting(componentNameFor(context, icon))
      }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

      if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
        prefs.edit().putString(KEY_SELECTED_ICON_ID, icon.id).apply()
        return icon
      }
    }

    // 2. If all aliases are in COMPONENT_ENABLED_STATE_DEFAULT, check if Classic is still default-enabled
    val classicState = runCatching {
      pm.getComponentEnabledSetting(componentNameFor(context, OrbiAppIcon.CLASSIC))
    }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

    if (classicState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
      prefs.edit().putString(KEY_SELECTED_ICON_ID, OrbiAppIcon.CLASSIC.id).apply()
      return OrbiAppIcon.CLASSIC
    }

    // 3. Fallback to saved preference ID
    val savedId = prefs.getString(KEY_SELECTED_ICON_ID, OrbiAppIcon.CLASSIC.id)
    return OrbiAppIcon.fromId(savedId)
  }

  /**
   * Switches the launcher icon to [targetIcon]:
   * 1. Enables the chosen `<activity-alias>` FIRST with [PackageManager.DONT_KILL_APP].
   * 2. Disables the other four aliases with [PackageManager.DONT_KILL_APP].
   * 3. Saves [targetIcon]'s ID in SharedPreferences.
   */
  fun applyIcon(context: Context, targetIcon: OrbiAppIcon): Boolean {
    return runCatching {
      val pm = context.packageManager
      val targetComponent = componentNameFor(context, targetIcon)

      // 1. Enable the new alias first
      pm.setComponentEnabledSetting(
        targetComponent,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
      )

      // 2. Disable the other four aliases
      for (icon in OrbiAppIcon.entries) {
        if (icon != targetIcon) {
          pm.setComponentEnabledSetting(
            componentNameFor(context, icon),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
          )
        }
      }

      // 3. Persist selection
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_SELECTED_ICON_ID, targetIcon.id)
        .apply()
      true
    }.getOrDefault(false)
  }
}
