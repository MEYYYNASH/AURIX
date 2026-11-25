package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

private val Context.widgetStyleDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "aurix_widget_styles"
)

/**
 * Per-widget instance style persistence backed by Jetpack DataStore.
 * Keyed strictly by appWidgetId ("style_<id>") so changing one widget's style
 * never affects any other widget, even two of the same type.
 */
object WidgetStyleStore {
  private val inMemoryCache = ConcurrentHashMap<Int, WidgetGlassStyle>()
  private val lastChosenByKind = ConcurrentHashMap<WidgetKind, WidgetGlassStyle>()

  fun styleKey(appWidgetId: Int): Preferences.Key<String> =
    stringPreferencesKey("style_$appWidgetId")

  fun lastKindKey(kind: WidgetKind): Preferences.Key<String> =
    stringPreferencesKey("last_kind_${kind.id}")

  suspend fun getStyle(context: Context, appWidgetId: Int): WidgetGlassStyle {
    if (appWidgetId <= 0) return WidgetGlassStyle.FROST_PILL
    inMemoryCache[appWidgetId]?.let { return it }
    val appCtx = context.applicationContext
    val prefs = appCtx.widgetStyleDataStore.data.first()
    val raw = prefs[styleKey(appWidgetId)]
    val resolved = WidgetGlassStyle.fromId(raw)
    inMemoryCache[appWidgetId] = resolved
    return resolved
  }

  fun getStyleBlocking(context: Context, appWidgetId: Int): WidgetGlassStyle {
    if (appWidgetId <= 0) return WidgetGlassStyle.FROST_PILL
    inMemoryCache[appWidgetId]?.let { return it }
    return runCatching {
      runBlocking { getStyle(context, appWidgetId) }
    }.getOrDefault(WidgetGlassStyle.FROST_PILL)
  }

  suspend fun setStyle(
    context: Context,
    appWidgetId: Int,
    style: WidgetGlassStyle,
    kind: WidgetKind? = null
  ) {
    if (appWidgetId <= 0) return
    val appCtx = context.applicationContext
    inMemoryCache[appWidgetId] = style
    if (kind != null) {
      lastChosenByKind[kind] = style
    }
    appCtx.widgetStyleDataStore.edit { prefs ->
      prefs[styleKey(appWidgetId)] = style.id
      if (kind != null) {
        prefs[lastKindKey(kind)] = style.id
      }
    }
  }

  suspend fun deleteStyle(context: Context, appWidgetId: Int) {
    if (appWidgetId <= 0) return
    inMemoryCache.remove(appWidgetId)
    val appCtx = context.applicationContext
    appCtx.widgetStyleDataStore.edit { prefs ->
      prefs.remove(styleKey(appWidgetId))
    }
  }

  suspend fun deleteStyles(context: Context, appWidgetIds: IntArray) {
    if (appWidgetIds.isEmpty()) return
    appWidgetIds.forEach { inMemoryCache.remove(it) }
    val appCtx = context.applicationContext
    appCtx.widgetStyleDataStore.edit { prefs ->
      for (id in appWidgetIds) {
        if (id > 0) {
          prefs.remove(styleKey(id))
        }
      }
    }
  }

  suspend fun remapWidgetIds(
    context: Context,
    oldWidgetIds: IntArray,
    newWidgetIds: IntArray
  ) {
    val count = minOf(oldWidgetIds.size, newWidgetIds.size)
    if (count <= 0) return
    val appCtx = context.applicationContext
    appCtx.widgetStyleDataStore.edit { prefs ->
      for (i in 0 until count) {
        val oldId = oldWidgetIds[i]
        val newId = newWidgetIds[i]
        if (oldId > 0 && newId > 0) {
          val oldStyle = prefs[styleKey(oldId)]
          if (oldStyle != null) {
            val resolved = WidgetGlassStyle.fromId(oldStyle)
            prefs[styleKey(newId)] = resolved.id
            prefs.remove(styleKey(oldId))
            inMemoryCache.remove(oldId)
            inMemoryCache[newId] = resolved
          }
        }
      }
    }
  }

  suspend fun getLastChosenStyleForKind(context: Context, kind: WidgetKind): WidgetGlassStyle {
    lastChosenByKind[kind]?.let { return it }
    val appCtx = context.applicationContext
    val prefs = appCtx.widgetStyleDataStore.data.first()
    val raw = prefs[lastKindKey(kind)]
    val resolved = WidgetGlassStyle.fromId(raw)
    lastChosenByKind[kind] = resolved
    return resolved
  }

  suspend fun applyStyleToAllWidgets(
    context: Context,
    style: WidgetGlassStyle
  ): List<Pair<WidgetKind, Int>> {
    val appCtx = context.applicationContext
    val manager = runCatching { AppWidgetManager.getInstance(appCtx) }.getOrNull() ?: return emptyList()
    val allTargets = mutableListOf<Pair<WidgetKind, Int>>()

    val providers = listOf(
      WidgetKind.MUSIC to AurixMusicWidgetProvider::class.java,
      WidgetKind.LYRICS to AurixLyricsWidgetProvider::class.java,
      WidgetKind.COVER to AurixCoverWidgetProvider::class.java,
      WidgetKind.MINI to AurixMiniWidgetProvider::class.java
    )
    for ((kind, cls) in providers) {
      val ids = runCatching {
        manager.getAppWidgetIds(ComponentName(appCtx, cls))
      }.getOrDefault(IntArray(0))
      for (id in ids) {
        if (id > 0) {
          allTargets.add(kind to id)
        }
      }
    }

    appCtx.widgetStyleDataStore.edit { prefs ->
      for ((_, id) in allTargets) {
        prefs[styleKey(id)] = style.id
        inMemoryCache[id] = style
      }
      for (kind in WidgetKind.entries) {
        prefs[lastKindKey(kind)] = style.id
        lastChosenByKind[kind] = style
      }
    }
    return allTargets
  }

  fun resolveWidgetKind(context: Context, appWidgetId: Int): WidgetKind {
    if (appWidgetId <= 0) return WidgetKind.MUSIC
    val manager = runCatching { AppWidgetManager.getInstance(context.applicationContext) }.getOrNull()
    val info = runCatching { manager?.getAppWidgetInfo(appWidgetId) }.getOrNull()
    return WidgetKind.fromProviderClassName(info?.provider?.className)
  }

  internal fun clearInMemoryCacheForTests() {
    inMemoryCache.clear()
    lastChosenByKind.clear()
  }
}
