package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class AurixMusicWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    AurixWidgetController.updateSpecificMusicWidgets(context, appWidgetManager, appWidgetIds)
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle
  ) {
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    AurixWidgetController.updateSpecificMusicWidgets(
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
        AurixWidgetController.refreshAllWidgetsAsync(context, refreshMusic = true, refreshLyrics = true)
      }
    }
  }
}
