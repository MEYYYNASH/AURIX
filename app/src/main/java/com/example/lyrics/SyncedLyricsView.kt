package com.example.lyrics

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Real-time Choreographer-driven RecyclerView lyrics view.
 * Hook up via [positionProvider], [setLyrics], and [clear] without extra player listeners.
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
  private var lastRawPositionMs = Long.MIN_VALUE
  private var userDragging = false
  private var resumeAutoFollowAtMs = 0L
  private var needsAutoFollowResume = false

  private fun createSmoothScroller(targetPos: Int): LinearSmoothScroller {
    return object : LinearSmoothScroller(context) {
      override fun calculateDtToFit(
        viewStart: Int,
        viewEnd: Int,
        boxStart: Int,
        boxEnd: Int,
        snapPreference: Int
      ): Int {
        val boxHeight = boxEnd - boxStart
        val viewHeight = viewEnd - viewStart
        val desiredTop = boxStart + (boxHeight * ACTIVE_TOP_FRACTION - viewHeight * 0.5f).toInt()
        return desiredTop - viewStart
      }

      override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
        return 110f / displayMetrics.densityDpi
      }
    }.apply { targetPosition = targetPos }
  }

  private val frameCallback = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
      if (!isFrameLoopRunning) return
      tickFrame()
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
      isVerticalFadingEdgeEnabled = true
      setFadingEdgeLength((24f * dp).toInt())
      itemAnimator = null
      setPadding((4f * dp).toInt(), (12f * dp).toInt(), (4f * dp).toInt(), (160f * dp).toInt())
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

  fun setStyleColors(accentColor: Int, textColor: Int = Color.WHITE) {
    adapter.setStyle(accentColor, textColor)
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

    adapter.submitDocument(parsed)
    layoutManager.scrollToPositionWithOffset(0, 0)

    if (parsed.isSynced && isAttachedToWindow) {
      // Run immediate frame sync when switching tracks mid-playback
      tickFrame()
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
    choreographer.postFrameCallback(frameCallback)
  }

  private fun stopFrameLoop() {
    if (!isFrameLoopRunning) return
    isFrameLoopRunning = false
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

  private fun tickFrame() {
    val lines = document.lines
    if (!document.isSynced || lines.isEmpty()) return
    val provider = positionProvider ?: return

    val rawPos = provider().coerceAtLeast(0L)
    val delta = if (lastRawPositionMs == Long.MIN_VALUE) 0L else rawPos - lastRawPositionMs
    val isJump = lastRawPositionMs == Long.MIN_VALUE || abs(delta) > JUMP_THRESHOLD_MS

    // Replay or backwards scrub near track start resets manual scroll pause immediately
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
      // O(1) fast path during contiguous playback to avoid binary search on unchanged frames
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

    if (newIndex != prevIndex) {
      adapter.setActiveIndex(newIndex)
      if (canAutoFollow) {
        needsAutoFollowResume = false
        scrollToActiveLine(newIndex, isJump = isJump)
      }
    } else if (needsAutoFollowResume && canAutoFollow) {
      needsAutoFollowResume = false
      scrollToActiveLine(newIndex, isJump = true)
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

    val firstVisible = layoutManager.findFirstVisibleItemPosition()
    val distanceFromViewport = if (firstVisible == RecyclerView.NO_POSITION) 0 else abs(clampedTarget - firstVisible)

    // For large seeks or track jumps, snap close to target first, then glide smoothly to 37%
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
