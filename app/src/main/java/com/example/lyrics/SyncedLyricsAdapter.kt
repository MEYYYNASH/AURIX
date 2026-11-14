package com.example.lyrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Custom TextView that draws progressive word-level LRC highlight clipped cleanly at
 * precomputed BreakIterator grapheme boundaries with a soft leading-edge feather gradient,
 * without allocating Spans or objects per frame.
 */
class LyricLineTextView(context: Context) : TextView(context) {
  private var parsedLine: ParsedLyricLine? = null
  private var isActiveLine: Boolean = false
  private var revealCharEnd: Int = 0
  private var baseHighlightColor: Int = Color.WHITE
  private var baseDimmedColor: Int = Color.argb(115, 255, 255, 255)
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

  fun bindLine(line: ParsedLyricLine, active: Boolean) {
    parsedLine = line
    isActiveLine = active
    val display = line.text.ifBlank { "•••" }
    if (text.toString() != display) {
      text = display
    }
  }

  fun updateRevealPosition(positionMs: Long): Boolean {
    val line = parsedLine ?: return false
    if (!isActiveLine || line.words.isEmpty()) return false
    val nextReveal = line.computeRevealCharIndex(positionMs)
    if (nextReveal != revealCharEnd) {
      revealCharEnd = nextReveal
      invalidate()
      return true
    }
    return false
  }

  fun resetReveal(fullReveal: Boolean) {
    val target = if (fullReveal) parsedLine?.text?.length ?: 0 else 0
    if (revealCharEnd != target) {
      revealCharEnd = target
      invalidate()
    }
  }

  override fun onDraw(canvas: Canvas) {
    val line = parsedLine
    val textLayout = layout
    if (!isActiveLine || line == null || line.words.isEmpty() || textLayout == null) {
      super.onDraw(canvas)
      return
    }

    val safeReveal = revealCharEnd.coerceIn(0, line.text.length)
    val padLeft = compoundPaddingLeft.toFloat()
    val padTop = extendedPaddingTop.toFloat()

    // Pass 1: draw full line in dimmed color so unrevealed words remain legible
    paint.color = baseDimmedColor
    paint.drawableState = drawableState
    canvas.save()
    canvas.translate(padLeft, padTop)
    textLayout.draw(canvas)
    canvas.restore()

    if (safeReveal <= 0) return

    // Pass 2: draw revealed portion line-by-line clipped strictly on grapheme-cluster X boundaries
    paint.color = baseHighlightColor
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
}

class SyncedLyricsAdapter(
  private val onLineTapped: (ParsedLyricLine) -> Unit
) : RecyclerView.Adapter<SyncedLyricsAdapter.LyricViewHolder>() {

  companion object {
    const val PAYLOAD_STATE_CHANGED = 1
    private val SMOOTH_EASE = PathInterpolator(0.22f, 1f, 0.36f, 1f)
  }

  private val lines = ArrayList<ParsedLyricLine>()
  private var isSynced = true
  private var activeIndex = -1
  private var accentColor = Color.parseColor("#0A84FF")
  private var textColor = Color.WHITE
  private var reducedMotion = false

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
    notifyDataSetChanged()
  }

  fun clear() {
    if (lines.isEmpty()) return
    lines.clear()
    activeIndex = -1
    notifyDataSetChanged()
  }

  fun setStyle(accent: Int, text: Int) {
    if (accentColor == accent && textColor == text) return
    accentColor = accent
    textColor = text
    notifyItemRangeChanged(0, lines.size, PAYLOAD_STATE_CHANGED)
  }

  fun getAccentColor(): Int = accentColor

  fun setReducedMotion(enabled: Boolean) {
    reducedMotion = enabled
  }

  fun getActiveIndex(): Int = activeIndex

  fun isSyncedLyrics(): Boolean = isSynced

  fun getLineAt(index: Int): ParsedLyricLine? = lines.getOrNull(index)

  fun setActiveIndex(newIndex: Int) {
    if (newIndex == activeIndex) return
    val oldIndex = activeIndex
    activeIndex = newIndex

    // Rebind only the old and new neighborhoods whose distance-based dimming changed
    if (oldIndex >= 0 && lines.isNotEmpty()) {
      val start = (oldIndex - 4).coerceAtLeast(0)
      val end = (oldIndex + 4).coerceAtMost(lines.lastIndex)
      if (end >= start) {
        notifyItemRangeChanged(start, end - start + 1, PAYLOAD_STATE_CHANGED)
      }
    }
    if (newIndex >= 0 && lines.isNotEmpty()) {
      val start = (newIndex - 4).coerceAtLeast(0)
      val end = (newIndex + 4).coerceAtMost(lines.lastIndex)
      if (end >= start) {
        notifyItemRangeChanged(start, end - start + 1, PAYLOAD_STATE_CHANGED)
      }
    }
    if (oldIndex == -1 || newIndex == -1) {
      val count = minOf(5, lines.size)
      if (count > 0) notifyItemRangeChanged(0, count, PAYLOAD_STATE_CHANGED)
    }
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
      // Consistent 21sp layout size so StaticLayout never re-wraps or jumps during scale transitions
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

  inner class LyricViewHolder(
    private val container: FrameLayout,
    private val textView: LyricLineTextView,
    private val density: Float
  ) : RecyclerView.ViewHolder(container) {

    private var currentBoundLine: ParsedLyricLine? = null
    private var lastAppliedActive: Boolean? = null
    private var lastAppliedAlpha: Float = -1f

    init {
      container.setOnClickListener {
        val line = currentBoundLine ?: return@setOnClickListener
        if (isSynced) {
          onLineTapped(line)
        }
      }
    }

    fun bind(line: ParsedLyricLine, position: Int, fullBind: Boolean) {
      currentBoundLine = line
      val isActive = isSynced && position == activeIndex
      val distance = when {
        !isSynced -> 0
        activeIndex == -1 -> position + 1
        else -> abs(position - activeIndex)
      }

      val targetAlpha = when {
        !isSynced -> 0.90f
        isActive -> 1.0f
        distance == 1 -> 0.56f
        distance == 2 -> 0.35f
        distance == 3 -> 0.22f
        else -> 0.15f
      }
      val targetScale = when {
        !isSynced -> 1.0f
        isActive -> 1.0f
        distance == 1 -> 0.965f
        else -> 0.945f
      }
      val targetTranslationY = when {
        !isSynced || isActive -> 0f
        position > activeIndex -> 2.5f * density
        else -> -1.5f * density
      }

      if (fullBind) {
        textView.bindLine(line, isActive)
        textView.resetReveal(fullReveal = !isActive)
      } else {
        textView.bindLine(line, isActive)
        if (!isActive) textView.resetReveal(fullReveal = true)
      }

      val dimmedHighlight = Color.argb(
        115,
        Color.red(textColor),
        Color.green(textColor),
        Color.blue(textColor)
      )
      textView.setHighlightColors(textColor, dimmedHighlight)

      val stateChanged = lastAppliedActive != isActive || lastAppliedAlpha != targetAlpha
      lastAppliedActive = isActive
      lastAppliedAlpha = targetAlpha

      textView.typeface = if (isActive) extraBoldTypeface else semiBoldTypeface
      textView.pivotX = 0f
      textView.pivotY = (textView.height.takeIf { it > 0 } ?: (26f * density).toInt()) * 0.5f

      if (fullBind || reducedMotion) {
        textView.animate().cancel()
        textView.alpha = targetAlpha
        textView.scaleX = targetScale
        textView.scaleY = targetScale
        textView.translationY = targetTranslationY
      } else if (stateChanged) {
        textView.animate().cancel()
        textView.animate()
          .alpha(targetAlpha)
          .scaleX(targetScale)
          .scaleY(targetScale)
          .translationY(targetTranslationY)
          .setDuration(if (isActive) 340L else 280L)
          .setInterpolator(SMOOTH_EASE)
          .start()
      }
    }

    fun updateWordProgress(positionMs: Long) {
      textView.updateRevealPosition(positionMs)
    }
  }
}
