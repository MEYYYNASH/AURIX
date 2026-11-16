package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Song
import com.example.recommendation.BecauseYouListenedResult
import com.example.recommendation.VocalRecommendationItem
import com.example.ui.theme.HeartRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BecauseBaseBg = Color(0xFF0B0D17)
private val BecauseGlowGreen = Color(0xFF3DDC84)
private val BecauseGlowPurple = Color(0xFF7C5CFF)
private val BecauseGlowRed = Color(0xFFE0413F)

private val BecauseGlassFill = Color.White.copy(alpha = 0.08f)
private val BecauseGlassBorder = Color.White.copy(alpha = 0.16f)
private val BecauseGlassTopHighlight = Color.White.copy(alpha = 0.18f)
private val BecauseGlassShadow = Color.Black.copy(alpha = 0.25f)

private val BecauseAccentGreen = Color(0xFF3DDC84)
private val BecauseOnAccentDark = Color(0xFF07110B)

private val BecauseTitleColor = Color(0xFFFFFFFF)
private val BecauseArtistColor = Color(0xFFC3CADB)
private val BecauseReasonColor = Color(0xFFA4ADC2)

private val InterFont = FontFamily(
  Font(R.font.inter, FontWeight.Normal),
  Font(R.font.inter, FontWeight.Medium),
  Font(R.font.inter, FontWeight.SemiBold),
  Font(R.font.inter, FontWeight.Bold),
  Font(R.font.inter, FontWeight.ExtraBold)
)

@Composable
fun BecauseYouListenedSection(
  result: BecauseYouListenedResult,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  verifiedLyricsIds: Set<String> = emptySet(),
  onCheckLyrics: (Song) -> Unit = {},
  onPlayAll: (List<VocalRecommendationItem>) -> Unit,
  onPlayItem: (VocalRecommendationItem, List<VocalRecommendationItem>) -> Unit,
  onOpenItem: (VocalRecommendationItem, List<VocalRecommendationItem>) -> Unit = onPlayItem,
  onFeedback: (Song, Boolean) -> Unit,
  onOpenDetail: (Boolean) -> Unit
) {
  var selectedMixTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Sad Vibes, 1 = Chill Mix
  val rawActiveList = if (selectedMixTab == 0) result.sadVibes else result.chillMix

  // Track rows mid fade-out when disliked so they disappear smoothly
  val dismissingIds = remember { mutableStateListOf<String>() }
  val activeList = remember(rawActiveList, dismissingIds.toList()) {
    rawActiveList.filter { it.song.id !in dismissingIds }
  }

  val anyActiveRowPlaying = remember(activeList, currentSong?.id) {
    currentSong != null && activeList.any { it.song.id == currentSong.id }
  }

  val pages = remember(rawActiveList) { rawActiveList.chunked(3) }
  val pageCount = pages.size.coerceAtLeast(1)
  val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(selectedMixTab, pageCount) {
    if (pagerState.currentPage >= pageCount) {
      pagerState.scrollToPage((pageCount - 1).coerceAtLeast(0))
    }
  }
  LaunchedEffect(selectedMixTab) {
    pagerState.scrollToPage(0)
  }

  val sectionShape = RoundedCornerShape(28.dp)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .shadow(
        elevation = 14.dp,
        shape = sectionShape,
        ambientColor = Color.Black.copy(alpha = 0.35f),
        spotColor = Color.Black.copy(alpha = 0.35f)
      )
      .clip(sectionShape)
      .background(BecauseBaseBg)
      .border(1.dp, Color.White.copy(alpha = 0.14f), sectionShape)
      .testTag("because_you_listened_section")
  ) {
    // Soft ambient blurred glows behind the glass content (#3DDC84 top-left, #7C5CFF right, #E0413F bottom)
    Canvas(modifier = Modifier.matchParentSize()) {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            BecauseGlowGreen.copy(alpha = 0.26f),
            BecauseGlowGreen.copy(alpha = 0.08f),
            Color.Transparent
          ),
          center = Offset(size.width * 0.08f, size.height * 0.06f),
          radius = size.width * 0.72f
        ),
        radius = size.width * 0.72f,
        center = Offset(size.width * 0.08f, size.height * 0.06f)
      )
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            BecauseGlowPurple.copy(alpha = 0.26f),
            BecauseGlowPurple.copy(alpha = 0.09f),
            Color.Transparent
          ),
          center = Offset(size.width * 0.96f, size.height * 0.44f),
          radius = size.width * 0.68f
        ),
        radius = size.width * 0.68f,
        center = Offset(size.width * 0.96f, size.height * 0.44f)
      )
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            BecauseGlowRed.copy(alpha = 0.22f),
            BecauseGlowRed.copy(alpha = 0.07f),
            Color.Transparent
          ),
          center = Offset(size.width * 0.36f, size.height * 1.02f),
          radius = size.width * 0.68f
        ),
        radius = size.width * 0.68f,
        center = Offset(size.width * 0.36f, size.height * 1.02f)
      )
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // HEADER ROW
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(end = 10.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = BecauseAccentGreen,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = stringResource(id = R.string.because_section_title),
              fontFamily = InterFont,
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              lineHeight = 22.sp,
              color = BecauseTitleColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Text(
            text = stringResource(id = R.string.because_section_sub_30d),
            fontFamily = InterFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp,
            color = BecauseReasonColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (activeList.isNotEmpty()) {
          Box(
            modifier = Modifier
              .height(44.dp)
              .becauseGreenGlow(enabled = true, cornerRadius = 22.dp)
              .clip(CircleShape)
              .background(BecauseAccentGreen)
              .clickable { onPlayAll(activeList) }
              .padding(horizontal = 14.dp)
              .testTag("because_play_all_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = BecauseOnAccentDark,
                modifier = Modifier.size(17.dp)
              )
              Text(
                text = stringResource(id = R.string.because_play_all_compact),
                fontFamily = InterFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = BecauseOnAccentDark,
                maxLines = 1
              )
            }
          }
        }
      }

      // CHIPS ROW: 8dp gap, 44dp height, fully rounded, 13sp text
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val tabs = listOf(
          0 to stringResource(id = R.string.because_tab_sad_short),
          1 to stringResource(id = R.string.because_tab_chill_short)
        )
        tabs.forEach { (idx, label) ->
          val selected = selectedMixTab == idx
          val animBg by animateColorAsState(
            targetValue = if (selected) BecauseAccentGreen else BecauseGlassFill,
            animationSpec = if (reducedMotion) snap() else tween(200),
            label = "because_chip_bg_$idx"
          )
          val animTextColor by animateColorAsState(
            targetValue = if (selected) BecauseOnAccentDark else BecauseTitleColor,
            animationSpec = if (reducedMotion) snap() else tween(200),
            label = "because_chip_text_$idx"
          )

          Box(
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .becauseGreenGlow(enabled = selected, cornerRadius = 22.dp)
              .then(
                if (selected) {
                  Modifier
                    .clip(CircleShape)
                    .background(animBg)
                } else {
                  Modifier.becauseGlassSurface(
                    shape = CircleShape,
                    cornerRadius = 22.dp,
                    fillColor = animBg
                  )
                }
              )
              .clickable { selectedMixTab = idx }
              .padding(horizontal = 10.dp)
              .testTag(if (idx == 0) "because_tab_sad" else "because_tab_chill"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              fontFamily = InterFont,
              fontSize = 13.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
              color = animTextColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        if (activeList.isNotEmpty()) {
          Box(
            modifier = Modifier
              .height(44.dp)
              .becauseGlassSurface(shape = CircleShape, cornerRadius = 22.dp)
              .clickable { onOpenDetail(selectedMixTab == 0) }
              .padding(horizontal = 12.dp)
              .testTag("because_see_all_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = stringResource(id = R.string.because_all_count, activeList.size),
                fontFamily = InterFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BecauseAccentGreen,
                maxLines = 1
              )
              Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = BecauseAccentGreen,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      // RECOMMENDATION LIST (Slim glass rows + pager + pagination dots)
      if (activeList.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .becauseGlassSurface(shape = RoundedCornerShape(22.dp), cornerRadius = 22.dp)
            .padding(16.dp)
        ) {
          Text(
            text = stringResource(
              id = if (selectedMixTab == 0) R.string.because_empty_sad else R.string.because_empty_chill
            ),
            fontFamily = InterFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = BecauseReasonColor
          )
        }
      } else {
        HorizontalPager(
          state = pagerState,
          modifier = Modifier.fillMaxWidth(),
          pageSpacing = 12.dp,
          verticalAlignment = Alignment.Top
        ) { pageIdx ->
          val pageItems = pages.getOrNull(pageIdx).orEmpty()
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            pageItems.forEachIndexed { localIdx, item ->
              val globalIdx = (pageIdx * 3) + localIdx
              val isRowVisible = item.song.id !in dismissingIds
              val isCurrent = currentSong?.id == item.song.id
              val isAccentPlay = isCurrent || (!anyActiveRowPlaying && globalIdx == 0)

              LaunchedEffect(item.song.id) {
                onCheckLyrics(item.song)
              }

              AnimatedVisibility(
                visible = isRowVisible,
                enter = fadeIn(if (reducedMotion) snap() else tween(160)),
                exit = if (reducedMotion) {
                  fadeOut(snap())
                } else {
                  fadeOut(tween(180)) + shrinkVertically(tween(200))
                }
              ) {
                BecauseYouListenedSlimRow(
                  item = item,
                  isCurrent = isCurrent,
                  isPlaying = isPlaying,
                  isAccentPlay = isAccentPlay,
                  reducedMotion = reducedMotion,
                  onRowClick = { onOpenItem(item, activeList) },
                  onPlayInlineClick = { onPlayItem(item, activeList) },
                  onDislikeClick = {
                    if (item.song.id !in dismissingIds) {
                      dismissingIds.add(item.song.id)
                      coroutineScope.launch {
                        delay(if (reducedMotion) 20L else 190L)
                        onFeedback(item.song, false)
                        dismissingIds.remove(item.song.id)
                      }
                    }
                  }
                )
              }
            }
          }
        }

        // Pagination dots (active dot is a green pill)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          repeat(pageCount) { dotIdx ->
            val isActive = pagerState.currentPage == dotIdx
            val dotWidth by animateDpAsState(
              targetValue = if (isActive) 18.dp else 6.dp,
              animationSpec = if (reducedMotion) snap() else tween(220),
              label = "because_dot_w_$dotIdx"
            )
            val dotColor by animateColorAsState(
              targetValue = if (isActive) BecauseAccentGreen else Color.White.copy(alpha = 0.28f),
              animationSpec = if (reducedMotion) snap() else tween(220),
              label = "because_dot_c_$dotIdx"
            )
            Box(
              modifier = Modifier
                .padding(horizontal = 3.dp)
                .height(6.dp)
                .width(dotWidth)
                .clip(CircleShape)
                .background(dotColor)
                .clickable {
                  coroutineScope.launch { pagerState.animateScrollToPage(dotIdx) }
                }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BecauseYouListenedSlimRow(
  item: VocalRecommendationItem,
  isCurrent: Boolean,
  isPlaying: Boolean,
  isAccentPlay: Boolean,
  reducedMotion: Boolean,
  onRowClick: () -> Unit,
  onPlayInlineClick: () -> Unit,
  onDislikeClick: () -> Unit
) {
  val song = item.song
  val rowShape = RoundedCornerShape(22.dp)
  val rowBg = if (isCurrent) {
    Color.White.copy(alpha = 0.13f)
  } else {
    BecauseGlassFill
  }
  val rowBorder = if (isCurrent) {
    BecauseAccentGreen.copy(alpha = 0.65f)
  } else {
    BecauseGlassBorder
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .becauseGlassSurface(
        shape = rowShape,
        cornerRadius = 22.dp,
        fillColor = rowBg,
        borderColor = rowBorder
      )
      .clickable { onRowClick() }
      .padding(10.dp)
      .testTag("because_card_${song.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 64x64 cover (radius 16, soft shadow)
    Box(
      modifier = Modifier
        .size(64.dp)
        .shadow(
          elevation = 8.dp,
          shape = RoundedCornerShape(16.dp),
          ambientColor = Color.Black.copy(alpha = 0.35f),
          spotColor = Color.Black.copy(alpha = 0.35f)
        )
    ) {
      SongArtwork(
        song = song,
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 16.dp,
        iconSize = 24.dp
      )
      if (isCurrent) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 5.dp, vertical = 3.dp)
        ) {
          EqualizerBars(
            isPlaying = isPlaying,
            color = BecauseAccentGreen,
            reducedMotion = reducedMotion,
            barWidth = 2.dp,
            maxHeight = 9.dp
          )
        }
      }
    }

    // Center metadata column
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = song.title,
          fontFamily = InterFont,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          lineHeight = 18.sp,
          color = BecauseTitleColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
        Surface(
          shape = CircleShape,
          color = BecauseAccentGreen.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, BecauseAccentGreen.copy(alpha = 0.34f)),
          modifier = Modifier.testTag("because_match_badge_${song.id}")
        ) {
          Text(
            text = stringResource(id = R.string.because_match_short, item.matchPercent),
            fontFamily = InterFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 13.sp,
            color = BecauseAccentGreen,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
          )
        }
      }

      Text(
        text = song.artist,
        fontFamily = InterFont,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
        color = BecauseArtistColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Text(
        text = item.reason,
        fontFamily = InterFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 15.sp,
        color = BecauseReasonColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    // Right actions: 44dp dislike + 44dp round play button
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .clickable { onDislikeClick() }
          .testTag("because_dislike_${song.id}"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Outlined.ThumbDown,
          contentDescription = stringResource(id = R.string.because_dislike_cd),
          tint = BecauseReasonColor,
          modifier = Modifier.size(18.dp)
        )
      }

      Box(
        modifier = Modifier
          .size(44.dp)
          .becauseGreenGlow(enabled = isAccentPlay, cornerRadius = 22.dp)
          .then(
            if (isAccentPlay) {
              Modifier
                .clip(CircleShape)
                .background(BecauseAccentGreen)
            } else {
              Modifier.becauseGlassSurface(
                shape = CircleShape,
                cornerRadius = 22.dp,
                fillColor = Color.White.copy(alpha = 0.10f)
              )
            }
          )
          .clickable { onPlayInlineClick() }
          .testTag("because_play_${song.id}"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = stringResource(id = R.string.because_play_all_compact),
          tint = if (isAccentPlay) BecauseOnAccentDark else BecauseTitleColor,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

private fun Modifier.becauseGlassSurface(
  shape: Shape,
  cornerRadius: Dp,
  fillColor: Color = BecauseGlassFill,
  borderColor: Color = BecauseGlassBorder
): Modifier {
  return this
    .shadow(
      elevation = 8.dp,
      shape = shape,
      ambientColor = BecauseGlassShadow,
      spotColor = BecauseGlassShadow
    )
    .clip(shape)
    .background(fillColor)
    .border(1.dp, borderColor, shape)
    .drawWithContent {
      drawContent()
      val r = cornerRadius.toPx()
      drawRoundRect(
        brush = Brush.verticalGradient(
          colors = listOf(BecauseGlassTopHighlight, Color.Transparent),
          startY = 0f,
          endY = size.height * 0.24f
        ),
        cornerRadius = CornerRadius(r, r)
      )
    }
}

private fun Modifier.becauseGreenGlow(
  enabled: Boolean,
  cornerRadius: Dp
): Modifier {
  if (!enabled) return this
  return this.drawBehind {
    val expand = 8.dp.toPx()
    val r = cornerRadius.toPx() + expand
    drawRoundRect(
      brush = Brush.radialGradient(
        colors = listOf(
          BecauseAccentGreen.copy(alpha = 0.36f),
          BecauseAccentGreen.copy(alpha = 0.10f),
          Color.Transparent
        ),
        center = Offset(size.width / 2f, size.height / 2f),
        radius = (maxOf(size.width, size.height) / 2f) + expand
      ),
      topLeft = Offset(-expand, -expand),
      size = androidx.compose.ui.geometry.Size(size.width + expand * 2, size.height + expand * 2),
      cornerRadius = CornerRadius(r, r)
    )
  }
}


@Composable
fun BecauseYouListenedDetailScreen(
  title: String,
  subtitle: String,
  items: List<VocalRecommendationItem>,
  emptyMessage: String,
  currentSong: Song?,
  isPlaying: Boolean,
  darkTheme: Boolean,
  reducedMotion: Boolean,
  verifiedLyricsIds: Set<String> = emptySet(),
  onBack: () -> Unit,
  onPlayAll: () -> Unit,
  onShuffleAll: () -> Unit,
  onCheckLyrics: (Song) -> Unit,
  onPlayItem: (VocalRecommendationItem) -> Unit,
  onFavoriteClick: (Song) -> Unit,
  onFeedback: (Song, Boolean) -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .testTag("because_detail_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(
      top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
      bottom = if (currentSong != null) 205.dp else 135.dp
    )
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val iconBg = if (darkTheme) Color(0xFF151F30) else Color.White.copy(alpha = 0.85f)
        val iconBorder = if (darkTheme) Color(0xFF273348) else Color.White
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .clip(CircleShape)
            .background(iconBg)
            .border(1.dp, iconBorder, CircleShape)
            .testTag("because_detail_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(id = R.string.insights_back_cd),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(id = R.string.because_detail_badge),
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.8.sp
            ),
            color = AurixTealAccent
          )
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 24.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    if (items.isNotEmpty()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = onPlayAll,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
              containerColor = AurixTealAccent,
              contentColor = Color(0xFF06201C)
            ),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .testTag("because_detail_play_all")
          ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(id = R.string.because_play_all), fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = onShuffleAll,
            shape = CircleShape,
            border = BorderStroke(1.dp, AurixTealAccent.copy(alpha = 0.65f)),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 48.dp)
              .testTag("because_detail_shuffle_all")
          ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp), tint = AurixTealAccent)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(id = R.string.moodflow_label),
              fontWeight = FontWeight.Bold,
              color = AurixTealAccent
            )
          }
        }
      }

      itemsIndexed(items, key = { _, item -> item.song.id }) { idx, item ->
        LaunchedEffect(item.song.id) {
          onCheckLyrics(item.song)
        }
        val song = item.song
        val isCurrent = currentSong?.id == song.id
        GlassCard(
          darkTheme = darkTheme,
          cornerRadius = 20.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            SongRow(
              song = song,
              isPlaying = isPlaying,
              isCurrent = isCurrent,
              reducedMotion = reducedMotion,
              darkTheme = darkTheme,
              hasLyrics = song.id in verifiedLyricsIds,
              rank = idx + 1,
              showSourceBadge = false,
              onCheckLyrics = onCheckLyrics,
              onSongClick = { onPlayItem(item) },
              onFavoriteClick = { onFavoriteClick(song) }
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp, end = 6.dp, bottom = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Surface(
                  shape = CircleShape,
                  color = AurixTealAccent.copy(alpha = 0.16f)
                ) {
                  Text(
                    text = stringResource(id = R.string.because_match_badge, item.matchPercent),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AurixTealAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
                Text(
                  text = item.reason,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Row {
                val liked = item.feedback == true
                val disliked = item.feedback == false
                IconButton(
                  onClick = { onFeedback(song, true) },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(
                    imageVector = if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = stringResource(id = R.string.because_like_cd),
                    tint = if (liked) AurixTealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
                IconButton(
                  onClick = { onFeedback(song, false) },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(
                    imageVector = if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = stringResource(id = R.string.because_dislike_cd),
                    tint = if (disliked) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }
      }
    } else {
      item {
        GlassCard(darkTheme = darkTheme, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
          Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp)
          )
        }
      }
    }
  }
}
