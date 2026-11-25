package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.LocaleHelper
import com.example.R
import com.example.ui.theme.AurixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-widget configuration Activity launched automatically when adding any AURIX home screen
 * widget and when choosing Long-press > Reconfigure on an existing widget.
 *
 * Persists style strictly by `appWidgetId` ("style_<id>" in WidgetStyleStore) and only updates
 * the configured widget unless the user explicitly taps "Apply to all widgets".
 */
class WidgetConfigureActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
    val lang = LocaleHelper.getPersistedLanguage(newBase)
    super.attachBaseContext(LocaleHelper.wrapBaseContext(newBase, lang))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setResult(RESULT_CANCELED)

    val appWidgetId = intent?.extras?.getInt(
      AppWidgetManager.EXTRA_APPWIDGET_ID,
      AppWidgetManager.INVALID_APPWIDGET_ID
    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }

    val appCtx = applicationContext
    val kind = WidgetStyleStore.resolveWidgetKind(appCtx, appWidgetId)
    val initialStyle = WidgetStyleStore.getStyleBlocking(appCtx, appWidgetId)
    val initialSnapshot = AurixWidgetController.readSnapshot(appCtx)
    val lang = LocaleHelper.getPersistedLanguage(appCtx)
    val isKhmer = lang == LocaleHelper.LANG_KM

    setContent {
      AurixTheme(darkTheme = true, amoledTheme = false) {
        val scope = rememberCoroutineScope()
        var selectedStyle by remember { mutableStateOf(initialStyle) }
        var snapshot by remember { mutableStateOf(initialSnapshot) }
        var rawArtwork by remember { mutableStateOf<Bitmap?>(null) }
        var isSaving by remember { mutableStateOf(false) }

        LaunchedEffect(appWidgetId) {
          val freshStyle = withContext(Dispatchers.IO) {
            WidgetStyleStore.getStyle(appCtx, appWidgetId)
          }
          selectedStyle = freshStyle
          val freshSnapshot = withContext(Dispatchers.IO) {
            AurixWidgetController.readSnapshot(appCtx)
          }
          snapshot = freshSnapshot
          rawArtwork = AurixWidgetController.loadArtworkBitmapForPreview(appCtx, freshSnapshot)
        }

        WidgetConfigureScreen(
          kind = kind,
          selectedStyle = selectedStyle,
          snapshot = snapshot,
          rawArtwork = rawArtwork,
          isKhmer = isKhmer,
          isSaving = isSaving,
          onSelectStyle = { style ->
            if (!isSaving) selectedStyle = style
          },
          onApplyToThisWidget = {
            if (isSaving) return@WidgetConfigureScreen
            isSaving = true
            scope.launch {
              runCatching {
                withContext(Dispatchers.IO) {
                  WidgetStyleStore.setStyle(appCtx, appWidgetId, selectedStyle, kind)
                  AurixWidgetController.updateSingleWidgetBlocking(appCtx, appWidgetId, kind)
                  AurixWidgetController.updateWidgetPickerPreviewForKind(appCtx, kind, selectedStyle)
                }
              }
              Toast.makeText(
                this@WidgetConfigureActivity,
                getString(R.string.widget_cfg_applied_toast),
                Toast.LENGTH_SHORT
              ).show()
              setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
              )
              finish()
            }
          },
          onApplyToAllWidgets = {
            if (isSaving) return@WidgetConfigureScreen
            isSaving = true
            scope.launch {
              runCatching {
                withContext(Dispatchers.IO) {
                  WidgetStyleStore.setStyle(appCtx, appWidgetId, selectedStyle, kind)
                  WidgetStyleStore.applyStyleToAllWidgets(appCtx, selectedStyle)
                  AurixWidgetController.updateSingleWidgetBlocking(appCtx, appWidgetId, kind)
                  AurixWidgetController.refreshWidgetsBlocking(
                    context = appCtx,
                    refreshMusic = true,
                    refreshLyrics = true,
                    refreshCover = true,
                    refreshMini = true,
                    forceArtworkReload = false
                  )
                  AurixWidgetController.updateWidgetPickerPreviews(appCtx, selectedStyle)
                }
              }
              Toast.makeText(
                this@WidgetConfigureActivity,
                getString(R.string.widget_cfg_applied_all_toast),
                Toast.LENGTH_SHORT
              ).show()
              setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
              )
              finish()
            }
          },
          onCancel = {
            setResult(
              RESULT_CANCELED,
              Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
          }
        )
      }
    }
  }
}
