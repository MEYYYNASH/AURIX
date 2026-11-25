package com.example.lyrics

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ui.theme.KhmerLyricsFont
import com.example.ui.theme.KhmerTypography
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Custom TextView supporting all 5 AURIX lyric line animation styles:
 * - slide_up / blur_fade / pop_zoom (plus optional word-level LRC highlight if tags exist)
 * - karaoke_fill: 35% dim white base with continuous left-to-right white sweep (word-level or line duration)
 * - typewriter: grapheme-safe character-by-character reveal over ~1.4s with blinking cyan (#43C7F4) caret
 */
class LyricLineTextView(context: Context) : TextView(context) {
  private var parsedLine: ParsedLyricLine? = null
  private var isActiveLine: Boolean = false
  private var animationStyle: LyricsAnimationStyle = LyricsAnimationStyle.SLIDE_UP
  private var reducedMotion: Boolean = false
  private var isPlayingNow: Boolean = true

  private var revealCharEnd: Int = 0
  private var karaokeProgress: Float = 1f // 0..1 continuous sweep fraction for line-level karaoke_fill
  private var typewriterCharEnd: Int = 0
  private var caretVisible: Boolean = true

  private var baseHighlightColor: Int = Color.WHITE
  private var baseDimmedColor: Int = Color.argb(115, 255, 255, 255)
  private val karaokeDimColor: Int = Color.argb(89, 255, 255, 255) // 35% dim white per spec
  private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#43C7F4")
    style = Paint.Style.FILL
  }
  private val tempClipRect = Rect()

  init {
    includeFontPadding = true
    gravity = Gravity.START or Gravity.CENTER_VERTICAL
    setTextColor(Color.WHITE)
  }

  fun setHighlightColors(highlightColor: Int, dimmedColor: Int) {
    baseHighlightColor = highlightColor
    baseDimmedColor = dimmedColor
    setTextColor(highlightColor)
    invalidate()
  }

  fun setAnimationConfig(style: LyricsAnimationStyle, reduced: Boolean, playing: Boolean) {
    val changed = animationStyle != style || reducedMotion != reduced || isPlayingNow != playing
    animationStyle = style
    reducedMotion = reduced
    isPlayingNow = playing
    if (reduced) {
      karaokeProgress = 1f
      typewriterCharEnd = parsedLine?.text?.length ?: 0
      revealCharEnd = parsedLine?.text?.length ?: 0
      caretVisible = false
    }
    if (changed) invalidate()
  }

  private var lastBoundDisplay: String = ""
  private var lastBoundFont: KhmerLyricsFont? = null
  private var lastBoundBold: Boolean? = null

  fun bindLine(
    line: ParsedLyricLine,
    active: Boolean,
    khmerLyricsFont: KhmerLyricsFont = KhmerLyricsFont.KANTUMRUY_PRO
  ) {
    val lineChanged = parsedLine != line
    parsedLine = line
    isActiveLine = active
    val display = line.text.ifBlank { "•••" }
    if (lastBoundDisplay != display || lastBoundFont != khmerLyricsFont || lastBoundBold != active) {
      lastBoundDisplay = display
      lastBoundFont = khmerLyricsFont
      lastBoundBold = active
      text = KhmerTypography.buildStyledSpannable(
        context = context,
        text = display,
        isTitle = false,
        khmerLyricsFont = khmerLyricsFont,
        isBold = active
      )
    }
    if (lineChanged && !active) {
      karaokeProgress = 1f
      typewriterCharEnd = line.text.length
      revealCharEnd = line.text.length
    }
  }

  fun updatePlaybackProgress(positionMs: Long, isPlaying: Boolean): Boolean {
    isPlayingNow = isPlaying
    val line = parsedLine ?: return false
    if (!isActiveLine) return false
    if (reducedMotion) {
      val fullLen = line.text.length
      if (revealCharEnd != fullLen || karaokeProgress != 1f || typewriterCharEnd != fullLen) {
        revealCharEnd = fullLen
        karaokeProgress = 1f
        typewriterCharEnd = fullLen
        caretVisible = false
        invalidate()
        return true
      }
      return false
    }

    var dirty = false
    when (animationStyle) {
      LyricsAnimationStyle.KARAOKE_FILL -> {
        if (line.words.isNotEmpty()) {
          val nextReveal = line.computeRevealCharIndex(positionMs)
          if (nextReveal != revealCharEnd) {
            revealCharEnd = nextReveal
            dirty = true
          }
        } else {
          val duration = (line.endTimestampMs - line.timestampMs).coerceIn(500L, 12_000L)
          val nextFrac = ((positionMs - line.timestampMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
          if (abs(nextFrac - karaokeProgress) > 0.002f) {
            karaokeProgress = nextFrac
            dirty = true
          }
        }
      }
      LyricsAnimationStyle.TYPEWRITER -> {
        val lineDuration = (line.endTimestampMs - line.timestampMs).coerceAtLeast(450L)
        val typeDuration = minOf(1400L, lineDuration).coerceAtLeast(400L)
        val frac = ((positionMs - line.timestampMs).toFloat() / typeDuration.toFloat()).coerceIn(0f, 1f)
        val boundaries = line.graphemeBoundaries
        val nextCharEnd = if (boundaries.size > 1) {
          val targetIdx = ((boundaries.size - 1) * frac).roundToInt().coerceIn(0, boundaries.lastIndex)
          boundaries[targetIdx]
        } else {
          (line.text.length * frac).roundToInt().coerceIn(0, line.text.length)
        }
        val nextCaret = if (isPlaying) ((SystemClock.uptimeMillis() / 460L) % 2L == 0L) else true
        if (nextCharEnd != typewriterCharEnd || nextCaret != caretVisible) {
          typewriterCharEnd = nextCharEnd
          caretVisible = nextCaret
          dirty = true
        }
      }
      else -> {
        if (line.words.isNotEmpty()) {
          val nextReveal = line.computeRevealCharIndex(positionMs)
          if (nextReveal != revealCharEnd) {
            revealCharEnd = nextReveal
            dirty = true
          }
        }
      }
    }

    if (dirty) invalidate()
    return dirty
  }

  fun resetReveal(fullReveal: Boolean) {
    val len = parsedLine?.text?.length ?: 0
    val targetReveal = if (fullReveal) len else 0
    val targetKaraoke = if (fullReveal) 1f else 0f
    val targetTypewriter = if (fullReveal) len else 0
    if (revealCharEnd != targetReveal || karaokeProgress != targetKaraoke || typewriterCharEnd != targetTypewriter) {
      revealCharEnd = targetReveal
      karaokeProgress = targetKaraoke
      typewriterCharEnd = targetTypewriter
      invalidate()
    }
  }

  override fun onDraw(canvas: Canvas) {
    val line = parsedLine
    val textLayout = layout
    if (!isActiveLine || line == null || textLayout == null || reducedMotion) {
      super.onDraw(canvas)
      return
    }

    when {
      animationStyle == LyricsAnimationStyle.KARAOKE_FILL && line.words.isEmpty() -> {
        drawLineKaraokeSweep(canvas, textLayout, karaokeProgress.coerceIn(0f, 1f))
      }
      animationStyle == LyricsAnimationStyle.KARAOKE_FILL && line.words.isNotEmpty() -> {
        drawGraphemeRevealedFill(
          canvas = canvas,
          textLayout = textLayout,
          safeReveal = revealCharEnd.coerceIn(0, line.text.length),
          dimColor = karaokeDimColor,
          highlightColor = Color.WHITE
        )
      }
      animationStyle == LyricsAnimationStyle.TYPEWRITER -> {
        drawTypewriterReveal(canvas, textLayout, typewriterCharEnd.coerceIn(0, line.text.length))
      }
      line.words.isNotEmpty() -> {
        drawGraphemeRevealedFill(
          canvas = canvas,
          textLayout = textLayout,
          safeReveal = revealCharEnd.coerceIn(0, line.text.length),
          dimColor = baseDimmedColor,
          highlightColor = baseHighlightColor
        )
      }
      else -> super.onDraw(canvas)
    }
  }

  private fun drawLineKaraokeSweep(canvas: Canvas, textLayout: Layout, progress: Float) {
    val padLeft = compoundPaddingLeft.toFloat()
    val padTop = extendedPaddingTop.toFloat()

    // Base pass: 35% dim white
    paint.color = karaokeDimColor
    paint.drawableState = drawableState
    canvas.save()
    canvas.translate(padLeft, padTop)
    textLayout.draw(canvas)
    canvas.restore()

    if (progress <= 0.001f) return

    // Compute total width across wrapped visual lines so multi-line lyrics sweep sequentially
    val lineCount = textLayout.lineCount
    var totalWidth = 0f
    val lineWidths = FloatArray(lineCount)
    for (i in 0 until lineCount) {
      val lw = textLayout.getLineWidth(i).coerceAtLeast(1f)
      lineWidths[i] = lw
      totalWidth += lw
    }
    if (totalWidth <= 0f) return

    var remainingSweepPx = totalWidth * progress
    paint.color = Color.WHITE

    for (i in 0 until lineCount) {
      if (remainingSweepPx <= 0f) break
      val top = if (i == 0) 0 else (textLayout.getLineTop(i) + extendedPaddingTop)
      val bottom = if (i == lineCount - 1) height else (textLayout.getLineBottom(i) + extendedPaddingTop)
      val lw = lineWidths[i]
      val lineLeft = textLayout.getLineLeft(i) + padLeft
      val clipRight = if (remainingSweepPx >= lw) {
        width
      } else {
        (lineLeft + remainingSweepPx).roundToInt().coerceIn(0, width)
      }
      remainingSweepPx -= lw

      tempClipRect.set(0, top, clipRight, bottom)
      if (tempClipRect.width() > 0 && tempClipRect.height() > 0) {
        canvas.save()
        canvas.clipRect(tempClipRect)
        canvas.translate(padLeft, padTop)
        textLayout.draw(canvas)
        canvas.restore()
      }
    }
  }

  private fun drawGraphemeRevealedFill(
    canvas: Canvas,
    textLayout: Layout,
    safeReveal: Int,
    dimColor: Int,
    highlightColor: Int
  ) {
    val padLeft = compoundPaddingLeft.toFloat()
    val padTop = extendedPaddingTop.toFloat()

    paint.color = dimColor
    paint.drawableState = drawableState
    canvas.save()
    canvas.translate(padLeft, padTop)
    textLayout.draw(canvas)
    canvas.restore()

    if (safeReveal <= 0) return

    paint.color = highlightColor
    val contentRight = width - compoundPaddingRight
    val lineCount = textLayout.lineCount
    for (i in 0 until lineCount) {
      val lineStart = textLayout.getLineStart(i)
      val lineEnd = textLayout.getLineEnd(i)
      if (safeReveal <= lineStart) break

      val top = if (i == 0) 0 else (textLayout.getLineTop(i) + extendedPaddingTop)
      val bottom = if (i == lineCount - 1) height else (textLayout.getLineBottom(i) + extendedPaddingTop)

      if (safeReveal >= lineEnd) {
        tempClipRect.set(0, top, width, bottom)
      } else {
        val boundaryX = (textLayout.getPrimaryHorizontal(safeReveal) + padLeft).toInt()
        val isLtr = textLayout.getParagraphDirection(i) != Layout.DIR_RIGHT_TO_LEFT
        if (isLtr) {
          tempClipRect.set(0, top, boundaryX.coerceIn(0, contentRight), bottom)
        } else {
          tempClipRect.set(boundaryX.coerceIn(compoundPaddingLeft, width), top, width, bottom)
        }
      }

      if (tempClipRect.width() > 0 && tempClipRect.height() > 0) {
        canvas.save()
        canvas.clipRect(tempClipRect)
        canvas.translate(padLeft, padTop)
        textLayout.draw(canvas)
        canvas.restore()
      }
    }
  }

  private fun drawTypewriterReveal(canvas: Canvas, textLayout: Layout, safeReveal: Int) {
    val padLeft = compoundPaddingLeft.toFloat()
    val padTop = extendedPaddingTop.toFloat()
    val contentRight = width - compoundPaddingRight
    val lineCount = textLayout.lineCount
    val dp = resources.displayMetrics.density

    paint.color = baseHighlightColor
    paint.drawableState = drawableState

    var caretX = padLeft
    var caretTop = padTop
    var caretBottom = padTop + textLayout.getLineBottom(0).toFloat()

    if (safeReveal > 0) {
      for (i in 0 until lineCount) {
        val lineStart = textLayout.getLineStart(i)
        val lineEnd = textLayout.getLineEnd(i)
        if (safeReveal <= lineStart) break

        val top = if (i == 0) 0 else (textLayout.getLineTop(i) + extendedPaddingTop)
        val bottom = if (i == lineCount - 1) height else (textLayout.getLineBottom(i) + extendedPaddingTop)

        if (safeReveal >= lineEnd) {
          tempClipRect.set(0, top, width, bottom)
          caretX = (textLayout.getLineRight(i) + padLeft).coerceAtMost(contentRight.toFloat())
          caretTop = textLayout.getLineTop(i) + padTop + 2f * dp
          caretBottom = textLayout.getLineBottom(i) + padTop - 2f * dp
        } else {
          val boundaryX = (textLayout.getPrimaryHorizontal(safeReveal) + padLeft)
          caretX = boundaryX.coerceIn(padLeft, contentRight.toFloat())
          caretTop = textLayout.getLineTop(i) + padTop + 2f * dp
          caretBottom = textLayout.getLineBottom(i) + padTop - 2f * dp
          val isLtr = textLayout.getParagraphDirection(i) != Layout.DIR_RIGHT_TO_LEFT
          if (isLtr) {
            tempClipRect.set(0, top, boundaryX.toInt().coerceIn(0, contentRight), bottom)
          } else {
            tempClipRect.set(boundaryX.toInt().coerceIn(compoundPaddingLeft, width), top, width, bottom)
          }
        }

        if (tempClipRect.width() > 0 && tempClipRect.height() > 0) {
          canvas.save()
          canvas.clipRect(tempClipRect)
          canvas.translate(padLeft, padTop)
          textLayout.draw(canvas)
          canvas.restore()
        }
      }
    }

    // Blinking cyan (#43C7F4) caret
    if (caretVisible) {
      val caretW = (2.4f * dp).coerceAtLeast(2f)
      val drawLeft = (caretX + 1.5f * dp).coerceAtMost((width - caretW - 2f).coerceAtLeast(0f))
      val drawTop = caretTop.coerceAtLeast(0f)
      val drawBottom = caretBottom.coerceAtLeast(drawTop + 14f * dp).coerceAtMost(height.toFloat())
      canvas.drawRoundRect(
        drawLeft,
        drawTop,
        drawLeft + caretW,
        drawBottom,
        caretW * 0.5f,
        caretW * 0.5f,
        caretPaint
      )
    }
  }
}

class SyncedLyricsAdapter(
  private val onLineTapped: (ParsedLyricLine) -> Unit
) : RecyclerView.Adapter<SyncedLyricsAdapter.LyricViewHolder>() {

  companion object {
    const val PAYLOAD_STATE_CHANGED = 1
    private val SMOOTH_EASE = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val SLIDE_DECEL = DecelerateInterpolator(2.0f)
    private val POP_OVERSHOOT = OvershootInterpolator(1.35f)
  }

  private val lines = ArrayList<ParsedLyricLine>()
  private var isSynced = true
  private var activeIndex = -1
  private var previousActiveIndex = -1
  private var accentColor = Color.parseColor("#0A84FF")
  private var textColor = Color.WHITE
  private var reducedMotion = false
  private var animationStyle = LyricsAnimationStyle.SLIDE_UP
  private var khmerLyricsFont = KhmerLyricsFont.KANTUMRUY_PRO
  private var isPlayingNow = true
  private var isForegroundActive = true

  private val semiBoldTypeface: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Typeface.create(Typeface.DEFAULT, 600, false)
  } else {
    Typeface.create("sans-serif-medium", Typeface.NORMAL)
  }
  private val extraBoldTypeface: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Typeface.create(Typeface.DEFAULT, 800, false)
  } else {
    Typeface.DEFAULT_BOLD
  }

  fun submitDocument(doc: ParsedLyricsDocument) {
    lines.clear()
    lines.addAll(doc.lines)
    isSynced = doc.isSynced
    activeIndex = -1
    previousActiveIndex = -1
    notifyDataSetChanged()
  }

  fun clear() {
    if (lines.isEmpty()) return
    lines.clear()
    activeIndex = -1
    previousActiveIndex = -1
    notifyDataSetChanged()
  }

  fun setStyle(accent: Int, text: Int) {
    if (accentColor == accent && textColor == text) return
    accentColor = accent
    textColor = text
    notifyItemRangeChanged(0, lines.size, PAYLOAD_STATE_CHANGED)
  }

  fun setAnimationStyle(style: LyricsAnimationStyle) {
    if (animationStyle == style) return
    animationStyle = style
    if (lines.isNotEmpty()) {
      notifyItemRangeChanged(0, lines.size, PAYLOAD_STATE_CHANGED)
    }
  }

  fun setKhmerLyricsFont(font: KhmerLyricsFont) {
    if (khmerLyricsFont == font) return
    khmerLyricsFont = font
    if (lines.isNotEmpty()) {
      notifyItemRangeChanged(0, lines.size, PAYLOAD_STATE_CHANGED)
    }
  }

  fun getKhmerLyricsFont(): KhmerLyricsFont = khmerLyricsFont

  fun getAnimationStyle(): LyricsAnimationStyle = animationStyle

  fun setPlaybackAndForeground(playing: Boolean, foreground: Boolean) {
    val wasAnimating = isPlayingNow && isForegroundActive
    val nowAnimating = playing && foreground
    isPlayingNow = playing
    isForegroundActive = foreground
    if (wasAnimating != nowAnimating) {
      val rv = attachedRecyclerView ?: return
      for (i in 0 until rv.childCount) {
        val child = rv.getChildAt(i)
        val holder = rv.getChildViewHolder(child) as? LyricViewHolder ?: continue
        holder.onPlaybackOrForegroundChanged(nowAnimating)
      }
    }
  }

  fun getAccentColor(): Int = accentColor

  fun setReducedMotion(enabled: Boolean) {
    if (reducedMotion == enabled) return
    reducedMotion = enabled
    if (lines.isNotEmpty()) {
      notifyItemRangeChanged(0, lines.size, PAYLOAD_STATE_CHANGED)
    }
  }

  fun getActiveIndex(): Int = activeIndex

  fun isSyncedLyrics(): Boolean = isSynced

  fun getLineAt(index: Int): ParsedLyricLine? = lines.getOrNull(index)

  fun setActiveIndex(newIndex: Int) {
    if (newIndex == activeIndex) return
    val oldIndex = activeIndex
    previousActiveIndex = oldIndex
    activeIndex = newIndex

    val safeNotifyItem: (Int, Int) -> Unit = { idx, payload ->
      if (idx in 0 until lines.size) {
        val rv = attachedRecyclerView
        if (rv != null && rv.isComputingLayout) {
          rv.post {
            runCatching { notifyItemChanged(idx, payload) }
          }
        } else {
          runCatching { notifyItemChanged(idx, payload) }
        }
      }
    }

    // Only notify the outgoing line and the newly active line so upcoming lines never animate early
    if (oldIndex >= 0) {
      safeNotifyItem(oldIndex, PAYLOAD_STATE_CHANGED)
    }
    if (newIndex >= 0) {
      safeNotifyItem(newIndex, PAYLOAD_STATE_CHANGED)
    }
  }

  private var attachedRecyclerView: RecyclerView? = null

  override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
    super.onAttachedToRecyclerView(recyclerView)
    attachedRecyclerView = recyclerView
  }

  override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
    if (attachedRecyclerView == recyclerView) {
      attachedRecyclerView = null
    }
    super.onDetachedFromRecyclerView(recyclerView)
  }

  override fun getItemCount(): Int = lines.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
    val dp = parent.resources.displayMetrics.density
    val hMargin = (8f * dp).toInt()
    val hPad = (18f * dp).toInt()
    val vPad = (13f * dp).toInt()

    val container = FrameLayout(parent.context).apply {
      layoutParams = RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply {
        leftMargin = hMargin
        rightMargin = hMargin
        bottomMargin = (8f * dp).toInt()
      }
      minimumHeight = (50f * dp).toInt()
      setPadding(hPad, vPad, hPad, vPad)
      clipChildren = false
      clipToPadding = false
      isClickable = true
      isFocusable = true
    }

    val textView = LyricLineTextView(parent.context).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER_VERTICAL
      )
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
      setLineSpacing((6f * dp), 1.04f)
      pivotX = 0f
    }
    container.addView(textView)
    return LyricViewHolder(container, textView, dp)
  }

  override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
    val line = lines[position]
    holder.bind(line, position, fullBind = true)
  }

  override fun onBindViewHolder(holder: LyricViewHolder, position: Int, payloads: MutableList<Any>) {
    val line = lines[position]
    val partialOnly = payloads.isNotEmpty() && payloads.all { it == PAYLOAD_STATE_CHANGED }
    holder.bind(line, position, fullBind = !partialOnly)
  }

  override fun onViewRecycled(holder: LyricViewHolder) {
    holder.cancelAnimators()
    super.onViewRecycled(holder)
  }

  inner class LyricViewHolder(
    private val container: FrameLayout,
    private val textView: LyricLineTextView,
    private val density: Float
  ) : RecyclerView.ViewHolder(container) {

    private var currentBoundLine: ParsedLyricLine? = null
    private var lastAppliedActive: Boolean? = null
    private var lastAppliedAlpha: Float = -1f
    private var activeValueAnimator: ValueAnimator? = null

    init {
      container.setOnClickListener {
        val line = currentBoundLine ?: return@setOnClickListener
        if (isSynced) {
          onLineTapped(line)
        }
      }
    }

    fun cancelAnimators() {
      activeValueAnimator?.cancel()
      activeValueAnimator = null
      textView.animate().cancel()
      clearBlurEffect()
      textView.letterSpacing = 0f
    }

    fun onPlaybackOrForegroundChanged(canAnimate: Boolean) {
      textView.setAnimationConfig(animationStyle, reducedMotion, isPlayingNow && isForegroundActive)
      val anim = activeValueAnimator ?: return
      if (canAnimate) {
        if (anim.isPaused) anim.resume()
      } else {
        if (anim.isRunning && !anim.isPaused) anim.pause()
      }
    }

    private fun applyBlurDp(blurDp: Float) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val px = blurDp * density
        if (px <= 0.35f) {
          textView.setRenderEffect(null)
        } else {
          textView.setRenderEffect(RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP))
        }
      }
    }

    private fun clearBlurEffect() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        textView.setRenderEffect(null)
      }
    }

    fun bind(line: ParsedLyricLine, position: Int, fullBind: Boolean) {
      currentBoundLine = line
      val isActive = isSynced && position == activeIndex
      val wasActive = lastAppliedActive == true
      val distance = when {
        !isSynced -> 0
        activeIndex == -1 -> position + 1
        else -> abs(position - activeIndex)
      }

      val targetAlpha = when {
        !isSynced -> 0.90f
        isActive -> 1.0f
        else -> 0.42f
      }
      val targetScale = 1.0f
      val targetTranslationY = 0f

      textView.typeface = if (isActive) extraBoldTypeface else semiBoldTypeface
      textView.setAnimationConfig(animationStyle, reducedMotion, isPlayingNow && isForegroundActive)
      if (fullBind) {
        textView.bindLine(line, isActive, khmerLyricsFont)
        textView.resetReveal(fullReveal = !isActive || reducedMotion)
      } else {
        textView.bindLine(line, isActive, khmerLyricsFont)
        if (!isActive || reducedMotion) {
          textView.resetReveal(fullReveal = true)
        } else if (!wasActive) {
          // Newly activated line starts progressive reveal from 0 for karaoke/typewriter
          if (animationStyle == LyricsAnimationStyle.KARAOKE_FILL || animationStyle == LyricsAnimationStyle.TYPEWRITER) {
            textView.resetReveal(fullReveal = false)
          }
        }
      }

      val dimmedHighlight = Color.argb(
        115,
        Color.red(textColor),
        Color.green(textColor),
        Color.blue(textColor)
      )
      textView.setHighlightColors(textColor, dimmedHighlight)

      val justBecameActive = isActive && !wasActive
      lastAppliedActive = isActive
      lastAppliedAlpha = targetAlpha

      textView.pivotX = 0f
      textView.pivotY = (textView.height.takeIf { it > 0 } ?: (26f * density).toInt()) * 0.5f

      val canRunMotion = !fullBind && !reducedMotion && isPlayingNow && isForegroundActive

      // Strictly enforce: ONLY the currently active lyric line receives animation.
      // Upcoming or inactive lyric lines stay completely static until they become the active line.
      if (!isActive || !canRunMotion) {
        cancelAnimators()
        textView.alpha = targetAlpha
        textView.scaleX = targetScale
        textView.scaleY = targetScale
        textView.translationY = targetTranslationY
        textView.letterSpacing = 0f
        clearBlurEffect()
        return
      }

      if (justBecameActive) {
        cancelAnimators()
        when (animationStyle) {
          LyricsAnimationStyle.SLIDE_UP -> {
            // New line translates up 24dp, fades 0 -> 1, 550ms, decelerate
            textView.translationY = 24f * density
            textView.alpha = 0f
            textView.scaleX = 1f
            textView.scaleY = 1f
            textView.letterSpacing = 0f
            clearBlurEffect()
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
              duration = 550L
              interpolator = SLIDE_DECEL
              addUpdateListener { va ->
                val f = va.animatedValue as Float
                textView.translationY = (24f * density) * (1f - f)
                textView.alpha = f
              }
            }
            activeValueAnimator = anim
            anim.start()
          }
          LyricsAnimationStyle.BLUR_FADE -> {
            // Blur 10dp -> 0, alpha 0 -> 1, letter spacing 0.08em -> 0, 700ms
            textView.translationY = 0f
            textView.scaleX = 1f
            textView.scaleY = 1f
            textView.alpha = 0f
            textView.letterSpacing = 0.08f
            applyBlurDp(10f)
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
              duration = 700L
              interpolator = SMOOTH_EASE
              addUpdateListener { va ->
                val f = va.animatedValue as Float
                val inv = 1f - f
                textView.alpha = f
                textView.letterSpacing = 0.08f * inv
                applyBlurDp(10f * inv)
              }
              addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                  textView.letterSpacing = 0f
                  clearBlurEffect()
                }
              })
            }
            activeValueAnimator = anim
            anim.start()
          }
          LyricsAnimationStyle.POP_ZOOM -> {
            // Scale 0.6 -> 1 with overshoot interpolator, alpha 0 -> 1, 600ms
            textView.translationY = 0f
            textView.scaleX = 0.6f
            textView.scaleY = 0.6f
            textView.alpha = 0f
            textView.letterSpacing = 0f
            clearBlurEffect()
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
              duration = 600L
              interpolator = POP_OVERSHOOT
              addUpdateListener { va ->
                val f = va.animatedValue as Float
                val scale = 0.6f + 0.4f * f
                textView.scaleX = scale
                textView.scaleY = scale
                textView.alpha = f.coerceIn(0f, 1f)
              }
              addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                  textView.scaleX = 1f
                  textView.scaleY = 1f
                  textView.alpha = 1f
                }
              })
            }
            activeValueAnimator = anim
            anim.start()
          }
          LyricsAnimationStyle.KARAOKE_FILL, LyricsAnimationStyle.TYPEWRITER -> {
            // Crisp focus lock-in; karaoke sweep / typewriter caret runs smoothly per-frame
            textView.translationY = 0f
            textView.scaleX = 1f
            textView.scaleY = 1f
            textView.letterSpacing = 0f
            clearBlurEffect()
            textView.alpha = 1f
          }
        }
      }
    }

    fun updatePlaybackProgress(positionMs: Long, isPlaying: Boolean) {
      textView.updatePlaybackProgress(positionMs, isPlaying)
    }
  }
}
