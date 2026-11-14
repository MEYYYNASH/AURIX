package com.example.lyrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.exp

/**
 * Real-time Choreographer-driven RecyclerView lyrics view with a continuous morphing
 * Liquid Glass Box that smoothly glides and resizes between active lyric lines.
 */
class SyncedLyricsView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  companion object {
    private const val ACTIVE_TOP_FRACTION = 0.37f // Keeps active line 35-40% from top
    private const val JUMP_THRESHOLD_MS = 1200L
    private const val BIG_JUMP_LINE_THRESHOLD = 6
    private const val MANUAL_SCROLL_PAUSE_MS = 3000L
    private const val LEAD_TOLERANCE_MS = 150L
    private val SCROLL_EASE = PathInterpolator(0.22f, 1f, 0.36f, 1f)
  }

  /** Single hookup point returning the player's current playback position in milliseconds. */
  var positionProvider: (() -> Long)? = null

  /** Optional callback invoked when the user taps a synced lyric line. */
  var onLineClick: ((timestampMs: Long) -> Unit)? = null

  /** Manual timing correction offset in milliseconds. */
  var offsetMs: Long = 0L

  private var reducedMotionOverride: Boolean? = null
  private var currentRawLrc: String? = null
  private var document = ParsedLyricsDocument(emptyList(), isSynced = false)

  private val recyclerView: RecyclerView
  private val layoutManager: LinearLayoutManager
  private val adapter: SyncedLyricsAdapter
  private val choreographer = Choreographer.getInstance()

  private var isFrameLoopRunning = false
  private var lastFrameTimeNanos = 0L
  private var lastRawPositionMs = Long.MIN_VALUE
  private var userDragging = false
  private var resumeAutoFollowAtMs = 0L
  private var needsAutoFollowResume = false
  private var pendingViewportAlign = false

  // Morphing Liquid Glass Box state (tracks relative offsets from the active ViewHolder so it
  // moves in lockstep with RecyclerView scrolling while gliding between lines)
  private var glassBoxInitialized = false
  private var glassTopOffset = 0f
  private var glassBottomOffset = 0f
  private var glassAlpha = 0f
  private var lastDrawnBoxTop = 0f
  private var lastDrawnBoxBottom = 0f
  private var hasLastDrawnBox = false

  private val glassBoxRect = RectF()
  private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
  }
  private val glassSpecularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
  }

  private fun createSmoothScroller(targetPos: Int): LinearSmoothScroller {
    return object : LinearSmoothScroller(context) {
      override fun calculateDtToFit(
        viewStart: Int,
        viewEnd: Int,
        boxStart: Int,
        boxEnd: Int,
        snapPreference: Int
      ): Int {
        val visibleHeight = recyclerView.height.takeIf { it > 0 } ?: (boxEnd - boxStart)
        val viewHeight = viewEnd - viewStart
        val desiredTop = (visibleHeight * ACTIVE_TOP_FRACTION - viewHeight * 0.5f).toInt()
        return desiredTop - viewStart
      }

      override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
        return 125f / displayMetrics.densityDpi
      }
    }.apply { targetPosition = targetPos }
  }

  private val frameCallback = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
      if (!isFrameLoopRunning) return
      val dtSeconds = if (lastFrameTimeNanos == 0L) {
        0.016f
      } else {
        ((frameTimeNanos - lastFrameTimeNanos) * 1e-9f).coerceIn(0.001f, 0.064f)
      }
      lastFrameTimeNanos = frameTimeNanos
      tickFrame(dtSeconds)
      choreographer.postFrameCallback(this)
    }
  }

  init {
    val dp = resources.displayMetrics.density
    layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
    adapter = SyncedLyricsAdapter { line ->
      onLineClick?.invoke((line.timestampMs - offsetMs).coerceAtLeast(0L))
    }

    clipChildren = false
    clipToPadding = false
    recyclerView = RecyclerView(context).apply {
      layoutParams = LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      layoutManager = this@SyncedLyricsView.layoutManager
      adapter = this@SyncedLyricsView.adapter
      clipChildren = false
      clipToPadding = false
      overScrollMode = OVER_SCROLL_NEVER
      isVerticalFadingEdgeEnabled = false
      itemAnimator = null
      setPadding((4f * dp).toInt(), (16f * dp).toInt(), (4f * dp).toInt(), (140f * dp).toInt())

      // Draw the morphing Liquid Glass Box behind the lyric text
      addItemDecoration(object : RecyclerView.ItemDecoration() {
        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
          drawMorphingGlassBox(canvas, parent, dp)
        }
      })
    }

    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
      override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
        when (newState) {
          RecyclerView.SCROLL_STATE_DRAGGING -> {
            userDragging = true
            resumeAutoFollowAtMs = Long.MAX_VALUE
            needsAutoFollowResume = true
          }
          RecyclerView.SCROLL_STATE_SETTLING -> {
            if (userDragging) {
              resumeAutoFollowAtMs = Long.MAX_VALUE
            }
          }
          RecyclerView.SCROLL_STATE_IDLE -> {
            if (userDragging) {
              userDragging = false
              resumeAutoFollowAtMs = SystemClock.uptimeMillis() + MANUAL_SCROLL_PAUSE_MS
              needsAutoFollowResume = true
            }
          }
        }
      }
    })

    addView(recyclerView)
    applyReducedMotionFlag()
  }

  private fun drawMorphingGlassBox(canvas: Canvas, parent: RecyclerView, dp: Float) {
    if (!adapter.isSyncedLyrics()) return
    val activeIdx = adapter.getActiveIndex()
    if (activeIdx < 0) return
    val activeView = parent.findViewHolderForAdapterPosition(activeIdx)?.itemView ?: return

    val targetTop = activeView.top.toFloat()
    val targetBottom = activeView.bottom.toFloat()
    val boxLeft = activeView.left.toFloat()
    val boxRight = activeView.right.toFloat()

    val drawTop = targetTop + glassTopOffset
    val drawBottom = targetBottom + glassBottomOffset
    lastDrawnBoxTop = drawTop
    lastDrawnBoxBottom = drawBottom
    hasLastDrawnBox = true

    val alphaFactor = glassAlpha.coerceIn(0f, 1f)
    if (alphaFactor <= 0.01f || drawBottom <= drawTop || boxRight <= boxLeft) return

    val cornerRadius = 20f * dp
    glassBoxRect.set(boxLeft, drawTop, boxRight, drawBottom)

    val accent = adapter.getAccentColor()
    val topGlassColor = Color.argb((48 * alphaFactor).toInt(), 255, 255, 255)
    val midGlassColor = Color.argb((30 * alphaFactor).toInt(), 255, 255, 255)
    val bottomTintColor = ColorUtils.setAlphaComponent(
      ColorUtils.blendARGB(accent, Color.WHITE, 0.55f),
      (34 * alphaFactor).toInt()
    )

    // 1. Frosted liquid glass diagonal gradient fill
    glassFillPaint.shader = LinearGradient(
      glassBoxRect.left,
      glassBoxRect.top,
      glassBoxRect.right,
      glassBoxRect.bottom,
      intArrayOf(topGlassColor, midGlassColor, bottomTintColor),
      floatArrayOf(0f, 0.55f, 1f),
      Shader.TileMode.CLAMP
    )
    canvas.drawRoundRect(glassBoxRect, cornerRadius, cornerRadius, glassFillPaint)

    // 2. Crisp 1dp outer glass border with top-to-bottom luminance gradient
    val strokeW = (1.15f * dp).coerceAtLeast(1f)
    val halfStroke = strokeW * 0.5f
    glassBoxRect.inset(halfStroke, halfStroke)
    glassBorderPaint.strokeWidth = strokeW
    glassBorderPaint.shader = LinearGradient(
      glassBoxRect.left,
      glassBoxRect.top,
      glassBoxRect.left,
      glassBoxRect.bottom,
      Color.argb((105 * alphaFactor).toInt(), 255, 255, 255),
      Color.argb((38 * alphaFactor).toInt(), 255, 255, 255),
      Shader.TileMode.CLAMP
    )
    canvas.drawRoundRect(
      glassBoxRect,
      cornerRadius - halfStroke,
      cornerRadius - halfStroke,
      glassBorderPaint
    )

    // 3. Subtle top specular highlight rim
    val specHeight = (glassBoxRect.height() * 0.38f).coerceAtLeast(1f)
    glassSpecularPaint.strokeWidth = (1.4f * dp).coerceAtLeast(1f)
    glassSpecularPaint.shader = LinearGradient(
      glassBoxRect.left,
      glassBoxRect.top,
      glassBoxRect.left,
      glassBoxRect.top + specHeight,
      Color.argb((85 * alphaFactor).toInt(), 255, 255, 255),
      Color.TRANSPARENT,
      Shader.TileMode.CLAMP
    )
    canvas.drawRoundRect(
      glassBoxRect,
      cornerRadius - halfStroke,
      cornerRadius - halfStroke,
      glassSpecularPaint
    )
  }

  fun setStyleColors(accentColor: Int, textColor: Int = Color.WHITE) {
    adapter.setStyle(accentColor, textColor)
    recyclerView.invalidate()
  }

  fun setReducedMotion(enabled: Boolean?) {
    reducedMotionOverride = enabled
    applyReducedMotionFlag()
  }

  fun setLyrics(lrcText: String?) {
    if (lrcText == currentRawLrc && document.lines.isNotEmpty()) return
    currentRawLrc = lrcText
    val parsed = LrcParser.parse(lrcText)
    document = parsed
    lastRawPositionMs = Long.MIN_VALUE
    userDragging = false
    resumeAutoFollowAtMs = 0L
    needsAutoFollowResume = false
    pendingViewportAlign = true
    glassBoxInitialized = false
    hasLastDrawnBox = false
    glassTopOffset = 0f
    glassBottomOffset = 0f
    glassAlpha = 0f

    adapter.submitDocument(parsed)
    layoutManager.scrollToPositionWithOffset(0, 0)

    if (parsed.isSynced && isAttachedToWindow) {
      tickFrame(0.016f)
      startFrameLoop()
    } else {
      stopFrameLoop()
    }
  }

  fun clear() {
    stopFrameLoop()
    currentRawLrc = null
    document = ParsedLyricsDocument(emptyList(), isSynced = false)
    lastRawPositionMs = Long.MIN_VALUE
    userDragging = false
    resumeAutoFollowAtMs = 0L
    needsAutoFollowResume = false
    pendingViewportAlign = false
    glassBoxInitialized = false
    hasLastDrawnBox = false
    glassTopOffset = 0f
    glassBottomOffset = 0f
    glassAlpha = 0f
    adapter.clear()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    applyReducedMotionFlag()
    if (document.isSynced && document.lines.isNotEmpty()) {
      startFrameLoop()
    }
  }

  override fun onDetachedFromWindow() {
    stopFrameLoop()
    super.onDetachedFromWindow()
  }

  private fun startFrameLoop() {
    if (isFrameLoopRunning) return
    isFrameLoopRunning = true
    lastFrameTimeNanos = 0L
    choreographer.postFrameCallback(frameCallback)
  }

  private fun stopFrameLoop() {
    if (!isFrameLoopRunning) return
    isFrameLoopRunning = false
    lastFrameTimeNanos = 0L
    choreographer.removeFrameCallback(frameCallback)
  }

  private fun isSystemReducedMotion(): Boolean {
    val override = reducedMotionOverride
    if (override == true) return true
    val scale = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.ANIMATOR_DURATION_SCALE,
      1.0f
    )
    return scale == 0f
  }

  private fun applyReducedMotionFlag() {
    adapter.setReducedMotion(isSystemReducedMotion())
  }

  private fun tickFrame(dtSeconds: Float) {
    val lines = document.lines
    if (!document.isSynced || lines.isEmpty()) return
    val provider = positionProvider ?: return

    val rawPos = provider().coerceAtLeast(0L)
    val delta = if (lastRawPositionMs == Long.MIN_VALUE) 0L else rawPos - lastRawPositionMs
    val isJump = lastRawPositionMs == Long.MIN_VALUE || abs(delta) > JUMP_THRESHOLD_MS

    if (lastRawPositionMs != Long.MIN_VALUE && delta < -JUMP_THRESHOLD_MS && rawPos < 1500L) {
      userDragging = false
      resumeAutoFollowAtMs = 0L
      needsAutoFollowResume = false
    }
    lastRawPositionMs = rawPos

    val effectivePos = (rawPos + offsetMs).coerceAtLeast(0L)
    val queryPos = effectivePos + LEAD_TOLERANCE_MS
    val prevIndex = adapter.getActiveIndex()

    val newIndex = if (!isJump && prevIndex in lines.indices) {
      val curLine = lines[prevIndex]
      val nextLine = if (prevIndex + 1 < lines.size) lines[prevIndex + 1] else null
      when {
        queryPos < curLine.timestampMs -> findActiveLineBinary(lines, queryPos)
        nextLine == null || queryPos < nextLine.timestampMs -> prevIndex
        prevIndex + 2 >= lines.size || queryPos < lines[prevIndex + 2].timestampMs -> prevIndex + 1
        else -> findActiveLineBinary(lines, queryPos)
      }
    } else {
      findActiveLineBinary(lines, queryPos)
    }

    val nowUptime = SystemClock.uptimeMillis()
    val canAutoFollow = !userDragging && nowUptime >= resumeAutoFollowAtMs
    val hasViewportHeight = recyclerView.height > 0
    val reducedMotion = isSystemReducedMotion()

    if (newIndex != prevIndex) {
      // Compute relative morph offset from the previously drawn Glass Box to the new active ViewHolder
      if (!reducedMotion && hasLastDrawnBox && newIndex >= 0) {
        val nextView = recyclerView.findViewHolderForAdapterPosition(newIndex)?.itemView
        if (nextView != null) {
          glassTopOffset = (lastDrawnBoxTop - nextView.top).coerceIn(-recyclerView.height * 0.6f, recyclerView.height * 0.6f)
          glassBottomOffset = (lastDrawnBoxBottom - nextView.bottom).coerceIn(-recyclerView.height * 0.6f, recyclerView.height * 0.6f)
          glassBoxInitialized = true
        } else {
          glassTopOffset = 0f
          glassBottomOffset = 0f
        }
      } else {
        glassTopOffset = 0f
        glassBottomOffset = 0f
      }

      adapter.setActiveIndex(newIndex)
      if (canAutoFollow) {
        if (hasViewportHeight) {
          needsAutoFollowResume = false
          pendingViewportAlign = false
          scrollToActiveLine(newIndex, isJump = isJump)
        } else {
          pendingViewportAlign = true
        }
      }
    } else if ((needsAutoFollowResume || pendingViewportAlign) && canAutoFollow && hasViewportHeight) {
      needsAutoFollowResume = false
      pendingViewportAlign = false
      scrollToActiveLine(newIndex, isJump = true)
    }

    // Smoothly interpolate the Liquid Glass Box offsets and opacity every Choreographer frame
    val targetAlpha = if (newIndex >= 0) 1f else 0f
    if (reducedMotion) {
      if (glassTopOffset != 0f || glassBottomOffset != 0f || glassAlpha != targetAlpha) {
        glassTopOffset = 0f
        glassBottomOffset = 0f
        glassAlpha = targetAlpha
        recyclerView.invalidate()
      }
    } else {
      val decay = 1f - exp(-13.5f * dtSeconds)
      val alphaDecay = 1f - exp(-10f * dtSeconds)
      var needsInvalidate = false

      if (abs(glassTopOffset) > 0.35f || abs(glassBottomOffset) > 0.35f) {
        glassTopOffset *= (1f - decay)
        glassBottomOffset *= (1f - decay)
        needsInvalidate = true
      } else if (glassTopOffset != 0f || glassBottomOffset != 0f) {
        glassTopOffset = 0f
        glassBottomOffset = 0f
        needsInvalidate = true
      }

      if (abs(glassAlpha - targetAlpha) > 0.005f) {
        glassAlpha += (targetAlpha - glassAlpha) * alphaDecay
        needsInvalidate = true
      } else if (glassAlpha != targetAlpha) {
        glassAlpha = targetAlpha
        needsInvalidate = true
      }

      if (needsInvalidate) {
        recyclerView.invalidate()
      }
    }

    if (newIndex in lines.indices && lines[newIndex].words.isNotEmpty()) {
      val holder = recyclerView.findViewHolderForAdapterPosition(newIndex) as? SyncedLyricsAdapter.LyricViewHolder
      holder?.updateWordProgress(effectivePos)
    }
  }

  private fun findActiveLineBinary(lines: List<ParsedLyricLine>, queryPosMs: Long): Int {
    if (lines.isEmpty() || queryPosMs < lines[0].timestampMs) return -1
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (lines[mid].timestampMs <= queryPosMs) {
        result = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return result
  }

  private fun scrollToActiveLine(targetIndex: Int, isJump: Boolean) {
    val lines = document.lines
    if (lines.isEmpty()) return

    val clampedTarget = if (targetIndex < 0) 0 else targetIndex.coerceIn(0, lines.lastIndex)
    val rvHeight = recyclerView.height
    val desiredTopPx = if (rvHeight > 0) (rvHeight * ACTIVE_TOP_FRACTION).toInt() else 0

    if (isSystemReducedMotion()) {
      layoutManager.scrollToPositionWithOffset(clampedTarget, if (targetIndex < 0) 0 else desiredTopPx)
      return
    }

    if (targetIndex < 0) {
      layoutManager.startSmoothScroll(createSmoothScroller(0))
      return
    }

    // If the target view is already laid out on screen, use a silky-smooth cubic-bezier scroll
    // directly to the exact 37% viewport anchor.
    val targetView = layoutManager.findViewByPosition(clampedTarget)
    if (targetView != null && rvHeight > 0) {
      val desiredTop = (rvHeight * ACTIVE_TOP_FRACTION - targetView.height * 0.5f).toInt()
      val dy = targetView.top - desiredTop
      if (abs(dy) > 2) {
        recyclerView.smoothScrollBy(0, dy, SCROLL_EASE, 420)
      }
      return
    }

    val firstVisible = layoutManager.findFirstVisibleItemPosition()
    val distanceFromViewport = if (firstVisible == RecyclerView.NO_POSITION) 0 else abs(clampedTarget - firstVisible)

    if (isJump && distanceFromViewport > BIG_JUMP_LINE_THRESHOLD) {
      val nearIndex = if (clampedTarget > firstVisible) {
        (clampedTarget - 3).coerceAtLeast(0)
      } else {
        (clampedTarget + 3).coerceAtMost(lines.lastIndex)
      }
      layoutManager.scrollToPositionWithOffset(nearIndex, desiredTopPx)
    }

    layoutManager.startSmoothScroll(createSmoothScroller(clampedTarget))
  }
}
