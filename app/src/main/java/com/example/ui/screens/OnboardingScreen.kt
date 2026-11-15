package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.launch

data class OnboardingPage(
  val title: String,
  val description: String,
  val icon: ImageVector
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
  val pages = listOf(
    OnboardingPage(stringResource(R.string.onboarding_p1_title), stringResource(R.string.onboarding_p1_desc), Icons.Rounded.MusicNote),
    OnboardingPage(stringResource(R.string.onboarding_p2_title), stringResource(R.string.onboarding_p2_desc), Icons.Rounded.Equalizer),
    OnboardingPage(stringResource(R.string.onboarding_p3_title), stringResource(R.string.onboarding_p3_desc), Icons.Rounded.Palette),
    OnboardingPage(stringResource(R.string.onboarding_p4_title), stringResource(R.string.onboarding_p4_desc), Icons.Rounded.PlayArrow)
  )

  val pagerState = rememberPagerState(pageCount = { pages.size })
  val scope = rememberCoroutineScope()

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(onClick = onFinished) {
          Text(stringResource(R.string.onboarding_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
          text = "${pagerState.currentPage + 1} / ${pages.size}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(32.dp))

      HorizontalPager(
        state = pagerState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) { pageIndex ->
        val page = pages[pageIndex]
        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          if (pageIndex == 0) {
            AurixBrandLogo(
              size = 200.dp,
              cornerRadius = 40.dp,
              animated = true,
              reducedMotion = false
            )
          } else {
            Box(
              modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                  Brush.linearGradient(
                    colors = listOf(
                      MaterialTheme.colorScheme.primary,
                      MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    )
                  )
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(76.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(48.dp))

          Text(
            text = page.title,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
          )

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          repeat(pages.size) { index ->
            val isSelected = pagerState.currentPage == index
            Box(
              modifier = Modifier
                .height(8.dp)
                .width(if (isSelected) 24.dp else 8.dp)
                .clip(CircleShape)
                .background(
                  if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            )
          }
        }

        if (pagerState.currentPage == pages.size - 1) {
          Button(
            onClick = onFinished,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.height(56.dp)
          ) {
            Text(stringResource(R.string.onboarding_get_started), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
          }
        } else {
          FloatingActionButton(
            onClick = {
              scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
              }
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next", tint = Color.White)
          }
        }
      }
    }
  }
}
