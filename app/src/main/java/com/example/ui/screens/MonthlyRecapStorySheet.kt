package com.example.ui.screens

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.SuccessResult
import com.example.R
import com.example.recommendation.MonthlyRecapSummary
import com.example.ui.screens.onboarding.OnboardingOrbiMascot
import com.example.ui.theme.AurixDesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val RECAP_SLIDE_COUNT = 4

@Composable
fun MonthlyRecapStoryOverlay(
  recap: MonthlyRecapSummary,
  accentColor: Color,
  reducedMotion: Boolean,
  onClose: () -> Unit
) {
  BackHandler { onClose() }

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var currentSlide by remember { mutableIntStateOf(0) }
  var isExporting by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(AurixDesignTokens.Background)
      .pointerInput(currentSlide) {
        var totalDrag = 0f
        detectHorizontalDragGestures(
          onDragStart = { totalDrag = 0f },
          onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
          onDragEnd = {
            if (totalDrag < -70f && currentSlide < RECAP_SLIDE_COUNT - 1) {
              currentSlide += 1
            } else if (totalDrag > 70f && currentSlide > 0) {
              currentSlide -= 1
            }
          }
        )
      }
      .testTag("monthly_recap_story_overlay")
  ) {
    // Ambient neon aurora glow matching AURIX obsidian Liquid Glass theme
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = 0.24f), Color.Transparent),
            radius = 900f
          )
        )
    )
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color.Transparent,
              AurixDesignTokens.AccentB.copy(alpha = 0.16f),
              AurixDesignTokens.Background.copy(alpha = 0.94f)
            )
          )
        )
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top Story Progress Bars + Header
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          for (i in 0 until RECAP_SLIDE_COUNT) {
            val fill by animateFloatAsState(
              targetValue = if (i <= currentSlide) 1f else 0f,
              animationSpec = tween(if (reducedMotion) 0 else 320),
              label = "recap_progress_$i"
            )
            Box(
              modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
            ) {
              Box(
                modifier = Modifier
                  .fillMaxHeight()
                  .fillMaxWidth(fill)
                  .background(Color.White)
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OnboardingOrbiMascot(
              isActive = true,
              reducedMotion = reducedMotion,
              replaySeed = 0,
              accentColor = accentColor,
              modifier = Modifier.size(34.dp)
            )
            Column {
              Text(
                text = stringResource(id = R.string.recap_story_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
              )
              Text(
                text = recap.monthLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f)
              )
            }
          }

          IconButton(
            onClick = onClose,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .size(40.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.10f))
              .testTag("recap_close_button")
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(id = R.string.cd_close_sheet),
              tint = Color.White
            )
          }
        }
      }

      // Center Story Slide Stage
      AnimatedContent(
        targetState = currentSlide,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
        label = "recap_slide_content",
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(vertical = 12.dp)
      ) { slide ->
        when (slide) {
          0 -> RecapSlideOverview(recap = recap, accentColor = accentColor)
          1 -> RecapSlideTopArtistAndTrack(recap = recap, accentColor = accentColor)
          2 -> RecapSlideMusicDna(recap = recap, accentColor = accentColor)
          else -> RecapSlideShareSummary(recap = recap, accentColor = accentColor, reducedMotion = reducedMotion)
        }
      }

      // Bottom Navigation / Share Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (currentSlide > 0) {
          Surface(
            onClick = { currentSlide -= 1 },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            modifier = Modifier
              .size(54.dp)
              .testTag("recap_prev_button")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Previous slide",
                tint = Color.White
              )
            }
          }
        }

        if (currentSlide < RECAP_SLIDE_COUNT - 1) {
          Button(
            onClick = { currentSlide += 1 },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = accentColor,
              contentColor = Color(0xFF06101C)
            ),
            modifier = Modifier
              .weight(1f)
              .height(54.dp)
              .testTag("recap_next_button")
          ) {
            Text(
              text = stringResource(id = R.string.onboarding_next),
              style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
          }
        } else {
          Button(
            onClick = {
              if (isExporting) return@Button
              isExporting = true
              scope.launch {
                val res = MonthlyRecapCardExporter.shareRecapStory(context, recap, accentColor)
                isExporting = false
                res.onFailure {
                  Toast.makeText(context, "Could not share recap story", Toast.LENGTH_SHORT).show()
                }
              }
            },
            enabled = !isExporting,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = accentColor,
              contentColor = Color(0xFF06101C)
            ),
            modifier = Modifier
              .weight(1f)
              .height(54.dp)
              .testTag("recap_share_button")
          ) {
            if (isExporting) {
              CircularProgressIndicator(strokeWidth = 2.dp, color = Color(0xFF06101C), modifier = Modifier.size(18.dp))
            } else {
              Icon(imageVector = Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(19.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(id = R.string.recap_share_story),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
              )
            }
          }

          Surface(
            onClick = {
              if (isExporting) return@Surface
              isExporting = true
              scope.launch {
                val res = MonthlyRecapCardExporter.saveRecapToGallery(context, recap, accentColor)
                isExporting = false
                res.onSuccess {
                  Toast.makeText(context, "Recap saved to Gallery", Toast.LENGTH_SHORT).show()
                }.onFailure {
                  Toast.makeText(context, "Could not save recap", Toast.LENGTH_SHORT).show()
                }
              }
            },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
            modifier = Modifier
              .size(54.dp)
              .testTag("recap_save_button")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "Save to gallery",
                tint = Color.White
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RecapSlideOverview(recap: MonthlyRecapSummary, accentColor: Color) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Surface(
      shape = CircleShape,
      color = accentColor.copy(alpha = 0.16f),
      border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
      Text(
        text = recap.peakVibeBadge.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp),
        color = accentColor,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "${recap.totalListeningMinutes}",
      style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, fontWeight = FontWeight.ExtraBold),
      color = Color.White
    )
    Text(
      text = stringResource(id = R.string.recap_minutes_listened),
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      color = Color.White.copy(alpha = 0.78f)
    )

    Spacer(modifier = Modifier.height(28.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      RecapGlassMetricTile(
        icon = Icons.Rounded.MusicNote,
        value = "${recap.totalPlays}",
        label = stringResource(id = R.string.recap_total_plays),
        accent = accentColor,
        modifier = Modifier.weight(1f)
      )
      RecapGlassMetricTile(
        icon = Icons.Rounded.LocalFireDepartment,
        value = "${recap.longestStreakDays}d",
        label = stringResource(id = R.string.recap_streak_days),
        accent = Color(0xFFF59E0B),
        modifier = Modifier.weight(1f)
      )
      RecapGlassMetricTile(
        icon = Icons.Rounded.Star,
        value = "${recap.uniqueArtistsCount}",
        label = stringResource(id = R.string.recap_artists_count),
        accent = AurixDesignTokens.AccentB,
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun RecapSlideTopArtistAndTrack(recap: MonthlyRecapSummary, accentColor: Color) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(id = R.string.recap_top_artist_label),
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
      color = accentColor
    )
    Spacer(modifier = Modifier.height(12.dp))

    Box(
      modifier = Modifier
        .size(168.dp)
        .clip(RoundedCornerShape(28.dp))
        .background(Color.White.copy(alpha = 0.08f))
        .border(1.5.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(28.dp)),
      contentAlignment = Alignment.Center
    ) {
      val artUrl = recap.topTrack?.song?.artworkUrl?.takeIf { it.isNotBlank() }
        ?: recap.topArtistArtworkUrl
      if (artUrl.isNotBlank()) {
        AsyncImage(
          model = artUrl,
          contentDescription = recap.topArtistName,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Icon(
          imageVector = Icons.Rounded.GraphicEq,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(64.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = recap.topArtistName,
      style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
      color = Color.White,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    if (recap.topArtistPlays > 0) {
      Text(
        text = "${recap.topArtistPlays} plays this month",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.72f)
      )
    }

    recap.topTrack?.let { top ->
      Spacer(modifier = Modifier.height(22.dp))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(22.dp))
          .background(Color.White.copy(alpha = 0.07f))
          .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
          .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = stringResource(id = R.string.recap_top_track_label),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = AurixDesignTokens.AccentB
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = top.song.title,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "${top.song.artist} · ${top.playCount}x",
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.72f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun RecapSlideMusicDna(recap: MonthlyRecapSummary, accentColor: Color) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(id = R.string.recap_dna_label),
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
      color = accentColor
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = recap.dominantMood,
      style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
      color = Color.White
    )
    Spacer(modifier = Modifier.height(22.dp))

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(Color.White.copy(alpha = 0.07f))
        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      RecapDnaBar("Energy", recap.energyPercent, Color(0xFF43C7F4))
      RecapDnaBar("Chill", recap.chillPercent, Color(0xFF7965FF))
      RecapDnaBar("Romantic", recap.romanticPercent, Color(0xFFEC4899))
      RecapDnaBar("Upbeat", recap.happyPercent, Color(0xFF22C55E))
    }

    if (recap.topGenres.isNotEmpty()) {
      Spacer(modifier = Modifier.height(18.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        recap.topGenres.take(3).forEach { (genre, _) ->
          Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = genre,
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              textAlign = TextAlign.Center,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RecapSlideShareSummary(
  recap: MonthlyRecapSummary,
  accentColor: Color,
  reducedMotion: Boolean
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(28.dp))
      .background(
        Brush.linearGradient(
          colors = listOf(Color(0xFF10192D), Color(0xFF0A0F1D))
        )
      )
      .border(1.2.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
      .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column {
        Text(
          text = "AURIX RECAP",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp),
          color = accentColor
        )
        Text(
          text = recap.monthLabel,
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
          color = Color.White
        )
      }
      OnboardingOrbiMascot(
        isActive = true,
        reducedMotion = reducedMotion,
        replaySeed = 0,
        accentColor = accentColor,
        modifier = Modifier.size(46.dp)
      )
    }

    HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column {
        Text("TOP ARTIST", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(
          text = recap.topArtistName,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text("VIBE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(
          text = "${recap.dominantMood} · ${recap.peakVibeBadge}",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = accentColor
        )
      }
    }

    if (recap.topTracks.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "TOP TRACKS",
          style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
          color = Color.White.copy(alpha = 0.6f)
        )
        recap.topTracks.take(4).forEachIndexed { idx, entry ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = "${idx + 1}",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
              color = accentColor,
              modifier = Modifier.width(18.dp)
            )
            Text(
              text = "${entry.song.title} — ${entry.song.artist}",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Color.White.copy(alpha = 0.06f))
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "${recap.totalListeningMinutes} min · ${recap.totalPlays} plays",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = Color.White
      )
      Text(
        text = "Streak ${recap.longestStreakDays}d",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = Color(0xFFF59E0B)
      )
    }
  }
}

@Composable
private fun RecapGlassMetricTile(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  value: String,
  label: String,
  accent: Color,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(20.dp))
      .background(Color.White.copy(alpha = 0.07f))
      .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
      .padding(vertical = 14.dp, horizontal = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
      color = Color.White
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White.copy(alpha = 0.7f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun RecapDnaBar(label: String, percent: Int, color: Color) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
      Text("$percent%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = color)
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.12f))
    ) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth((percent / 100f).coerceIn(0.04f, 1f))
          .clip(CircleShape)
          .background(color)
      )
    }
  }
}

/**
 * Renders a crisp 1080x1920 9:16 PNG Story poster of the user's Monthly AURIX Recap
 * and exports via FileProvider or MediaStore Gallery with zero storage permissions.
 */
object MonthlyRecapCardExporter {
  suspend fun renderRecapStoryBitmap(
    context: Context,
    recap: MonthlyRecapSummary,
    accentColor: Color
  ): Bitmap = withContext(Dispatchers.Default) {
    val w = 1080
    val h = 1920
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)

    val accentInt = android.graphics.Color.argb(
      255,
      (accentColor.red * 255).toInt(),
      (accentColor.green * 255).toInt(),
      (accentColor.blue * 255).toInt()
    )
    val violetInt = android.graphics.Color.rgb(121, 101, 255)

    // Obsidian background + twin aurora blooms
    canvas.drawColor(android.graphics.Color.rgb(8, 12, 20))
    val glow1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(
        w * 0.22f, h * 0.18f, w * 0.85f,
        android.graphics.Color.argb(110, android.graphics.Color.red(accentInt), android.graphics.Color.green(accentInt), android.graphics.Color.blue(accentInt)),
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow1)
    val glow2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(
        w * 0.85f, h * 0.72f, w * 0.82f,
        android.graphics.Color.argb(95, 121, 101, 255),
        android.graphics.Color.TRANSPARENT,
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow2)

    val titleTypeface = runCatching {
      ResourcesCompat.getFont(context, R.font.sora)?.let { Typeface.create(it, Typeface.BOLD) }
    }.getOrNull() ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val bodyTypeface = runCatching { ResourcesCompat.getFont(context, R.font.kantumruy_pro_semibold) }.getOrNull()
      ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    // Glass Frame Card
    val cardRect = RectF(76f, 160f, w - 76f, h - 160f)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f, cardRect.top, w.toFloat(), cardRect.bottom,
        intArrayOf(android.graphics.Color.argb(215, 18, 25, 44), android.graphics.Color.argb(235, 10, 14, 24)),
        null, Shader.TileMode.CLAMP
      )
    }
    canvas.drawRoundRect(cardRect, 64f, 64f, cardPaint)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 3f
      color = android.graphics.Color.argb(72, 255, 255, 255)
    }
    canvas.drawRoundRect(cardRect, 64f, 64f, borderPaint)

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = accentInt
      textSize = 36f
      typeface = titleTypeface
      letterSpacing = 0.12f
    }
    canvas.drawText("AURIX MONTHLY RECAP", 140f, 275f, labelPaint)

    val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      textSize = 74f
      typeface = titleTypeface
    }
    canvas.drawText(recap.monthLabel, 140f, 368f, monthPaint)

    // Hero Minutes stat
    val bigStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      textSize = 148f
      typeface = titleTypeface
    }
    canvas.drawText("${recap.totalListeningMinutes} min", 140f, 560f, bigStatPaint)

    val subStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(200, 226, 234, 250)
      textSize = 42f
      typeface = bodyTypeface
    }
    canvas.drawText(
      "${recap.totalPlays} plays · ${recap.longestStreakDays}d streak · ${recap.peakVibeBadge}",
      140f, 635f, subStatPaint
    )

    // Top Artist & Mood
    val secHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(165, 255, 255, 255)
      textSize = 30f
      typeface = titleTypeface
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      textSize = 54f
      typeface = bodyTypeface
    }
    canvas.drawText("TOP ARTIST", 140f, 780f, secHeadPaint)
    canvas.drawText(recap.topArtistName.take(26), 140f, 850f, valuePaint)

    canvas.drawText("MUSIC DNA MOOD", 140f, 970f, secHeadPaint)
    val moodPaint = Paint(valuePaint).apply { color = accentInt }
    canvas.drawText("${recap.dominantMood}  ·  Energy ${recap.energyPercent}%", 140f, 1040f, moodPaint)

    // Top Tracks List
    canvas.drawText("TOP TRACKS", 140f, 1175f, secHeadPaint)
    var y = 1255f
    val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      textSize = 40f
      typeface = bodyTypeface
    }
    recap.topTracks.take(4).forEachIndexed { idx, entry ->
      canvas.drawText("${idx + 1}. ${entry.song.title.take(30)}", 140f, y, rowPaint)
      y += 88f
    }

    // Footer brand pill
    val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.argb(220, 247, 249, 255)
      textSize = 38f
      typeface = titleTypeface
      letterSpacing = 0.14f
    }
    canvas.drawText("AURIX MUSIC · v2.4.8", 140f, h - 225f, footPaint)

    // Accent bar on bottom-left
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(140f, 0f, 460f, 0f, accentInt, violetInt, Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(RectF(140f, h - 280f, 420f, h - 270f), 8f, 8f, barPaint)

    bmp
  }

  suspend fun shareRecapStory(
    context: Context,
    recap: MonthlyRecapSummary,
    accentColor: Color
  ): Result<Unit> = runCatching {
    val bmp = renderRecapStoryBitmap(context, recap, accentColor)
    val uri = withContext(Dispatchers.IO) {
      val dir = File(context.cacheDir, "shared_lyrics").apply { if (!exists()) mkdirs() }
      val file = File(dir, "aurix_recap_${System.currentTimeMillis()}.png")
      FileOutputStream(file).use { fos ->
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
      }
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_TEXT, "My ${recap.monthLabel} AURIX Recap — ${recap.totalListeningMinutes} mins listened! Top artist: ${recap.topArtistName}")
      clipData = ClipData.newRawUri("AURIX Recap", uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share AURIX Recap").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
  }

  suspend fun saveRecapToGallery(
    context: Context,
    recap: MonthlyRecapSummary,
    accentColor: Color
  ): Result<Unit> = runCatching {
    val bmp = renderRecapStoryBitmap(context, recap, accentColor)
    withContext(Dispatchers.IO) {
      val filename = "AURIX_Recap_${System.currentTimeMillis()}.png"
      val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AURIX MUSIC")
          put(MediaStore.Images.Media.IS_PENDING, 1)
        }
      }
      val col = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
      } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
      }
      val uri = context.contentResolver.insert(col, values) ?: error("Failed to insert MediaStore image")
      context.contentResolver.openOutputStream(uri)?.use { os ->
        bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
        os.flush()
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
      }
    }
  }
}
