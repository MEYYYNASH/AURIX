package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AurixLyricsWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    AurixWidgetController.updateSpecificLyricsWidgets(context, appWidgetManager, appWidgetIds)
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle
  ) {
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    AurixWidgetController.updateSpecificLyricsWidgets(
      context = context,
      appWidgetManager = appWidgetManager,
      appWidgetIds = intArrayOf(appWidgetId)
    )
  }

  override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    super.onDeleted(context, appWidgetIds)
    AurixWidgetController.clearPerWidgetCaches(appWidgetIds)
    val appCtx = context.applicationContext
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        WidgetStyleStore.deleteStyles(appCtx, appWidgetIds)
      } finally {
        pendingResult.finish()
      }
    }
  }

  override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
    super.onRestored(context, oldWidgetIds, newWidgetIds)
    val appCtx = context.applicationContext
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        WidgetStyleStore.remapWidgetIds(appCtx, oldWidgetIds, newWidgetIds)
        val manager = AppWidgetManager.getInstance(appCtx)
        AurixWidgetController.updateSpecificLyricsWidgets(appCtx, manager, newWidgetIds)
      } finally {
        pendingResult.finish()
      }
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == AurixWidgetController.ACTION_WIDGET_REFRESH) {
      AurixWidgetController.refreshAllWidgetsAsync(context, refreshMusic = false, refreshLyrics = true)
    }
  }
}
