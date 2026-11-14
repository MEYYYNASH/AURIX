package com.example

import com.example.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

  @Test
  fun emptyAndNullLyricsProduceEmptyUnsyncedDocument() {
    val nullDoc = LrcParser.parse(null)
    assertTrue(nullDoc.lines.isEmpty())
    assertFalse(nullDoc.isSynced)

    val blankDoc = LrcParser.parse("   \n\t  ")
    assertTrue(blankDoc.lines.isEmpty())
    assertFalse(blankDoc.isSynced)
  }

  @Test
  fun parsesStandardAndMillisecondTimestampsMultiTagsOffsetAndMetadata() {
    val lrc = """
      [ar:Artist Name]
      [ti:Track Title]
      [al:Album]
      [offset:500]
      [00:10.50][00:30.250]Chorus line
      [00:20.12]Second line
    """.trimIndent()

    val doc = LrcParser.parse(lrc)
    assertTrue(doc.isSynced)
    assertEquals(3, doc.lines.size)
    // Positive [offset:500] shifts timestamps 500ms earlier
    assertEquals(10_000L, doc.lines[0].timestampMs)
    assertEquals("Chorus line", doc.lines[0].text)
    assertEquals(19_620L, doc.lines[1].timestampMs)
    assertEquals("Second line", doc.lines[1].text)
    assertEquals(29_750L, doc.lines[2].timestampMs)
    assertEquals("Chorus line", doc.lines[2].text)
  }

  @Test
  fun plainTextWithoutTimestampsFallsBackToStaticLyrics() {
    val plain = """
      [ti:Ignored Title]
      Line one
      Line two
    """.trimIndent()

    val doc = LrcParser.parse(plain)
    assertFalse(doc.isSynced)
    assertEquals(2, doc.lines.size)
    assertEquals("Line one", doc.lines[0].text)
    assertEquals("Line two", doc.lines[1].text)
  }

  @Test
  fun enhancedWordTagsSnapToKhmerAndEmojiGraphemeBoundaries() {
    val khmerAndEmoji = "[00:12.00]<00:12.00>ស្រឡាញ់ <00:12.80>👨‍👩‍👧‍👦"
    val doc = LrcParser.parse(khmerAndEmoji)
    assertTrue(doc.isSynced)
    assertEquals(1, doc.lines.size)

    val line = doc.lines.first()
    assertEquals(2, line.words.size)

    // Mid-emoji progressive fill must never land inside a UTF-16 surrogate/ZWJ sequence
    val midEmojiReveal = line.computeRevealCharIndex(13_000L)
    assertTrue(midEmojiReveal in line.graphemeBoundaries.toList())
    // Mid-Khmer cluster progressive fill must only land on BreakIterator grapheme boundaries
    val midKhmerReveal = line.computeRevealCharIndex(12_300L)
    assertTrue(midKhmerReveal in line.graphemeBoundaries.toList())
  }
}
