package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Responsive resizable home-screen widget ("AURIX Now Playing") supporting 3 sizes
 * (Compact 4x1, Player 4x2, Lyrics 4x2+) and 10 distinct styles configured from Settings.
 */
class AurixNowPlayingWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    AurixWidgetController.updateSpecificNowPlayingWidgets(context, appWidgetManager, appWidgetIds)
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle
  ) {
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    AurixWidgetController.updateSpecificNowPlayingWidgets(
      context = context,
      appWidgetManager = appWidgetManager,
      appWidgetIds = intArrayOf(appWidgetId)
    )
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    when (intent.action) {
      AurixWidgetController.ACTION_WIDGET_PREV,
      AurixWidgetController.ACTION_WIDGET_PLAY_PAUSE,
      AurixWidgetController.ACTION_WIDGET_NEXT,
      AurixWidgetController.ACTION_WIDGET_LIKE -> {
        AurixWidgetController.dispatchWidgetControlToPlaybackService(context, intent.action!!)
      }
      AurixWidgetController.ACTION_WIDGET_REFRESH -> {
        AurixWidgetController.refreshAllWidgetsAsync(
          context = context,
          refreshMusic = true,
          refreshLyrics = true,
          refreshCover = true,
          refreshMini = true,
          refreshNowPlaying = true
        )
      }
    }
  }
}
