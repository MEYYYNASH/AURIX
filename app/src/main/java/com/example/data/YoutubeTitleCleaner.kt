package com.example.data

object YoutubeTitleCleaner {

  private val BRACKETED_NOISE_REGEX = Regex(
    """[\(\[]\s*(?:official\s*(?:music\s*|lyric\s*|hd\s*|4k\s*)?(?:video|audio|lyric\s*video|visualizer|mv|m/v|version|clip)?|lyric(?:s|\s*video)?|audio\s*(?:only)?|full\s*(?:audio|song|album|video)|m/?v|hd|4k(?:\s*remaster(?:ed)?)?|hq|remaster(?:ed)?(?:\s*\d{4})?|explicit|clean(?:\s*version)?|visualizer|prod\.?\s*by\s+[^)\]]+)\s*[\)\]]""",
    RegexOption.IGNORE_CASE
  )

  private val TRAILING_UNCLOSED_NOISE_REGEX = Regex(
    """[\(\[]\s*(?:official|lyric|audio|mv|m/v|visualizer|hd|4k)[^\)\]]*$""",
    RegexOption.IGNORE_CASE
  )

  private val TRAILING_PIPE_REGEX = Regex("""\s*\|\s*[^|]+$""")
  private val MULTI_SPACE_REGEX = Regex("""\s+""")
  private val TRAILING_NOISE_WORDS_REGEX = Regex(
    """(?:\s*[-–—]\s*|\s+)(?:official\s*(?:music\s*)?(?:video|audio|lyric\s*video|visualizer)|lyric\s*video|audio\s*only)$""",
    RegexOption.IGNORE_CASE
  )

  fun cleanChannelAuthor(rawAuthor: String): String {
    return rawAuthor
      .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""\s*VEVO$""", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""\s+Official$""", RegexOption.IGNORE_CASE), "")
      .replace(MULTI_SPACE_REGEX, " ")
      .trim()
  }

  fun stripNoiseTags(rawTitle: String, enabled: Boolean = true): String {
    val trimmed = rawTitle.trim()
    if (!enabled || trimmed.isEmpty()) return trimmed

    var cleaned = trimmed
      .replace(BRACKETED_NOISE_REGEX, " ")
      .replace(TRAILING_UNCLOSED_NOISE_REGEX, " ")
      .replace(TRAILING_PIPE_REGEX, "")
      .replace(TRAILING_NOISE_WORDS_REGEX, "")
      .replace(MULTI_SPACE_REGEX, " ")
      .trim()

    cleaned = cleaned.trimEnd('-', '–', '—', '|', ' ').trimStart('-', '–', '—', '|', ' ').trim()
    return cleaned.ifBlank { trimmed }
  }

  fun parseTitleAndArtist(
    rawTitle: String,
    rawAuthor: String,
    cleanEnabled: Boolean = true
  ): Pair<String, String> {
    val cleanedNoise = stripNoiseTags(rawTitle, enabled = cleanEnabled)
    val channelArtist = cleanChannelAuthor(rawAuthor)
    val separators = listOf(" - ", " – ", " — ")
    for (sep in separators) {
      val idx = cleanedNoise.indexOf(sep)
      if (idx > 0 && idx + sep.length < cleanedNoise.length) {
        val left = cleanedNoise.substring(0, idx).trim()
        val right = cleanedNoise.substring(idx + sep.length).trim()
        if (left.isNotBlank() && right.isNotBlank()) {
          return right to left
        }
      }
    }
    return cleanedNoise.ifBlank { rawTitle.trim() } to channelArtist
  }
}
