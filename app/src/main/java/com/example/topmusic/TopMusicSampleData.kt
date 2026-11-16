package com.example.topmusic

/**
 * Sample chart dataset used when no live YouTube Charts backend is connected.
 * Stored in one clearly named array so the UI labels it "Sample data" and it can
 * be swapped with live chart responses seamlessly.
 */
val TOP_MUSIC_SAMPLE_DATA: List<TopMusicRawEntry> = listOf(
  // =========================================================================
  // TRENDING CHART (YouTube Trending Music)
  // =========================================================================

  // Multi-country KH + TH + VN crossover: #1 KH (20) + #3 TH (18) + #5 VN (16) = 54 * 1.50 = 81.0
  // Beats any single-country #1 (20.0) in Global!
  TopMusicRawEntry(
    id = "tr_kh_crossover_time_to_rise",
    videoId = "W1m_wJ3Y4pA",
    title = "Time To Rise (ពេលវេលាភ្ញាក់)",
    artist = "VannDa feat. Master Kong Nay",
    artworkUrl = "https://i.ytimg.com/vi/W1m_wJ3Y4pA/hqdefault.jpg",
    durationSeconds = 236L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 1, previousRank = 3),
      CountryChartRank("TH", rankToday = 3, previousRank = 7),
      CountryChartRank("VN", rankToday = 5, previousRank = 9)
    )
  ),
  // Pure Cambodia hits (never appear in Asia/Europe tabs)
  TopMusicRawEntry(
    id = "tr_kh_sneha_tae_oun",
    videoId = "0y5wX-f3E7A",
    title = "ស្នេហ៍តែអូន (Only Love You)",
    artist = "Suly Pheng",
    artworkUrl = "https://i.ytimg.com/vi/0y5wX-f3E7A/hqdefault.jpg",
    durationSeconds = 248L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 2, previousRank = 8)
    )
  ),
  TopMusicRawEntry(
    id = "tr_kh_solo_again",
    videoId = "c5p_9E2t6V0",
    title = "ភ្លេចសន្យាហើយមែនទេ",
    artist = "Tena Khimphun",
    artworkUrl = "https://i.ytimg.com/vi/c5p_9E2t6V0/hqdefault.jpg",
    durationSeconds = 224L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 3, previousRank = null)
    )
  ),
  TopMusicRawEntry(
    id = "tr_kh_phnom_penh_night",
    videoId = "jL1z0Y4xE1U",
    title = "រាត្រីភ្នំពេញ (Phnom Penh Lights)",
    artist = "G-Devith",
    artworkUrl = "https://i.ytimg.com/vi/jL1z0Y4xE1U/hqdefault.jpg",
    durationSeconds = 212L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 4, previousRank = 14)
    )
  ),
  TopMusicRawEntry(
    id = "tr_kh_khmau_sros",
    videoId = "K9dJ4vN2P0s",
    title = "កុំបោះបង់ក្តីស្រមៃ",
    artist = "Meas Soksophea",
    artworkUrl = "https://i.ytimg.com/vi/K9dJ4vN2P0s/hqdefault.jpg",
    durationSeconds = 254L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 5, previousRank = 5)
    )
  ),
  TopMusicRawEntry(
    id = "tr_kh_chnam_thmei_vibe",
    videoId = "pX7rA4lP5hY",
    title = "រាំវង់សម័យថ្មី (New Era)",
    artist = "Sovannak & Tep Boprek",
    artworkUrl = "https://i.ytimg.com/vi/pX7rA4lP5hY/hqdefault.jpg",
    durationSeconds = 205L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 6, previousRank = null)
    )
  ),

  // Multi-region Global + Asia + Europe crossover hits
  TopMusicRawEntry(
    id = "tr_global_apt",
    videoId = "ekr2nIex040",
    title = "APT.",
    artist = "ROSÉ & Bruno Mars",
    artworkUrl = "https://i.ytimg.com/vi/ekr2nIex040/hqdefault.jpg",
    durationSeconds = 173L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("KR", rankToday = 1, previousRank = 2),
      CountryChartRank("JP", rankToday = 2, previousRank = 4),
      CountryChartRank("PH", rankToday = 1, previousRank = 1),
      CountryChartRank("ID", rankToday = 2, previousRank = 5),
      CountryChartRank("GB", rankToday = 2, previousRank = 6),
      CountryChartRank("DE", rankToday = 4, previousRank = 8),
      CountryChartRank("FR", rankToday = 5, previousRank = 7)
    )
  ),
  TopMusicRawEntry(
    id = "tr_global_die_with_a_smile",
    videoId = "kPa7bsKwL-c",
    title = "Die With A Smile",
    artist = "Lady Gaga & Bruno Mars",
    artworkUrl = "https://i.ytimg.com/vi/kPa7bsKwL-c/hqdefault.jpg",
    durationSeconds = 252L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("PH", rankToday = 2, previousRank = 3),
      CountryChartRank("ID", rankToday = 1, previousRank = 2),
      CountryChartRank("GB", rankToday = 3, previousRank = 3),
      CountryChartRank("FR", rankToday = 2, previousRank = 4),
      CountryChartRank("ES", rankToday = 3, previousRank = 6)
    )
  ),
  TopMusicRawEntry(
    id = "tr_asia_bini_cherry",
    videoId = "J1g4h6S0y0M",
    title = "Cherry On Top",
    artist = "BINI",
    artworkUrl = "https://i.ytimg.com/vi/J1g4h6S0y0M/hqdefault.jpg",
    durationSeconds = 178L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("PH", rankToday = 3, previousRank = 12),
      CountryChartRank("ID", rankToday = 6, previousRank = 15),
      CountryChartRank("TH", rankToday = 9, previousRank = null)
    )
  ),
  TopMusicRawEntry(
    id = "tr_asia_jeff_satur_ghost",
    videoId = "U6q6t1zK2sM",
    title = "ซ่อน(ไม่)หา (Ghost)",
    artist = "Jeff Satur",
    artworkUrl = "https://i.ytimg.com/vi/U6q6t1zK2sM/hqdefault.jpg",
    durationSeconds = 231L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("TH", rankToday = 1, previousRank = 2),
      CountryChartRank("VN", rankToday = 4, previousRank = 10)
    )
  ),
  TopMusicRawEntry(
    id = "tr_asia_sontung_dunglamtraitim",
    videoId = "abPmZCZZrFA",
    title = "Đừng Làm Trái Tim Anh Đau",
    artist = "Sơn Tùng M-TP",
    artworkUrl = "https://i.ytimg.com/vi/abPmZCZZrFA/hqdefault.jpg",
    durationSeconds = 278L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("VN", rankToday = 1, previousRank = 1),
      CountryChartRank("TH", rankToday = 8, previousRank = null)
    )
  ),
  TopMusicRawEntry(
    id = "tr_asia_yoasobi_idol",
    videoId = "ZRtdQ81jPUQ",
    title = "アイドル (Idol)",
    artist = "YOASOBI",
    artworkUrl = "https://i.ytimg.com/vi/ZRtdQ81jPUQ/hqdefault.jpg",
    durationSeconds = 225L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("JP", rankToday = 1, previousRank = 1),
      CountryChartRank("KR", rankToday = 6, previousRank = 9)
    )
  ),
  TopMusicRawEntry(
    id = "tr_asia_babymonster_drip",
    videoId = "Zp-Jhuhq0bQ",
    title = "DRIP",
    artist = "BABYMONSTER",
    artworkUrl = "https://i.ytimg.com/vi/Zp-Jhuhq0bQ/hqdefault.jpg",
    durationSeconds = 185L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("KR", rankToday = 3, previousRank = null),
      CountryChartRank("ID", rankToday = 4, previousRank = 11),
      CountryChartRank("TH", rankToday = 5, previousRank = 13)
    )
  ),
  TopMusicRawEntry(
    id = "tr_eu_central_cee_band4band",
    videoId = "pDddlvHGmrw",
    title = "BAND4BAND",
    artist = "Central Cee & Lil Baby",
    artworkUrl = "https://i.ytimg.com/vi/pDddlvHGmrw/hqdefault.jpg",
    durationSeconds = 148L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("GB", rankToday = 1, previousRank = 4),
      CountryChartRank("DE", rankToday = 3, previousRank = 7),
      CountryChartRank("FR", rankToday = 6, previousRank = 12)
    )
  ),
  TopMusicRawEntry(
    id = "tr_eu_indila_aitana_si_no_estas",
    videoId = "h7V9Q5B8m2s",
    title = "4TO 23",
    artist = "Aitana",
    artworkUrl = "https://i.ytimg.com/vi/h7V9Q5B8m2s/hqdefault.jpg",
    durationSeconds = 192L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("ES", rankToday = 1, previousRank = null),
      CountryChartRank("FR", rankToday = 4, previousRank = 9)
    )
  ),
  TopMusicRawEntry(
    id = "tr_eu_semicenk_gozlerime",
    videoId = "l9Y0s3R4u1w",
    title = "Gözlerime Bak",
    artist = "Semicenk",
    artworkUrl = "https://i.ytimg.com/vi/l9Y0s3R4u1w/hqdefault.jpg",
    durationSeconds = 204L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("TR", rankToday = 1, previousRank = 5),
      CountryChartRank("DE", rankToday = 6, previousRank = 14)
    )
  ),
  TopMusicRawEntry(
    id = "tr_eu_apache_berlin",
    videoId = "x6P2w5nL1q8",
    title = "Miami",
    artist = "Apache 207",
    artworkUrl = "https://i.ytimg.com/vi/x6P2w5nL1q8/hqdefault.jpg",
    durationSeconds = 189L,
    chartType = TopMusicChartType.TRENDING,
    countryRanks = listOf(
      CountryChartRank("DE", rankToday = 1, previousRank = 1),
      CountryChartRank("FR", rankToday = 8, previousRank = null),
      CountryChartRank("TR", rankToday = 7, previousRank = 11)
    )
  ),

  // =========================================================================
  // WEEKLY TOP SONGS CHART (Smoothed average rank)
  // =========================================================================
  TopMusicRawEntry(
    id = "ws_kh_time_to_rise",
    videoId = "W1m_wJ3Y4pA",
    title = "Time To Rise (ពេលវេលាភ្ញាក់)",
    artist = "VannDa feat. Master Kong Nay",
    artworkUrl = "https://i.ytimg.com/vi/W1m_wJ3Y4pA/hqdefault.jpg",
    durationSeconds = 236L,
    chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 1, previousRank = 1),
      CountryChartRank("TH", rankToday = 4, previousRank = 6)
    )
  ),
  TopMusicRawEntry(
    id = "ws_kh_suly_chill_night",
    videoId = "0y5wX-f3E7A",
    title = "ស្នេហ៍តែអូន (Only Love You)",
    artist = "Suly Pheng",
    artworkUrl = "https://i.ytimg.com/vi/0y5wX-f3E7A/hqdefault.jpg",
    durationSeconds = 248L,
    chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 2, previousRank = 2)
    )
  ),
  TopMusicRawEntry(
    id = "ws_kh_tena_memories",
    videoId = "c5p_9E2t6V0",
    title = "ភ្លេចសន្យាហើយមែនទេ",
    artist = "Tena Khimphun",
    artworkUrl = "https://i.ytimg.com/vi/c5p_9E2t6V0/hqdefault.jpg",
    durationSeconds = 224L,
    chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 3, previousRank = 4)
    )
  ),
  TopMusicRawEntry(
    id = "ws_kh_meas_dream",
    videoId = "K9dJ4vN2P0s",
    title = "កុំបោះបង់ក្តីស្រមៃ",
    artist = "Meas Soksophea",
    artworkUrl = "https://i.ytimg.com/vi/K9dJ4vN2P0s/hqdefault.jpg",
    durationSeconds = 254L,
    chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 5, previousRank = 3)
    )
  ),
  TopMusicRawEntry(
    id = "ws_global_apt",
    videoId = "ekr2nIex040",
    title = "APT.",
    artist = "ROSÉ & Bruno Mars",
    artworkUrl = "https://i.ytimg.com/vi/ekr2nIex040/hqdefault.jpg",
    durationSeconds = 173L,
    chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
    countryRanks = listOf(
      CountryChartRank("KR", rankToday = 1, previousRank = 1),
      CountryChartRank("JP", rankToday = 1, previousRank = 2),
      CountryChartRank("PH", rankToday = 1, previousRank = 2),
      CountryChartRank("TH", rankToday = 2, previousRank = 3),
      CountryChartRank("GB", rankToday = 2, previousRank = 3),
      CountryChartRank("DE", rankToday = 3, previousRank = 5)
    )
  ),
  TopMusicRawEntry(
    id = "ws_global_birds_of_a_feather",
    videoId = "V9PVRfjEBTI",
    title = "BIRDS OF A FEATHER",
    artist = "Billie Eilish",
    artworkUrl = "https://i.ytimg.com/vi/V9PVRfjEBTI/hqdefault.jpg",
    durationSeconds = 211L,
    chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
    countryRanks = listOf(
      CountryChartRank("GB", rankToday = 1, previousRank = 1),
      CountryChartRank("DE", rankToday = 2, previousRank = 2),
      CountryChartRank("FR", rankToday = 2, previousRank = 3),
      CountryChartRank("ES", rankToday = 4, previousRank = 5),
      CountryChartRank("PH", rankToday = 4, previousRank = 4)
    )
  ),
  TopMusicRawEntry(
    id = "ws_asia_jeff_ghost",
    videoId = "U6q6t1zK2sM",
    title = "ซ่อน(ไม่)หา (Ghost)",
    artist = "Jeff Satur",
    artworkUrl = "https://i.ytimg.com/vi/U6q6t1zK2sM/hqdefault.jpg",
    durationSeconds = 231L,
    chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
    countryRanks = listOf(
      CountryChartRank("TH", rankToday = 1, previousRank = 2),
      CountryChartRank("VN", rankToday = 3, previousRank = 4),
      CountryChartRank("ID", rankToday = 5, previousRank = 7)
    )
  ),
  TopMusicRawEntry(
    id = "ws_eu_gims_spider",
    videoId = "n9V4r5G2d1k",
    title = "SPIDER",
    artist = "GIMS & DYSTINCT",
    artworkUrl = "https://i.ytimg.com/vi/n9V4r5G2d1k/hqdefault.jpg",
    durationSeconds = 197L,
    chartType = TopMusicChartType.WEEKLY_TOP_SONGS,
    countryRanks = listOf(
      CountryChartRank("FR", rankToday = 1, previousRank = 1),
      CountryChartRank("ES", rankToday = 3, previousRank = 4),
      CountryChartRank("TR", rankToday = 4, previousRank = 5)
    )
  ),

  // =========================================================================
  // DAILY TOP MUSIC VIDEOS CHART (Best single-country rank, then score)
  // =========================================================================
  TopMusicRawEntry(
    id = "mv_kh_vannda_time_to_rise",
    videoId = "W1m_wJ3Y4pA",
    title = "Time To Rise (Official MV)",
    artist = "VannDa feat. Master Kong Nay",
    artworkUrl = "https://i.ytimg.com/vi/W1m_wJ3Y4pA/hqdefault.jpg",
    durationSeconds = 258L,
    chartType = TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 1, previousRank = 1),
      CountryChartRank("TH", rankToday = 5, previousRank = 8)
    )
  ),
  TopMusicRawEntry(
    id = "mv_kh_gdevith_phnom_penh",
    videoId = "jL1z0Y4xE1U",
    title = "រាត្រីភ្នំពេញ (Official Video)",
    artist = "G-Devith",
    artworkUrl = "https://i.ytimg.com/vi/jL1z0Y4xE1U/hqdefault.jpg",
    durationSeconds = 228L,
    chartType = TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 2, previousRank = 5)
    )
  ),
  TopMusicRawEntry(
    id = "mv_kh_suly_moonlight",
    videoId = "0y5wX-f3E7A",
    title = "ស្នេហ៍តែអូន (Music Video)",
    artist = "Suly Pheng",
    artworkUrl = "https://i.ytimg.com/vi/0y5wX-f3E7A/hqdefault.jpg",
    durationSeconds = 264L,
    chartType = TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS,
    countryRanks = listOf(
      CountryChartRank("KH", rankToday = 3, previousRank = null)
    )
  ),
  TopMusicRawEntry(
    id = "mv_global_apt",
    videoId = "ekr2nIex040",
    title = "APT. (Official Music Video)",
    artist = "ROSÉ & Bruno Mars",
    artworkUrl = "https://i.ytimg.com/vi/ekr2nIex040/hqdefault.jpg",
    durationSeconds = 178L,
    chartType = TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS,
    countryRanks = listOf(
      CountryChartRank("KR", rankToday = 1, previousRank = 1),
      CountryChartRank("JP", rankToday = 1, previousRank = 2),
      CountryChartRank("ID", rankToday = 2, previousRank = 3),
      CountryChartRank("GB", rankToday = 2, previousRank = 4)
    )
  ),
  TopMusicRawEntry(
    id = "mv_asia_sontung_video",
    videoId = "abPmZCZZrFA",
    title = "Đừng Làm Trái Tim Anh Đau (MV)",
    artist = "Sơn Tùng M-TP",
    artworkUrl = "https://i.ytimg.com/vi/abPmZCZZrFA/hqdefault.jpg",
    durationSeconds = 304L,
    chartType = TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS,
    countryRanks = listOf(
      CountryChartRank("VN", rankToday = 1, previousRank = 2),
      CountryChartRank("TH", rankToday = 6, previousRank = 9)
    )
  ),
  TopMusicRawEntry(
    id = "mv_eu_lady_gaga_smile",
    videoId = "kPa7bsKwL-c",
    title = "Die With A Smile (Official MV)",
    artist = "Lady Gaga & Bruno Mars",
    artworkUrl = "https://i.ytimg.com/vi/kPa7bsKwL-c/hqdefault.jpg",
    durationSeconds = 256L,
    chartType = TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS,
    countryRanks = listOf(
      CountryChartRank("GB", rankToday = 1, previousRank = 2),
      CountryChartRank("FR", rankToday = 1, previousRank = 2),
      CountryChartRank("DE", rankToday = 2, previousRank = 3),
      CountryChartRank("ES", rankToday = 2, previousRank = 4)
    )
  ),
  TopMusicRawEntry(
    id = "mv_eu_semicenk_video",
    videoId = "l9Y0s3R4u1w",
    title = "Gözlerime Bak (Klip)",
    artist = "Semicenk",
    artworkUrl = "https://i.ytimg.com/vi/l9Y0s3R4u1w/hqdefault.jpg",
    durationSeconds = 210L,
    chartType = TopMusicChartType.DAILY_TOP_MUSIC_VIDEOS,
    countryRanks = listOf(
      CountryChartRank("TR", rankToday = 1, previousRank = 3),
      CountryChartRank("DE", rankToday = 5, previousRank = 10)
    )
  )
)
