package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        AurixWidgetController.updateSpecificCoverWidgets(appCtx, manager, newWidgetIds)
      } finally {
        pendingResult.finish()
      }
    }
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
