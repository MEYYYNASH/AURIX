package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.example.MainActivity
import com.example.data.AppNotificationHelper

/**
 * Lightweight widget reconfigure trampoline launched when the user long-presses
 * the AURIX Lyrics widget on the home screen and taps Customize / Settings.
 * Immediately confirms the widget ID to the launcher and opens AURIX directly
 * on the Widget Customizer screen.
 */
class WidgetConfigureActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val appWidgetId = intent?.extras?.getInt(
      AppWidgetManager.EXTRA_APPWIDGET_ID,
      AppWidgetManager.INVALID_APPWIDGET_ID
    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
      val resultIntent = Intent().apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      }
      setResult(RESULT_OK, resultIntent)
    } else {
      setResult(RESULT_OK)
    }

    val openCustomizerIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(AppNotificationHelper.EXTRA_OPEN_SCREEN, AppNotificationHelper.SCREEN_WIDGET_CUSTOMIZER)
    }
    runCatching { startActivity(openCustomizerIntent) }
    finish()
  }
}
