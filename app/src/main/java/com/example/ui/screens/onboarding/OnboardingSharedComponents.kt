package com.example.ui.screens.onboarding

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Song
import com.example.ui.image.AurixImagePipeline
import com.example.ui.image.rememberCrispArtworkRequest
import com.example.ui.theme.AurixDesignTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure swipe physics helpers matching Section 5:
 * - Drag starts after 8px (8dp) horizontal movement
 * - 30% resistance at first & last slide
 * - Release advances if drag exceeds 18% of viewport width
 */
object OnboardingSwipePhysics {
  const val DRAG_START_SLOP_DP = 8f
  const val EDGE_RESISTANCE = 0.30f
  const val ADVANCE_THRESHOLD_FRACTION = 0.18f

  fun applyEdgeResistance(rawDragPx: Float, currentIndex: Int, pageCount: Int): Float {
    val pushingLeftEdge = currentIndex == 0 && rawDragPx > 0f
    val pushingRightEdge = currentIndex == pageCount - 1 && rawDragPx < 0f
    return if (pushingLeftEdge || pushingRightEdge) rawDragPx * EDGE_RESISTANCE else rawDragPx
  }

  fun resolveTargetIndex(
    rawDragPx: Float,
    viewportWidthPx: Float,
    currentIndex: Int,
    pageCount: Int
  ): Int {
    if (viewportWidthPx <= 1f || pageCount <= 1) return currentIndex
    val threshold = viewportWidthPx * ADVANCE_THRESHOLD_FRACTION
    return when {
      rawDragPx <= -threshold && currentIndex < pageCount - 1 -> currentIndex + 1
      rawDragPx >= threshold && currentIndex > 0 -> currentIndex - 1
      else -> currentIndex
    }
  }
}

/**
 * Renders real album artwork via Coil over a smooth HSL gradient fallback so covers stay vivid
 * immediately on frame 1 and upgrade seamlessly as real thumbnails load.
 */
@Composable
fun OnboardingArtworkBox(
  artworkUrl: String?,
  baseHue: Float,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  fallbackContent: @Composable BoxScope.() -> Unit = {}
) {
  Box(
    modifier = modifier.background(AurixDesignTokens.coverGradientBrush(baseHue)),
    contentAlignment = Alignment.Center
  ) {
    fallbackContent()
    val url = artworkUrl?.trim().orEmpty()
    if (url.isNotEmpty()) {
      val request = rememberCrispArtworkRequest(model = url, crossfade = true)
      AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        filterQuality = AurixImagePipeline.ARTWORK_FILTER_QUALITY,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

/**
 * Reads the real local file extension (MP3, FLAC, M4A, WAV, OGG, AAC) for Slide 1's "On your phone" badge.
 */
fun extractLocalAudioFormatBadge(song: Song?): String {
  val candidates = listOfNotNull(song?.streamUrl, song?.title)
  val known = listOf("FLAC", "MP3", "M4A", "WAV", "OGG", "AAC", "OPUS")
  for (raw in candidates) {
    val clean = raw.substringBefore('?').substringBefore('#')
    val ext = clean.substringAfterLast('.', "").trim().uppercase()
    if (ext in known) return ext
  }
  return "MP3"
}

/**
 * Shared dark Liquid Glass modifier:
 * 1dp border rgba(255,255,255,.14), fill rgba(255,255,255,.07), backdrop blur 14dp on Android 12+.
 */
fun Modifier.onboardingGlass(
  shape: Shape,
  hazeState: HazeState?,
  fillColor: Color = AurixDesignTokens.GlassFill,
  borderColor: Color = AurixDesignTokens.GlassBorder,
  borderWidth: Dp = 1.dp
): Modifier = composed {
  val hazeStyle = remember(fillColor) {
    HazeStyle(
      backgroundColor = AurixDesignTokens.Background,
      tint = HazeTint(fillColor),
      blurRadius = AurixDesignTokens.GlassBlurRadius,
      noiseFactor = 0f
    )
  }
  val base = this.clip(shape)
  val blurred = if (hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    base.hazeChild(state = hazeState, style = hazeStyle)
  } else {
    base.background(fillColor, shape)
  }
  blurred.border(borderWidth, borderColor, shape)
}

/**
 * Animated Love (Favourite) heart toggle with spring pop feedback.
 */
@Composable
fun OnboardingLoveButton(
  isLoved: Boolean,
  loveCd: String,
  unloveCd: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  reducedMotion: Boolean = false
) {
  val heartScale = remember { Animatable(1f) }
  var prevLoved by remember { mutableStateOf(isLoved) }
  LaunchedEffect(isLoved, reducedMotion) {
    if (isLoved != prevLoved) {
      prevLoved = isLoved
      if (!reducedMotion) {
        heartScale.snapTo(0.72f)
        heartScale.animateTo(
          targetValue = 1.0f,
          animationSpec = tween(durationMillis = 360, easing = AurixDesignTokens.SpringEasing)
        )
      }
    }
  }
  val activeRed = Color(0xFFFF4D7D)
  Box(
    modifier = modifier
      .size(38.dp)
      .clip(androidx.compose.foundation.shape.CircleShape)
      .background(if (isLoved) activeRed.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
      .border(
        width = 1.dp,
        color = if (isLoved) activeRed.copy(alpha = 0.55f) else AurixDesignTokens.GlassBorder,
        shape = androidx.compose.foundation.shape.CircleShape
      )
      .semantics { contentDescription = if (isLoved) unloveCd else loveCd }
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = if (isLoved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
      contentDescription = null,
      tint = if (isLoved) activeRed else AurixDesignTokens.TextMuted,
      modifier = Modifier
        .size(18.dp)
        .graphicsLayer {
          val s = heartScale.value
          scaleX = s
          scaleY = s
        }
    )
  }
}

/**
 * Accessible keyboard/D-pad focus ring: 2dp solid white stroke offset 2dp outside the element.
 */
fun Modifier.onboardingFocusRing(shape: Shape): Modifier = composed {
  var isFocused by remember { mutableStateOf(false) }
  this
    .onFocusChanged { isFocused = it.isFocused }
    .drawWithContent {
      drawContent()
      if (isFocused) {
        val offsetPx = 2.dp.toPx()
        val strokePx = 2.dp.toPx()
        val outline = shape.createOutline(
          size = Size(size.width + offsetPx * 2f, size.height + offsetPx * 2f),
          layoutDirection = layoutDirection,
          density = this
        )
        withTransform({ translate(-offsetPx, -offsetPx) }) {
          drawOutline(
            outline = outline,
            color = Color.White,
            style = Stroke(width = strokePx)
          )
        }
      }
    }
}

/**
 * 4-bar animated equalizer matching Section 4 Slide 1 & Slide 4:
 * 3dp bars, Accent A color, scaleY 0.25 <-> 1, 800ms loop, staggered delays 0 / 200 / 400 / 100ms.
 */
@Composable
fun OnboardingEqualizer(
  accentColor: Color,
  isActive: Boolean,
  reducedMotion: Boolean,
  modifier: Modifier = Modifier,
  height: Dp = 16.dp
) {
  val delays = remember { intArrayOf(0, 200, 400, 100) }
  val staticHeights = remember { floatArrayOf(0.55f, 0.92f, 0.42f, 0.78f) }

  val transition = if (isActive && !reducedMotion) {
    rememberInfiniteTransition(label = "onboarding_eq")
  } else {
    null
  }
  val barStates = List(4) { idx ->
    transition?.animateFloat(
      initialValue = 0.25f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
        initialStartOffset = androidx.compose.animation.core.StartOffset(delays[idx])
      ),
      label = "onboarding_eq_bar_$idx"
    )
  }

  Row(
    modifier = modifier.height(height),
    horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    verticalAlignment = Alignment.Bottom
  ) {
    for (i in 0 until 4) {
      val state = barStates[i]
      Box(
        modifier = Modifier
          .width(3.dp)
          .height(height)
          .graphicsLayer {
            scaleY = state?.value ?: staticHeights[i]
            transformOrigin = TransformOrigin(0.5f, 1f)
          }
          .clip(RoundedCornerShape(99.dp))
          .background(accentColor)
      )
    }
  }
}

/**
 * 3D Sculpted Official AURIX Logo Orb (based 1:1 on R.drawable.ic_launcher_foreground geometry):
 * - Exact official AURIX logo silhouette: tilted (-12 deg) white glossy planet sphere, two tall
 *   dark oval eyes (#0B0C10) positioned slightly above center, wrapped by the signature Saturn ring
 *   (back half behind the sphere, front half cleanly in front).
 * - True 3D studio render & perspective motion:
 *   - Real 3D camera parallax sway (rotationX/rotationY + cameraDistance) + ±7dp float (3.6s loop)
 *   - Multi-layered 3D porcelain/ceramic sphere shading: top-left studio key light, glossy specular
 *     highlight dome, ambient bottom-right accent/violet bounce light, inner rim sheen, and a soft
 *     cast shadow thrown by the front Saturn ring directly onto the lower surface of the sphere
 *   - 3D beveled rotating Saturn ring (-12 deg with ±3.5 deg precession): metallic slate body
 *     (#3A3B42 -> #636B80), animated luminous accent/white/violet specular sweep (4.8s continuous
 *     orbit), and 3D depth-scaled orbiting light moonlets passing behind & in front of the sphere
 *   - Glossy eyes with dual specular catchlights, subtle 3D glance, and natural 4s blink cycle.
 */
@Composable
fun OnboardingOrbiMascot(
  isActive: Boolean,
  reducedMotion: Boolean,
  replaySeed: Int,
  accentColor: Color = AurixDesignTokens.AccentADefault,
  modifier: Modifier = Modifier
) {
  val enterScale = remember { Animatable(if (reducedMotion) 1f else 0.4f) }
  val enterRotate = remember { Animatable(if (reducedMotion) 0f else -25f) }

  LaunchedEffect(isActive, replaySeed, reducedMotion) {
    if (!isActive) return@LaunchedEffect
    if (reducedMotion) {
      enterScale.snapTo(1f)
      enterRotate.snapTo(0f)
    } else {
      enterScale.snapTo(0.4f)
      enterRotate.snapTo(-25f)
      launch {
        enterScale.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 1000, easing = AurixDesignTokens.SpringEasing)
        )
      }
      launch {
        enterRotate.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 1000, easing = AurixDesignTokens.SpringEasing)
        )
      }
    }
  }

  val loopTransition = if (isActive && !reducedMotion) {
    rememberInfiniteTransition(label = "orbi_mascot_loop")
  } else {
    null
  }
  val floatOffsetState = loopTransition?.animateFloat(
    initialValue = -7f,
    targetValue = 7f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orbi_float_y"
  )
  // Smooth 3D perspective sway (rotationY & rotationX) so the official AURIX logo orb feels sculptural
  val tilt3DYState = loopTransition?.animateFloat(
    initialValue = -11f,
    targetValue = 11f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orbi_3d_rot_y"
  )
  val tilt3DXState = loopTransition?.animateFloat(
    initialValue = 6f,
    targetValue = -6f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orbi_3d_rot_x"
  )
  val eyeBlinkScaleYState = loopTransition?.animateFloat(
    initialValue = 1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 4000
        1.0f at 0
        1.0f at 3800
        0.1f at 3880 // 97% of 4000ms
        1.0f at 3980
        1.0f at 4000
      },
      repeatMode = RepeatMode.Restart
    ),
    label = "orbi_blink"
  )
  // Continuous 360-degree orbital phase driving the rotating 3D Saturn ring shimmer & orbiting moonlets
  val saturnOrbitAngleState = loopTransition?.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "orbi_saturn_orbit"
  )
  // Subtle 3D tilt precession (-15.5deg <-> -8.5deg) centered on official ic_launcher_foreground -12deg tilt
  val saturnTiltState = loopTransition?.animateFloat(
    initialValue = -15.5f,
    targetValue = -8.5f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orbi_saturn_tilt"
  )

  Canvas(
    modifier = modifier.graphicsLayer {
      val s = enterScale.value
      scaleX = s
      scaleY = s
      rotationZ = enterRotate.value
      rotationY = tilt3DYState?.value ?: -6f
      rotationX = tilt3DXState?.value ?: 3f
      cameraDistance = 12f * density
      translationY = (floatOffsetState?.value ?: 0f) * density
    }
  ) {
    // Official AURIX ic_launcher_foreground coordinate system (108x108 scaled cleanly to fill canvas)
    val u = minOf(size.width, size.height) / 92f
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val center = Offset(cx, cy)

    // Proportional to ic_launcher_foreground: sphere r=20.5, ring rx=29.5 ry=7.8, eyes at (-4.5, -5.5) & (+4.5, -5.5)
    val sphereRadius = 24.8f * u
    val ringRx = 37.8f * u
    val ringRy = 10.6f * u
    val ringRect = Rect(
      left = cx - ringRx,
      top = cy - ringRy,
      right = cx + ringRx,
      bottom = cy + ringRy
    )

    val tiltDeg = saturnTiltState?.value ?: -12f
    val orbitDeg = saturnOrbitAngleState?.value ?: 42f
    val orbitRad = orbitDeg * (PI / 180.0)
    val secondOrbitRad = ((orbitDeg + 180f) % 360f) * (PI / 180.0)
    val floatNorm = ((floatOffsetState?.value ?: 0f) + 7f) / 14f // 0 at top, 1 at bottom

    fun ellipsePoint(rad: Double): Offset = Offset(
      x = cx + (cos(rad) * ringRx).toFloat(),
      y = cy + (sin(rad) * ringRy).toFloat()
    )

    val ringDarkMetallic = Color(0xFF2B2E38)
    val ringOfficialSlate = Color(0xFF3A3B42)
    val ringLightMetallic = Color(0xFF6B7285)

    // Animated 3D specular highlight sweeping around the rotating Saturn ring
    val sweepStart = ellipsePoint(orbitRad - 0.90)
    val sweepEnd = ellipsePoint(orbitRad + 0.90)
    val rotatingRingBrush = Brush.linearGradient(
      colors = listOf(
        ringOfficialSlate,
        accentColor.copy(alpha = 0.90f),
        Color.White.copy(alpha = 0.96f),
        AurixDesignTokens.AccentB.copy(alpha = 0.85f),
        ringOfficialSlate
      ),
      start = sweepStart,
      end = sweepEnd
    )

    // 0) Soft 3D ambient floor shadow floating beneath the orb (reacts to vertical bob)
    val shadowScale = 0.88f + 0.16f * floatNorm
    val shadowY = cy + sphereRadius + 9.5f * u
    drawOval(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.Black.copy(alpha = 0.42f * shadowScale),
          accentColor.copy(alpha = 0.14f * shadowScale),
          Color.Transparent
        ),
        center = Offset(cx, shadowY),
        radius = sphereRadius * 1.15f * shadowScale
      ),
      topLeft = Offset(cx - sphereRadius * 1.1f * shadowScale, shadowY - 5.2f * u),
      size = Size(sphereRadius * 2.2f * shadowScale, 10.4f * u)
    )

    // 1) BACK HALF of 3D rotating tilted Saturn ring (180..360 deg = upper arc behind planet)
    rotate(degrees = tiltDeg, pivot = center) {
      // Back ring metallic dark bevel
      drawArc(
        color = ringDarkMetallic.copy(alpha = 0.88f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = ringRect.topLeft,
        size = ringRect.size,
        style = Stroke(width = 6.2f * u, cap = StrokeCap.Round)
      )
      // Back ring inner metallic sheen
      drawArc(
        brush = rotatingRingBrush,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = ringRect.topLeft,
        size = ringRect.size,
        style = Stroke(width = 2.8f * u, cap = StrokeCap.Round),
        alpha = 0.58f
      )
      // Orbiting 3D moonlets passing behind the planet (smaller & softer for depth)
      if (sin(orbitRad) < 0.0) {
        val depth = (1.0 + 0.25 * sin(orbitRad)).toFloat()
        val pt = ellipsePoint(orbitRad)
        drawCircle(color = accentColor.copy(alpha = 0.28f), radius = 6.0f * u * depth, center = pt)
        drawCircle(color = Color.White.copy(alpha = 0.78f), radius = 2.6f * u * depth, center = pt)
      }
      if (sin(secondOrbitRad) < 0.0) {
        val depth = (1.0 + 0.25 * sin(secondOrbitRad)).toFloat()
        val pt2 = ellipsePoint(secondOrbitRad)
        drawCircle(color = AurixDesignTokens.AccentB.copy(alpha = 0.26f), radius = 4.8f * u * depth, center = pt2)
        drawCircle(color = AurixDesignTokens.AccentB.copy(alpha = 0.85f), radius = 2.0f * u * depth, center = pt2)
      }
    }

    // 2) 3D SCULPTED OFFICIAL WHITE SPHERE (studio key-light + core shadow + colored bounce rim light)
    // 2a) Base 3D sphere gradient (bright porcelain top-left -> cool slate shadow bottom-right)
    drawCircle(
      brush = Brush.radialGradient(
        colorStops = arrayOf(
          0.00f to Color(0xFFFFFFFF),
          0.42f to Color(0xFFF4F7FF),
          0.74f to Color(0xFFD2DCEE),
          0.92f to Color(0xFF9EAAC2),
          1.00f to Color(0xFF7D8AA6)
        ),
        center = Offset(cx - sphereRadius * 0.34f, cy - sphereRadius * 0.36f),
        radius = sphereRadius * 1.48f
      ),
      radius = sphereRadius,
      center = center
    )

    // 2b) Colored ambient bounce reflection on bottom-right limb from AURIX neon backdrop
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          accentColor.copy(alpha = 0.34f),
          AurixDesignTokens.AccentB.copy(alpha = 0.16f),
          Color.Transparent
        ),
        center = Offset(cx + sphereRadius * 0.56f, cy + sphereRadius * 0.54f),
        radius = sphereRadius * 0.92f
      ),
      radius = sphereRadius,
      center = center
    )

    // 2c) Top-left glossy 3D specular highlight dome
    rotate(degrees = -28f, pivot = Offset(cx - sphereRadius * 0.34f, cy - sphereRadius * 0.42f)) {
      drawOval(
        brush = Brush.radialGradient(
          colors = listOf(
            Color.White.copy(alpha = 0.95f),
            Color.White.copy(alpha = 0.40f),
            Color.Transparent
          ),
          center = Offset(cx - sphereRadius * 0.34f, cy - sphereRadius * 0.42f),
          radius = sphereRadius * 0.42f
        ),
        topLeft = Offset(cx - sphereRadius * 0.66f, cy - sphereRadius * 0.62f),
        size = Size(sphereRadius * 0.64f, sphereRadius * 0.38f)
      )
    }

    // 2d) Subtle Fresnel glass rim around the 3D sphere edge
    drawCircle(
      brush = Brush.linearGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.72f),
          Color.Transparent,
          accentColor.copy(alpha = 0.45f)
        ),
        start = Offset(cx - sphereRadius, cy - sphereRadius),
        end = Offset(cx + sphereRadius, cy + sphereRadius)
      ),
      radius = sphereRadius - 0.8f * u,
      center = center,
      style = Stroke(width = 1.4f * u)
    )

    // 2e) Soft 3D cast ring shadow falling across the lower front curve of the white sphere
    rotate(degrees = tiltDeg, pivot = center) {
      drawArc(
        color = Color(0xFF151C2E).copy(alpha = 0.22f),
        startAngle = 24f,
        sweepAngle = 132f,
        useCenter = false,
        topLeft = Offset(ringRect.left, ringRect.top + 3.2f * u),
        size = ringRect.size,
        style = Stroke(width = 5.4f * u, cap = StrokeCap.Round)
      )
    }

    // 3) OFFICIAL AURIX LOGO TALL OVAL EYES (tilted -12 deg like ic_launcher_foreground, glossy 3D catchlights)
    val blinkY = eyeBlinkScaleYState?.value ?: 1f
    val glanceX = ((tilt3DYState?.value ?: 0f) / 11f) * 1.15f * u
    val eyeRx = 3.05f * u
    val eyeRy = (5.25f * blinkY).coerceAtLeast(0.55f) * u
    val eyeY = cy - 4.4f * u
    val leftEyeCenter = Offset(cx - 5.6f * u + glanceX, eyeY)
    val rightEyeCenter = Offset(cx + 5.6f * u + glanceX, eyeY)

    rotate(degrees = tiltDeg, pivot = center) {
      listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
        // Subtle 3D glossy dark eye gradient (#1E2436 top -> #0B0C10 deep obsidian)
        drawOval(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF23293A), Color(0xFF0B0C10)),
            startY = eyePos.y - eyeRy,
            endY = eyePos.y + eyeRy
          ),
          topLeft = Offset(eyePos.x - eyeRx, eyePos.y - eyeRy),
          size = Size(eyeRx * 2f, eyeRy * 2f)
        )
        if (blinkY > 0.45f) {
          // Primary studio specular catchlight dot
          drawCircle(
            color = Color.White.copy(alpha = 0.94f),
            radius = 1.18f * u,
            center = Offset(eyePos.x - 0.95f * u, eyePos.y - 1.85f * u)
          )
          // Tiny secondary accent bounce catchlight
          drawCircle(
            color = accentColor.copy(alpha = 0.72f),
            radius = 0.58f * u,
            center = Offset(eyePos.x + 0.9f * u, eyePos.y + 1.65f * u)
          )
        }
      }
    }

    // 4) FRONT HALF of 3D rotating tilted Saturn ring (0..180 deg = front arc cleanly across lower sphere)
    rotate(degrees = tiltDeg, pivot = center) {
      // Soft neon atmospheric glow behind front ring
      drawArc(
        brush = rotatingRingBrush,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = ringRect.topLeft,
        size = ringRect.size,
        style = Stroke(width = 9.0f * u, cap = StrokeCap.Round),
        alpha = 0.24f
      )
      // Thick 3D sculpted ring lower bevel (official #3A3B42 metallic slate)
      drawArc(
        brush = Brush.verticalGradient(
          colors = listOf(ringLightMetallic, ringOfficialSlate, ringDarkMetallic),
          startY = ringRect.top,
          endY = ringRect.bottom
        ),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = ringRect.topLeft,
        size = ringRect.size,
        style = Stroke(width = 6.2f * u, cap = StrokeCap.Round)
      )
      // Rotating luminous 3D specular highlight band gliding smoothly along the front ring
      drawArc(
        brush = rotatingRingBrush,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(ringRect.left, ringRect.top - 0.75f * u),
        size = ringRect.size,
        style = Stroke(width = 2.6f * u, cap = StrokeCap.Round)
      )

      // 3D depth-scaled glowing moonlets + comet tail gliding in front of the sphere (sin >= 0)
      if (sin(orbitRad) >= 0.0) {
        val depth = (1.0 + 0.28 * sin(orbitRad)).toFloat()
        val tailPt = ellipsePoint(orbitRad - 0.18)
        val pt = ellipsePoint(orbitRad)
        drawCircle(color = accentColor.copy(alpha = 0.28f), radius = 4.2f * u * depth, center = tailPt)
        drawCircle(color = accentColor.copy(alpha = 0.44f), radius = 7.4f * u * depth, center = pt)
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color.White, accentColor),
            center = Offset(pt.x - 0.8f * u, pt.y - 0.8f * u),
            radius = 3.8f * u * depth
          ),
          radius = 3.4f * u * depth,
          center = pt
        )
      }
      if (sin(secondOrbitRad) >= 0.0) {
        val depth = (1.0 + 0.25 * sin(secondOrbitRad)).toFloat()
        val pt2 = ellipsePoint(secondOrbitRad)
        drawCircle(color = AurixDesignTokens.AccentB.copy(alpha = 0.36f), radius = 5.6f * u * depth, center = pt2)
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color.White, AurixDesignTokens.AccentB),
            center = Offset(pt2.x - 0.6f * u, pt2.y - 0.6f * u),
            radius = 2.8f * u * depth
          ),
          radius = 2.4f * u * depth,
          center = pt2
        )
      }
    }
  }
}

/**
 * Custom crisp icons with exact stroke weights from the spec.
 */
@Composable
fun OnboardingArrowLeftIcon(
  color: Color,
  modifier: Modifier = Modifier.size(22.dp),
  strokeDp: Dp = 2.4.dp
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = strokeDp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(w * 0.78f, h * 0.5f), Offset(w * 0.22f, h * 0.5f), stroke.width, StrokeCap.Round)
    val chevron = Path().apply {
      moveTo(w * 0.48f, h * 0.22f)
      lineTo(w * 0.20f, h * 0.50f)
      lineTo(w * 0.48f, h * 0.78f)
    }
    drawPath(chevron, color, style = stroke)
  }
}

@Composable
fun OnboardingArrowRightIcon(
  color: Color,
  modifier: Modifier = Modifier.size(20.dp),
  strokeDp: Dp = 2.6.dp
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = strokeDp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(w * 0.20f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), stroke.width, StrokeCap.Round)
    val chevron = Path().apply {
      moveTo(w * 0.52f, h * 0.22f)
      lineTo(w * 0.80f, h * 0.50f)
      lineTo(w * 0.52f, h * 0.78f)
    }
    drawPath(chevron, color, style = stroke)
  }
}

@Composable
fun OnboardingMagnifierIcon(
  color: Color = AurixDesignTokens.TextMuted,
  modifier: Modifier = Modifier.size(18.dp)
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val strokePx = 1.9.dp.toPx()
    drawCircle(
      color = color,
      radius = w * 0.31f,
      center = Offset(w * 0.43f, h * 0.43f),
      style = Stroke(width = strokePx)
    )
    drawLine(
      color = color,
      start = Offset(w * 0.66f, h * 0.66f),
      end = Offset(w * 0.88f, h * 0.88f),
      strokeWidth = strokePx,
      cap = StrokeCap.Round
    )
  }
}

@Composable
fun OnboardingSpeakerIcon(
  soundOn: Boolean,
  color: Color,
  modifier: Modifier = Modifier.size(20.dp)
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val body = Path().apply {
      moveTo(w * 0.16f, h * 0.38f)
      lineTo(w * 0.34f, h * 0.38f)
      lineTo(w * 0.56f, h * 0.20f)
      lineTo(w * 0.56f, h * 0.80f)
      lineTo(w * 0.34f, h * 0.62f)
      lineTo(w * 0.16f, h * 0.62f)
      close()
    }
    drawPath(body, color, style = stroke)
    if (soundOn) {
      drawArc(
        color = color,
        startAngle = -45f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(w * 0.52f, h * 0.32f),
        size = Size(w * 0.24f, h * 0.36f),
        style = stroke
      )
      drawArc(
        color = color,
        startAngle = -48f,
        sweepAngle = 96f,
        useCenter = false,
        topLeft = Offset(w * 0.58f, h * 0.18f),
        size = Size(w * 0.34f, h * 0.64f),
        style = stroke
      )
    }
  }
}

@Composable
fun OnboardingMusicNoteIcon(
  color: Color = Color.White,
  modifier: Modifier = Modifier.size(64.dp),
  strokeDp: Dp = 1.8.dp
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = strokeDp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Two beamed eighth notes
    val stemPath = Path().apply {
      moveTo(w * 0.36f, h * 0.72f)
      lineTo(w * 0.36f, h * 0.25f)
      lineTo(w * 0.78f, h * 0.18f)
      lineTo(w * 0.78f, h * 0.64f)
    }
    drawPath(stemPath, color, style = stroke)
    drawLine(
      color = color,
      start = Offset(w * 0.36f, h * 0.37f),
      end = Offset(w * 0.78f, h * 0.30f),
      strokeWidth = stroke.width,
      cap = StrokeCap.Round
    )
    drawOval(
      color = color,
      topLeft = Offset(w * 0.19f, h * 0.65f),
      size = Size(w * 0.18f, h * 0.14f),
      style = stroke
    )
    drawOval(
      color = color,
      topLeft = Offset(w * 0.61f, h * 0.58f),
      size = Size(w * 0.18f, h * 0.14f),
      style = stroke
    )
  }
}
