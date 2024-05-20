package ru.push.musicfeed.platform.external.source.ytdlp

import kotlin.time.Duration

data class TrackSearchResult(
    val items: List<TrackInfo>
)

data class TrackInfo(
    val title: String,
    val artistNames: List<String>,
    val url: String,
    val duration: Duration
)
