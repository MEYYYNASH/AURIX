package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class AurixCoverWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    AurixWidgetController.updateSpecificCoverWidgets(context, appWidgetManager, appWidgetIds)
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle
  ) {
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    AurixWidgetController.updateSpecificCoverWidgets(
      context = context,
      appWidgetManager = appWidgetManager,
      appWidgetIds = intArrayOf(appWidgetId)
    )
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    when (intent.action) {
      AurixWidgetController.ACTION_WIDGET_PLAY_PAUSE -> {
        AurixWidgetController.dispatchWidgetControlToPlaybackService(context, intent.action!!)
      }
      AurixWidgetController.ACTION_WIDGET_REFRESH -> {
        AurixWidgetController.refreshAllWidgetsAsync(
          context = context,
          refreshMusic = false,
          refreshLyrics = false,
          refreshCover = true,
          refreshMini = false
        )
      }
    }
  }
}
