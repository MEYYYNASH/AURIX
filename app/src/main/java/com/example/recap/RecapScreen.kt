package com.example.recap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.glass.GlassSegmented
import com.example.ui.glass.SegOption
import com.example.ui.glass.aurixGlassHazeStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val STORY_AUTO_ADVANCE_MS = 5500L

/**
 * Full-screen edge-to-edge Listening Recap Story Viewer:
 * - 6 progress bars auto-advancing every 5.5s
 * - Tap right (65%) for next, tap left (35%) for previous, press & hold to pause, swipe down to close
 * - Spring card transitions (dampingRatio 0.75, stiffness 300), active scale 1.0, neighbors 0.9
 * - Monthly / Yearly Liquid Glass segmented toggle (resets to card 1)
 * - Bottom Share, Save image, and Share All 6 actions with non-blocking 1080x1920 PNG export
 */
@Composable
fun RecapScreen(
  recapData: RecapData?,
  selectedPeriod: RecapPeriod,
  isLoading: Boolean,
  reducedMotion: Boolean,
  onSelectPeriod: (RecapPeriod) -> Unit,
  onClose: () -> Unit
) {
  BackHandler(onBack = onClose)

  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val systemAnimationsOff = remember(context) {
    runCatching {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
  }
  val noMotion = reducedMotion || systemAnimationsOff

  val pagerState = rememberPagerState(initialPage = 0, pageCount = { RECAP_CARD_COUNT })
  var isHoldPaused by remember { mutableStateOf(false) }
  var isExporting by remember { mutableStateOf(false) }
  val activeProgress = remember { mutableFloatStateOf(0f) }

  // Vertical swipe-down-to-close offset (read only in graphicsLayer for 120 Hz smoothness)
  val dismissDragY = remember { Animatable(0f) }
  val captureGraphicsLayer = rememberGraphicsLayer()

  // Reset to card 1 whenever Monthly / Yearly period switches
  LaunchedEffect(selectedPeriod) {
    activeProgress.floatValue = 0f
    if (pagerState.currentPage != 0) {
      if (noMotion) {
        pagerState.scrollToPage(0)
      } else {
        pagerState.animateScrollToPage(
          page = 0,
          animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)
        )
      }
    }
  }

  // Smooth frame-clocked 5.5s auto-advance progress bar
  LaunchedEffect(pagerState.currentPage, selectedPeriod, isHoldPaused, isExporting, isLoading, recapData != null) {
    if (recapData == null || isLoading) return@LaunchedEffect
    activeProgress.floatValue = 0f
    var elapsedMs = 0L
    var lastNanos = 0L
    while (elapsedMs < STORY_AUTO_ADVANCE_MS) {
      withFrameNanos { nowNanos ->
        if (lastNanos != 0L && !isHoldPaused && !isExporting && !pagerState.isScrollInProgress) {
          val dtMs = ((nowNanos - lastNanos) / 1_000_000L).coerceIn(0L, 100L)
          elapsedMs += dtMs
          activeProgress.floatValue = (elapsedMs.toFloat() / STORY_AUTO_ADVANCE_MS.toFloat()).coerceIn(0f, 1f)
        }
        lastNanos = nowNanos
      }
    }
    if (pagerState.currentPage < RECAP_CARD_COUNT - 1) {
      val next = pagerState.currentPage + 1
      if (noMotion) {
        pagerState.scrollToPage(next)
      } else {
        pagerState.animateScrollToPage(
          page = next,
          animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)
        )
      }
    }
  }

  fun performSave(data: RecapData) {
    if (isExporting) return
    scope.launch {
      isExporting = true
      val ok = RecapExporter.saveCardToPhotos(
        context = context,
        data = data,
        cardIndex = pagerState.currentPage,
        graphicsLayer = captureGraphicsLayer
      )
      isExporting = false
      snackbarHostState.showSnackbar(
        message = context.getString(
          if (ok) R.string.recap_saved_snackbar else R.string.recap_save_failed_snackbar
        ),
        duration = SnackbarDuration.Short
      )
    }
  }

  // Runtime permission launcher only for Android 9 and below (API <= 28)
  val legacyWriteLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    val activeData = recapData
    if (granted && activeData != null) {
      performSave(activeData)
    } else {
      scope.launch {
        snackbarHostState.showSnackbar(context.getString(R.string.recap_permission_needed))
      }
    }
  }

  val viewerHaze = remember { HazeState() }
  val safeHazeStyle = remember { aurixGlassHazeStyle(darkTheme = true, backgroundColor = Color(0xFF060910)) }

  CompositionLocalProvider(LocalHazeStyle provides safeHazeStyle) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF060910))
        .graphicsLayer {
          val dy = dismissDragY.value.coerceAtLeast(0f)
          translationY = dy
          val shrink = (1f - (dy / 1800f)).coerceIn(0.86f, 1f)
          scaleX = shrink
          scaleY = shrink
        }
        .pointerInput(noMotion) {
          detectVerticalDragGestures(
            onVerticalDrag = { _, dragAmount ->
              if (dragAmount > 0f || dismissDragY.value > 0f) {
                scope.launch {
                  dismissDragY.snapTo((dismissDragY.value + dragAmount).coerceAtLeast(0f))
                }
              }
            },
            onDragEnd = {
              if (dismissDragY.value > 210f) {
                onClose()
              } else {
                scope.launch {
                  if (noMotion) {
                    dismissDragY.snapTo(0f)
                  } else {
                    dismissDragY.animateTo(
                      0f,
                      animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)
                    )
                  }
                }
              }
            },
            onDragCancel = {
              scope.launch { dismissDragY.snapTo(0f) }
            }
          )
        }
        .testTag("recap_screen")
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.systemBars)
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // 1. 6 Story Progress Bars
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("recap_progress_bars"),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          for (idx in 0 until RECAP_CARD_COUNT) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(3.5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
            ) {
              Box(
                modifier = Modifier
                  .fillMaxHeight()
                  .fillMaxWidth()
                  .graphicsLayer {
                    val fillFraction = when {
                      idx < pagerState.currentPage -> 1f
                      idx == pagerState.currentPage -> activeProgress.floatValue
                      else -> 0f
                    }
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    scaleX = fillFraction
                  }
                  .background(Color.White)
              )
            }
          }
        }

        // 2. Top Controls: Monthly / Yearly GlassSegmented + Close Button
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          val periodOptions = listOf(
            SegOption(stringResource(id = R.string.recap_period_monthly)),
            SegOption(stringResource(id = R.string.recap_period_yearly))
          )
          GlassSegmented(
            options = periodOptions,
            selectedIndex = if (selectedPeriod == RecapPeriod.MONTHLY) 0 else 1,
            onSelect = { idx ->
              onSelectPeriod(if (idx == 0) RecapPeriod.MONTHLY else RecapPeriod.YEARLY)
            },
            fillWidth = false,
            darkTheme = true,
            backdrop = Modifier.hazeChild(state = viewerHaze, style = safeHazeStyle),
            modifier = Modifier.testTag("recap_period_segmented")
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            AnimatedVisibility(visible = isHoldPaused, enter = fadeIn(), exit = fadeOut()) {
              Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f)
              ) {
                Text(
                  text = stringResource(id = R.string.recap_paused_badge),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }

            IconButton(
              onClick = onClose,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                .testTag("recap_close_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(id = R.string.recap_close_cd),
                tint = Color.White
              )
            }
          }
        }

        // 3. Center 9:16 Story Pager with Spring Physics (dampingRatio 0.75, stiffness 300)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .haze(state = viewerHaze),
          contentAlignment = Alignment.Center
        ) {
          if (isLoading || recapData == null) {
            CircularProgressIndicator(
              color = Color(0xFF43C7F4),
              modifier = Modifier.testTag("recap_loading_indicator")
            )
          } else {
            val springFling = PagerDefaults.flingBehavior(
              state = pagerState,
              snapAnimationSpec = if (noMotion) snap() else spring(dampingRatio = 0.75f, stiffness = 300f)
            )

            HorizontalPager(
              state = pagerState,
              flingBehavior = springFling,
              beyondViewportPageCount = 1,
              pageSpacing = 12.dp,
              modifier = Modifier
                .fillMaxSize()
                .pointerInput(pagerState.currentPage, noMotion) {
                  detectTapGestures(
                    onPress = {
                      isHoldPaused = true
                      try {
                        tryAwaitRelease()
                      } finally {
                        isHoldPaused = false
                      }
                    },
                    onTap = { offset ->
                      val tapLeftZone = offset.x < size.width * 0.35f
                      val target = if (tapLeftZone) {
                        (pagerState.currentPage - 1).coerceAtLeast(0)
                      } else {
                        (pagerState.currentPage + 1).coerceAtMost(RECAP_CARD_COUNT - 1)
                      }
                      if (target != pagerState.currentPage) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                          if (noMotion) {
                            pagerState.scrollToPage(target)
                          } else {
                            pagerState.animateScrollToPage(
                              page = target,
                              animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)
                            )
                          }
                        }
                      }
                    }
                  )
                }
                .testTag("recap_story_pager")
            ) { page ->
              val isActivePage = page == pagerState.currentPage
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                    val pageOffset = (
                      (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                      ).absoluteValue.coerceIn(0f, 1f)
                    // Current card at 1.0, neighbors at 0.9
                    val targetScale = if (noMotion) 1f else (1f - 0.10f * pageOffset)
                    scaleX = targetScale
                    scaleY = targetScale
                    alpha = 1f - 0.18f * pageOffset
                  },
                contentAlignment = Alignment.Center
              ) {
                val cardModifier = if (isActivePage) {
                  Modifier
                    .fillMaxHeight()
                    .drawWithContent {
                      captureGraphicsLayer.record {
                        this@drawWithContent.drawContent()
                      }
                      drawLayer(captureGraphicsLayer)
                    }
                } else {
                  Modifier.fillMaxHeight()
                }

                RecapStoryCard(
                  cardIndex = page,
                  data = recapData,
                  modifier = cardModifier.testTag("recap_story_card_$page"),
                  reducedMotion = noMotion,
                  isExportMode = false
                )
              }
            }
          }
        }

        // 4. Bottom Action Row: Share · Save image · Share all 6
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Button(
            onClick = {
              val data = recapData ?: return@Button
              if (isExporting) return@Button
              scope.launch {
                isExporting = true
                val ok = RecapExporter.shareSingleCard(
                  context = context,
                  data = data,
                  cardIndex = pagerState.currentPage,
                  graphicsLayer = captureGraphicsLayer
                )
                isExporting = false
                if (!ok) {
                  snackbarHostState.showSnackbar(context.getString(R.string.recap_share_failed_snackbar))
                }
              }
            },
            enabled = recapData != null && !isExporting,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF43C7F4),
              contentColor = Color(0xFF06101D)
            ),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 50.dp)
              .testTag("recap_share_button")
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = R.string.recap_share_button),
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1
            )
          }

          OutlinedButton(
            onClick = {
              val data = recapData ?: return@OutlinedButton
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                performSave(data)
              } else {
                val hasPerm = ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                  performSave(data)
                } else {
                  legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
              }
            },
            enabled = recapData != null && !isExporting,
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.34f)),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = Color.White.copy(alpha = 0.12f),
              contentColor = Color.White
            ),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 50.dp)
              .testTag("recap_save_image_button")
          ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = R.string.recap_save_button),
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          OutlinedButton(
            onClick = {
              val data = recapData ?: return@OutlinedButton
              if (isExporting) return@OutlinedButton
              scope.launch {
                isExporting = true
                val ok = RecapExporter.shareAllSixCards(context = context, data = data)
                isExporting = false
                if (!ok) {
                  snackbarHostState.showSnackbar(context.getString(R.string.recap_share_failed_snackbar))
                }
              }
            },
            enabled = recapData != null && !isExporting,
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = Color.White.copy(alpha = 0.08f),
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier
              .defaultMinSize(minHeight = 50.dp)
              .testTag("recap_share_all_button")
          ) {
            Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(id = R.string.recap_share_all_button),
              fontWeight = FontWeight.Bold,
              maxLines = 1
            )
          }
        }
      }

      // Non-blocking export overlay
      AnimatedVisibility(
        visible = isExporting,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center)
      ) {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color(0xFF0C1426).copy(alpha = 0.92f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            CircularProgressIndicator(
              color = Color(0xFF43C7F4),
              strokeWidth = 2.5.dp,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = stringResource(id = R.string.recap_exporting_label),
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = Color.White
            )
          }
        }
      }

      // Snackbar host ("Saved to Photos · បានរក្សាទុក")
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(bottom = 74.dp, start = 16.dp, end = 16.dp)
      ) { snackData ->
        Snackbar(
          snackbarData = snackData,
          shape = CircleShape,
          containerColor = Color(0xFF121C32),
          contentColor = Color.White
        )
      }
    }
  }
}

/**
 * Compact "Your Recap" entry card shown on HomeScreen and at the top of Statistics (MusicInsightsScreen).
 */
@Composable
fun RecapEntryCard(
  periodLabel: String?,
  darkTheme: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val shape = RoundedCornerShape(22.dp)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .background(Color(0xFF080C14))
      .background(
        Brush.linearGradient(
          colors = listOf(
            Color(0xFF43C7F4).copy(alpha = 0.34f),
            Color(0xFF7965FF).copy(alpha = 0.32f),
            Color(0xFFA855F7).copy(alpha = 0.28f)
          ),
          start = Offset(0f, 0f),
          end = Offset(720f, 240f)
        )
      )
      .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
          colors = listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.10f))
        ),
        shape = shape
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag("your_recap_entry_card")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
        modifier = Modifier.size(48.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = stringResource(id = R.string.recap_entry_open_cd),
            tint = Color.White,
            modifier = Modifier.size(24.dp)
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = stringResource(id = R.string.recap_entry_title),
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
          ),
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = if (!periodLabel.isNullOrBlank()) {
            "$periodLabel · ${stringResource(id = R.string.recap_entry_subtitle)}"
          } else {
            stringResource(id = R.string.recap_entry_subtitle)
          },
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.80f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f),
        modifier = Modifier.size(36.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

/**
 * Small start-of-month / start-of-year "Your recap is ready" prompt pill.
 */
@Composable
fun RecapReadyPromptBanner(
  prompt: RecapReadyPrompt,
  onOpen: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val shape = RoundedCornerShape(18.dp)
  val message = if (prompt.period == RecapPeriod.YEARLY) {
    stringResource(id = R.string.recap_ready_banner_year, prompt.periodLabel)
  } else {
    stringResource(id = R.string.recap_ready_banner_month, prompt.periodLabel)
  }

  Surface(
    shape = shape,
    color = Color(0xFF10192E),
    border = BorderStroke(1.dp, Color(0xFF43C7F4).copy(alpha = 0.55f)),
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onOpen)
      .testTag("recap_ready_prompt_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = Icons.Rounded.Celebration,
        contentDescription = null,
        tint = Color(0xFF43C7F4),
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = message,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      TextButton(
        onClick = onOpen,
        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
      ) {
        Text(
          text = stringResource(id = R.string.recap_ready_cta),
          fontWeight = FontWeight.ExtraBold,
          color = Color(0xFF43C7F4)
        )
      }
      IconButton(
        onClick = onDismiss,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("recap_ready_dismiss_button")
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = stringResource(id = R.string.recap_ready_dismiss_cd),
          tint = Color.White.copy(alpha = 0.7f),
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
