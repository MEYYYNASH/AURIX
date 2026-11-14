package com.example.lyrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Custom TextView that draws progressive word-level LRC highlight clipped cleanly at
 * precomputed BreakIterator grapheme boundaries without allocating Spans or Rects per frame.
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
    if (text.toString() != line.text) {
      text = line.text.ifBlank { "..." }
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

  fun setReducedMotion(enabled: Boolean) {
    reducedMotion = enabled
  }

  fun getActiveIndex(): Int = activeIndex

  fun getLineAt(index: Int): ParsedLyricLine? = lines.getOrNull(index)

  fun setActiveIndex(newIndex: Int) {
    if (newIndex == activeIndex) return
    val oldIndex = activeIndex
    activeIndex = newIndex

    // Rebind only the old and new neighborhoods whose distance-based dimming changed
    if (oldIndex >= 0 && lines.isNotEmpty()) {
      val start = (oldIndex - 3).coerceAtLeast(0)
      val end = (oldIndex + 3).coerceAtMost(lines.lastIndex)
      if (end >= start) {
        notifyItemRangeChanged(start, end - start + 1, PAYLOAD_STATE_CHANGED)
      }
    }
    if (newIndex >= 0 && lines.isNotEmpty()) {
      val start = (newIndex - 3).coerceAtLeast(0)
      val end = (newIndex + 3).coerceAtMost(lines.lastIndex)
      if (end >= start) {
        notifyItemRangeChanged(start, end - start + 1, PAYLOAD_STATE_CHANGED)
      }
    }
    if (oldIndex == -1 || newIndex == -1) {
      val count = minOf(4, lines.size)
      if (count > 0) notifyItemRangeChanged(0, count, PAYLOAD_STATE_CHANGED)
    }
  }

  override fun getItemCount(): Int = lines.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
    val dp = parent.resources.displayMetrics.density
    val hMargin = (4f * dp).toInt()
    val container = FrameLayout(parent.context).apply {
      layoutParams = RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply {
        leftMargin = hMargin
        rightMargin = hMargin
        bottomMargin = (8f * dp).toInt()
      }
      minimumHeight = (48f * dp).toInt()
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

    private val activePillDrawable = GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 20f * density
      orientation = GradientDrawable.Orientation.LEFT_RIGHT
    }

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
        distance == 1 -> 0.58f
        distance == 2 -> 0.38f
        else -> 0.24f
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

      if (lastAppliedActive != isActive) {
        lastAppliedActive = isActive
        val hPad = (16f * density).toInt()
        val vPad = ((if (isActive) 14f else 9f) * density).toInt()
        container.setPadding(hPad, vPad, hPad, vPad)

        if (isActive) {
          val startTint = Color.argb(56, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
          val endTint = Color.argb(28, 255, 255, 255)
          activePillDrawable.colors = intArrayOf(startTint, endTint)
          activePillDrawable.setStroke((1f * density).toInt().coerceAtLeast(1), Color.argb(72, 255, 255, 255))
          container.background = activePillDrawable
          textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
          textView.setLineSpacing((8f * density), 1f)
          textView.typeface = extraBoldTypeface
        } else {
          container.background = null
          textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
          textView.setLineSpacing((7f * density), 1f)
          textView.typeface = semiBoldTypeface
        }

        // Keep horizontal scale at 1.0f so the left/right rounded corners of the active pill are never clipped
        container.animate().cancel()
        container.scaleX = 1.0f
        container.scaleY = 1.0f
      }

      if (lastAppliedAlpha != targetAlpha) {
        lastAppliedAlpha = targetAlpha
        if (reducedMotion || fullBind) {
          textView.animate().cancel()
          textView.alpha = targetAlpha
        } else {
          textView.animate()
            .alpha(targetAlpha)
            .setDuration(220L)
            .start()
        }
      }
    }

    fun updateWordProgress(positionMs: Long) {
      textView.updateRevealPosition(positionMs)
    }
  }
}
